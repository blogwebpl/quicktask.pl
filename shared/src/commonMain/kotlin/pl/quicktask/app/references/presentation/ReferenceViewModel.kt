package pl.quicktask.app.references.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import pl.quicktask.app.items.model.InputFile
import pl.quicktask.app.items.presentation.itemErrorResource
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.references.data.ReferenceOperations
import pl.quicktask.app.references.model.ReferenceItem
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_delete_item
import todo.shared.generated.resources.error_fetch_items
import todo.shared.generated.resources.error_restore_item
import todo.shared.generated.resources.error_save_item

data class ReferenceUiState(
    val items: List<ReferenceItem> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val busyItemId: String? = null,
    val errorMessageRes: StringResource? = null,
    val uploadProgress: Float? = null,
)

class ReferenceViewModel(
    private val repository: ReferenceOperations,
    private val store: ItemStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReferenceUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.referencesFlow.collect { items -> _uiState.update { it.copy(items = items) } }
        }
        loadItems()
    }

    fun loadItems() {
        _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }
        viewModelScope.launch {
            repository.getReferenceItems(forceFetch = true)
                .onFailure { error -> showError(error, Res.string.error_fetch_items) }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun create(
        title: String,
        note: String,
        tagIds: List<String>,
        newTagNames: List<String>,
        files: List<InputFile>,
        onSuccess: () -> Unit,
    ) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, errorMessageRes = null, uploadProgress = null) }
        viewModelScope.launch {
            repository.createReference(title, note, tagIds, newTagNames, files,
                onProgress = { progress -> _uiState.update { it.copy(uploadProgress = progress) } },
            ).onSuccess {
                if (!store.isReferencesCacheValid) loadItems()
                onSuccess()
            }
                .onFailure { error -> showError(error, Res.string.error_save_item) }
            _uiState.update { it.copy(isSaving = false, uploadProgress = null) }
        }
    }

    fun updateTags(itemId: String, tagIds: List<String>, newTagNames: List<String>, onSuccess: () -> Unit) {
        operate(itemId, Res.string.error_save_item, onSuccess) {
            repository.updateTags(itemId, tagIds, newTagNames)
        }
    }

    fun restoreToInbox(itemId: String) {
        operate(itemId, Res.string.error_restore_item) { repository.restoreToInbox(itemId) }
    }

    fun moveToTrash(itemId: String) {
        operate(itemId, Res.string.error_delete_item) { repository.deleteReference(itemId) }
    }

    fun clearError() = _uiState.update { it.copy(errorMessageRes = null) }

    private fun operate(
        itemId: String,
        fallback: StringResource,
        onSuccess: () -> Unit = {},
        action: suspend () -> Result<Unit>,
    ) {
        if (_uiState.value.busyItemId != null || _uiState.value.isSaving) return
        _uiState.update { it.copy(busyItemId = itemId, errorMessageRes = null) }
        viewModelScope.launch {
            action().onSuccess {
                if (!store.isReferencesCacheValid) loadItems()
                onSuccess()
            }.onFailure { error -> showError(error, fallback) }
            _uiState.update { it.copy(busyItemId = null) }
        }
    }

    private fun showError(error: Throwable, fallback: StringResource) {
        _uiState.update { it.copy(errorMessageRes = itemErrorResource(error, fallback)) }
    }
}
