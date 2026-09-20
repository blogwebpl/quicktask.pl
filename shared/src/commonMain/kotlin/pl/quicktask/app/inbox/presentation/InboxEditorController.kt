package pl.quicktask.app.inbox.presentation

import pl.quicktask.app.items.model.DecryptedAttachment
import pl.quicktask.app.items.model.InboxItem
import pl.quicktask.app.items.model.InputFile
import pl.quicktask.app.items.model.MAX_ATTACHMENTS
import pl.quicktask.app.items.model.itemResult

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import pl.quicktask.app.auth.crypto.createItemKey
import pl.quicktask.app.inbox.data.InboxOperations
import pl.quicktask.app.items.presentation.itemErrorResource
import pl.quicktask.app.items.store.ItemStore
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_max_attachments
import todo.shared.generated.resources.error_fetch_items
import todo.shared.generated.resources.error_save_item
import todo.shared.generated.resources.error_update_item
import kotlin.random.Random

internal class InboxEditorController(
    private val repository: InboxOperations,
    private val store: ItemStore,
    private val scope: CoroutineScope,
    private val currentState: () -> InboxUiState,
    private val updateState: ((InboxUiState) -> InboxUiState) -> Unit,
) {
    fun openNew() = updateState { it.copy(editor = EditorState.Adding()) }

    fun openEdit(item: InboxItem) {
        if (item.isPendingConfirmation) return
        updateState { it.copy(editor = EditorState.Editing(item, EditorDraft(existingAttachments = item.attachments))) }
    }

    fun dismiss() = updateState { it.copy(editor = EditorState.Closed) }

    fun addFile(file: InputFile) {
        val state = currentState()
        if (state.existingAttachments.size + state.selectedFiles.size >= MAX_ATTACHMENTS) {
            updateState { it.copy(errorMessageRes = Res.string.error_max_attachments) }
            return
        }
        updateState { it.copy(editor = it.editor.updateDraft { draft -> draft.copy(selectedFiles = draft.selectedFiles + file) }) }
    }

    fun removeExistingAttachment(attachmentId: String) = updateState {
        it.copy(editor = it.editor.updateDraft { draft -> draft.copy(
            existingAttachments = draft.existingAttachments.filterNot { attachment -> attachment.attachmentId == attachmentId },
            removedAttachmentIds = draft.removedAttachmentIds + attachmentId,
        ) })
    }

    fun removeSelectedFile(index: Int) = updateState {
        it.copy(editor = it.editor.updateDraft { draft ->
            if (index !in draft.selectedFiles.indices) draft
            else draft.copy(selectedFiles = draft.selectedFiles.filterIndexed { position, _ -> position != index })
        })
    }

    fun save(title: String, note: String) {
        if (title.isBlank()) return
        val state = currentState()
        if (state.editor == EditorState.Closed) return
        val files = state.selectedFiles
        val removedAttachmentIds = state.removedAttachmentIds
        val editingItem = state.editingItem
        val existingAttachments = state.existingAttachments
        if (editingItem != null && store.hasPendingOperation(editingItem.itemId)) return

        dismiss()

        if (editingItem != null) updateExisting(editingItem, title, note, files, existingAttachments, removedAttachmentIds)
        else createNew(title, note, files)
    }

    private fun updateExisting(
        item: InboxItem,
        title: String,
        note: String,
        files: List<InputFile>,
        existingAttachments: List<DecryptedAttachment>,
        removedAttachmentIds: List<String>,
    ) {
        val pendingAttachments = files.mapIndexed { index, file ->
            DecryptedAttachment("temp_att_$index", "", file.fileName, file.mimeType, "", item.itemKey)
        }
        val pending = item.copy(title = title, note = note, attachments = existingAttachments + pendingAttachments)
        val operation = store.beginOperation(item.itemId, pending) ?: return
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            runPendingItemMutation(
                store, operation,
                mutate = { repository.updateInboxItem(item, title, note, files, removedAttachmentIds) },
                refresh = { repository.getItems(forceFetch = true) },
                onMutationError = { error -> updateState { it.copy(errorMessageRes = itemErrorResource(error, Res.string.error_update_item)) } },
                onRefreshError = ::showRefreshError,
            )
        }
    }

    private fun createNew(title: String, note: String, files: List<InputFile>) {
        val tempId = "temp_${Random.nextLong().toULong().toString(16)}"
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val key = itemResult { createItemKey() }.getOrElse { error ->
                updateState { it.copy(errorMessageRes = itemErrorResource(error, Res.string.error_save_item)) }
                return@launch
            }
            val attachments = files.mapIndexed { index, file ->
                DecryptedAttachment("temp_att_$index", "", file.fileName, file.mimeType, "", key)
            }
            val pending = InboxItem(tempId, title, note, key, "", "", attachments)
            val operation = store.beginOperation(tempId, pending) ?: return@launch
            runPendingItemMutation(
                store, operation,
                mutate = {
                    repository.createInboxItem(title, note, files, itemKey = key).onSuccess { realId ->
                        store.bindRealId(tempId, realId)
                    }.map { }
                },
                refresh = { repository.getItems(forceFetch = true, completedOperation = operation) },
                onMutationError = { error -> updateState { it.copy(errorMessageRes = itemErrorResource(error, Res.string.error_save_item)) } },
                onRefreshError = ::showRefreshError,
            )
        }
    }

    private fun showRefreshError(error: Throwable) = updateState {
        it.copy(errorMessageRes = itemErrorResource(error, Res.string.error_fetch_items))
    }
}
