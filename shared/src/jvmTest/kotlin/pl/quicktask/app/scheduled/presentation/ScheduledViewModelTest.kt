package pl.quicktask.app.scheduled.presentation

import pl.quicktask.app.scheduled.model.RecurrenceRule

import androidx.lifecycle.ViewModelStore
import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import pl.quicktask.app.items.model.InputFile
import pl.quicktask.app.items.model.InboxItem
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.nextactions.model.NextActionOptions
import pl.quicktask.app.scheduled.data.ScheduledOperations
import pl.quicktask.app.scheduled.model.ScheduledTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduledViewModelTest {

    @Test
    fun successfulCreateClosesDialogAndForcesListRefresh() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = FakeScheduledOperations()
        val viewModels = ViewModelStore()
        try {
            val viewModel = ScheduledViewModel(repository, ItemStore())
            viewModels.put("scheduled", viewModel)
            viewModel.openAddDialog()

            viewModel.createScheduledTask(
                title = "Przegląd kwartalny",
                note = "Przygotuj raport",
                scheduledAt = "2026-10-01T12:00:00.000Z",
                deferUntil = "2026-09-28T12:00:00.000Z",
                dueAt = "2026-10-03T12:00:00.000Z",
                projectId = "project-1",
                contextIds = listOf("context-1"),
                newContextNames = listOf("Biuro"),
                tagIds = listOf("tag-1"),
                newTagNames = listOf("ważne"),
                files = emptyList(),
            )

            assertEquals(
                CreateCall(
                    "Przegląd kwartalny",
                    "Przygotuj raport",
                    "2026-10-01T12:00:00.000Z",
                    "2026-09-28T12:00:00.000Z",
                    "2026-10-03T12:00:00.000Z",
                    "project-1",
                    listOf("context-1"),
                    listOf("Biuro"),
                    listOf("tag-1"),
                    listOf("ważne"),
                ),
                repository.createCall,
            )
            assertEquals(listOf(false, true), repository.fetchCalls)
            assertFalse(viewModel.uiState.value.showAddEditDialog)
            assertFalse(viewModel.uiState.value.isSubmitting)
            assertNull(viewModel.uiState.value.errorMessageRes)
        } finally {
            viewModels.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun rejectedCreateKeepsDialogOpenAndExposesError() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = FakeScheduledOperations(createResult = Result.failure(IllegalStateException("rejected")))
        val viewModels = ViewModelStore()
        try {
            val viewModel = ScheduledViewModel(repository, ItemStore())
            viewModels.put("scheduled", viewModel)
            viewModel.openAddDialog()

            viewModel.createScheduledTask(
                title = "Niepoprawne zadanie",
                note = "",
                scheduledAt = "not-a-date",
                deferUntil = null,
                dueAt = null,
                projectId = null,
                contextIds = emptyList(),
                newContextNames = emptyList(),
                tagIds = emptyList(),
                newTagNames = emptyList(),
                files = emptyList(),
            )

            assertTrue(viewModel.uiState.value.showAddEditDialog)
            assertFalse(viewModel.uiState.value.isSubmitting)
            assertNotNull(viewModel.uiState.value.errorMessageRes)
            assertEquals(listOf(false), repository.fetchCalls)
        } finally {
            viewModels.clear()
            Dispatchers.resetMain()
        }
    }
}

private data class CreateCall(
    val title: String,
    val note: String,
    val scheduledAt: String,
    val deferUntil: String?,
    val dueAt: String?,
    val projectId: String?,
    val contextIds: List<String>,
    val newContextNames: List<String>,
    val tagIds: List<String>,
    val newTagNames: List<String>,
)

private class FakeScheduledOperations(
    private val createResult: Result<String> = Result.success("item-123"),
) : ScheduledOperations {
    val fetchCalls = mutableListOf<Boolean>()
    var createCall: CreateCall? = null

    override suspend fun getScheduledTasks(forceFetch: Boolean): Result<List<ScheduledTask>> {
        fetchCalls += forceFetch
        return Result.success(emptyList())
    }

    override suspend fun getNextActionOptions() = Result.success(NextActionOptions())

    override suspend fun createScheduledTask(
        recurrence: RecurrenceRule?,
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
        onProgress: ((Float) -> Unit)?,
        itemKey: AES.GCM.Key?,
    ): Result<String> {
        createCall = CreateCall(
            title, note, scheduledAt, deferUntil, dueAt, projectId,
            contextIds, newContextNames, tagIds, newTagNames,
        )
        return createResult
    }

    override suspend fun convertFromInbox(
        recurrence: RecurrenceRule?,
        item: InboxItem, title: String, note: String, scheduledAt: String, deferUntil: String?, dueAt: String?, projectId: String?,
        contextIds: List<String>, newContextNames: List<String>, tagIds: List<String>, newTagNames: List<String>,
        newFiles: List<InputFile>, removedAttachmentIds: List<String>,
    ) = Result.success(Unit)

    override suspend fun updateScheduledTask(
        recurrence: RecurrenceRule?,
        item: ScheduledTask, title: String, note: String, scheduledAt: String, deferUntil: String?, dueAt: String?,
        projectId: String?, contextIds: List<String>, newContextNames: List<String>, tagIds: List<String>,
        newTagNames: List<String>, newFiles: List<InputFile>, removedAttachmentIds: List<String>,
    ) = Result.success(Unit)

    override suspend fun changeDateDuringReview(itemId: String, scheduledAt: String) = Result.success(Unit)
    override suspend fun markTaskAsReviewed(itemId: String) = Result.success(Unit)
    override suspend fun changeTaskDueDate(itemId: String, dueAt: String) = Result.success(Unit)
    override suspend fun completeTask(itemId: String) = Result.success(Unit)
    override suspend fun cancelTask(itemId: String) = Result.success(Unit)
    override suspend fun moveToSomedayMaybe(itemId: String) = Result.success(Unit)
    override suspend fun restoreToInbox(itemId: String) = Result.success(Unit)
    override suspend fun deleteScheduledTask(itemId: String) = Result.success(Unit)
    override suspend fun changeTags(itemId: String, tagIds: List<String>, newTagNames: List<String>) = Result.success(Unit)
}
