package pl.quicktask.app.inbox

import pl.quicktask.app.items.support.jsonHeaders
import pl.quicktask.app.items.support.mockClient
import pl.quicktask.app.items.support.encryptedFixture
import pl.quicktask.app.items.support.RecordingFiles

import pl.quicktask.app.items.model.AttachmentLimitException
import pl.quicktask.app.items.model.InputFile
import pl.quicktask.app.items.model.MAX_ATTACHMENTS

import pl.quicktask.app.inbox.data.*
import pl.quicktask.app.trash.model.*

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InboxOperationsTest {
    @Test
    fun attachmentLimitIsTypedAndRequestIsNotSent(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val client = mockClient(MockEngine { error("Must not send") })
        try {
            val result = fixture.module(client).inbox.createInboxItem(
                "Tytuł", "", List(MAX_ATTACHMENTS + 1) { InputFile("$it.txt", "text/plain", byteArrayOf()) },
            )
            assertFailsWith<AttachmentLimitException> { result.getOrThrow() }
        } finally { client.close() }
    }

    @Test
    fun uploadedFilesAreCleanedWhenCreatingItemFails(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val files = RecordingFiles()
        val client = mockClient(MockEngine { respond("failure", HttpStatusCode.InternalServerError, jsonHeaders) })
        try {
            val result = fixture.module(client, files).inbox.createInboxItem(
                "Tytuł", "", listOf(InputFile("a.txt", "text/plain", byteArrayOf(1))),
            )
            assertEquals(true, result.isFailure)
            assertEquals(listOf("a.txt"), files.uploaded)
            assertEquals(listOf("uploaded-1"), files.deleted)
        } finally { client.close() }
    }

    @Test
    fun uploadProgressIsAggregatedAcrossFiles(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val files = RecordingFiles()
        val progress = mutableListOf<Float>()
        val client = mockClient(MockEngine { respond("""{"itemId":"new","createdAt":"now"}""", HttpStatusCode.OK, jsonHeaders) })
        try {
            fixture.module(client, files).inbox.createInboxItem(
                "Tytuł", "", listOf(
                    InputFile("a.txt", "text/plain", byteArrayOf(1)),
                    InputFile("b.txt", "text/plain", byteArrayOf(2)),
                ), progress::add,
            ).getOrThrow()
            assertEquals(listOf(0.25f, 0.5f, 0.75f, 1f, 1f), progress)
        } finally { client.close() }
    }

    @Test
    fun cancellationFromUploadIsPropagated(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val files = RecordingFiles().apply {
            failureAt = 0
            uploadFailure = CancellationException("cancelled")
        }
        val client = mockClient(MockEngine { error("Must not send") })
        try {
            assertFailsWith<CancellationException> {
                fixture.module(client, files).inbox.createInboxItem(
                    "Tytuł", "", listOf(InputFile("a.txt", "text/plain", byteArrayOf(1))),
                )
            }
        } finally { client.close() }
    }
}
