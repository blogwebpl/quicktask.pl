package pl.quicktask.app.items.domain

import pl.quicktask.app.items.support.encryptedFixture

import pl.quicktask.app.items.model.AttachmentMetadataState
import pl.quicktask.app.items.model.ItemDecryptionException

import pl.quicktask.app.now.model.NowItemDto
import pl.quicktask.app.trash.model.*

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ItemCryptoMapperTest {
    @Test
    fun mapsNowItemDtoCorrectly(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val mapper = fixture.mapper
        val dto = NowItemDto(
            itemId = fixture.inbox.itemId,
            taskId = fixture.inbox.itemId,
            gtdState = "NEXT",
            encryptedTitle = fixture.inbox.encryptedTitle,
            encryptedNote = fixture.inbox.encryptedNote,
            encryptedItemKey = fixture.inbox.encryptedItemKey,
            createdAt = fixture.inbox.createdAt,
            updatedAt = fixture.inbox.updatedAt,
            attachments = fixture.inbox.attachments,
            dueAt = "2026-09-19T10:00:00.000Z",
        )
        val nowItem = mapper.nowItem(dto)
        assertEquals("Tytuł użytkownika", nowItem.title)
        assertEquals("Notatka", nowItem.note)
        assertEquals("NEXT", nowItem.gtdState)
        assertEquals("2026-09-19T10:00:00.000Z", nowItem.dueAt)
    }

    @Test
    fun mapsInboxTrashCompletedAndSyncWithOneImplementation(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val mapper = fixture.mapper
        val inbox = mapper.inbox(fixture.inbox)
        val trash = mapper.trash(fixture.trash)
        val completed = mapper.completed(fixture.completed)
        val syncedInbox = mapper.syncInbox(fixture.sync)
        val syncedTrash = mapper.syncTrash(fixture.sync)

        listOf(inbox.title, trash.title, completed.title, syncedInbox.title, syncedTrash.title)
            .forEach { assertEquals("Tytuł użytkownika", it) }
        listOf(inbox.attachments.single(), trash.attachments.single(), completed.attachments.single())
            .forEach {
                assertEquals("dokument.pdf", it.name)
                assertEquals("application/pdf", it.type)
                assertEquals(AttachmentMetadataState.AVAILABLE, it.metadataState)
            }
        assertEquals("2026-09-03", trash.deletedAt)
        assertEquals("2026-09-03", completed.processedAt)
    }

    @Test
    fun absentNoteBecomesEmptyString(): Unit = runBlocking {
        val fixture = encryptedFixture()
        assertEquals("", fixture.mapper.inbox(fixture.inbox.copy(encryptedNote = null)).note)
    }

    @Test
    fun damagedAttachmentMetadataIsRepresentedWithoutLocalizedText(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val damaged = fixture.inbox.copy(
            attachments = listOf(fixture.inbox.attachments.single().copy(encryptedMetadata = "damaged")),
        )
        val attachment = fixture.mapper.inbox(damaged).attachments.single()
        assertNull(attachment.name)
        assertEquals("", attachment.type)
        assertEquals(AttachmentMetadataState.DECRYPTION_FAILED, attachment.metadataState)
    }

    @Test
    fun damagedWrappedKeyFailsWholeItemWithTypedError(): Unit = runBlocking {
        val fixture = encryptedFixture()
        assertFailsWith<ItemDecryptionException> {
            fixture.mapper.inbox(fixture.inbox.copy(encryptedItemKey = "damaged"))
        }
    }

    @Test
    fun cancellationFromKeyProviderIsPropagated(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val mapper = ItemCryptoMapper(UserKeysProvider { throw CancellationException("cancelled") })
        assertFailsWith<CancellationException> { mapper.inbox(fixture.inbox) }
    }

    @Test
    fun damagedAttachmentKeyFailsWholeItem(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val damaged = fixture.inbox.copy(
            attachments = listOf(fixture.inbox.attachments.single().copy(encryptedFileKey = "damaged")),
        )
        assertFailsWith<ItemDecryptionException> { fixture.mapper.inbox(damaged) }
    }
}
