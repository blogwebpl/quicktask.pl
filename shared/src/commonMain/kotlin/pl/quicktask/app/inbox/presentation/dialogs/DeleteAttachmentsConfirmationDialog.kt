package pl.quicktask.app.inbox.presentation.dialogs




import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.ui.components.AppConfirmationDialog
import pl.quicktask.app.ui.components.DialogButton
import pl.quicktask.app.ui.components.DialogButtonStyle
import pl.quicktask.app.ui.components.DialogType
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.dialog_delete_attachments_confirm
import todo.shared.generated.resources.dialog_delete_attachments_question
import todo.shared.generated.resources.dialog_delete_attachments_title
import todo.shared.generated.resources.dialog_keep_attachments
import todo.shared.generated.resources.ic_attach_file
import todo.shared.generated.resources.timer_cancel

@Composable
internal fun DeleteAttachmentsConfirmationDialog(
    onDeleteAttachments: () -> Unit,
    onKeepAttachments: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppConfirmationDialog(
        title = stringResource(Res.string.dialog_delete_attachments_title),
        text = stringResource(Res.string.dialog_delete_attachments_question),
        type = DialogType.WARNING,
        iconPainter = painterResource(Res.drawable.ic_attach_file),
        buttons = listOf(
            DialogButton(
                text = stringResource(Res.string.dialog_delete_attachments_confirm),
                onClick = onDeleteAttachments,
                style = DialogButtonStyle.DESTRUCTIVE,
            ),
            DialogButton(
                text = stringResource(Res.string.dialog_keep_attachments),
                onClick = onKeepAttachments,
                style = DialogButtonStyle.SECONDARY,
            ),
            DialogButton(
                text = stringResource(Res.string.timer_cancel),
                onClick = onDismiss,
                style = DialogButtonStyle.TEXT,
            ),
        ),
        onDismissRequest = onDismiss,
    )
}
