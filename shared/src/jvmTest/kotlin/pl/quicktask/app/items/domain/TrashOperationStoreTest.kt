package pl.quicktask.app.items.domain

import kotlinx.coroutines.runBlocking
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.items.store.TrashOperationKind
import pl.quicktask.app.items.support.encryptedFixture
import kotlin.test.*

class TrashOperationStoreTest {
    @Test
    fun pendingOverlaysSurviveSnapshotsAndSyncInEitherCompletionOrder() = runBlocking {
        val fixture = encryptedFixture()
        val item = fixture.mapper.trash(fixture.trash)
        for (reverse in listOf(false, true)) {
            val store = ItemStore()
            val second = item.copy(itemId = "second")
            store.cacheTrash(listOf(item, second))
            val firstOperation = assertNotNull(store.beginTrashOperation(item.itemId, TrashOperationKind.Restore))
            val secondOperation = assertNotNull(store.beginTrashOperation(second.itemId, TrashOperationKind.PermanentDelete))
            assertNull(store.beginTrashOperation(item.itemId, TrashOperationKind.Restore))
            assertNull(store.beginTrashOperation(null, null))
            assertNull(store.beginOperation(item.itemId, null))
            assertTrue(store.trashItemsFlow.value.isEmpty())
            store.cacheTrash(listOf(item, second))
            store.applyTrash(second, second.itemId)
            assertTrue(store.trashItemsFlow.value.isEmpty())
            store.finishTrashOperation(if (reverse) secondOperation else firstOperation)
            assertEquals(listOf(if (reverse) second.itemId else item.itemId), store.trashItemsFlow.value.map { it.itemId })
            store.finishTrashOperation(if (reverse) firstOperation else secondOperation)
            assertEquals(2, store.trashItemsFlow.value.size)
            assertTrue(store.trashOperationsFlow.value.isEmpty())
            val exclusive = assertNotNull(store.beginTrashOperation(null, null))
            assertNull(store.beginTrashOperation(item.itemId, TrashOperationKind.Restore))
            assertTrue(store.trashItemsFlow.value.isEmpty())
            store.finishTrashOperation(exclusive)
            assertFalse(store.emptyingTrashFlow.value)
        }
    }
}
