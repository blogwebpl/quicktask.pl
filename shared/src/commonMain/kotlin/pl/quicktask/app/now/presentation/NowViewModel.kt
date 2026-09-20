package pl.quicktask.app.now.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import pl.quicktask.app.common.AppLoggerManager
import pl.quicktask.app.items.presentation.itemErrorResource
import pl.quicktask.app.nextactions.data.NextActionsOperations
import pl.quicktask.app.nextactions.model.NewContextInput
import pl.quicktask.app.now.data.NowOperations
import pl.quicktask.app.now.model.NowItem
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_delete_item
import todo.shared.generated.resources.error_fetch_items
import todo.shared.generated.resources.error_save_item

class NowViewModel(
    private val nowRepository: NowOperations,
    private val nextActionsRepository: NextActionsOperations,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NowUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadNowData()
    }

    fun loadNowData() {
        AppLoggerManager.logFunction("NowViewModel", "loadNowData")
        AppLoggerManager.logRefresh("NowViewModel", "Pobieranie danych na teraz")
        _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                nowRepository.getNowData().onSuccess { data ->
                    AppLoggerManager.logStateChange("NowViewModel", "Pobrano dane na teraz")
                    _uiState.update { it.copy(nowData = data) }
                }.onFailure { error ->
                    showError(error, Res.string.error_fetch_items)
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadOptions() {
        AppLoggerManager.logFunction("NowViewModel", "loadOptions")
        viewModelScope.launch {
            nextActionsRepository.getNextActionOptions().onSuccess { options ->
                _uiState.update { it.copy(options = options) }
            }
        }
    }

    fun openEditDialog(item: NowItem) {
        AppLoggerManager.logFunction("NowViewModel", "openEditDialog", "itemId=${item.itemId}")
        _uiState.update { it.copy(showAddEditDialog = true, selectedItemToEdit = item) }
        loadOptions()
    }

    fun closeAddEditDialog() {
        AppLoggerManager.logFunction("NowViewModel", "closeAddEditDialog")
        _uiState.update { it.copy(showAddEditDialog = false, selectedItemToEdit = null) }
    }

    fun updateNextAction(
        item: NowItem,
        title: String,
        note: String,
        projectId: String?,
        dueAt: String?,
        contextIds: List<String>,
        newContextNames: List<String>,
        newContexts: List<NewContextInput> = emptyList(),
        tagIds: List<String>,
        newTagNames: List<String>,
    ) {
        AppLoggerManager.logFunction("NowViewModel", "updateNextAction", "itemId=${item.itemId}")
        _uiState.update { it.copy(isSubmitting = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                nextActionsRepository.updateNextAction(
                    item = item.toNextAction(),
                    title = title,
                    note = note,
                    projectId = projectId,
                    dueAt = dueAt,
                    contextIds = contextIds,
                    newContextNames = newContextNames,
                    newContexts = newContexts,
                    tagIds = tagIds,
                    newTagNames = newTagNames,
                ).onSuccess {
                    closeAddEditDialog()
                    loadNowData()
                    loadOptions()
                }.onFailure { error ->
                    showError(error, Res.string.error_save_item)
                }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun deleteItem(itemId: String) {
        AppLoggerManager.logFunction("NowViewModel", "deleteItem", "itemId=$itemId")
        viewModelScope.launch {
            nowRepository.deleteItem(itemId).onSuccess {
                loadNowData()
            }.onFailure { error ->
                showError(error, Res.string.error_delete_item)
            }
        }
    }

    fun restoreToInbox(itemId: String) {
        AppLoggerManager.logFunction("NowViewModel", "restoreToInbox", "itemId=$itemId")
        viewModelScope.launch {
            nowRepository.restoreToInbox(itemId).onSuccess {
                loadNowData()
            }.onFailure { error ->
                showError(error, Res.string.error_save_item)
            }
        }
    }

    private fun showError(error: Throwable, fallback: StringResource) {
        if (error is CancellationException) throw error
        _uiState.update { it.copy(errorMessageRes = itemErrorResource(error, fallback)) }
    }
}
