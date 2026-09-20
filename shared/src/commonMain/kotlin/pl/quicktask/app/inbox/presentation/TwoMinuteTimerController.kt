package pl.quicktask.app.inbox.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import pl.quicktask.app.items.data.CompletedItemsOperations
import pl.quicktask.app.items.model.InboxItem
import pl.quicktask.app.items.presentation.itemErrorResource
import pl.quicktask.app.items.store.ItemStore
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_complete_2min
import todo.shared.generated.resources.error_fetch_items

private const val TIMER_TICK_MS = 1_000L

internal class TwoMinuteTimerController(
    private val completed: CompletedItemsOperations,
    private val store: ItemStore,
    private val scope: CoroutineScope,
    private val currentState: () -> InboxUiState,
    private val updateState: ((InboxUiState) -> InboxUiState) -> Unit,
    private val refreshItems: suspend () -> Result<List<InboxItem>>,
) {
    private var timerJob: Job? = null

    fun start(item: InboxItem) {
        if (item.isPendingConfirmation) return
        timerJob?.cancel()
        updateState { it.copy(timer = TimerState.Running(item)) }
        timerJob = scope.launch {
            while (currentState().twoMinuteSecondsRemaining > 0) {
                delay(TIMER_TICK_MS)
                updateState { state -> state.copy(timer = when (val timer = state.timer) {
                    TimerState.Idle -> timer
                    is TimerState.Running -> timer.copy(secondsRemaining = (timer.secondsRemaining - 1).coerceAtLeast(0))
                    is TimerState.AwaitingAttachmentDecision -> timer.copy(secondsRemaining = (timer.secondsRemaining - 1).coerceAtLeast(0))
                }) }
            }
        }
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        updateState { it.copy(timer = TimerState.Idle) }
    }

    fun doneClicked() {
        val state = currentState()
        val item = state.activeTwoMinuteItem ?: return
        if (item.isPendingConfirmation) return
        if (item.attachments.isNotEmpty()) updateState {
            it.copy(timer = TimerState.AwaitingAttachmentDecision(item, it.twoMinuteSecondsRemaining))
        } else complete(false)
    }

    fun confirm(deleteAttachments: Boolean) {
        if (currentState().timer !is TimerState.AwaitingAttachmentDecision) return
        complete(deleteAttachments)
    }

    fun dismissAttachmentPrompt() = updateState { state ->
        val timer = state.timer
        if (timer is TimerState.AwaitingAttachmentDecision)
            state.copy(timer = TimerState.Running(timer.item, timer.secondsRemaining)) else state
    }

    fun complete(deleteAttachments: Boolean = false) {
        val item = currentState().activeTwoMinuteItem ?: return
        if (item.isPendingConfirmation) return
        val operation = store.beginOperation(item.itemId, null) ?: return
        cancel()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            runPendingItemMutation(
                store, operation,
                mutate = { completed.completeInTwoMinutes(item.itemId, deleteAttachments) },
                refresh = refreshItems,
                onMutationError = { error -> updateState { it.copy(errorMessageRes = itemErrorResource(error, Res.string.error_complete_2min)) } },
                onRefreshError = { error -> updateState { it.copy(errorMessageRes = itemErrorResource(error, Res.string.error_fetch_items)) } },
            )
        }
    }
}
