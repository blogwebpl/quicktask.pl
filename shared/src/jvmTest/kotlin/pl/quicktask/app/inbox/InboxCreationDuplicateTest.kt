package pl.quicktask.app.inbox

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.encodeToString
import pl.quicktask.app.inbox.presentation.InboxEditorController
import pl.quicktask.app.inbox.presentation.InboxUiState
import pl.quicktask.app.items.model.InboxItem
import pl.quicktask.app.items.model.ItemSyncStateResponseDto
import pl.quicktask.app.items.support.*
import kotlin.test.*

class InboxCreationDuplicateTest {
    @Test
    fun creationDoesNotEmitDuplicateWhenSseArrivesBeforeRefreshCompletes(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val realDto = fixture.inbox.copy(itemId = "real-uuid-123", encryptedTitle = fixture.inbox.encryptedTitle)
        val realItem = fixture.mapper.inbox(realDto)

        val enteredPost = CompletableDeferred<Unit>()
        val releasePost = CompletableDeferred<Unit>()

        val client = mockClient(MockEngine { request ->
            if (request.method.value == "POST") {
                enteredPost.complete(Unit)
                releasePost.await()
                respond("""{"itemId":"real-uuid-123","createdAt":"now"}""", headers = jsonHeaders)
            } else {
                respond(testJson.encodeToString(listOf(realDto)), headers = jsonHeaders)
            }
        })

        try {
            val module = fixture.module(client)
            var state = InboxUiState()
            val controller = InboxEditorController(
                repository = module.inbox,
                store = module.store,
                scope = this,
                currentState = { state },
                updateState = { transform -> state = transform(state) },
            )

            val snapshots = mutableListOf<List<InboxItem>>()
            val observer = launch(start = CoroutineStart.UNDISPATCHED, context = Dispatchers.Unconfined) {
                module.store.itemsFlow.collect { items ->
                    snapshots.add(items)
                }
            }

            try {
                controller.openNew()
                controller.save(realItem.title, realItem.note)

                enteredPost.await()

                // Simulate SSE sync arriving while POST response/refresh is in flight
                module.sync.applySyncState(ItemSyncStateResponseDto("inbox", item = fixture.sync.copy(itemId = "real-uuid-123")), "real-uuid-123")

                releasePost.complete(Unit)

                // Wait for background launch in createNew to complete
                yield()

                // Check that no snapshot contained more than 1 item with the title
                snapshots.forEach { list ->
                    val matching = list.filter { it.title == realItem.title }
                    assertTrue(matching.size <= 1, "Duplicate item emitted! List contained: ${list.map { it.itemId to it.title }}")
                }
            } finally {
                observer.cancelAndJoin()
            }
        } finally {
            client.close()
        }
    }
}
