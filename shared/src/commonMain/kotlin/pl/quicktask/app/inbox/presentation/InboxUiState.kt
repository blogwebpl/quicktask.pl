package pl.quicktask.app.inbox.presentation

import org.jetbrains.compose.resources.StringResource
import pl.quicktask.app.items.model.DecryptedAttachment
import pl.quicktask.app.items.model.InboxItem
import pl.quicktask.app.items.model.InputFile

data class EditorDraft(
    val existingAttachments: List<DecryptedAttachment> = emptyList(),
    val removedAttachmentIds: List<String> = emptyList(),
    val selectedFiles: List<InputFile> = emptyList(),
)

sealed interface EditorState {
    data object Closed : EditorState
    data class Adding(val draft: EditorDraft = EditorDraft()) : EditorState
    data class Editing(val item: InboxItem, val draft: EditorDraft) : EditorState
}

internal fun EditorState.updateDraft(transform: (EditorDraft) -> EditorDraft): EditorState = when (this) {
    EditorState.Closed -> this
    is EditorState.Adding -> copy(draft = transform(draft))
    is EditorState.Editing -> copy(draft = transform(draft))
}

const val TWO_MINUTE_DURATION_SECONDS = 120
sealed interface TimerState {
    data object Idle : TimerState
    data class Running(val item: InboxItem, val secondsRemaining: Int = TWO_MINUTE_DURATION_SECONDS) : TimerState
    data class AwaitingAttachmentDecision(val item: InboxItem, val secondsRemaining: Int) : TimerState
}

data class InboxUiState(
    val isLoading: Boolean = false,
    val items: List<InboxItem> = emptyList(),
    val errorMessageRes: StringResource? = null,
    val editor: EditorState = EditorState.Closed,
    val timer: TimerState = TimerState.Idle,
    val itemToDelete: InboxItem? = null,
) {
    val showAddDialog get() = editor != EditorState.Closed
    val editingItem get() = (editor as? EditorState.Editing)?.item
    val draft get() = when (val value = editor) {
        EditorState.Closed -> EditorDraft()
        is EditorState.Adding -> value.draft
        is EditorState.Editing -> value.draft
    }
    val existingAttachments get() = draft.existingAttachments
    val removedAttachmentIds get() = draft.removedAttachmentIds
    val selectedFiles get() = draft.selectedFiles
    val activeTwoMinuteItem get() = when (val value = timer) {
        TimerState.Idle -> null
        is TimerState.Running -> value.item
        is TimerState.AwaitingAttachmentDecision -> value.item
    }
    val twoMinuteSecondsRemaining get() = when (val value = timer) {
        TimerState.Idle -> TWO_MINUTE_DURATION_SECONDS
        is TimerState.Running -> value.secondsRemaining
        is TimerState.AwaitingAttachmentDecision -> value.secondsRemaining
    }
}
