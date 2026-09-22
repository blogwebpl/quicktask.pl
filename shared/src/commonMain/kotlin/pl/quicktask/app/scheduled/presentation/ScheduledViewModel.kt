package pl.quicktask.app.scheduled.presentation

import pl.quicktask.app.scheduled.model.RecurrenceRule

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
import pl.quicktask.app.scheduled.data.ScheduledOperations
import pl.quicktask.app.nextactions.model.NewContextInput
import pl.quicktask.app.scheduled.model.ScheduledTask
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_delete_item
import todo.shared.generated.resources.error_fetch_items
import todo.shared.generated.resources.error_save_item

class ScheduledViewModel(
    private val repository: ScheduledOperations,
    private val store: ItemStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduledUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.scheduledTasksFlow.collect { items ->
                AppLoggerManager.logStateChange("ScheduledViewModel", "Aktualizacja listy zaplanowanych zadań", "count=${items.size}")
                _uiState.update { it.copy(items = items) }
            }
        }
        loadScheduledTasks()
        loadOptions()
    }

    fun loadScheduledTasks(forceFetch: Boolean = false) {
        AppLoggerManager.logFunction("ScheduledViewModel", "loadScheduledTasks", "forceFetch=$forceFetch")
        AppLoggerManager.logRefresh("ScheduledViewModel", "Pobieranie zaplanowanych zadań")
        _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                repository.getScheduledTasks(forceFetch).onFailure { error ->
                    showError(error, Res.string.error_fetch_items)
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadOptions() {
        AppLoggerManager.logFunction("ScheduledViewModel", "loadOptions")
        viewModelScope.launch {
            repository.getNextActionOptions().onSuccess { options ->
                _uiState.update { it.copy(options = options) }
            }
        }
    }

    fun setContextFilter(contextFilter: String?) {
        AppLoggerManager.logFunction("ScheduledViewModel", "setContextFilter", "contextFilter=$contextFilter")
        AppLoggerManager.logStateChange("ScheduledViewModel", "Zmiana filtra kontekstu", "filter=$contextFilter")
        _uiState.update { it.copy(selectedContextFilter = contextFilter) }
    }

    fun setProjectFilter(projectFilter: String?) {
        AppLoggerManager.logFunction("ScheduledViewModel", "setProjectFilter", "projectFilter=$projectFilter")
        AppLoggerManager.logStateChange("ScheduledViewModel", "Zmiana filtra projektu", "filter=$projectFilter")
        _uiState.update { it.copy(selectedProjectIdFilter = projectFilter) }
    }

    fun openAddDialog() {
        AppLoggerManager.logFunction("ScheduledViewModel", "openAddDialog")
        _uiState.update { it.copy(showAddEditDialog = true, selectedItemToEdit = null, errorMessageRes = null) }
        loadOptions()
    }

    fun openEditDialog(item: ScheduledTask) {
        AppLoggerManager.logFunction("ScheduledViewModel", "openEditDialog", "itemId=${item.itemId}")
        _uiState.update { it.copy(showAddEditDialog = true, selectedItemToEdit = item, errorMessageRes = null) }
        loadOptions()
    }

    fun closeAddEditDialog() {
        AppLoggerManager.logFunction("ScheduledViewModel", "closeAddEditDialog")
        _uiState.update { it.copy(showAddEditDialog = false, selectedItemToEdit = null, errorMessageRes = null) }
    }

    fun createScheduledTask(
        recurrence: RecurrenceRule? = null,
        title: String,
        note: String,
        scheduledAt: String,
        deferUntil: String?,
        dueAt: String?,
        projectId: String?,
        contextIds: List<String>,
        newContextNames: List<String>,
        tagIds: List<String>,
        newTagNames: List<String>,
        files: List<InputFile>,
    ) {
        if (_uiState.value.isSubmitting) return
        AppLoggerManager.logFunction("ScheduledViewModel", "createScheduledTask", "title=$title")
        _uiState.update { it.copy(isSubmitting = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                repository.createScheduledTask(
                    title = title,
                    note = note,
                    recurrence = recurrence,
                    scheduledAt = scheduledAt,
                    deferUntil = deferUntil,
                    dueAt = dueAt,
                    projectId = projectId,
                    contextIds = contextIds,
                    newContextNames = newContextNames,
                    tagIds = tagIds,
                    newTagNames = newTagNames,
                    files = files,
                ).onSuccess {
                    closeAddEditDialog()
                    loadScheduledTasks(forceFetch = true)
                    loadOptions()
                }.onFailure { error ->
                    showError(error, Res.string.error_save_item)
                }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun updateScheduledTask(
        recurrence: RecurrenceRule? = null,
        item: ScheduledTask,
        title: String,
        note: String,
        scheduledAt: String,
        deferUntil: String?,
        dueAt: String?,
        projectId: String?,
        contextIds: List<String>,
        newContextNames: List<String>,
        tagIds: List<String>,
        newTagNames: List<String>,
        newFiles: List<InputFile>,
        removedAttachmentIds: List<String>,
    ) {
        if (_uiState.value.isSubmitting) return
        AppLoggerManager.logFunction("ScheduledViewModel", "updateScheduledTask", "itemId=${item.itemId}")
        _uiState.update { it.copy(isSubmitting = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                repository.updateScheduledTask(
                    item = item,
                    title = title,
                    note = note,
                    recurrence = recurrence,
                    scheduledAt = scheduledAt,
                    deferUntil = deferUntil,
                    dueAt = dueAt,
                    projectId = projectId,
                    contextIds = contextIds,
                    newContextNames = newContextNames,
                    tagIds = tagIds,
                    newTagNames = newTagNames,
                    newFiles = newFiles,
                    removedAttachmentIds = removedAttachmentIds,
                ).onSuccess {
                    closeAddEditDialog()
                    loadScheduledTasks(forceFetch = true)
                    loadOptions()
                }.onFailure { error ->
                    showError(error, Res.string.error_save_item)
                }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun deleteScheduledTask(itemId: String) {
        AppLoggerManager.logFunction("ScheduledViewModel", "deleteScheduledTask", "itemId=$itemId")
        viewModelScope.launch {
            repository.deleteScheduledTask(itemId).onFailure { error ->
                showError(error, Res.string.error_delete_item)
            }
        }
    }

    fun restoreToInbox(itemId: String) {
        AppLoggerManager.logFunction("ScheduledViewModel", "restoreToInbox", "itemId=$itemId")
        viewModelScope.launch {
            repository.restoreToInbox(itemId).onFailure { error ->
                showError(error, Res.string.error_save_item)
            }
        }
    }
    
    fun completeTask(itemId: String) {
        AppLoggerManager.logFunction("ScheduledViewModel", "completeTask", "itemId=$itemId")
        viewModelScope.launch {
            repository.completeTask(itemId).onFailure { error ->
                showError(error, Res.string.error_save_item)
            }
        }
    }
    
    fun cancelTask(itemId: String) {
        AppLoggerManager.logFunction("ScheduledViewModel", "cancelTask", "itemId=$itemId")
        viewModelScope.launch {
            repository.cancelTask(itemId).onFailure { error ->
                showError(error, Res.string.error_save_item)
            }
        }
    }

    private fun showError(error: Throwable, fallback: StringResource) {
        if (error is CancellationException) throw error
        _uiState.update { it.copy(errorMessageRes = itemErrorResource(error, fallback)) }
    }
}
