package pl.quicktask.app.inbox

import kotlinx.coroutines.runBlocking
import pl.quicktask.app.auth.createItemKey
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OptimisticPendingTest {

    @Test
    fun testInboxItemIsPendingConfirmation() = runBlocking {
        val key = createItemKey()
        val confirmedItem = InboxItem(
            itemId = "real-uuid-123",
            title = "Confirmed task",
            note = "",
            itemKey = key,
            createdAt = "",
            updatedAt = "",
            isPending = false,
        )
        assertFalse(confirmedItem.isPendingConfirmation)

        val optimisticCreatedItem = InboxItem(
            itemId = "temp_12345",
            title = "Optimistic task",
            note = "",
            itemKey = key,
            createdAt = "",
            updatedAt = "",
            isPending = true,
        )
        assertTrue(optimisticCreatedItem.isPendingConfirmation)

        val optimisticUpdatedItem = confirmedItem.copy(isPending = true)
        assertTrue(optimisticUpdatedItem.isPendingConfirmation)
    }

    @Test
    fun testTrashItemIsPendingConfirmation() = runBlocking {
        val key = createItemKey()
        val confirmedTrashItem = TrashItem(
            itemId = "trash-uuid-123",
            title = "Trash task",
            note = "",
            itemKey = key,
            createdAt = "",
            updatedAt = "",
            deletedAt = "",
            purgeAfter = null,
            isPending = false,
        )
        assertFalse(confirmedTrashItem.isPendingConfirmation)

        val pendingTrashItem = confirmedTrashItem.copy(isPending = true)
        assertTrue(pendingTrashItem.isPendingConfirmation)

        val tempTrashItem = confirmedTrashItem.copy(itemId = "temp_999")
        assertTrue(tempTrashItem.isPendingConfirmation)
    }

    @Test
    fun testInboxViewModelIgnoresActionsOnPendingItem() = runBlocking {
        val key = createItemKey()
        val pendingItem = InboxItem(
            itemId = "temp_98765",
            title = "Pending Task",
            note = "Note",
            itemKey = key,
            createdAt = "",
            updatedAt = "",
            isPending = true,
        )

        val viewModel = InboxViewModel()

        viewModel.openEditDialog(pendingItem)
        assertFalse(viewModel.uiState.value.showAddDialog)
        assertNull(viewModel.uiState.value.editingItem)

        viewModel.requestDeleteItem(pendingItem)
        assertNull(viewModel.uiState.value.itemToDelete)

        viewModel.startTwoMinuteTimer(pendingItem)
        assertNull(viewModel.uiState.value.activeTwoMinuteItem)
    }

    @Test
    fun testTrashViewModelIgnoresActionsOnPendingItem() = runBlocking {
        val key = createItemKey()
        val pendingTrashItem = TrashItem(
            itemId = "temp_trash_123",
            title = "Pending Trash Item",
            note = "",
            itemKey = key,
            createdAt = "",
            updatedAt = "",
            deletedAt = "",
            purgeAfter = null,
            isPending = true,
        )

        val viewModel = TrashViewModel()

        viewModel.requestPermanentDelete(pendingTrashItem)
        assertNull(viewModel.uiState.value.itemToPermanentlyDelete)
    }
}
