package pl.quicktask.app.sync.domain

import pl.quicktask.app.sync.model.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncEventParserTest {

    @Test
    fun testParseSyncRequiredWithLF() {
        val parser = SyncEventParser()
        val lines = listOf(
            "event: sync.required",
            "data: {\"event\":\"sync.required\"}",
            "",
        )

        val events = lines.flatMap { parser.feedLine(it) }

        assertEquals(1, events.size)
        assertEquals(SyncEvent.SyncRequired, events.first())
    }

    @Test
    fun testParseItemsChangedWithCRLF() {
        val parser = SyncEventParser()
        val lines = listOf(
            "event: items.changed\r",
            "id: event-123\r",
            "data: {\"event\":\"items.changed\",\"eventId\":\"event-123\",\"changedAt\":\"2026-03-29T10:00:00Z\"}\r",
            "\r",
        )

        val events = lines.flatMap { parser.feedLine(it) }

        assertEquals(1, events.size)
        val event = events.first() as SyncEvent.ItemsChanged
        assertEquals("event-123", event.eventId)
        assertEquals("2026-03-29T10:00:00Z", event.changedAt)
        assertEquals(null, event.itemId)
    }

    @Test
    fun testParseItemsChangedWithItemId() {
        val parser = SyncEventParser()
        val lines = listOf(
            "id: e82e56f8-8582-4d53-a48d-52f4ca47012a",
            "event: items.changed",
            "data: {\"event\":\"items.changed\",\"eventId\":\"e82e56f8-8582-4d53-a48d-52f4ca47012a\",\"changedAt\":\"2026-09-11T17:00:00.000Z\",\"itemId\":\"3f1c8d2a-7b64-4e0f-9c2a-1b8e6d4f0a21\"}",
            "",
        )

        val events = lines.flatMap { parser.feedLine(it) }

        assertEquals(1, events.size)
        val event = events.first() as SyncEvent.ItemsChanged
        assertEquals("e82e56f8-8582-4d53-a48d-52f4ca47012a", event.eventId)
        assertEquals("2026-09-11T17:00:00.000Z", event.changedAt)
        assertEquals("3f1c8d2a-7b64-4e0f-9c2a-1b8e6d4f0a21", event.itemId)
    }

    @Test
    fun testIgnoreHeartbeatAndComments() {
        val parser = SyncEventParser()
        val lines = listOf(
            ": heartbeat",
            ": another comment",
            "",
            "event: sync.required",
            "data: {\"event\":\"sync.required\"}",
            "",
        )

        val events = lines.flatMap { parser.feedLine(it) }

        assertEquals(1, events.size)
        assertEquals(SyncEvent.SyncRequired, events.first())
    }

    @Test
    fun testIgnoreUnknownEventsAndInvalidJson() {
        val parser = SyncEventParser()
        val lines = listOf(
            "event: unknown.event",
            "data: {invalid json}",
            "",
            "event: items.changed",
            "id: valid-id",
            "data: {\"event\":\"items.changed\",\"eventId\":\"valid-id\"}",
            "",
        )

        val events = lines.flatMap { parser.feedLine(it) }

        assertEquals(1, events.size)
        val event = events.first() as SyncEvent.ItemsChanged
        assertEquals("valid-id", event.eventId)
    }

    @Test
    fun testFragmentedChunksParsing() {
        val parser = SyncEventParser()

        val chunk1 = "event: items.changed\nid: chunk-1\ndata: {\"event\":\"items.changed\","
        val chunk2 = "\"eventId\":\"chunk-1\"}\n\n: heartbeat\n\nevent: sync.required\ndata: {\"event\":\"sync.required\"}\n\n"

        val events1 = parser.feedChunk(chunk1)
        assertEquals(0, events1.size, "Chunk 1 is incomplete so no events emitted yet")

        val events2 = parser.feedChunk(chunk2)
        assertEquals(2, events2.size, "Chunk 2 completes chunk-1 and sends sync.required")

        assertTrue(events2[0] is SyncEvent.ItemsChanged)
        assertEquals("chunk-1", (events2[0] as SyncEvent.ItemsChanged).eventId)
        assertEquals(SyncEvent.SyncRequired, events2[1])
    }

    @Test
    fun testMultipleDataLinesJoinedWithNewline() {
        val parser = SyncEventParser()
        val lines = listOf(
            "event: sync.required",
            "data: {",
            "data:   \"event\": \"sync.required\"",
            "data: }",
            "",
        )

        val events = lines.flatMap { parser.feedLine(it) }

        assertEquals(1, events.size)
        assertEquals(SyncEvent.SyncRequired, events.first())
    }
}
