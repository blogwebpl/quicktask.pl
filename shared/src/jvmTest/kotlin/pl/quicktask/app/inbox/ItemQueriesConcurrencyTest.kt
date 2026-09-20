package pl.quicktask.app.inbox

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.encodeToString
import pl.quicktask.app.items.support.*
import kotlin.test.*

class ItemQueriesConcurrencyTest {
    @Test
    fun creationRefreshReplacesTemporaryItemWithoutEmittingDuplicateOrGap(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val item = fixture.mapper.inbox(fixture.inbox)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val client = mockClient(MockEngine {
            entered.complete(Unit)
            release.await()
            respond(testJson.encodeToString(listOf(fixture.inbox)), headers = jsonHeaders)
        })
        try {
            val module = fixture.module(client)
            val operation = module.store.beginOperation("temp_created", item.copy(itemId = "temp_created"))!!
            val other = module.store.beginOperation("temp_other", item.copy(itemId = "temp_other", title = "Other"))!!
            val snapshots = mutableListOf<List<String>>()
            val observer = launch(start = CoroutineStart.UNDISPATCHED, context = Dispatchers.Unconfined) {
                module.store.itemsFlow.collect { snapshots.add(it.map { item -> item.itemId }) }
            }
            try {
                val refresh = async { module.inbox.getItems(true, operation).getOrThrow() }
                entered.await()
                assertEquals(listOf("temp_created", "temp_other"), module.store.itemsFlow.value.map { it.itemId })
                release.complete(Unit)
                assertEquals(listOf("temp_other", item.itemId), refresh.await().map { it.itemId })
                module.store.finishOperation(operation)
                assertEquals(
                    listOf(listOf("temp_created", "temp_other"), listOf("temp_other", item.itemId)),
                    snapshots,
                )
                assertFalse(module.store.hasPendingOperation(operation.itemId))
                assertTrue(module.store.hasPendingOperation(other.itemId))
            } finally { observer.cancelAndJoin() }
        } finally { client.close() }
    }

    @Test
    fun concurrentForceRefreshesShareOneRequest(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var requests = 0
        val client = mockClient(MockEngine {
            requests++
            entered.complete(Unit)
            release.await()
            respond("[]", headers = jsonHeaders)
        })
        try {
            val module = encryptedFixture().module(client)
            val first = async { module.inbox.getItems(true).getOrThrow() }
            entered.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { module.inbox.getItems(true).getOrThrow() }
            release.complete(Unit)
            assertEquals(emptyList(), first.await())
            assertEquals(emptyList(), second.await())
            assertEquals(1, requests)
        } finally { client.close() }
    }

    @Test
    fun invalidationDuringFetchRetriesInsteadOfCachingOldResponse(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var requests = 0
        val client = mockClient(MockEngine {
            requests++
            if (requests == 1) {
                entered.complete(Unit)
                release.await()
            }
            respond("[]", headers = jsonHeaders)
        })
        try {
            val module = encryptedFixture().module(client)
            val request = async { module.inbox.getItems(true).getOrThrow() }
            entered.await()
            module.store.invalidateCache()
            release.complete(Unit)
            request.await()
            assertEquals(2, requests)
            assertTrue(module.store.isCacheValid)
        } finally { client.close() }
    }
}
