package pl.quicktask.app.trash.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import pl.quicktask.app.common.AppLoggerManager
import pl.quicktask.app.items.presentation.itemErrorResource
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.items.store.TrashOperationKind
import pl.quicktask.app.trash.data.TrashOperations
import pl.quicktask.app.trash.model.TrashItem
import todo.shared.generated.resources.*

data class TrashUiState(
    val isLoading: Boolean = false,
    val items: List<TrashItem> = emptyList(),
    val errorMessageRes: StringResource? = null,
    val itemToPermanentlyDelete: TrashItem? = null,
    val showEmptyTrashConfirmation: Boolean = false,
    val itemOperations: Map<String, TrashOperationKind> = emptyMap(),
    val isEmptyingTrash: Boolean = false,
) {
    val canEmptyTrash get() = !isEmptyingTrash && itemOperations.isEmpty()
    fun isOperating(itemId: String) = isEmptyingTrash || itemId in itemOperations
}

class TrashViewModel(
    private val repository: TrashOperations,
    private val store: ItemStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.trashItemsFlow.collect { items ->
                AppLoggerManager.logStateChange("TrashViewModel", "Aktualizacja elementów kosza", "count=${items.size}")
                _uiState.update { it.copy(items = items) }
            }
        }
        loadTrash()
    }

    fun loadTrash() {
        AppLoggerManager.logFunction("TrashViewModel", "loadTrash")
        AppLoggerManager.logRefresh("TrashViewModel", "Pobieranie zawartości kosza")
        _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                repository.getTrashItems().onFailure { error -> showError(error, Res.string.error_fetch_trash) }
            } finally { _uiState.update { it.copy(isLoading = false) } }
        }
    }

    fun restoreItem(itemId: String) {
        AppLoggerManager.logFunction("TrashViewModel", "restoreItem", "itemId=$itemId")
        if (_uiState.value.items.find { it.itemId == itemId }?.isPendingConfirmation == true) return
        runOperation(itemId, TrashOperationKind.Restore, Res.string.error_restore_item) { repository.restoreFromTrash(itemId) }
    }

    fun requestPermanentDelete(item: TrashItem) {
        AppLoggerManager.logFunction("TrashViewModel", "requestPermanentDelete", "itemId=${item.itemId}")
        if (item.isPendingConfirmation || _uiState.value.isOperating(item.itemId)) return
        _uiState.update { it.copy(itemToPermanentlyDelete = item) }
    }

    fun cancelPermanentDelete() {
        AppLoggerManager.logFunction("TrashViewModel", "cancelPermanentDelete")
        _uiState.update { it.copy(itemToPermanentlyDelete = null) }
    }

    fun confirmPermanentDelete() {
        AppLoggerManager.logFunction("TrashViewModel", "confirmPermanentDelete")
        val item = _uiState.value.itemToPermanentlyDelete ?: return
        if (item.isPendingConfirmation) { cancelPermanentDelete(); return }
        runOperation(item.itemId, TrashOperationKind.PermanentDelete, Res.string.error_permanent_delete) {
            repository.permanentlyDeleteItem(item.itemId)
        }
    }

    fun requestEmptyTrash() {
        AppLoggerManager.logFunction("TrashViewModel", "requestEmptyTrash")
        if (_uiState.value.canEmptyTrash) _uiState.update { it.copy(showEmptyTrashConfirmation = true) }
    }
    fun cancelEmptyTrash() {
        AppLoggerManager.logFunction("TrashViewModel", "cancelEmptyTrash")
        _uiState.update { it.copy(showEmptyTrashConfirmation = false) }
    }
    fun confirmEmptyTrash() {
        AppLoggerManager.logFunction("TrashViewModel", "confirmEmptyTrash")
        if (!_uiState.value.showEmptyTrashConfirmation) return
        runOperation(null, null, Res.string.error_empty_trash) { repository.emptyTrash() }
    }

    private fun runOperation(
        itemId: String?, kind: TrashOperationKind?, fallback: StringResource,
        operation: suspend () -> Result<Unit>,
    ) {
        while (true) {
            val before = _uiState.value
            if (if (itemId == null) !before.canEmptyTrash else before.isOperating(itemId)) return
            val after = before.copy(
                itemOperations = if (itemId != null) before.itemOperations + (itemId to requireNotNull(kind)) else before.itemOperations,
                isEmptyingTrash = itemId == null,
                errorMessageRes = null,
            )
            if (_uiState.compareAndSet(before, after)) break
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                operation().onSuccess {
                    AppLoggerManager.logStateChange("TrashViewModel", "Operacja kosza zakończona sukcesem")
                    _uiState.update { state -> state.copy(
                        itemToPermanentlyDelete = if (kind == TrashOperationKind.PermanentDelete && state.itemToPermanentlyDelete?.itemId == itemId) null else state.itemToPermanentlyDelete,
                        showEmptyTrashConfirmation = if (itemId == null) false else state.showEmptyTrashConfirmation,
                    ) }
                }.onFailure { error -> showError(error, fallback) }
            } catch (error: CancellationException) {
                store.invalidateCache()
                throw error
            } catch (error: Exception) {
                store.invalidateCache()
                showError(error, fallback)
            } finally {
                _uiState.update { it.copy(
                    itemOperations = if (itemId != null) it.itemOperations - itemId else it.itemOperations,
                    isEmptyingTrash = if (itemId == null) false else it.isEmptyingTrash,
                    itemToPermanentlyDelete = if (kind == TrashOperationKind.PermanentDelete && it.itemToPermanentlyDelete?.itemId == itemId) null else it.itemToPermanentlyDelete,
                    showEmptyTrashConfirmation = if (itemId == null) false else it.showEmptyTrashConfirmation,
                ) }
            }
        }
    }

    private fun showError(error: Throwable, fallback: StringResource) {
        if (error is CancellationException) throw error
        _uiState.update { it.copy(errorMessageRes = itemErrorResource(error, fallback)) }
    }
}
