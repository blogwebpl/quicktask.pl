package pl.quicktask.app.items.domain

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import pl.quicktask.app.items.model.ItemSyncStateResponseDto
import pl.quicktask.app.items.support.*
import kotlin.test.*

class CompletedItemsTest {
    @Test
    fun syncMovesCompletedItemsBetweenListsAndRejectsStaleSnapshots() = runBlocking {
        val fixture = encryptedFixture()
        val client = mockClient(MockEngine { error("No requests expected") })
        try {
            val module = fixture.module(client)
            val item = fixture.mapper.inbox(fixture.inbox)
            module.store.cacheInbox(listOf(item))
            val snapshot = ItemSyncStateResponseDto("completed-in-two-minutes", item = fixture.sync.copy(processedAt = "2026-09-03"))
            val generation = module.store.generation
            module.sync.applySyncState(snapshot, item.itemId)
            module.sync.applySyncState(snapshot, item.itemId)
            val completed = module.store.completedItemsFlow.value.single()
            assertEquals(item.title, completed.title)
            assertEquals(item.note, completed.note)
            assertEquals("dokument.pdf", completed.attachments.single().name)
            assertTrue(module.store.itemsFlow.value.isEmpty())
            assertFalse(module.store.cacheCompleted(emptyList(), generation))

            module.sync.applySyncState(ItemSyncStateResponseDto("trash", item = fixture.sync), item.itemId)
            assertTrue(module.store.completedItemsFlow.value.isEmpty())
            module.sync.applySyncState(snapshot, item.itemId)
            assertTrue(module.store.trashItemsFlow.value.isEmpty())
            module.sync.applySyncState(ItemSyncStateResponseDto("inbox", item = fixture.sync), item.itemId)
            assertTrue(module.store.completedItemsFlow.value.isEmpty())
            module.sync.applySyncState(snapshot, item.itemId)
            module.sync.applySyncState(ItemSyncStateResponseDto("removed", itemId = item.itemId), item.itemId)
            assertTrue(module.store.completedItemsFlow.value.isEmpty())
        } finally { client.close() }
    }

    @Test
    fun repositoryLoadsDecryptsRestoresAndTrashesCompletedItems() = runBlocking {
        val fixture = encryptedFixture()
        var completed = true
        val mutations = mutableListOf<Pair<HttpMethod, String>>()
        val client = mockClient(MockEngine { request ->
            val path = request.url.encodedPath
            if (request.method != HttpMethod.Get) {
                mutations.add(request.method to path)
                completed = false
                respond("{}", HttpStatusCode.OK, jsonHeaders)
            } else {
                val body = when (path) {
                    "/inbox/completed-in-two-minutes" -> testJson.encodeToString(if (completed) listOf(fixture.completed) else emptyList())
                    "/inbox/projects" -> "{}"
                    "/inbox" -> testJson.encodeToString(listOf(fixture.inbox))
                    "/inbox/trash" -> testJson.encodeToString(listOf(fixture.trash))
                    else -> error(path)
                }
                respond(body, HttpStatusCode.OK, jsonHeaders)
            }
        })
        try {
            val module = fixture.module(client)
            val loaded = module.completed.getCompletedInTwoMinutes().getOrThrow().single()
            assertEquals("Tytuł użytkownika", loaded.title)
            assertEquals(loaded, module.store.completedItemsFlow.value.single())
            module.completed.restoreFromTwoMinutes(loaded.itemId).getOrThrow()
            assertTrue(module.store.completedItemsFlow.value.isEmpty())
            assertEquals(loaded.itemId, module.store.itemsFlow.value.single().itemId)
            completed = true
            module.completed.getCompletedInTwoMinutes().getOrThrow()
            module.completed.deleteCompletedInTwoMinutes(loaded.itemId).getOrThrow()
            assertTrue(module.store.completedItemsFlow.value.isEmpty())
            assertEquals(loaded.itemId, module.store.trashItemsFlow.value.single().itemId)
            assertEquals(listOf(
                HttpMethod.Post to "/inbox/item-1/restore-from-two-minutes",
                HttpMethod.Delete to "/inbox/item-1/completed-in-two-minutes",
            ), mutations)
        } finally { client.close() }
    }

    @Test
    fun failedMutationKeepsCompletedItemVisible() = runBlocking {
        val fixture = encryptedFixture()
        val client = mockClient(MockEngine { respond("failure", HttpStatusCode.InternalServerError, jsonHeaders) })
        try {
            val module = fixture.module(client)
            val item = fixture.mapper.completed(fixture.completed)
            module.store.cacheCompleted(listOf(item))
            assertTrue(module.completed.restoreFromTwoMinutes(item.itemId).isFailure)
            assertEquals(listOf(item), module.store.completedItemsFlow.value)
            assertTrue(module.completed.deleteCompletedInTwoMinutes(item.itemId).isFailure)
            assertEquals(listOf(item), module.store.completedItemsFlow.value)
        } finally { client.close() }
    }
}
