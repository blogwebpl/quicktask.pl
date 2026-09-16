package pl.quicktask.app.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import pl.quicktask.app.auth.createItemKey
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_complete_2min
import todo.shared.generated.resources.error_delete_item
import todo.shared.generated.resources.error_fetch_items
import todo.shared.generated.resources.error_max_attachments
import todo.shared.generated.resources.error_save_item
import todo.shared.generated.resources.error_update_item
import kotlin.random.Random

data class InboxUiState(
    val isLoading: Boolean = false,
    val items: List<InboxItem> = emptyList(),
    val errorMessageRes: StringResource? = null,
    val errorMessage: String? = null,
    val showAddDialog: Boolean = false,
    val editingItem: InboxItem? = null,
    val existingAttachments: List<DecryptedAttachment> = emptyList(),
    val removedAttachmentIds: List<String> = emptyList(),
    val isSubmitting: Boolean = false,
    val selectedFiles: List<InputFile> = emptyList(),
    val uploadProgress: Float? = null,
    val activeTwoMinuteItem: InboxItem? = null,
    val twoMinuteSecondsRemaining: Int = 120,
    val isCompletingTwoMinuteItem: Boolean = false,
    val showDeleteAttachmentPrompt: Boolean = false,
    val itemToDelete: InboxItem? = null,
)

