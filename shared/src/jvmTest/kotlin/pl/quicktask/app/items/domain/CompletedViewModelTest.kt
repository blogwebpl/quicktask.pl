package pl.quicktask.app.items.domain

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import pl.quicktask.app.completed.presentation.CompletedViewModel
import pl.quicktask.app.items.data.CompletedItemsOperations
import pl.quicktask.app.items.model.CompletedInTwoMinutesItem
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.items.support.encryptedFixture
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class CompletedViewModelTest {
    @Test
    fun preventsDuplicateOperationsAndClosesDetailsWhenItemLeavesList() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val models = ViewModelStore()
        try {
            val fixture = encryptedFixture()
            val item = fixture.mapper.completed(fixture.completed)
            val store = ItemStore().apply { cacheCompleted(listOf(item)) }
            val gate = CompletableDeferred<Unit>()
            var calls = 0
            val repository = object : CompletedItemsOperations {
                override suspend fun getCompletedInTwoMinutes() = Result.success(store.completedItemsFlow.value)
                override suspend fun completeInTwoMinutes(itemId: String, deleteAttachments: Boolean) = error("Unused")
                override suspend fun restoreFromTwoMinutes(itemId: String): Result<Unit> {
                    calls++
                    gate.await()
                    store.applyRemoval(itemId)
                    return Result.success(Unit)
                }
                override suspend fun deleteCompletedInTwoMinutes(itemId: String) = restoreFromTwoMinutes(itemId)
            }
            val vm = CompletedViewModel(repository, store)
            models.put("completed", vm)
            vm.openItem(item.itemId)
            vm.restoreItem(item.itemId)
            vm.restoreItem(item.itemId)
            vm.deleteItem(item.itemId)
            assertEquals(1, calls)
            assertTrue(item.itemId in vm.uiState.value.operatingIds)
            gate.complete(Unit)
            assertTrue(vm.uiState.value.items.isEmpty())
            assertNull(vm.uiState.value.openedItemId)
            assertTrue(vm.uiState.value.operatingIds.isEmpty())
        } finally { models.clear(); Dispatchers.resetMain() }
    }

    @Test
    fun failedLoadCanBeRetried() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val models = ViewModelStore()
        try {
            var fail = true
            val repository = object : CompletedItemsOperations {
                override suspend fun getCompletedInTwoMinutes(): Result<List<CompletedInTwoMinutesItem>> =
                    if (fail) Result.failure(IllegalStateException("offline")) else Result.success(emptyList())
                override suspend fun completeInTwoMinutes(itemId: String, deleteAttachments: Boolean) = error("Unused")
                override suspend fun restoreFromTwoMinutes(itemId: String) = error("Unused")
                override suspend fun deleteCompletedInTwoMinutes(itemId: String) = error("Unused")
            }
            val vm = CompletedViewModel(repository, ItemStore())
            models.put("completed", vm)
            assertNotNull(vm.uiState.value.errorMessageRes)
            assertFalse(vm.uiState.value.isLoading)
            fail = false
            vm.refresh()
            assertNull(vm.uiState.value.errorMessageRes)
            assertFalse(vm.uiState.value.isLoading)
        } finally { models.clear(); Dispatchers.resetMain() }
    }
}
