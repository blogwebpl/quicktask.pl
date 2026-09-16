package pl.quicktask.app.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_empty_trash
import todo.shared.generated.resources.error_fetch_trash
import todo.shared.generated.resources.error_permanent_delete
import todo.shared.generated.resources.error_restore_item

data class TrashUiState(
    val isLoading: Boolean = false,
    val items: List<TrashItem> = emptyList(),
    val errorMessageRes: StringResource? = null,
    val errorMessage: String? = null,
    val itemToPermanentlyDelete: TrashItem? = null,
    val showEmptyTrashConfirmation: Boolean = false,
    val isOperating: Boolean = false,
)

class TrashViewModel(
    private val repository: InboxRepository = sharedInboxRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.trashItemsFlow.collect { items ->
                _uiState.value = _uiState.value.copy(items = items)
            }
        }
        loadTrash()
    }

    fun loadTrash() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, errorMessageRes = null)
        viewModelScope.launch {
            repository.getTrashItems()
                .onSuccess { items ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        items = items,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessageRes = Res.string.error_fetch_trash,
                        errorMessage = error.message,
                    )
                }
        }
    }

    fun restoreItem(itemId: String) {
        val targetItem = _uiState.value.items.find { it.itemId == itemId }
        if (targetItem?.isPendingConfirmation == true) return

        _uiState.value = _uiState.value.copy(isOperating = true, errorMessage = null, errorMessageRes = null)
        viewModelScope.launch {
            repository.restoreFromTrash(itemId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isOperating = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isOperating = false,
                        errorMessageRes = Res.string.error_restore_item,
                        errorMessage = error.message,
                    )
                }
        }
    }

    fun requestPermanentDelete(item: TrashItem) {
        if (item.isPendingConfirmation) return
        _uiState.value = _uiState.value.copy(itemToPermanentlyDelete = item)
    }

    fun cancelPermanentDelete() {
        _uiState.value = _uiState.value.copy(itemToPermanentlyDelete = null)
    }

    fun confirmPermanentDelete() {
        val item = _uiState.value.itemToPermanentlyDelete ?: return
        if (item.isPendingConfirmation) {
            _uiState.value = _uiState.value.copy(itemToPermanentlyDelete = null)
            return
        }
        _uiState.value = _uiState.value.copy(
            isOperating = true,
            errorMessage = null,
            errorMessageRes = null,
        )
        viewModelScope.launch {
            repository.permanentlyDeleteItem(item.itemId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        itemToPermanentlyDelete = null,
                        isOperating = false,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        itemToPermanentlyDelete = null,
                        isOperating = false,
                        errorMessageRes = Res.string.error_permanent_delete,
                        errorMessage = error.message,
                    )
                }
        }
    }

    fun requestEmptyTrash() {
        _uiState.value = _uiState.value.copy(showEmptyTrashConfirmation = true)
    }

    fun cancelEmptyTrash() {
        _uiState.value = _uiState.value.copy(showEmptyTrashConfirmation = false)
    }

    fun confirmEmptyTrash() {
        _uiState.value = _uiState.value.copy(
            isOperating = true,
            errorMessage = null,
            errorMessageRes = null,
        )
        viewModelScope.launch {
            repository.emptyTrash()
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        showEmptyTrashConfirmation = false,
                        isOperating = false,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        showEmptyTrashConfirmation = false,
                        isOperating = false,
                        errorMessageRes = Res.string.error_empty_trash,
                        errorMessage = error.message,
                    )
                }
        }
    }
}
