package pl.quicktask.app.sync

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventDeduplicatorTest {

    @Test
    fun testDuplicateDetection() {
        val deduplicator = EventDeduplicator(maxSize = 100)

        assertFalse(deduplicator.isDuplicate("event-1"), "First time should not be duplicate")
        assertTrue(deduplicator.isDuplicate("event-1"), "Second time should be duplicate")
        assertFalse(deduplicator.isDuplicate("event-2"), "Different event ID should not be duplicate")
    }

    @Test
    fun testMaxSizeEviction() {
        val deduplicator = EventDeduplicator(maxSize = 3)

        deduplicator.isDuplicate("e1")
        deduplicator.isDuplicate("e2")
        deduplicator.isDuplicate("e3")

        // Adding e4 should evict e1
        deduplicator.isDuplicate("e4")

        assertTrue(deduplicator.isDuplicate("e2"), "e2 should still be in cache")
        assertFalse(deduplicator.isDuplicate("e1"), "e1 was evicted, so it should be considered new")
    }

    @Test
    fun testClear() {
        val deduplicator = EventDeduplicator(maxSize = 100)

        deduplicator.isDuplicate("event-1")
        assertTrue(deduplicator.isDuplicate("event-1"))

        deduplicator.clear()
        assertFalse(deduplicator.isDuplicate("event-1"), "After clear, event-1 should not be duplicate")
    }
}
