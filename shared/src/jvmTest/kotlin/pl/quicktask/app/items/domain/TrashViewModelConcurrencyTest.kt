package pl.quicktask.app.items.domain

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.items.support.encryptedFixture
import pl.quicktask.app.trash.data.TrashOperations
import pl.quicktask.app.trash.presentation.TrashViewModel
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelConcurrencyTest {
    @Test
    fun itemOperationsAreIndependentAndEmptyingIsExclusive() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModels = ViewModelStore()
        try {
            val fixture = encryptedFixture()
            val item = fixture.mapper.trash(fixture.trash)
            val second = item.copy(itemId = "second")
            val store = ItemStore().apply { cacheTrash(listOf(item, second)) }
            val firstGate = CompletableDeferred<Unit>()
            val secondGate = CompletableDeferred<Unit>()
            val emptyGate = CompletableDeferred<Unit>()
            val calls = mutableListOf<String>()
            val repository = object : TrashOperations {
                override suspend fun getTrashItems(forceFetch: Boolean) = Result.success(listOf(item, second))
                override suspend fun restoreFromTrash(itemId: String): Result<Unit> {
                    calls.add(itemId)
                    if (itemId == item.itemId) firstGate.await() else secondGate.await()
                    return if (itemId == item.itemId) Result.failure(IllegalStateException("failed")) else Result.success(Unit)
                }
                override suspend fun permanentlyDeleteItem(itemId: String) = restoreFromTrash(itemId)
                override suspend fun emptyTrash(): Result<Unit> { calls.add("empty"); emptyGate.await(); return Result.success(Unit) }
            }
            val vm = TrashViewModel(repository, store)
            viewModels.put("trash", vm)
            vm.restoreItem(item.itemId)
            vm.restoreItem(second.itemId)
            vm.restoreItem(item.itemId)
            vm.requestEmptyTrash()
            vm.confirmEmptyTrash()
            assertEquals(listOf(item.itemId, second.itemId), calls)
            assertEquals(2, vm.uiState.value.itemOperations.size)
            firstGate.complete(Unit)
            assertNotNull(vm.uiState.value.errorMessageRes)
            assertFalse(vm.uiState.value.isOperating(item.itemId))
            assertTrue(vm.uiState.value.isOperating(second.itemId))
            secondGate.complete(Unit)
            assertTrue(vm.uiState.value.canEmptyTrash)
            vm.requestEmptyTrash(); vm.confirmEmptyTrash()
            assertTrue(vm.uiState.value.isEmptyingTrash)
            vm.restoreItem(item.itemId)
            assertEquals(listOf(item.itemId, second.itemId, "empty"), calls)
            emptyGate.complete(Unit)
            assertTrue(vm.uiState.value.canEmptyTrash)
            assertFalse(vm.uiState.value.showEmptyTrashConfirmation)
        } finally { viewModels.clear(); Dispatchers.resetMain() }
    }

    @Test
    fun clearingViewModelReleasesOperationsAndInvalidatesCache() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModels = ViewModelStore()
        try {
            val store = ItemStore()
            val repository = object : TrashOperations {
                override suspend fun getTrashItems(forceFetch: Boolean) = Result.success(emptyList<pl.quicktask.app.trash.model.TrashItem>())
                override suspend fun restoreFromTrash(itemId: String): Result<Unit> { awaitCancellation() }
                override suspend fun permanentlyDeleteItem(itemId: String) = restoreFromTrash(itemId)
                override suspend fun emptyTrash() = restoreFromTrash("all")
            }
            val vm = TrashViewModel(repository, store)
            viewModels.put("trash", vm)
            vm.restoreItem("first")
            assertTrue(vm.uiState.value.isOperating("first"))
            viewModels.clear()
            assertTrue(vm.uiState.value.itemOperations.isEmpty())
            assertFalse(store.isTrashCacheValid)
        } finally { viewModels.clear(); Dispatchers.resetMain() }
    }
}
