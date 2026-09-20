package pl.quicktask.app.nextactions.presentation

import org.jetbrains.compose.resources.StringResource
import pl.quicktask.app.nextactions.model.NextAction
import pl.quicktask.app.nextactions.model.NextActionOptions

data class NextActionsUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val items: List<NextAction> = emptyList(),
    val options: NextActionOptions = NextActionOptions(),
    val errorMessageRes: StringResource? = null,
    val selectedItemToEdit: NextAction? = null,
    val showAddEditDialog: Boolean = false,
    val itemToMoveToTrash: NextAction? = null,
    val selectedContextFilter: String? = null,
    val selectedProjectIdFilter: String? = null,
) {
    val filteredItems: List<NextAction>
        get() = items.filter { item ->
            val matchesContext = selectedContextFilter == null || item.contexts.any { it.contextId == selectedContextFilter || it.name == selectedContextFilter }
            val matchesProject = selectedProjectIdFilter == null || item.project?.projectId == selectedProjectIdFilter
            matchesContext && matchesProject
        }
}
