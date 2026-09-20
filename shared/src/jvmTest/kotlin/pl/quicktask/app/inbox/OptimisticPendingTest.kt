package pl.quicktask.app.inbox



import pl.quicktask.app.items.model.InboxItem

import pl.quicktask.app.inbox.presentation.*
import pl.quicktask.app.trash.model.*
import pl.quicktask.app.trash.presentation.*

import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.runBlocking
import pl.quicktask.app.auth.crypto.createItemKey
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.lifecycle.viewModelScope

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OptimisticPendingTest {
    private val viewModels = androidx.lifecycle.ViewModelStore()
    private val clients = mutableListOf<io.ktor.client.HttpClient>()

    @kotlin.test.BeforeTest
    fun setupDispatcher() {
        kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
    }

    @kotlin.test.AfterTest
    fun cleanup() {
        val jobs = viewModels.keys().mapNotNull { viewModels[it]?.viewModelScope?.coroutineContext?.get(kotlinx.coroutines.Job) }
        viewModels.clear()
        clients.forEach { it.close() }
        kotlinx.coroutines.runBlocking { jobs.forEach { it.join() } }
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private suspend fun module(): pl.quicktask.app.items.di.ItemModule {
        val client = pl.quicktask.app.items.support.mockClient(io.ktor.client.engine.mock.MockEngine {
            respond("[]", headers = pl.quicktask.app.items.support.jsonHeaders)
        })
        clients.add(client)
        return pl.quicktask.app.items.support.encryptedFixture().module(client)
    }


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

        val module = module()
        val viewModel = InboxViewModel(module.inbox, module.store, module.lifecycle, module.completed)
        viewModels.put("inbox", viewModel)

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

        val module = module()
        val viewModel = TrashViewModel(module.trash, module.store)
        viewModels.put("trash", viewModel)

        viewModel.requestPermanentDelete(pendingTrashItem)
        assertNull(viewModel.uiState.value.itemToPermanentlyDelete)
    }
}
