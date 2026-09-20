package pl.quicktask.app.items.domain

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import pl.quicktask.app.items.support.*
import kotlin.test.*

class TrashRepositoryConcurrencyTest {
    @Test
    fun successfulOperationCannotRevealOtherPendingItemAndFailureReconciles() = runBlocking {
        val fixture = encryptedFixture()
        val second = fixture.trash.copy(itemId = "second")
        val server = MutableStateFlow(listOf(fixture.trash, second))
        val enteredFirst = CompletableDeferred<Unit>()
        val enteredSecond = CompletableDeferred<Unit>()
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        val client = mockClient(MockEngine { request ->
            when {
                request.method == HttpMethod.Get -> respond(testJson.encodeToString(server.value), headers = jsonHeaders)
                request.url.encodedPath.contains("second") -> {
                    enteredSecond.complete(Unit)
                    secondGate.await()
                    respond("failed", HttpStatusCode.BadRequest)
                }
                else -> {
                    enteredFirst.complete(Unit)
                    firstGate.await()
                    server.value = listOf(second)
                    respond("{}", headers = jsonHeaders)
                }
            }
        })
        try {
            val module = fixture.module(client)
            module.trash.getTrashItems().getOrThrow()
            val first = async { module.trash.permanentlyDeleteItem(fixture.trash.itemId) }
            enteredFirst.await()
            val other = async { module.trash.permanentlyDeleteItem("second") }
            enteredSecond.await()
            assertTrue(module.store.trashItemsFlow.value.isEmpty())
            assertTrue(module.trash.emptyTrash().isFailure)
            firstGate.complete(Unit)
            first.await().getOrThrow()
            assertTrue(module.store.trashItemsFlow.value.isEmpty())
            module.store.applyTrash(fixture.mapper.trash(second), "second")
            assertTrue(module.store.trashItemsFlow.value.isEmpty())
            secondGate.complete(Unit)
            assertTrue(other.await().isFailure)
            assertEquals(listOf("second"), module.store.trashItemsFlow.value.map { it.itemId })
            assertTrue(module.store.trashOperationsFlow.value.isEmpty())
        } finally { client.close() }
    }

    @Test
    fun cancellationReleasesOverlayAndFailedRefreshKeepsCacheInvalid() = runBlocking {
        val fixture = encryptedFixture()
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        var cancel = true
        val client = mockClient(MockEngine { request ->
            if (request.method == HttpMethod.Get) respond("unavailable", HttpStatusCode.ServiceUnavailable)
            else {
                entered.complete(Unit)
                if (cancel) gate.await()
                respond("{}", headers = jsonHeaders)
            }
        })
        try {
            val module = fixture.module(client)
            val item = fixture.mapper.trash(fixture.trash)
            module.store.cacheTrash(listOf(item))
            val pending = async { module.trash.permanentlyDeleteItem(item.itemId) }
            entered.await()
            pending.cancelAndJoin()
            assertFalse(module.store.isTrashCacheValid)
            assertTrue(module.store.trashOperationsFlow.value.isEmpty())
            assertEquals(listOf(item), module.store.trashItemsFlow.value)
            cancel = false
            assertTrue(module.trash.permanentlyDeleteItem(item.itemId).isFailure)
            assertFalse(module.store.isTrashCacheValid)
            assertTrue(module.store.trashOperationsFlow.value.isEmpty())
            assertTrue(module.store.trashItemsFlow.value.isEmpty())
        } finally { client.close() }
    }
}
