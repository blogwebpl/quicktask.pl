package pl.quicktask.todo.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InboxUiState(
    val isLoading: Boolean = false,
    val items: List<InboxItem> = emptyList(),
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
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
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
                    errorMessage = error.message ?: "Błąd pobierania elementów",
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
            _uiState.value = _uiState.value.copy(errorMessage = "Maksymalna liczba załączników to 10")
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
        _uiState.value = _uiState.value.copy(
            isSubmitting = true,
            uploadProgress = if (filesToUpload.isNotEmpty()) 0f else null,
        )
        viewModelScope.launch {
            val result = if (editingItem != null) {
                repository.updateInboxItem(
                    item = editingItem,
                    title = title,
                    note = note,
                    newFiles = filesToUpload,
                    removedAttachmentIds = removedAttachmentIds,
                )
            } else {
                repository.createInboxItem(
                    title = title,
                    note = note,
                    files = filesToUpload,
                    onProgress = { progress ->
                        _uiState.value = _uiState.value.copy(uploadProgress = progress)
                    },
                )
            }
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    showAddDialog = false,
                    editingItem = null,
                    existingAttachments = emptyList(),
                    removedAttachmentIds = emptyList(),
                    selectedFiles = emptyList(),
                    uploadProgress = null,
                )
                loadItems()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    uploadProgress = null,
                    errorMessage = error.message ?: "Błąd zapisywania elementu",
                )
            }
        }
    }

    fun requestDeleteItem(item: InboxItem) {
        _uiState.value = _uiState.value.copy(itemToDelete = item)
    }

    fun confirmDeleteItem() {
        val item = _uiState.value.itemToDelete ?: return
        _uiState.value = _uiState.value.copy(itemToDelete = null)
        viewModelScope.launch {
            val result = repository.deleteItem(item.itemId)
            result.onSuccess {
                loadItems()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message ?: "Błąd usuwania elementu",
                )
            }
        }
    }

    fun cancelDeleteItem() {
        _uiState.value = _uiState.value.copy(itemToDelete = null)
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

    private var timerJob: Job? = null

    fun startTwoMinuteTimer(item: InboxItem) {
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
        timerJob?.cancel()
        timerJob = null
        _uiState.value = _uiState.value.copy(
            isCompletingTwoMinuteItem = true,
            showDeleteAttachmentPrompt = false,
        )
        viewModelScope.launch {
            val result = repository.completeInTwoMinutes(item.itemId, deleteAttachments = deleteAttachments)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    activeTwoMinuteItem = null,
                    isCompletingTwoMinuteItem = false,
                    showDeleteAttachmentPrompt = false,
                )
                loadItems()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isCompletingTwoMinuteItem = false,
                    errorMessage = error.message ?: "Błąd oznaczania elementu jako wykonanego w 2 minuty",
                )
            }
        }
    }
}
