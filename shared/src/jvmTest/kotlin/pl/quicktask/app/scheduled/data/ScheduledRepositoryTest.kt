package pl.quicktask.app.scheduled.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.quicktask.app.auth.crypto.DefaultDPoPManager
import pl.quicktask.app.auth.crypto.decryptText
import pl.quicktask.app.auth.crypto.createItemKey
import pl.quicktask.app.auth.crypto.generateUserKeyPair
import pl.quicktask.app.auth.data.SessionRefresher
import pl.quicktask.app.auth.session.SessionManager
import pl.quicktask.app.items.di.ItemModule
import pl.quicktask.app.items.data.FileOperations
import pl.quicktask.app.items.domain.UserKeysProvider
import pl.quicktask.app.items.model.DecryptedAttachment
import pl.quicktask.app.items.model.DecryptedFile
import pl.quicktask.app.items.model.InboxItem
import pl.quicktask.app.items.model.InputFile
import pl.quicktask.app.scheduled.model.*
import pl.quicktask.app.testing.TestSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScheduledRepositoryTest {
    private val repeatRule = RecurrenceRule(RecurrenceFrequency.WEEKLY, 2, RecurrenceMode.SCHEDULED)

    @Test
    fun createScheduledTaskSendsCompleteRequestAndReturnsCreatedId(): Unit = runBlocking {
        var requestBody: String? = null
        val paths = mutableListOf<String>()
        val fixture = fixture { request ->
            paths += request.url.encodedPath
            when (request.url.encodedPath) {
                "/items" -> {
                    assertEquals("POST", request.method.value)
                    assertEquals(ContentType.Application.Json, request.body.contentType)
                    requestBody = (request.body as TextContent).text
                    respond(
                        """{"itemId":"item-123","createdAt":"2026-09-20T10:00:00Z"}""",
                        HttpStatusCode.Created,
                        jsonHeaders,
                    )
                }
                "/inbox/trash" -> respond("[]", HttpStatusCode.OK, jsonHeaders)
                else -> error("Unexpected URL: ${request.url.encodedPath}")
            }
        }

        try {
            val result = fixture.repository.createScheduledTask(
                recurrence = repeatRule,
                title = "Przegląd kwartalny",
                note = "Przygotuj raport",
                scheduledAt = "2026-10-01T12:00:00.000Z",
                deferUntil = "2026-09-28T12:00:00.000Z",
                dueAt = "2026-10-03T12:00:00.000Z",
                projectId = "project-1",
                contextIds = listOf("context-1"),
                newContextNames = listOf("Biuro"),
                tagIds = listOf("tag-1"),
                newTagNames = listOf("ważne"),
                files = emptyList(),
            )

            assertEquals("item-123", result.getOrThrow())
            assertEquals(listOf("/items", "/inbox/trash"), paths)

            val json = Json.parseToJsonElement(assertNotNull(requestBody)).jsonObject
            assertEquals("SCHEDULED", json.getValue("type").jsonPrimitive.content)
            assertEquals("WEEKLY", json.getValue("recurrence").jsonObject.getValue("frequency").jsonPrimitive.content)
            assertEquals("2026-10-01T12:00:00.000Z", json.getValue("scheduledAt").jsonPrimitive.content)
            assertEquals("2026-09-28T12:00:00.000Z", json.getValue("deferUntil").jsonPrimitive.content)
            assertEquals("2026-10-03T12:00:00.000Z", json.getValue("dueAt").jsonPrimitive.content)
            assertEquals("project-1", json.getValue("projectId").jsonPrimitive.content)
            assertEquals(listOf("context-1"), json.getValue("contextIds").jsonArray.map { it.jsonPrimitive.content })
            assertEquals(listOf("Biuro"), json.getValue("newContextNames").jsonArray.map { it.jsonPrimitive.content })
            assertEquals(listOf("tag-1"), json.getValue("tagIds").jsonArray.map { it.jsonPrimitive.content })
            assertEquals(listOf("ważne"), json.getValue("newTagNames").jsonArray.map { it.jsonPrimitive.content })
            assertTrue(json.getValue("encryptedTitle").jsonPrimitive.content.isNotBlank())
            assertTrue(json.getValue("encryptedNote").jsonPrimitive.content.isNotBlank())
            assertTrue(json.getValue("encryptedItemKey").jsonPrimitive.content.isNotBlank())
            assertFalse("newContexts" in json, "API odrzuca pola spoza kontraktu")
            assertEquals(JsonNull, json["fileIds"], "Pusta lista załączników powinna być zapisana jako null")
        } finally {
            fixture.close()
        }
    }

    @Test
    fun createScheduledTaskReportsApiRejectionAsFailure(): Unit = runBlocking {
        val paths = mutableListOf<String>()
        val fixture = fixture { request ->
            paths += request.url.encodedPath
            assertEquals("/items", request.url.encodedPath)
            respond(
                """{"message":"scheduledAt must be a valid ISO date"}""",
                HttpStatusCode.BadRequest,
                jsonHeaders,
            )
        }

        try {
            val result = fixture.repository.createScheduledTask(
                title = "Niepoprawne zadanie",
                note = "",
                scheduledAt = "not-a-date",
                files = emptyList(),
            )

            assertTrue(result.isFailure)
            assertNotNull(result.exceptionOrNull())
            assertEquals(listOf("/items"), paths)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun updateScheduledTaskSendsAttachmentChangesAndEditedNote(): Unit = runBlocking {
        var requestBody: String? = null
        val paths = mutableListOf<String>()
        val itemId = "550e8400-e29b-41d4-a716-446655440000"
        val attachmentId = "550e8400-e29b-41d4-a716-446655440001"
        val fixture = fixture { request ->
            paths += request.url.encodedPath
            when (request.url.encodedPath) {
                "/inbox/$itemId/scheduled" -> {
                    assertEquals("PATCH", request.method.value)
                    requestBody = (request.body as TextContent).text
                    respond("", HttpStatusCode.OK, jsonHeaders)
                }
                "/inbox/trash" -> respond("[]", HttpStatusCode.OK, jsonHeaders)
                else -> error("Unexpected URL: ${request.url.encodedPath}")
            }
        }

        try {
            val item = ScheduledTask(
                itemId = itemId,
                title = "Przegląd",
                note = "Notatka",
                itemKey = createItemKey(),
                createdAt = "2026-09-01T10:00:00.000Z",
                updatedAt = "2026-09-01T10:00:00.000Z",
                scheduledAt = "2026-10-01T12:00:00.000Z",
            )

            val result = fixture.repository.updateScheduledTask(
                item = item,
                title = item.title,
                note = "Zmieniona notatka\nDrugi wiersz",
                scheduledAt = item.scheduledAt,
                removedAttachmentIds = listOf(attachmentId),
            )

            assertTrue(result.isSuccess)
            assertEquals(listOf("/inbox/$itemId/scheduled", "/inbox/trash"), paths)
            val json = Json.parseToJsonElement(assertNotNull(requestBody)).jsonObject
            assertEquals(
                listOf(attachmentId),
                json.getValue("removedAttachmentIds").jsonArray.map { it.jsonPrimitive.content },
            )
            assertEquals(JsonNull, json["addedFileIds"])
            assertEquals(JsonNull, json.getValue("recurrence"))
            assertEquals(
                "Zmieniona notatka\nDrugi wiersz",
                decryptText(item.itemKey, json.getValue("encryptedNote").jsonPrimitive.content),
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun convertFromInboxSendsCompleteRequestAndRefreshesInbox(): Unit = runBlocking {
        var requestBody: String? = null
        val paths = mutableListOf<String>()
        val itemId = "550e8400-e29b-41d4-a716-446655440000"
        val projectId = "550e8400-e29b-41d4-a716-446655440001"
        val contextId = "550e8400-e29b-41d4-a716-446655440002"
        val tagId = "550e8400-e29b-41d4-a716-446655440003"
        val attachmentId = "550e8400-e29b-41d4-a716-446655440004"
        val uploadedFileId = "550e8400-e29b-41d4-a716-446655440005"
        val files = FakeFileOperations(uploadedFileId)
        val fixture = fixture(fileOperations = files) { request ->
            paths += request.url.encodedPath
            when (request.url.encodedPath) {
                "/inbox/$itemId/scheduled" -> {
                    assertEquals("POST", request.method.value)
                    assertEquals(ContentType.Application.Json, request.body.contentType)
                    requestBody = (request.body as TextContent).text
                    respond("", HttpStatusCode.NoContent, jsonHeaders)
                }
                "/inbox", "/inbox/trash" -> respond("[]", HttpStatusCode.OK, jsonHeaders)
                else -> error("Unexpected URL: ${request.url.encodedPath}")
            }
        }

        try {
            val item = InboxItem(
                itemId = itemId,
                title = "Pierwotny tytuł",
                note = "Pierwotna notatka",
                itemKey = createItemKey(),
                createdAt = "2026-09-20T10:00:00.000Z",
                updatedAt = "2026-09-20T10:00:00.000Z",
                attachments = listOf(
                    DecryptedAttachment(
                        attachmentId = attachmentId,
                        fileId = "old-file-id",
                        name = "stary.txt",
                        type = "text/plain",
                        ciphertextSha256 = "hash",
                        fileKey = createItemKey(),
                    ),
                ),
            )
            val result = fixture.repository.convertFromInbox(
                recurrence = repeatRule,
                item = item,
                title = "Zmieniony tytuł",
                note = "Zmieniona notatka",
                scheduledAt = "2026-10-01T12:00:00.000Z",
                deferUntil = "2026-09-28T12:00:00.000Z",
                dueAt = "2026-10-03T12:00:00.000Z",
                projectId = projectId,
                contextIds = listOf(contextId),
                newContextNames = listOf("Biuro"),
                tagIds = listOf(tagId),
                newTagNames = listOf("ważne"),
                newFiles = listOf(InputFile("nowy.txt", "text/plain", "treść".encodeToByteArray())),
                removedAttachmentIds = listOf(attachmentId),
            )

            assertTrue(result.isSuccess)
            assertEquals(
                listOf("/inbox/$itemId/scheduled", "/inbox", "/inbox/trash"),
                paths,
            )
            val json = Json.parseToJsonElement(assertNotNull(requestBody)).jsonObject
            assertEquals("2026-10-01T12:00:00.000Z", json.getValue("scheduledAt").jsonPrimitive.content)
            assertEquals("2026-09-28T12:00:00.000Z", json.getValue("deferUntil").jsonPrimitive.content)
            assertEquals("2026-10-03T12:00:00.000Z", json.getValue("dueAt").jsonPrimitive.content)
            assertEquals(projectId, json.getValue("projectId").jsonPrimitive.content)
            assertEquals(listOf(contextId), json.getValue("contextIds").jsonArray.map { it.jsonPrimitive.content })
            assertEquals(listOf("Biuro"), json.getValue("newContextNames").jsonArray.map { it.jsonPrimitive.content })
            assertEquals(listOf(tagId), json.getValue("tagIds").jsonArray.map { it.jsonPrimitive.content })
            assertEquals(listOf("ważne"), json.getValue("newTagNames").jsonArray.map { it.jsonPrimitive.content })
            assertEquals(
                "Zmieniony tytuł",
                decryptText(item.itemKey, json.getValue("encryptedTitle").jsonPrimitive.content),
            )
            assertEquals(
                "Zmieniona notatka",
                decryptText(item.itemKey, json.getValue("encryptedNote").jsonPrimitive.content),
            )
            assertEquals(listOf(uploadedFileId), json.getValue("addedFileIds").jsonArray.map { it.jsonPrimitive.content })
            assertEquals(listOf(attachmentId), json.getValue("removedAttachmentIds").jsonArray.map { it.jsonPrimitive.content })
            assertEquals(listOf("nowy.txt"), files.uploadedNames)
            assertFalse("newContexts" in json, "API odrzuca pola spoza kontraktu")
        } finally {
            fixture.close()
        }
    }

    @Test
    fun convertFromInboxReportsApiRejectionWithoutRefreshingLists(): Unit = runBlocking {
        val paths = mutableListOf<String>()
        val itemId = "550e8400-e29b-41d4-a716-446655440000"
        val fixture = fixture { request ->
            paths += request.url.encodedPath
            assertEquals("/inbox/$itemId/scheduled", request.url.encodedPath)
            respond(
                """{"message":"scheduledAt must be a valid ISO date"}""",
                HttpStatusCode.BadRequest,
                jsonHeaders,
            )
        }

        try {
            val item = InboxItem(
                itemId = itemId,
                title = "Niepoprawne zadanie",
                note = "",
                itemKey = createItemKey(),
                createdAt = "2026-09-20T10:00:00.000Z",
                updatedAt = "2026-09-20T10:00:00.000Z",
            )
            val result = fixture.repository.convertFromInbox(
                item = item,
                title = item.title,
                note = item.note,
                scheduledAt = "not-a-date",
            )

            assertTrue(result.isFailure)
            assertNotNull(result.exceptionOrNull())
            assertEquals(listOf("/inbox/$itemId/scheduled"), paths)
        } finally {
            fixture.close()
        }
    }

    private suspend fun fixture(
        fileOperations: FileOperations? = null,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Fixture {
        val session = SessionManager(TestSettings()).apply { accessToken = "test-token" }
        val client = HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json() }
        }
        val keys = generateUserKeyPair()
        val module = ItemModule(
            httpClient = client,
            sessionManager = session,
            dPoPManager = DefaultDPoPManager(TestSettings()),
            baseUrl = "https://example.test/",
            sessionRefresher = SessionRefresher { Result.success(Unit) },
            keysProvider = UserKeysProvider { keys },
            fileOperations = fileOperations,
        )
        return Fixture(module.scheduled, client, session)
    }

    private class Fixture(
        val repository: ScheduledOperations,
        private val client: HttpClient,
        private val session: SessionManager,
    ) {
        fun close() {
            client.close()
            session.clearSession()
        }
    }

    private class FakeFileOperations(
        private val uploadedFileId: String,
    ) : FileOperations {
        val uploadedNames = mutableListOf<String>()

        override suspend fun uploadEncryptedFile(
            fileName: String,
            mimeType: String,
            fileBytes: ByteArray,
            onProgress: ((Float) -> Unit)?,
        ): Result<String> {
            uploadedNames += fileName
            onProgress?.invoke(1f)
            return Result.success(uploadedFileId)
        }

        override suspend fun deleteUploadedFile(fileId: String) = Result.success(Unit)

        override suspend fun downloadEncryptedFile(fileId: String) =
            Result.success(DecryptedFile("unused", "application/octet-stream", byteArrayOf()))
    }

    private companion object {
        val jsonHeaders = headersOf("Content-Type", ContentType.Application.Json.toString())
    }
}
