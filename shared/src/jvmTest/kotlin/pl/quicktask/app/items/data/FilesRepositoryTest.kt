package pl.quicktask.app.items.data

import pl.quicktask.app.items.support.testJson
import pl.quicktask.app.items.support.jsonHeaders
import pl.quicktask.app.items.support.RecordingDPoP
import pl.quicktask.app.items.support.mockClient

import pl.quicktask.app.items.di.ItemModule
import pl.quicktask.app.items.domain.UserKeysProvider
import pl.quicktask.app.items.model.FileIntegrityException
import pl.quicktask.app.items.model.FileMetadataDto
import pl.quicktask.app.items.model.StoredFileDto

import pl.quicktask.app.testing.TestSettings
import pl.quicktask.app.trash.model.*

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import pl.quicktask.app.auth.crypto.createItemKey
import pl.quicktask.app.auth.crypto.encryptText
import pl.quicktask.app.auth.crypto.generateUserKeyPair
import pl.quicktask.app.auth.crypto.wrapItemKey
import kotlin.test.Test
import kotlin.test.assertFailsWith

class FilesRepositoryTest {
    @Test
    fun checksumMismatchUsesTypedIntegrityError(): Unit = runBlocking {
        val keys = generateUserKeyPair()
        val fileKey = createItemKey()
        val metadata = encryptText(
            fileKey,
            testJson.encodeToString(FileMetadataDto.serializer(), FileMetadataDto("plik.txt", "text/plain")),
        )
        val stored = StoredFileDto(
            fileId = "file-1",
            encryptedMetadata = metadata,
            encryptedFileKey = wrapItemKey(keys.publicKey, fileKey),
            ciphertextSha256 = "expected-but-wrong",
        )
        var request = 0
        val client = mockClient(MockEngine {
            request++
            if (request == 1) {
                respond(testJson.encodeToString(StoredFileDto.serializer(), stored), HttpStatusCode.OK, jsonHeaders)
            } else {
                respond(byteArrayOf(1, 2, 3), HttpStatusCode.OK, headersOf())
            }
        })
        try {
            val module = ItemModule(
                httpClient = client, sessionRefresher = pl.quicktask.app.auth.data.SessionRefresher { error("No refresh expected") },
                sessionManager = pl.quicktask.app.auth.session.SessionManager(TestSettings()).apply { accessToken = "token" },
                dPoPManager = RecordingDPoP(),
                baseUrl = "https://example.test",
                keysProvider = UserKeysProvider { keys },
            )
            assertFailsWith<FileIntegrityException> { module.files.downloadEncryptedFile("file-1").getOrThrow() }
        } finally { client.close() }
    }
}
