package pl.quicktask.app.inbox.presentation

import pl.quicktask.app.scheduled.model.RecurrenceRule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.update
import pl.quicktask.app.common.AppLoggerManager
import pl.quicktask.app.inbox.data.InboxOperations
import pl.quicktask.app.items.data.CompletedItemsOperations
import pl.quicktask.app.items.domain.ItemLifecycleOperations
import pl.quicktask.app.items.model.InboxItem
import pl.quicktask.app.items.model.InputFile
import pl.quicktask.app.items.presentation.itemErrorResource
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.nextactions.data.NextActionsOperations
import pl.quicktask.app.nextactions.model.NewContextInput
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_delete_item
import todo.shared.generated.resources.error_fetch_items
import todo.shared.generated.resources.error_save_item

class InboxViewModel(
    private val repository: InboxOperations,
    private val store: ItemStore,
    private val lifecycle: ItemLifecycleOperations,
    completed: CompletedItemsOperations,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    private val editor = InboxEditorController(
        repository = repository,
        store = store,
        scope = viewModelScope,
        currentState = { _uiState.value },
        updateState = ::updateState,
    )
    private val timer = TwoMinuteTimerController(
        completed = completed,
        refreshItems = { repository.getItems() },
        store = store,
        scope = viewModelScope,
        currentState = { _uiState.value },
        updateState = ::updateState,
    )

    init {
        viewModelScope.launch {
            store.itemsFlow.collect { items ->
                AppLoggerManager.logStateChange("InboxViewModel", "Aktualizacja listy elementów z magazynu", "count=${items.size}")
                updateState { it.copy(items = items) }
            }
        }
        loadItems()
    }

    fun loadItems() {
        AppLoggerManager.logFunction("InboxViewModel", "loadItems")
        AppLoggerManager.logRefresh("InboxViewModel", "Pobieranie zadań Inbox")
        updateState { it.copy(isLoading = true, errorMessageRes = null) }
        viewModelScope.launch {
            repository.getItems(forceFetch = true)
                .onSuccess {
                    AppLoggerManager.logStateChange("InboxViewModel", "Pobrano zadania Inbox")
                    updateState { it.copy(isLoading = false) }
                }
                .onFailure { error ->
                    AppLoggerManager.logStateChange("InboxViewModel", "Błąd pobierania zadań Inbox", error.message)
                    updateState {
                        it.copy(
                            isLoading = false,
                            errorMessageRes = itemErrorResource(error, Res.string.error_fetch_items),
                        )
                    }
                }
        }
    }

    fun openAddDialog() {
        AppLoggerManager.logFunction("InboxViewModel", "openAddDialog")
        editor.openNew()
    }
    fun openEditDialog(item: InboxItem) {
        AppLoggerManager.logFunction("InboxViewModel", "openEditDialog", "itemId=${item.itemId}")
        editor.openEdit(item)
    }
    fun dismissEditor() {
        AppLoggerManager.logFunction("InboxViewModel", "dismissEditor")
        editor.dismiss()
    }
    fun addSelectedFile(file: InputFile) {
        AppLoggerManager.logFunction("InboxViewModel", "addSelectedFile", "fileName=${file.fileName}")
        editor.addFile(file)
    }
    fun removeExistingAttachment(attachmentId: String) {
        AppLoggerManager.logFunction("InboxViewModel", "removeExistingAttachment", "attachmentId=$attachmentId")
        editor.removeExistingAttachment(attachmentId)
    }
    fun removeSelectedFile(index: Int) {
        AppLoggerManager.logFunction("InboxViewModel", "removeSelectedFile", "index=$index")
        editor.removeSelectedFile(index)
    }
    fun saveItem(title: String, note: String) {
        AppLoggerManager.logFunction("InboxViewModel", "saveItem", "title=$title")
        editor.save(title, note)
    }

    fun requestDeleteItem(item: InboxItem) {
        AppLoggerManager.logFunction("InboxViewModel", "requestDeleteItem", "itemId=${item.itemId}")
        if (!item.isPendingConfirmation) updateState { it.copy(itemToDelete = item) }
    }

    fun confirmDeleteItem() {
        AppLoggerManager.logFunction("InboxViewModel", "confirmDeleteItem")
        val item = _uiState.value.itemToDelete ?: return
        updateState { it.copy(itemToDelete = null) }
        if (!item.isPendingConfirmation) deleteItem(item.itemId)
    }

    fun cancelDeleteItem() {
        AppLoggerManager.logFunction("InboxViewModel", "cancelDeleteItem")
        updateState { it.copy(itemToDelete = null) }
    }

    fun deleteItem(itemId: String) {
        AppLoggerManager.logFunction("InboxViewModel", "deleteItem", "itemId=$itemId")
        val targetItem = _uiState.value.items.find { it.itemId == itemId }
        if (targetItem?.isPendingConfirmation == true) return
        val operation = store.beginOperation(itemId, null) ?: return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runPendingItemMutation(
                store, operation,
                mutate = { lifecycle.deleteItem(itemId) },
                refresh = { repository.getItems() },
                onMutationError = { error -> updateState { it.copy(errorMessageRes = itemErrorResource(error, Res.string.error_delete_item)) } },
                onRefreshError = { error -> updateState { it.copy(errorMessageRes = itemErrorResource(error, Res.string.error_fetch_items)) } },
            )
        }
    }

    fun convertToProject(
        itemId: String,
        projectsOperations: pl.quicktask.app.projects.data.ProjectsOperations,
    ) {
        AppLoggerManager.logFunction("InboxViewModel", "convertToProject", "itemId=$itemId")
        val targetItem = _uiState.value.items.find { it.itemId == itemId }
        if (targetItem?.isPendingConfirmation == true) return
        val operation = store.beginOperation(itemId, null) ?: return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runPendingItemMutation(
                store, operation,
                mutate = { projectsOperations.convertFromInbox(itemId) },
                refresh = { repository.getItems() },
                onMutationError = { error -> updateState { it.copy(errorMessageRes = itemErrorResource(error, Res.string.error_save_item)) } },
                onRefreshError = { error -> updateState { it.copy(errorMessageRes = itemErrorResource(error, Res.string.error_fetch_items)) } },
            )
        }
    }

    fun convertToNextAction(
        itemId: String,
        projectId: String?,
        dueAt: String?,
        contextIds: List<String>,
        newContextNames: List<String>,
        newContexts: List<NewContextInput> = emptyList(),
        tagIds: List<String>,
        newTagNames: List<String>,
        nextActionsOperations: NextActionsOperations,
    ) {
        AppLoggerManager.logFunction("InboxViewModel", "convertToNextAction", "itemId=$itemId")
        val targetItem = _uiState.value.items.find { it.itemId == itemId }
        if (targetItem?.isPendingConfirmation == true) return
        val operation = store.beginOperation(itemId, null) ?: return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runPendingItemMutation(
                store, operation,
                mutate = {
                    nextActionsOperations.convertFromInbox(
                        itemId = itemId,
                        projectId = projectId,
                        dueAt = dueAt,
                        contextIds = contextIds,
                        newContextNames = newContextNames,
                        newContexts = newContexts,
                        tagIds = tagIds,
                        newTagNames = newTagNames,
                    )
                },
                refresh = { repository.getItems() },
                onMutationError = { error -> updateState { it.copy(errorMessageRes = itemErrorResource(error, Res.string.error_save_item)) } },
                onRefreshError = { error -> updateState { it.copy(errorMessageRes = itemErrorResource(error, Res.string.error_fetch_items)) } },
            )
        }
    }

    fun convertToScheduled(
        recurrence: RecurrenceRule? = null,
        item: InboxItem,
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
        scheduledOperations: pl.quicktask.app.scheduled.data.ScheduledOperations,
    ) {
        val itemId = item.itemId
        AppLoggerManager.logFunction("InboxViewModel", "convertToScheduled", "itemId=$itemId")
        val targetItem = _uiState.value.items.find { it.itemId == itemId }
        if (targetItem?.isPendingConfirmation == true) return
        val operation = store.beginOperation(itemId, null) ?: return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runPendingItemMutation(
                store, operation,
                mutate = {
                    scheduledOperations.convertFromInbox(
                        recurrence = recurrence,
                        item = item,
                        title = title,
                        note = note,
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
                    )
                },
                refresh = { repository.getItems() },
                onMutationError = { error -> updateState { it.copy(errorMessageRes = itemErrorResource(error, Res.string.error_save_item)) } },
                onRefreshError = { error -> updateState { it.copy(errorMessageRes = itemErrorResource(error, Res.string.error_fetch_items)) } },
            )
        }
    }

    fun clearError() = updateState { it.copy(errorMessageRes = null) }
    fun startTwoMinuteTimer(item: InboxItem) {
        AppLoggerManager.logFunction("InboxViewModel", "startTwoMinuteTimer", "itemId=${item.itemId}")
        timer.start(item)
    }
    fun cancelTwoMinuteTimer() {
        AppLoggerManager.logFunction("InboxViewModel", "cancelTwoMinuteTimer")
        timer.cancel()
    }
    fun onTwoMinuteDoneClicked() {
        AppLoggerManager.logFunction("InboxViewModel", "onTwoMinuteDoneClicked")
        timer.doneClicked()
    }
    fun confirmCompleteTwoMinuteTimer(deleteAttachments: Boolean) {
        AppLoggerManager.logFunction("InboxViewModel", "confirmCompleteTwoMinuteTimer", "deleteAttachments=$deleteAttachments")
        timer.confirm(deleteAttachments)
    }
    fun dismissDeleteAttachmentPrompt() {
        AppLoggerManager.logFunction("InboxViewModel", "dismissDeleteAttachmentPrompt")
        timer.dismissAttachmentPrompt()
    }
    fun completeTwoMinuteTimer(deleteAttachments: Boolean = false) {
        AppLoggerManager.logFunction("InboxViewModel", "completeTwoMinuteTimer", "deleteAttachments=$deleteAttachments")
        timer.complete(deleteAttachments)
    }

    private fun updateState(transform: (InboxUiState) -> InboxUiState) {
        _uiState.update(transform)
    }
}
