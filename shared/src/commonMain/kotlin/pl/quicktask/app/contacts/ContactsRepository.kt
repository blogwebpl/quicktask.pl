package pl.quicktask.app.contacts

import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.http.*
import kotlinx.serialization.Serializable
import pl.quicktask.app.auth.crypto.*
import pl.quicktask.app.items.domain.ItemCryptoMapper
import pl.quicktask.app.items.domain.ItemViewRefresher
import pl.quicktask.app.items.domain.UserKeysProvider
import pl.quicktask.app.items.model.itemResult
import pl.quicktask.app.items.model.InboxItem
import pl.quicktask.app.network.client.AuthenticatedApiClient
import pl.quicktask.app.projects.model.*

data class Contact(
    val id: String,
    val userId: String,
    val email: String,
    val displayName: String? = null,
    val status: String,
    internal val publicKey: String? = null,
) {
    val label: String get() = displayName?.takeIf { it.isNotBlank() }?.let { "$it ($email)" } ?: email
}
@Serializable
private data class ProfileDto(
    val id: String,
    val email: String,
)
@Serializable
private data class PublicKey(val publicKey: String, val userId: String? = null)
@Serializable
private data class ContactPayload(val email: String, val displayName: String? = null)
@Serializable
private data class EncryptedContactDto(
    val id: String,
    val userId: String,
    val encryptedData: String,
    val encryptedItemKey: String,
    val publicKey: String,
    val status: String,
)
@Serializable
private data class EncryptedContactEnvelope(val encryptedData: String, val encryptedItemKey: String)
@Serializable
private data class InviteContactRequest(
    val email: String,
    val requester: EncryptedContactEnvelope,
    val recipient: EncryptedContactEnvelope,
)
class ContactsRepository(
    private val api: AuthenticatedApiClient,
    private val mapper: ItemCryptoMapper,
    private val refresher: ItemViewRefresher,
    private val keysProvider: UserKeysProvider,
) {
    private suspend fun decrypt(dto: EncryptedContactDto): Contact {
        val key = unwrapItemKey(keysProvider.getUserKeys().privateKey, dto.encryptedItemKey)
        val payload = kotlinx.serialization.json.Json.decodeFromString<ContactPayload>(decryptText(key, dto.encryptedData))
        return Contact(dto.id, dto.userId, payload.email, payload.displayName, dto.status, dto.publicKey)
    }

    private suspend fun envelope(publicKeyBase64: String, payload: ContactPayload): EncryptedContactEnvelope {
        val publicKey = getCryptographyProvider().get(ECDH).publicKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PublicKey.Format.DER, publicKeyBase64.fromBase64())
        val key = createItemKey()
        return EncryptedContactEnvelope(
            encryptedData = encryptText(key, kotlinx.serialization.json.Json.encodeToString(ContactPayload.serializer(), payload)),
            encryptedItemKey = wrapItemKey(publicKey, key),
        )
    }

    private suspend fun decodeContacts(response: List<EncryptedContactDto>) = response.map { decrypt(it) }

    suspend fun list() = itemResult {
        decodeContacts(api.request(HttpMethod.Get, "contacts").body<List<EncryptedContactDto>>())
    }
    private suspend fun profileValue() = api.request(HttpMethod.Get, "contacts/profile").body<ProfileDto>()
    suspend fun saveContactName(contact: Contact, name: String) = itemResult {
        require(contact.status == "ACCEPTED")
        val ownKeys = keysProvider.getUserKeys()
        val encrypted = envelope(
            ownKeys.publicKeySpki.toBase64(),
            ContactPayload(contact.email, name.trim().ifBlank { null }),
        )
        api.request(HttpMethod.Patch, "contacts/${contact.id}") {
            contentType(ContentType.Application.Json)
            setBody(encrypted)
        }
        contact.copy(displayName = name.trim().ifBlank { null })
    }
    suspend fun invite(email: String) = itemResult {
        val normalizedEmail = email.trim().lowercase()
        val ownProfile = profileValue()
        val recipientKey = api.request(HttpMethod.Post, "contacts/lookup") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("email" to normalizedEmail))
        }.body<PublicKey>()
        val ownKeys = keysProvider.getUserKeys()
        val request = InviteContactRequest(
            email = normalizedEmail,
            requester = envelope(ownKeys.publicKeySpki.toBase64(), ContactPayload(normalizedEmail)),
            recipient = envelope(recipientKey.publicKey, ContactPayload(ownProfile.email)),
        )
        decodeContacts(api.request(HttpMethod.Post, "contacts/invitations") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<List<EncryptedContactDto>>())
    }
    suspend fun respond(id: String, accept: Boolean) = itemResult {
        val response = if (accept) {
            val contact = list().getOrThrow().first { it.id == id }
            val ownProfile = profileValue()
            val encrypted = envelope(requireNotNull(contact.publicKey), ContactPayload(ownProfile.email))
            api.request(HttpMethod.Post, "contacts/$id/accept") {
                contentType(ContentType.Application.Json)
                setBody(encrypted)
            }
        } else {
            api.request(HttpMethod.Post, "contacts/$id/reject")
        }
        decodeContacts(response.body<List<EncryptedContactDto>>())
    }
    suspend fun tasks() = itemResult {
        val contactsByUser = list().getOrThrow().associateBy { it.userId }
        api.request(HttpMethod.Get, "contacts/tasks").body<List<ProjectTaskDto>>().map { dto ->
            mapper.projectTask(dto).copy(waitingFor = dto.assignedByUserId?.let(contactsByUser::get)?.label)
        }
    }
    suspend fun complete(id: String) = itemResult { api.request(HttpMethod.Post, "contacts/tasks/$id/complete"); Unit }
    suspend fun delegate(contact: Contact, title: String, note: String, projectId: String?, dueAt: String?, followUpAt: String?, inboxItem: InboxItem? = null) = itemResult {
        val material = api.request(HttpMethod.Get, "contacts/users/${contact.userId}/key").body<PublicKey>()
        val publicKey = getCryptographyProvider().get(ECDH).publicKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PublicKey.Format.DER, material.publicKey.fromBase64())
        val key = inboxItem?.itemKey ?: createItemKey()
        val wrapped = wrapItemKey(publicKey, key)
        if (inboxItem == null) {
            val request = mapper.createWaitingTaskRequest(title, note, projectId, dueAt, contact.email, followUpAt, itemKey = key)
                .copy(assignedToUserId = contact.userId, recipientEncryptedItemKey = wrapped)
            api.request(HttpMethod.Post, "items") { contentType(ContentType.Application.Json); setBody(request) }
        } else {
            val request = ConvertInboxToWaitingRequestDto(projectId = projectId, dueAt = dueAt, waitingFor = encryptText(inboxItem.itemKey, contact.email), followUpAt = followUpAt,
                assignedToUserId = contact.userId, recipientEncryptedItemKey = wrapped)
            api.request(HttpMethod.Post, "inbox/${inboxItem.itemId}/waiting") { contentType(ContentType.Application.Json); setBody(request) }
        }
        // The write has succeeded; a failed refresh must not encourage duplicate delegation.
        itemResult { refresher.refreshViews(inbox = true) }
        Unit
    }
}
