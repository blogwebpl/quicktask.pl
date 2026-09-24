package pl.quicktask.app.completed.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import pl.quicktask.app.items.data.CompletedItemsOperations
import pl.quicktask.app.items.model.CompletedInTwoMinutesItem
import pl.quicktask.app.items.presentation.itemErrorResource
import pl.quicktask.app.items.store.ItemStore
import todo.shared.generated.resources.*

data class CompletedUiState(
    val items: List<CompletedInTwoMinutesItem> = emptyList(),
    val isLoading: Boolean = false,
    val operatingIds: Set<String> = emptySet(),
    val openedItemId: String? = null,
    val errorMessageRes: StringResource? = null,
)

class CompletedViewModel(
    private val repository: CompletedItemsOperations,
    private val store: ItemStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CompletedUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.completedItemsFlow.collect { items ->
                _uiState.update { state -> state.copy(
                    items = items,
                    openedItemId = state.openedItemId?.takeIf { id -> items.any { it.itemId == id } },
                ) }
            }
        }
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                repository.getCompletedInTwoMinutes().getOrThrow()
            } catch (error: Exception) {
                showError(error, Res.string.error_fetch_items)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun openItem(itemId: String) { _uiState.update { it.copy(openedItemId = itemId) } }
    fun closeItem() { _uiState.update { it.copy(openedItemId = null) } }
    fun restoreItem(itemId: String) = operate(itemId, Res.string.error_restore_item) {
        repository.restoreFromTwoMinutes(itemId)
    }
    fun deleteItem(itemId: String) = operate(itemId, Res.string.error_delete_item) {
        repository.deleteCompletedInTwoMinutes(itemId)
    }

    private fun operate(itemId: String, fallback: StringResource, action: suspend () -> Result<Unit>) {
        val state = _uiState.value
        if (itemId in state.operatingIds || state.items.none { it.itemId == itemId && !it.isPendingConfirmation }) return
        _uiState.update { it.copy(operatingIds = it.operatingIds + itemId, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                action().getOrThrow()
            } catch (error: Exception) {
                store.invalidateCache()
                showError(error, fallback)
            } finally {
                _uiState.update { it.copy(operatingIds = it.operatingIds - itemId) }
            }
        }
    }

    private fun showError(error: Throwable, fallback: StringResource) {
        if (error is CancellationException) throw error
        _uiState.update { it.copy(errorMessageRes = itemErrorResource(error, fallback)) }
    }
}
