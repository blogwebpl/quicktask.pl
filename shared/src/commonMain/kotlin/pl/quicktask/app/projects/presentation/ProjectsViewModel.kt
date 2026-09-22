package pl.quicktask.app.projects.presentation

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
import pl.quicktask.app.nextactions.data.NextActionsOperations
import pl.quicktask.app.nextactions.model.NewContextInput
import pl.quicktask.app.projects.data.ProjectsOperations
import pl.quicktask.app.projects.model.ProjectWithTasks
import pl.quicktask.app.scheduled.data.ScheduledOperations
import pl.quicktask.app.scheduled.model.RecurrenceRule
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_delete_item
import todo.shared.generated.resources.error_fetch_items
import todo.shared.generated.resources.error_save_item

class ProjectsViewModel(
    private val projectsRepository: ProjectsOperations,
    private val nextActionsRepository: NextActionsOperations,
    private val scheduledRepository: ScheduledOperations,
    private val store: ItemStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            store.projectsOverviewFlow.collect { overview ->
                AppLoggerManager.logStateChange("ProjectsViewModel", "Aktualizacja listy projektów", "count=${overview.projects.size}")
                _uiState.update { it.copy(projects = overview.projects, unassignedTasks = overview.unassignedTasks) }
            }
        }
        loadProjects()
        loadOptions()
    }

    fun loadProjects(forceFetch: Boolean = false) {
        AppLoggerManager.logFunction("ProjectsViewModel", "loadProjects", "forceFetch=$forceFetch")
        AppLoggerManager.logRefresh("ProjectsViewModel", "Pobieranie projektów z zadaniami")
        _uiState.update { it.copy(isLoading = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                projectsRepository.getProjects(forceFetch)
                    .onFailure { error ->
                        showError(error, Res.string.error_fetch_items)
                    }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadOptions() {
        AppLoggerManager.logFunction("ProjectsViewModel", "loadOptions")
        viewModelScope.launch {
            nextActionsRepository.getNextActionOptions().onSuccess { options ->
                _uiState.update { it.copy(options = options) }
            }
        }
    }

    fun openAddProjectDialog() {
        AppLoggerManager.logFunction("ProjectsViewModel", "openAddProjectDialog")
        _uiState.update { it.copy(showAddProjectDialog = true) }
    }

    fun closeAddProjectDialog() {
        AppLoggerManager.logFunction("ProjectsViewModel", "closeAddProjectDialog")
        _uiState.update { it.copy(showAddProjectDialog = false) }
    }

    fun createProject(title: String, note: String = "") {
        AppLoggerManager.logFunction("ProjectsViewModel", "createProject", "title=$title")
        _uiState.update { it.copy(isSubmitting = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                projectsRepository.createProject(title = title, note = note)
                    .onSuccess {
                        closeAddProjectDialog()
                        loadProjects(forceFetch = true)
                        loadOptions()
                    }
                    .onFailure { error ->
                        showError(error, Res.string.error_save_item)
                    }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun openAddTaskDialog(project: ProjectWithTasks? = null) {
        AppLoggerManager.logFunction("ProjectsViewModel", "openAddTaskDialog", "projectId=${project?.projectId}")
        _uiState.update {
            it.copy(
                showAddTaskDialog = true,
                selectedProjectForNewTask = project,
                selectedTaskToEdit = null,
            )
        }
        loadOptions()
    }

    fun closeAddTaskDialog() {
        AppLoggerManager.logFunction("ProjectsViewModel", "closeAddTaskDialog")
        _uiState.update {
            it.copy(
                showAddTaskDialog = false,
                selectedProjectForNewTask = null,
                selectedTaskToEdit = null,
            )
        }
    }

    fun createNextActionInProject(
        title: String,
        note: String,
        projectId: String?,
        dueAt: String?,
        contextIds: List<String>,
        newContextNames: List<String>,
        newContexts: List<NewContextInput> = emptyList(),
        tagIds: List<String>,
        newTagNames: List<String>,
        files: List<InputFile> = emptyList(),
    ) {
        AppLoggerManager.logFunction("ProjectsViewModel", "createNextActionInProject", "title=$title")
        _uiState.update { it.copy(isSubmitting = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                nextActionsRepository.createNextAction(
                    title = title,
                    note = note,
                    projectId = projectId,
                    dueAt = dueAt,
                    contextIds = contextIds,
                    newContextNames = newContextNames,
                    newContexts = newContexts,
                    tagIds = tagIds,
                    newTagNames = newTagNames,
                    files = files,
                ).onSuccess {
                    closeAddTaskDialog()
                    loadProjects(forceFetch = true)
                    loadOptions()
                }.onFailure { error ->
                    showError(error, Res.string.error_save_item)
                }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun createWaitingInProject(
        title: String,
        note: String,
        projectId: String?,
        dueAt: String?,
        waitingFor: String,
        followUpAt: String?,
        files: List<InputFile> = emptyList(),
    ) {
        AppLoggerManager.logFunction("ProjectsViewModel", "createWaitingInProject", "title=$title")
        _uiState.update { it.copy(isSubmitting = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                projectsRepository.createWaitingTask(
                    title = title,
                    note = note,
                    projectId = projectId,
                    dueAt = dueAt,
                    waitingFor = waitingFor,
                    followUpAt = followUpAt,
                    files = files,
                ).onSuccess {
                    closeAddTaskDialog()
                    loadProjects(forceFetch = true)
                    loadOptions()
                }.onFailure { error ->
                    showError(error, Res.string.error_save_item)
                }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun createScheduledInProject(
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
        files: List<InputFile> = emptyList(),
    ) {
        AppLoggerManager.logFunction("ProjectsViewModel", "createScheduledInProject", "title=$title")
        _uiState.update { it.copy(isSubmitting = true, errorMessageRes = null) }
        viewModelScope.launch {
            try {
                scheduledRepository.createScheduledTask(
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
                    closeAddTaskDialog()
                    loadProjects(forceFetch = true)
                    loadOptions()
                }.onFailure { error ->
                    showError(error, Res.string.error_save_item)
                }
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun deleteNextAction(itemId: String) {
        AppLoggerManager.logFunction("ProjectsViewModel", "deleteNextAction", "itemId=$itemId")
        viewModelScope.launch {
            nextActionsRepository.deleteNextAction(itemId)
                .onSuccess { loadProjects(forceFetch = true) }
                .onFailure { error -> showError(error, Res.string.error_delete_item) }
        }
    }

    fun deleteScheduledTask(itemId: String) {
        AppLoggerManager.logFunction("ProjectsViewModel", "deleteScheduledTask", "itemId=$itemId")
        viewModelScope.launch {
            scheduledRepository.deleteScheduledTask(itemId)
                .onSuccess { loadProjects(forceFetch = true) }
                .onFailure { error -> showError(error, Res.string.error_delete_item) }
        }
    }

    fun restoreToInbox(itemId: String) {
        AppLoggerManager.logFunction("ProjectsViewModel", "restoreToInbox", "itemId=$itemId")
        viewModelScope.launch {
            nextActionsRepository.restoreToInbox(itemId)
                .onSuccess { loadProjects(forceFetch = true) }
                .onFailure { error -> showError(error, Res.string.error_save_item) }
        }
    }

    private fun showError(error: Throwable, fallback: StringResource) {
        if (error is CancellationException) throw error
        _uiState.update { it.copy(errorMessageRes = itemErrorResource(error, fallback)) }
    }
}
