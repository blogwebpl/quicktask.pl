package pl.quicktask.app.inbox.presentation

import kotlinx.coroutines.*
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.items.support.encryptedFixture
import kotlin.test.*

class PendingItemMutationTest {
    @Test
    fun cancellationReleasesOnlyOwnedOverlayAndInvalidatesCache(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val item = fixture.mapper.inbox(fixture.inbox)
        val store = ItemStore()
        store.cacheInbox(listOf(item))
        val operation = store.beginOperation(item.itemId, null)!!
        val entered = CompletableDeferred<Unit>()
        var errors = 0
        val mutation = launch {
            runPendingItemMutation(store, operation,
                mutate = { entered.complete(Unit); awaitCancellation() },
                refresh = { error("Must not refresh") },
                onMutationError = { errors++ }, onRefreshError = { errors++ })
        }
        entered.await()
        mutation.cancelAndJoin()
        assertFalse(store.hasPendingOperation(item.itemId))
        assertFalse(store.isCacheValid)
        assertEquals(listOf(item), store.itemsFlow.value)
        assertEquals(0, errors)
    }

    @Test
    fun successfulMutationKeepsOverlayUntilRefreshAndReportsRefreshFailure(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val item = fixture.mapper.inbox(fixture.inbox)
        val store = ItemStore()
        store.cacheInbox(listOf(item))
        val operation = store.beginOperation(item.itemId, null)!!
        var refreshErrors = 0
        runPendingItemMutation(store, operation,
            mutate = { Result.success(Unit) },
            refresh = {
                assertTrue(store.hasPendingOperation(item.itemId))
                assertTrue(store.itemsFlow.value.isEmpty())
                Result.failure(IllegalStateException("offline"))
            }, onMutationError = { error("Write succeeded") }, onRefreshError = { refreshErrors++ })
        assertEquals(1, refreshErrors)
        assertFalse(store.hasPendingOperation(item.itemId))
        assertFalse(store.isCacheValid)
    }
}
