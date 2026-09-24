package pl.quicktask.app.contacts

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.quicktask.app.auth.crypto.*
import pl.quicktask.app.items.support.*
import pl.quicktask.app.projects.model.CreateWaitingTaskRequestDto
import pl.quicktask.app.projects.model.ConvertInboxToWaitingRequestDto
import kotlin.test.*

class ContactsRepositoryTest {
    @Test
    fun invitationEncryptsEachContactsIdentityForItsViewer() = runBlocking {
        val fixture = encryptedFixture()
        val recipient = generateUserKeyPair()
        var invitationBody: String? = null
        val client = mockClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/contacts/profile" -> respond(
                    """{"id":"alice","email":"alice@example.com"}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
                "/contacts/lookup" -> respond(
                    """{"userId":"bob","publicKey":"${recipient.publicKeySpki.toBase64()}"}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
                "/contacts/invitations" -> {
                    invitationBody = (request.body as TextContent).text
                    respond("[]", HttpStatusCode.OK, jsonHeaders)
                }
                else -> respond("{}", HttpStatusCode.NotFound, jsonHeaders)
            }
        })
        try {
            fixture.module(client).contacts.invite("Bob@Example.com").getOrThrow()
            val body = testJson.parseToJsonElement(assertNotNull(invitationBody)).jsonObject
            val requester = body.getValue("requester").jsonObject
            val requesterKey = unwrapItemKey(
                fixture.keys.getUserKeys().privateKey,
                requester.getValue("encryptedItemKey").jsonPrimitive.content,
            )
            assertContains(
                decryptText(requesterKey, requester.getValue("encryptedData").jsonPrimitive.content),
                "bob@example.com",
            )
            val recipientEnvelope = body.getValue("recipient").jsonObject
            val recipientKey = unwrapItemKey(
                recipient.privateKey,
                recipientEnvelope.getValue("encryptedItemKey").jsonPrimitive.content,
            )
            val recipientPayload = decryptText(
                recipientKey,
                recipientEnvelope.getValue("encryptedData").jsonPrimitive.content,
            )
            assertContains(recipientPayload, "alice@example.com")
            assertFalse(recipientPayload.contains("Alice"))
        } finally { client.close() }
    }

    @Test
    fun acceptedContactNameIsEncryptedForTheOwnerOnly() = runBlocking {
        val fixture = encryptedFixture()
        var updateBody: String? = null
        val client = mockClient(MockEngine { request ->
            if (request.url.encodedPath == "/contacts/contact-id" && request.method == io.ktor.http.HttpMethod.Patch) {
                updateBody = (request.body as TextContent).text
                respond("{}", HttpStatusCode.OK, jsonHeaders)
            } else respond("{}", HttpStatusCode.NotFound, jsonHeaders)
        })
        try {
            val contact = Contact("contact-id", "bob", "bob@example.com", status = "ACCEPTED")
            val updated = fixture.module(client).contacts.saveContactName(contact, "  Bob  ").getOrThrow()
            assertEquals("Bob", updated.displayName)
            val envelope = testJson.parseToJsonElement(assertNotNull(updateBody)).jsonObject
            val key = unwrapItemKey(
                fixture.keys.getUserKeys().privateKey,
                envelope.getValue("encryptedItemKey").jsonPrimitive.content,
            )
            val payload = decryptText(key, envelope.getValue("encryptedData").jsonPrimitive.content)
            assertContains(payload, "bob@example.com")
            assertContains(payload, "Bob")
            assertFalse(assertNotNull(updateBody).contains("Bob"))
        } finally { client.close() }
    }

    @Test
    fun newTaskCanBeDecryptedByOwnerAndRecipientAndRefreshFailureDoesNotFailWrite() = runBlocking {
        val fixture = encryptedFixture()
        val recipient = generateUserKeyPair()
        var draft: CreateWaitingTaskRequestDto? = null
        val client = mockClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/contacts/users/bob/key" -> respond("""{"publicKey":"${recipient.publicKeySpki.toBase64()}"}""", HttpStatusCode.OK, jsonHeaders)
                "/items" -> {
                    draft = testJson.decodeFromString<CreateWaitingTaskRequestDto>((request.body as TextContent).text)
                    respond("{}", HttpStatusCode.Created, jsonHeaders)
                }
                else -> respond("{}", HttpStatusCode.ServiceUnavailable, jsonHeaders)
            }
        })
        try {
            fixture.module(client).contacts.delegate(Contact("contact", "bob", "bob@example.com", "Bob", "ACCEPTED"), "Secret title", "Private note", null, null, null).getOrThrow()
            val sent = assertNotNull(draft)
            val recipientKey = unwrapItemKey(recipient.privateKey, assertNotNull(sent.recipientEncryptedItemKey))
            val ownerKey = unwrapItemKey(fixture.keys.getUserKeys().privateKey, sent.encryptedItemKey)
            assertEquals("Secret title", decryptText(recipientKey, sent.encryptedTitle))
            assertEquals("Private note", decryptText(ownerKey, assertNotNull(sent.encryptedNote)))
            assertEquals("bob@example.com", decryptText(ownerKey, sent.waitingFor))
            assertEquals("bob", sent.assignedToUserId)
            assertNull(sent.fileIds)
        } finally { client.close() }
    }

    @Test
    fun inboxConversionSharesExistingKeyAndNoAttachmentKeys() = runBlocking {
        val fixture = encryptedFixture()
        val recipient = generateUserKeyPair()
        var draft: ConvertInboxToWaitingRequestDto? = null
        val client = mockClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/contacts/users/bob/key" -> respond("""{"publicKey":"${recipient.publicKeySpki.toBase64()}"}""", HttpStatusCode.OK, jsonHeaders)
                "/inbox/item-1/waiting" -> {
                    draft = testJson.decodeFromString<ConvertInboxToWaitingRequestDto>((request.body as TextContent).text)
                    respond("{}", HttpStatusCode.OK, jsonHeaders)
                }
                else -> respond("{}", HttpStatusCode.ServiceUnavailable, jsonHeaders)
            }
        })
        try {
            val item = fixture.mapper.inbox(fixture.inbox)
            fixture.module(client).contacts.delegate(Contact("contact", "bob", "bob@example.com", status = "ACCEPTED"), item.title, item.note, null, null, null, item).getOrThrow()
            val key = unwrapItemKey(recipient.privateKey, assertNotNull(draft?.recipientEncryptedItemKey))
            assertEquals(item.title, decryptText(key, fixture.inbox.encryptedTitle))
            assertEquals("bob@example.com", decryptText(key, assertNotNull(draft).waitingFor))
            assertEquals("bob", draft.assignedToUserId)
        } finally { client.close() }
    }

    @Test
    fun rejectedContactKeyRequestNeverCreatesTask() = runBlocking {
        val fixture = encryptedFixture()
        val requests = mutableListOf<String>()
        val client = mockClient(MockEngine { request ->
            requests.add(request.url.encodedPath)
            respond("{}", HttpStatusCode.Forbidden, jsonHeaders)
        })
        try {
            assertTrue(fixture.module(client).contacts.delegate(Contact("contact", "bob", "bob@example.com", status = "ACCEPTED"), "Title", "", null, null, null).isFailure)
            assertEquals(listOf("/contacts/users/bob/key"), requests)
        } finally { client.close() }
    }
}
