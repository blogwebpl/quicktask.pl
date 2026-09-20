package pl.quicktask.app.items.support

import pl.quicktask.app.items.data.FileOperations
import pl.quicktask.app.items.di.ItemModule
import pl.quicktask.app.items.domain.ItemCryptoMapper
import pl.quicktask.app.items.domain.UserKeysProvider
import pl.quicktask.app.items.model.CompletedInTwoMinutesItemDto
import pl.quicktask.app.items.model.DecryptedFile
import pl.quicktask.app.items.model.FileMetadataDto
import pl.quicktask.app.items.model.InboxAttachmentDto
import pl.quicktask.app.items.model.InboxItemDto
import pl.quicktask.app.items.model.InboxTagDto
import pl.quicktask.app.items.model.SyncStateItemDto

import pl.quicktask.app.testing.TestSettings
import pl.quicktask.app.trash.model.*

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import pl.quicktask.app.auth.crypto.DPoPManager
import pl.quicktask.app.auth.session.SessionManager
import pl.quicktask.app.auth.crypto.createItemKey
import pl.quicktask.app.auth.crypto.encryptText
import pl.quicktask.app.auth.crypto.generateUserKeyPair
import pl.quicktask.app.auth.crypto.wrapItemKey

internal val testJson = Json { ignoreUnknownKeys = true }
internal val jsonHeaders = headersOf("Content-Type", ContentType.Application.Json.toString())

internal class RecordingDPoP : DPoPManager {
    val calls = mutableListOf<Triple<String, String, String?>>()
    override suspend fun generateDPoPProof(method: String, url: String, accessToken: String?): String {
        calls.add(Triple(method, url, accessToken))
        return "proof-${calls.size}"
    }
    override suspend fun clearKeyPair() {}
}

internal fun mockClient(engine: MockEngine) = HttpClient(engine) {
    install(ContentNegotiation) { json(testJson) }
    install(HttpTimeout)
}

internal suspend fun encryptedFixture(): ItemFixture {
    val keys = generateUserKeyPair()
    val itemKey = createItemKey()
    val fileKey = createItemKey()
    val attachment = InboxAttachmentDto(
        "attachment-1", "file-1", "2026-09-01",
        encryptText(fileKey, testJson.encodeToString(FileMetadataDto.serializer(), FileMetadataDto("dokument.pdf", "application/pdf"))),
        wrapItemKey(keys.publicKey, fileKey), "checksum", 120,
    )
    val dto = InboxItemDto(
        "item-1", encryptText(itemKey, "Tytuł użytkownika"), encryptText(itemKey, "Notatka"),
        wrapItemKey(keys.publicKey, itemKey), "2026-09-01", "2026-09-02",
        attachments = listOf(attachment), tags = listOf(InboxTagDto("tag-1", "@home")),
    )
    return ItemFixture(UserKeysProvider { keys }, dto)
}

internal data class ItemFixture(val keys: UserKeysProvider, val inbox: InboxItemDto) {
    val mapper get() = ItemCryptoMapper(keys)
    val trash get() = TrashItemDto(
        inbox.itemId, inbox.encryptedTitle, inbox.encryptedNote, inbox.encryptedItemKey,
        inbox.createdAt, inbox.updatedAt, "2026-09-03", "2026-10-03",
        isReference = true, attachments = inbox.attachments, tags = inbox.tags,
    )
    val completed get() = CompletedInTwoMinutesItemDto(
        inbox.itemId, inbox.encryptedTitle, inbox.encryptedNote, inbox.encryptedItemKey,
        inbox.createdAt, inbox.updatedAt, "2026-09-03", inbox.attachments, inbox.tags,
    )
    val sync get() = SyncStateItemDto(
        inbox.itemId, inbox.encryptedTitle, inbox.encryptedNote, inbox.encryptedItemKey,
        inbox.createdAt, inbox.updatedAt, inbox.attachments, inbox.tags,
        purgeAfter = "2026-10-03", isReference = true,
    )
    fun module(client: HttpClient, files: FileOperations? = null) = ItemModule(
        httpClient = client, sessionRefresher = pl.quicktask.app.auth.data.SessionRefresher { error("No refresh expected") }, sessionManager = SessionManager(TestSettings()).apply { accessToken = "token" },
        dPoPManager = RecordingDPoP(), baseUrl = "https://example.test/", keysProvider = keys,
        fileOperations = files,
    )
}

internal class RecordingFiles : FileOperations {
    val uploaded = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var failureAt: Int? = null
    var uploadFailure: Exception = IllegalStateException("upload failed")
    override suspend fun uploadEncryptedFile(
        fileName: String, mimeType: String, fileBytes: ByteArray, onProgress: ((Float) -> Unit)?,
    ): Result<String> {
        if (uploaded.size == failureAt) throw uploadFailure
        uploaded.add(fileName)
        onProgress?.invoke(0.5f)
        onProgress?.invoke(1f)
        return Result.success("uploaded-${uploaded.size}")
    }
    override suspend fun deleteUploadedFile(fileId: String): Result<Unit> {
        deleted.add(fileId)
        return Result.success(Unit)
    }
    override suspend fun downloadEncryptedFile(fileId: String): Result<DecryptedFile> = error("Unused in this test")
}
