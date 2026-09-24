package pl.quicktask.app.waiting.presentation

import org.jetbrains.compose.resources.StringResource
import pl.quicktask.app.nextactions.model.NextActionOptions
import pl.quicktask.app.projects.model.DecryptedProjectTask

data class WaitingListEntry(
    val task: DecryptedProjectTask,
    val projectTitle: String? = null,
)

data class WaitingUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val items: List<WaitingListEntry> = emptyList(),
    val options: NextActionOptions = NextActionOptions(),
    val showAddDialog: Boolean = false,
    val errorMessageRes: StringResource? = null,
)
