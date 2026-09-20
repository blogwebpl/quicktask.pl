package pl.quicktask.app.nextactions.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import pl.quicktask.app.common.AppLoggerManager
import pl.quicktask.app.items.model.InputFile
import pl.quicktask.app.items.presentation.itemErrorResource
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.nextactions.data.NextActionsOperations
import pl.quicktask.app.nextactions.model.NewContextInput
import pl.quicktask.app.nextactions.model.NextAction
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_delete_item
import todo.shared.generated.resources.error_fetch_items
import todo.shared.generated.resources.error_save_item

class NextActionsViewModel(
    private val repository: NextActionsOperations,
    private val store: ItemStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NextActionsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.nextActionsFlow.collect { items ->
                AppLoggerManager.logStateChange("NextActionsViewModel", "Aktualizacja listy najbliższych działań", "count=${items.size}")
                _uiState.update { it.copy(items = items) }
            }
        }
        loadNextActions()
        loadOptions()
    }

    fun loadNextActions(forceFetch: Boolean = false) {
        AppLoggerManager.logFunction("NextActionsViewModel", "loadNextActions", "forceFetch=$forceFetch")
        AppLoggerManager.logRefresh("NextActionsViewModel", "Pobieranie najbliższych działań")
        _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                repository.getNextActions(forceFetch).onFailure { error ->
                    showError(error, Res.string.error_fetch_items)
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadOptions() {
        AppLoggerManager.logFunction("NextActionsViewModel", "loadOptions")
        viewModelScope.launch {
            repository.getNextActionOptions().onSuccess { options ->
                _uiState.update { it.copy(options = options) }
            }
        }
    }

    fun setContextFilter(contextFilter: String?) {
        AppLoggerManager.logFunction("NextActionsViewModel", "setContextFilter", "contextFilter=$contextFilter")
        AppLoggerManager.logStateChange("NextActionsViewModel", "Zmiana filtra kontekstu", "filter=$contextFilter")
        _uiState.update { it.copy(selectedContextFilter = contextFilter) }
    }

    fun setProjectFilter(projectFilter: String?) {
        AppLoggerManager.logFunction("NextActionsViewModel", "setProjectFilter", "projectFilter=$projectFilter")
        AppLoggerManager.logStateChange("NextActionsViewModel", "Zmiana filtra projektu", "filter=$projectFilter")
        _uiState.update { it.copy(selectedProjectIdFilter = projectFilter) }
    }

    fun openAddDialog() {
        AppLoggerManager.logFunction("NextActionsViewModel", "openAddDialog")
        _uiState.update { it.copy(showAddEditDialog = true, selectedItemToEdit = null) }
        loadOptions()
    }

    fun openEditDialog(item: NextAction) {
        AppLoggerManager.logFunction("NextActionsViewModel", "openEditDialog", "itemId=${item.itemId}")
        _uiState.update { it.copy(showAddEditDialog = true, selectedItemToEdit = item) }
        loadOptions()
    }

    fun closeAddEditDialog() {
        AppLoggerManager.logFunction("NextActionsViewModel", "closeAddEditDialog")
        _uiState.update { it.copy(showAddEditDialog = false, selectedItemToEdit = null) }
    }

    fun createNextAction(
        title: String,
        note: String,
        projectId: String?,
        dueAt: String?,
        contextIds: List<String>,
        newContextNames: List<String>,
        newContexts: List<NewContextInput> = emptyList(),
        tagIds: List<String>,
        newTagNames: List<String>,
        files: List<InputFile>,
    ) {
        AppLoggerManager.logFunction("NextActionsViewModel", "createNextAction", "title=$title")
        _uiState.update { it.copy(isSubmitting = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                repository.createNextAction(
                    title = title,
                    note = note,
                    projectId = projectId,
                    dueAt = dueAt,
                    contextIds = contextIds,
                    newContextNames = newContextNames,
                    newContexts = newContexts,
                    tagIds = tagIds,
                    newTagNames = newTagNames,
                    files = files,
                ).onSuccess {
                    closeAddEditDialog()
                    loadNextActions(forceFetch = true)
                    loadOptions()
                }.onFailure { error ->
                    showError(error, Res.string.error_save_item)
                }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun updateNextAction(
        item: NextAction,
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
        AppLoggerManager.logFunction("NextActionsViewModel", "updateNextAction", "itemId=${item.itemId}")
        _uiState.update { it.copy(isSubmitting = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                repository.updateNextAction(
                    item = item,
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
                    loadNextActions(forceFetch = true)
                    loadOptions()
                }.onFailure { error ->
                    showError(error, Res.string.error_save_item)
                }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun deleteNextAction(itemId: String) {
        AppLoggerManager.logFunction("NextActionsViewModel", "deleteNextAction", "itemId=$itemId")
        viewModelScope.launch {
            repository.deleteNextAction(itemId).onFailure { error ->
                showError(error, Res.string.error_delete_item)
            }
        }
    }

    fun restoreToInbox(itemId: String) {
        AppLoggerManager.logFunction("NextActionsViewModel", "restoreToInbox", "itemId=$itemId")
        viewModelScope.launch {
            repository.restoreToInbox(itemId).onFailure { error ->
                showError(error, Res.string.error_save_item)
            }
        }
    }

    private fun showError(error: Throwable, fallback: StringResource) {
        if (error is CancellationException) throw error
        _uiState.update { it.copy(errorMessageRes = itemErrorResource(error, fallback)) }
    }
}
