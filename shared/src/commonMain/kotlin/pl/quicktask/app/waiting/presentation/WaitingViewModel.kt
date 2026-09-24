package pl.quicktask.app.waiting.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import pl.quicktask.app.items.presentation.itemErrorResource
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.nextactions.data.NextActionsOperations
import pl.quicktask.app.projects.data.ProjectsOperations
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_fetch_items
import todo.shared.generated.resources.error_save_item

class WaitingViewModel(
    private val projects: ProjectsOperations,
    private val nextActions: NextActionsOperations,
    private val store: ItemStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WaitingUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.projectsOverviewFlow.collect { overview ->
                val assigned = overview.projects.flatMap { project ->
                    project.tasks
                        .filter { it.gtdState.equals("WAITING", ignoreCase = true) }
                        .map { WaitingListEntry(it, project.title) }
                }
                val unassigned = overview.unassignedTasks
                    .filter { it.gtdState.equals("WAITING", ignoreCase = true) }
                    .map { WaitingListEntry(it) }
                _uiState.update { it.copy(items = assigned + unassigned) }
            }
        }
        loadWaiting()
        loadOptions()
    }

    fun loadWaiting(forceFetch: Boolean = false) {
        _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                projects.getProjects(forceFetch).onFailure { showError(it, Res.string.error_fetch_items) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun loadOptions() {
        viewModelScope.launch {
            nextActions.getNextActionOptions().onSuccess { options ->
                _uiState.update { it.copy(options = options) }
            }.onFailure { showError(it, Res.string.error_fetch_items) }
        }
    }

    fun openAddDialog() {
        _uiState.update { it.copy(showAddDialog = true) }
        loadOptions()
    }

    fun closeAddDialog() = _uiState.update { it.copy(showAddDialog = false) }

    fun createWaiting(
        title: String,
        note: String,
        projectId: String?,
        dueAt: String?,
        waitingFor: String,
        followUpAt: String?,
    ) {
        _uiState.update { it.copy(isSubmitting = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                projects.createWaitingTask(
                    title = title,
                    note = note,
                    projectId = projectId,
                    dueAt = dueAt,
                    waitingFor = waitingFor,
                    followUpAt = followUpAt,
                ).onSuccess {
                    closeAddDialog()
                    loadWaiting(forceFetch = true)
                }.onFailure { showError(it, Res.string.error_save_item) }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private fun showError(error: Throwable, fallback: StringResource) {
        if (error is CancellationException) throw error
        _uiState.update { it.copy(errorMessageRes = itemErrorResource(error, fallback)) }
    }
}
