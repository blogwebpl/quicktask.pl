package pl.quicktask.app.items.domain

import kotlinx.coroutines.runBlocking
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.items.support.encryptedFixture
import kotlin.test.*

class PendingItemStoreTest {
    @Test
    fun failureOfOneItemPreservesAnotherSuccessAndLatestSync(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val a = fixture.mapper.inbox(fixture.inbox)
        val b = a.copy(itemId = "B", title = "B")
        val store = ItemStore()
        store.cacheInbox(listOf(a, b))
        val first = store.beginOperation(a.itemId, a.copy(title = "local A"))!!
        val second = store.beginOperation(b.itemId, b.copy(title = "local B"))!!
        store.applyInbox(a.copy(title = "remote A"), a.itemId)
        store.applyInbox(b.copy(title = "saved B"), b.itemId)
        store.finishOperation(second)
        store.finishOperation(first)
        assertEquals("remote A", store.itemsFlow.value.single { it.itemId == a.itemId }.title)
        assertEquals("saved B", store.itemsFlow.value.single { it.itemId == b.itemId }.title)
    }

    @Test
    fun overlaySurvivesRefreshAndOldCompletionCannotRemoveNewOperation(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val a = fixture.mapper.inbox(fixture.inbox)
        val store = ItemStore()
        store.cacheInbox(listOf(a))
        val first = store.beginOperation(a.itemId, a.copy(title = "local"))!!
        assertNull(store.beginOperation(a.itemId, null))
        store.cacheInbox(listOf(a.copy(title = "remote")))
        assertEquals("local", store.itemsFlow.value.single().title)
        store.finishOperation(first)
        val next = store.beginOperation(a.itemId, null)!!
        store.finishOperation(first)
        assertTrue(store.hasPendingOperation(a.itemId))
        assertTrue(store.itemsFlow.value.isEmpty())
        store.finishOperation(next)
        assertEquals("remote", store.itemsFlow.value.single().title)
    }

    @Test
    fun invalidatedSnapshotIsRejected(): Unit = runBlocking {
        val store = ItemStore()
        val generation = store.generation
        store.invalidateCache()
        assertFalse(store.cacheInbox(emptyList(), generation))
        assertFalse(store.cacheTrash(emptyList(), generation))
        assertFalse(store.isCacheValid)
    }
}