class InboxViewModel(
    private val repository: InboxRepository = sharedInboxRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState())
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.itemsFlow.collect { items ->
                _uiState.value = _uiState.value.copy(items = items)
            }
        }
        loadItems()
    }

    fun loadItems() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, errorMessageRes = null)
        viewModelScope.launch {
            val result = repository.getItems(forceFetch = true)
            result.onSuccess { items ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    items = items,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessageRes = Res.string.error_fetch_items,
                    errorMessage = error.message,
                )
            }
        }
    }

    fun openAddDialog() {
        _uiState.value = _uiState.value.copy(
            showAddDialog = true,
            editingItem = null,
            existingAttachments = emptyList(),
            removedAttachmentIds = emptyList(),
            selectedFiles = emptyList(),
            uploadProgress = null,
        )
    }

    fun openEditDialog(item: InboxItem) {
        if (item.isPendingConfirmation) return
        _uiState.value = _uiState.value.copy(
            showAddDialog = true,
            editingItem = item,
            existingAttachments = item.attachments,
            removedAttachmentIds = emptyList(),
            selectedFiles = emptyList(),
            uploadProgress = null,
        )
    }

    fun dismissAddDialog() {
        _uiState.value = _uiState.value.copy(
            showAddDialog = false,
            editingItem = null,
            existingAttachments = emptyList(),
            removedAttachmentIds = emptyList(),
            selectedFiles = emptyList(),
            uploadProgress = null,
        )
    }

    fun addSelectedFile(file: InputFile) {
        val currentExistingCount = _uiState.value.existingAttachments.size
        val currentFiles = _uiState.value.selectedFiles
        if (currentExistingCount + currentFiles.size >= 10) {
            _uiState.value = _uiState.value.copy(errorMessageRes = Res.string.error_max_attachments)
            return
        }
        _uiState.value = _uiState.value.copy(selectedFiles = currentFiles + file)
    }

    fun removeExistingAttachment(attachmentId: String) {
        val currentExisting = _uiState.value.existingAttachments.filterNot { it.attachmentId == attachmentId }
        val currentRemoved = _uiState.value.removedAttachmentIds + attachmentId
        _uiState.value = _uiState.value.copy(
            existingAttachments = currentExisting,
            removedAttachmentIds = currentRemoved,
        )
    }

    fun removeSelectedFile(index: Int) {
        val currentFiles = _uiState.value.selectedFiles.toMutableList()
        if (index in currentFiles.indices) {
            currentFiles.removeAt(index)
            _uiState.value = _uiState.value.copy(selectedFiles = currentFiles)
        }
    }

    fun addItem(title: String, note: String) {
        if (title.isBlank()) return
        val filesToUpload = _uiState.value.selectedFiles
        val removedAttachmentIds = _uiState.value.removedAttachmentIds
        val editingItem = _uiState.value.editingItem
        val existingAttachments = _uiState.value.existingAttachments

        // Dismiss dialog immediately for optimistic UX
        _uiState.value = _uiState.value.copy(
            showAddDialog = false,
            editingItem = null,
            existingAttachments = emptyList(),
            removedAttachmentIds = emptyList(),
            selectedFiles = emptyList(),
            isSubmitting = false,
            uploadProgress = null,
        )

        val previousItems = repository.itemsFlow.value

        if (editingItem != null) {
            val dummyNewAttachments = filesToUpload.mapIndexed { index, file ->
                DecryptedAttachment(
                    attachmentId = "temp_att_$index",
                    fileId = "",
                    name = file.fileName,
                    type = file.mimeType,
                    ciphertextSha256 = "",
                    fileKey = editingItem.itemKey,
                )
            }
            val optimisticItem = editingItem.copy(
                title = title,
                note = note,
                attachments = existingAttachments + dummyNewAttachments,
                isPending = true,
            )
            repository.updateOptimisticItem(optimisticItem)

            viewModelScope.launch {
                val result = repository.updateInboxItem(
                    item = editingItem,
                    title = title,
                    note = note,
                    newFiles = filesToUpload,
                    removedAttachmentIds = removedAttachmentIds,
                )
                result.onSuccess {
                    loadItems()
                }.onFailure { error ->
                    repository.setOptimisticItems(previousItems)
                    _uiState.value = _uiState.value.copy(
                        errorMessageRes = Res.string.error_update_item,
                        errorMessage = error.message,
                    )
                }
            }
        } else {
            val tempId = "temp_${Random.nextLong().let { if (it < 0) -it else it }}"
            viewModelScope.launch {
                val itemKey = runCatching { createItemKey() }.getOrNull()
                val dummyAttachments = filesToUpload.mapIndexed { index, file ->
                    DecryptedAttachment(
                        attachmentId = "temp_att_$index",
                        fileId = "",
                        name = file.fileName,
                        type = file.mimeType,
                        ciphertextSha256 = "",
                        fileKey = itemKey ?: createItemKey(),
                    )
                }
                val optimisticItem = itemKey?.let { key ->
                    InboxItem(
                        itemId = tempId,
                        title = title,
                        note = note,
                        itemKey = key,
                        createdAt = "",
                        updatedAt = "",
                        attachments = dummyAttachments,
                        tags = emptyList(),
                        isPending = true,
                    )
                }
                if (optimisticItem != null) {
                    repository.addOptimisticItem(optimisticItem)
                }

                val result = repository.createInboxItem(
                    title = title,
                    note = note,
                    files = filesToUpload,
                )
                result.onSuccess {
                    loadItems()
                }.onFailure { error ->
                    repository.setOptimisticItems(previousItems)
                    _uiState.value = _uiState.value.copy(
                        errorMessageRes = Res.string.error_save_item,
                        errorMessage = error.message,
                    )
                }
            }
        }
    }

    fun requestDeleteItem(item: InboxItem) {
        if (item.isPendingConfirmation) return
        _uiState.value = _uiState.value.copy(itemToDelete = item)
    }

    fun confirmDeleteItem() {
        val item = _uiState.value.itemToDelete ?: return
        if (item.isPendingConfirmation) {
            _uiState.value = _uiState.value.copy(itemToDelete = null)
            return
        }
        _uiState.value = _uiState.value.copy(itemToDelete = null)
        deleteItem(item.itemId)
    }

    fun cancelDeleteItem() {
        _uiState.value = _uiState.value.copy(itemToDelete = null)
    }

    fun deleteItem(itemId: String) {
        val targetItem = _uiState.value.items.find { it.itemId == itemId }
        if (targetItem?.isPendingConfirmation == true) return

        val previousItems = repository.itemsFlow.value
        repository.removeOptimisticItem(itemId)
        viewModelScope.launch {
            val result = repository.deleteItem(itemId)
            result.onSuccess {
                loadItems()
            }.onFailure { error ->
                repository.setOptimisticItems(previousItems)
                _uiState.value = _uiState.value.copy(
                    errorMessageRes = Res.string.error_delete_item,
                    errorMessage = error.message,
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null, errorMessageRes = null)
    }

    private var timerJob: Job? = null

    fun startTwoMinuteTimer(item: InboxItem) {
        if (item.isPendingConfirmation) return
        timerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            activeTwoMinuteItem = item,
            twoMinuteSecondsRemaining = 120,
            isCompletingTwoMinuteItem = false,
            showDeleteAttachmentPrompt = false,
        )
        timerJob = viewModelScope.launch {
            while (_uiState.value.twoMinuteSecondsRemaining > 0) {
                delay(1000L)
                _uiState.value = _uiState.value.copy(
                    twoMinuteSecondsRemaining = _uiState.value.twoMinuteSecondsRemaining - 1,
                )
            }
        }
    }

    fun cancelTwoMinuteTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.value = _uiState.value.copy(
            activeTwoMinuteItem = null,
            twoMinuteSecondsRemaining = 120,
            isCompletingTwoMinuteItem = false,
            showDeleteAttachmentPrompt = false,
        )
    }

    fun onTwoMinuteDoneClicked() {
        val item = _uiState.value.activeTwoMinuteItem ?: return
        if (item.isPendingConfirmation) return
        if (item.attachments.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(showDeleteAttachmentPrompt = true)
        } else {
            completeTwoMinuteTimer(deleteAttachments = false)
        }
    }

    fun confirmCompleteTwoMinuteTimer(deleteAttachments: Boolean) {
        _uiState.value = _uiState.value.copy(showDeleteAttachmentPrompt = false)
        completeTwoMinuteTimer(deleteAttachments = deleteAttachments)
    }

    fun dismissDeleteAttachmentPrompt() {
        _uiState.value = _uiState.value.copy(showDeleteAttachmentPrompt = false)
    }

    fun completeTwoMinuteTimer(deleteAttachments: Boolean = false) {
        val item = _uiState.value.activeTwoMinuteItem ?: return
        if (item.isPendingConfirmation) return
        timerJob?.cancel()
        timerJob = null
        val previousItems = repository.itemsFlow.value
        repository.removeOptimisticItem(item.itemId)
        _uiState.value = _uiState.value.copy(
            activeTwoMinuteItem = null,
            isCompletingTwoMinuteItem = false,
            showDeleteAttachmentPrompt = false,
        )
        viewModelScope.launch {
            val result = repository.completeInTwoMinutes(item.itemId, deleteAttachments = deleteAttachments)
            result.onSuccess {
                loadItems()
            }.onFailure { error ->
                repository.setOptimisticItems(previousItems)
                _uiState.value = _uiState.value.copy(
                    isCompletingTwoMinuteItem = false,
                    errorMessageRes = Res.string.error_complete_2min,
                    errorMessage = error.message,
                )
            }
        }
    }
}
