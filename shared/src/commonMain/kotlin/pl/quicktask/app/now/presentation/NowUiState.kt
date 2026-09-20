package pl.quicktask.app.now.presentation

import org.jetbrains.compose.resources.StringResource
import pl.quicktask.app.nextactions.model.NextActionOptions
import pl.quicktask.app.now.model.NowData
import pl.quicktask.app.now.model.NowItem

data class NowUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val nowData: NowData = NowData(),
    val options: NextActionOptions = NextActionOptions(),
    val errorMessageRes: StringResource? = null,
    val selectedItemToEdit: NowItem? = null,
    val showAddEditDialog: Boolean = false,
)
