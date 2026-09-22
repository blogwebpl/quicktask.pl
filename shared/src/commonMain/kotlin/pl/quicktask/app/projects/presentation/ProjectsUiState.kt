package pl.quicktask.app.projects.presentation

import org.jetbrains.compose.resources.StringResource
import pl.quicktask.app.nextactions.model.NextActionOptions
import pl.quicktask.app.projects.model.DecryptedProjectTask
import pl.quicktask.app.projects.model.ProjectWithTasks

data class ProjectsUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val projects: List<ProjectWithTasks> = emptyList(),
    val unassignedTasks: List<DecryptedProjectTask> = emptyList(),
    val options: NextActionOptions = NextActionOptions(),
    val showAddProjectDialog: Boolean = false,
    val selectedProjectForNewTask: ProjectWithTasks? = null,
    val showAddTaskDialog: Boolean = false,
    val selectedTaskToEdit: DecryptedProjectTask? = null,
    val errorMessageRes: StringResource? = null,
)
