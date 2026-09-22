package pl.quicktask.app.scheduled.presentation

import org.jetbrains.compose.resources.StringResource
import pl.quicktask.app.nextactions.model.NextActionOptions
import pl.quicktask.app.scheduled.model.ScheduledTask

data class ScheduledUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val items: List<ScheduledTask> = emptyList(),
    val options: NextActionOptions = NextActionOptions(),
    val errorMessageRes: StringResource? = null,
    val selectedItemToEdit: ScheduledTask? = null,
    val showAddEditDialog: Boolean = false,
    val itemToMoveToTrash: ScheduledTask? = null,
    val selectedContextFilter: String? = null,
    val selectedProjectIdFilter: String? = null,
) {
    val filteredItems: List<ScheduledTask>
        get() = items.filter { item ->
            val matchesContext = selectedContextFilter == null || item.contexts.any { it.contextId == selectedContextFilter || it.name == selectedContextFilter }
            val matchesProject = selectedProjectIdFilter == null || item.project?.projectId == selectedProjectIdFilter
            matchesContext && matchesProject
        }.sortedBy { it.scheduledAt }
}
