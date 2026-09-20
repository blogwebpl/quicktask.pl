package pl.quicktask.app.inbox.presentation.dialogs




import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.ui.components.AppConfirmationDialog
import pl.quicktask.app.ui.components.DialogButton
import pl.quicktask.app.ui.components.DialogButtonStyle
import pl.quicktask.app.ui.components.DialogType
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.dialog_delete_confirm
import todo.shared.generated.resources.dialog_delete_item_question
import todo.shared.generated.resources.dialog_delete_item_title
import todo.shared.generated.resources.ic_delete
import todo.shared.generated.resources.timer_cancel

@Composable
internal fun DeleteInboxItemConfirmationDialog(
    itemTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AppConfirmationDialog(
        title = stringResource(Res.string.dialog_delete_item_title),
        text = stringResource(Res.string.dialog_delete_item_question, itemTitle),
        type = DialogType.DANGER,
        iconPainter = painterResource(Res.drawable.ic_delete),
        buttons = listOf(
            DialogButton(
                text = stringResource(Res.string.timer_cancel),
                onClick = onDismiss,
                style = DialogButtonStyle.TEXT,
            ),
            DialogButton(
                text = stringResource(Res.string.dialog_delete_confirm),
                onClick = onConfirm,
                style = DialogButtonStyle.DESTRUCTIVE,
            ),
        ),
        onDismissRequest = onDismiss,
    )
}
