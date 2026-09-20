package pl.quicktask.app.inbox.presentation

import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import pl.quicktask.app.items.model.InputFile
import pl.quicktask.app.items.model.MAX_ATTACHMENTS
import pl.quicktask.app.items.support.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class EditorAndTimerStateTest {
    @Test
    fun draftTransitionsAndAttachmentLimit() = runTest {
        val fixture = encryptedFixture()
        val client = mockClient(MockEngine { error("No request expected") })
        try {
            val module = fixture.module(client)
            var state = InboxUiState()
            val editor = InboxEditorController(module.inbox, module.store, backgroundScope, { state }, { state = it(state) })
            editor.addFile(InputFile("ignored", "text/plain", byteArrayOf()))
            assertEquals(EditorState.Closed, state.editor)
            editor.openNew()
            repeat(MAX_ATTACHMENTS + 1) { editor.addFile(InputFile("file-$it", "text/plain", byteArrayOf())) }
            assertEquals(MAX_ATTACHMENTS, state.draft.selectedFiles.size)
            assertNotNull(state.errorMessageRes)
            editor.dismiss()
            assertEquals(EditorDraft(), state.draft)
            val item = fixture.mapper.inbox(fixture.inbox)
            editor.openEdit(item)
            assertIs<EditorState.Editing>(state.editor)
            editor.removeExistingAttachment(item.attachments.single().attachmentId)
            assertTrue(state.draft.existingAttachments.isEmpty())
            assertEquals(listOf(item.attachments.single().attachmentId), state.draft.removedAttachmentIds)
            editor.openNew()
            assertEquals(EditorDraft(), state.draft)
        } finally { client.close() }
    }

    @Test
    fun timerDecisionRetainsCountdownAndZeroRemainsActive() = runTest {
        val fixture = encryptedFixture()
        val client = mockClient(MockEngine { error("No request expected") })
        try {
            val module = fixture.module(client)
            var state = InboxUiState()
            val timer = TwoMinuteTimerController(module.completed, module.store, backgroundScope, { state }, { state = it(state) }, { Result.success(emptyList()) })
            val item = fixture.mapper.inbox(fixture.inbox)
            timer.start(item)
            runCurrent()
            advanceTimeBy(1_000); runCurrent()
            assertEquals(119, state.twoMinuteSecondsRemaining)
            timer.doneClicked()
            assertIs<TimerState.AwaitingAttachmentDecision>(state.timer)
            advanceTimeBy(1_000); runCurrent()
            timer.dismissAttachmentPrompt()
            assertIs<TimerState.Running>(state.timer)
            assertEquals(118, state.twoMinuteSecondsRemaining)
            advanceTimeBy(118_000); runCurrent()
            assertEquals(0, state.twoMinuteSecondsRemaining)
            assertEquals(item, state.activeTwoMinuteItem)
            timer.cancel()
            assertEquals(TimerState.Idle, state.timer)
        } finally { client.close() }
    }
}
