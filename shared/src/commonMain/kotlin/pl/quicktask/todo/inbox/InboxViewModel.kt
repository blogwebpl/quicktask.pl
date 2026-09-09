package pl.quicktask.todo.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InboxUiState(
    val isLoading: Boolean = false,
    val items: List<InboxItem> = emptyList(),
    val errorMessage: String? = null,
    val showAddDialog: Boolean = false,
    val isSubmitting: Boolean = false,
)

class InboxViewModel(
    private val repository: InboxRepository = InboxRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    init {
        loadItems()
    }

    fun loadItems() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = repository.getItems()
            result.onSuccess { items ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    items = items,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "Błąd pobierania elementów",
                )
            }
        }
    }

    fun openAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true)
    }

    fun dismissAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun addItem(title: String, note: String) {
        if (title.isBlank()) return
        _uiState.value = _uiState.value.copy(isSubmitting = true)
        viewModelScope.launch {
            val result = repository.createInboxItem(title = title, note = note)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    showAddDialog = false,
                )
                loadItems()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    errorMessage = error.message ?: "Błąd dodawania elementu",
                )
            }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            val result = repository.deleteItem(itemId)
            result.onSuccess {
                loadItems()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message ?: "Błąd usuwania elementu",
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
