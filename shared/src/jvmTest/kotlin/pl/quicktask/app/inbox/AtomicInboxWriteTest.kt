package pl.quicktask.app.inbox

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import pl.quicktask.app.auth.crypto.encryptText
import pl.quicktask.app.auth.model.ApiException
import pl.quicktask.app.inbox.presentation.runPendingItemMutation
import pl.quicktask.app.items.data.FileOperations
import pl.quicktask.app.items.model.*
import pl.quicktask.app.items.support.*
import java.io.IOException
import kotlin.test.*

class AtomicInboxWriteTest {
    @Test
    fun uploadsBeforeOnePatchContainingContentAndAttachmentChanges(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val item = fixture.mapper.inbox(fixture.inbox)
        val files = RecordingFiles()
        var patches = 0
        val client = mockClient(MockEngine { request ->
            patches++
            assertEquals("PATCH", request.method.value)
            assertEquals(listOf("new.txt"), files.uploaded)
            val body = testJson.decodeFromString<UpdateInboxItemRequestDto>((request.body as TextContent).text)
            assertEquals(listOf("uploaded-1"), body.addedFileIds)
            assertEquals(listOf(item.attachments.single().attachmentId), body.removedAttachmentIds)
            assertNotEquals("new title", body.encryptedTitle)
            respond("""{"itemId":"item-1","updatedAt":"now"}""", headers = jsonHeaders)
        })
        try {
            fixture.module(client, files).inbox.updateInboxItem(item, "new title", "note", listOf(InputFile("new.txt", "text/plain", byteArrayOf(1))), listOf(item.attachments.single().attachmentId)).getOrThrow()
            assertEquals(1, patches)
            assertTrue(files.deleted.isEmpty())
        } finally { client.close() }
    }

    @Test
    fun lostPatchResponseReconcilesServerStateWithoutRepeatingWrite(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val item = fixture.mapper.inbox(fixture.inbox)
        val savedDto = fixture.inbox.copy(encryptedTitle = encryptText(item.itemKey, "saved on server"))
        var patches = 0
        var reads = 0
        val client = mockClient(MockEngine { request ->
            if (request.method.value == "PATCH") {
                patches++
                throw IOException("Response lost after commit")
            }
            reads++
            respond(testJson.encodeToString(listOf(savedDto)), headers = jsonHeaders)
        })
        try {
            val module = fixture.module(client)
            module.store.cacheInbox(listOf(item))
            val operation = module.store.beginOperation(item.itemId, item.copy(title = "local"))!!
            var error: Throwable? = null
            runPendingItemMutation(module.store, operation,
                mutate = { module.inbox.updateInboxItem(item, "saved on server", "", emptyList(), emptyList()) },
                refresh = { module.inbox.getItems(true) },
                onMutationError = { error = it }, onRefreshError = { throw it })
            assertIs<UncertainItemWriteException>(error)
            assertEquals(1, patches)
            assertEquals(1, reads)
            assertEquals("saved on server", module.store.itemsFlow.value.single().title)
            assertFalse(module.store.hasPendingOperation(item.itemId))
        } finally { client.close() }
    }

    @Test
    fun cleanupFailureDoesNotReplaceOriginalApiError(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val recording = RecordingFiles()
        val files = object : FileOperations by recording {
            override suspend fun deleteUploadedFile(fileId: String): Result<Unit> = throw IOException("Cleanup unavailable")
        }
        val client = mockClient(MockEngine { respond("original failure", HttpStatusCode.BadRequest, jsonHeaders) })
        try {
            val result = fixture.module(client, files).inbox.createInboxItem("title", "", listOf(InputFile("new.txt", "text/plain", byteArrayOf(1))))
            val error = assertIs<ApiException>(result.exceptionOrNull())
            assertTrue(error.message.orEmpty().contains("original failure"))
        } finally { client.close() }
    }
}
