package pl.quicktask.app.items.domain

import pl.quicktask.app.items.support.testJson
import pl.quicktask.app.items.support.jsonHeaders
import pl.quicktask.app.items.support.mockClient
import pl.quicktask.app.items.support.encryptedFixture

import pl.quicktask.app.items.model.ItemSyncStateResponseDto
import pl.quicktask.app.items.store.ItemStore

import pl.quicktask.app.inbox.data.*
import pl.quicktask.app.trash.data.*
import pl.quicktask.app.trash.model.*

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import pl.quicktask.app.auth.crypto.createItemKey
import pl.quicktask.app.auth.crypto.encryptText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemStoreAndSyncTest {
    @Test
    fun storeTracksCacheAndOptimisticInboxWithoutDuplicates(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val item = fixture.mapper.inbox(fixture.inbox)
        val changed = item.copy(title = "Zmieniony")
        val store = ItemStore()
        assertFalse(store.isCacheValid)
        store.cacheInbox(listOf(item))
        assertTrue(store.isCacheValid)
        store.addOptimisticItem(changed)
        assertEquals(listOf(changed), store.itemsFlow.value)
        store.updateOptimisticItem(item)
        assertEquals(listOf(item), store.itemsFlow.value)
        store.removeOptimisticItem(item.itemId)
        assertEquals(emptyList(), store.itemsFlow.value)
        store.setOptimisticItems(listOf(item))
        store.invalidateCache()
        assertFalse(store.isCacheValid)
        assertFalse(store.isTrashCacheValid)
    }

    @Test
    fun syncMovesItemsBetweenCachedInboxAndTrashAndRemovesUnknownLocations(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val client = mockClient(MockEngine { respond("{}", HttpStatusCode.OK, jsonHeaders) })
        try {
            val module = fixture.module(client)
            val inbox = fixture.mapper.inbox(fixture.inbox)
            val trash = fixture.mapper.trash(fixture.trash)
            module.store.cacheInbox(listOf(inbox))
            module.store.cacheTrash(emptyList())

            module.sync.applySyncState(ItemSyncStateResponseDto("trash", item = fixture.sync), inbox.itemId)
            assertEquals(emptyList(), module.store.itemsFlow.value)
            assertEquals(listOf(trash.itemId), module.store.trashItemsFlow.value.map { it.itemId })
            assertEquals(listOf(trash.title), module.store.trashItemsFlow.value.map { it.title })

            module.sync.applySyncState(ItemSyncStateResponseDto("inbox", item = fixture.sync), inbox.itemId)
            assertEquals(1, module.store.itemsFlow.value.size)
            assertEquals(emptyList(), module.store.trashItemsFlow.value)

            module.sync.applySyncState(ItemSyncStateResponseDto("next-actions", item = fixture.sync), inbox.itemId)
            assertEquals(emptyList(), module.store.itemsFlow.value)
            assertEquals(emptyList(), module.store.trashItemsFlow.value)
        } finally { client.close() }
    }

    @Test
    fun removedSyncLocationRemovesItemFromBothCaches(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val client = mockClient(MockEngine { respond("{}", HttpStatusCode.OK, jsonHeaders) })
        try {
            val module = fixture.module(client)
            module.store.cacheInbox(listOf(fixture.mapper.inbox(fixture.inbox)))
            module.store.cacheTrash(listOf(fixture.mapper.trash(fixture.trash)))
            module.sync.applySyncState(ItemSyncStateResponseDto("removed", itemId = "item-1"), "fallback")
            assertEquals(emptyList(), module.store.itemsFlow.value)
            assertEquals(emptyList(), module.store.trashItemsFlow.value)
        } finally { client.close() }
    }

    @Test
    fun failedSyncStateFallsBackToRefreshingBothLists(): Unit = runBlocking {
        val fixture = encryptedFixture()
        val responses = ArrayDeque(listOf(
            "failure" to HttpStatusCode.InternalServerError,
            testJson.encodeToString(listOf(fixture.inbox)) to HttpStatusCode.OK,
            testJson.encodeToString(listOf(fixture.trash)) to HttpStatusCode.OK,
        ))
        val paths = mutableListOf<String>()
        val client = mockClient(MockEngine { request ->
            paths.add(request.url.encodedPath)
            val (body, status) = responses.removeFirst()
            respond(body, status, jsonHeaders)
        })
        try {
            val module = fixture.module(client)
            module.sync.handleSyncStateForItem("item-1").getOrThrow()
            assertEquals(listOf("/inbox/item-1/sync-state", "/inbox", "/inbox/trash"), paths)
            assertEquals(1, module.store.itemsFlow.value.size)
            assertEquals(1, module.store.trashItemsFlow.value.size)
        } finally { client.close() }
    }
}
