package pl.quicktask.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
@Composable
internal fun RenderDialogButton(
    button: DialogButton,
    modifier: Modifier = Modifier,
) {
    val isEnabled = button.enabled && !button.isLoading

    val spinnerColor = when (button.style) {
        DialogButtonStyle.PRIMARY -> MaterialTheme.colorScheme.onPrimary
        DialogButtonStyle.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
        DialogButtonStyle.OUTLINED -> MaterialTheme.colorScheme.primary
        DialogButtonStyle.TEXT -> MaterialTheme.colorScheme.primary
        DialogButtonStyle.DESTRUCTIVE -> MaterialTheme.colorScheme.onError
    }

    @Composable
    fun ButtonContent() {
        if (button.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = spinnerColor,
            )
        } else {
            Text(
                text = button.text,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    when (button.style) {
        DialogButtonStyle.PRIMARY -> {
            Button(
                onClick = button.onClick,
                enabled = isEnabled,
                modifier = modifier,
            ) {
                ButtonContent()
            }
        }
        DialogButtonStyle.SECONDARY -> {
            FilledTonalButton(
                onClick = button.onClick,
                enabled = isEnabled,
                modifier = modifier,
            ) {
                ButtonContent()
            }
        }
        DialogButtonStyle.OUTLINED -> {
            OutlinedButton(
                onClick = button.onClick,
                enabled = isEnabled,
                modifier = modifier,
            ) {
                ButtonContent()
            }
        }
        DialogButtonStyle.TEXT -> {
            TextButton(
                onClick = button.onClick,
                enabled = isEnabled,
                modifier = modifier,
            ) {
                ButtonContent()
            }
        }
        DialogButtonStyle.DESTRUCTIVE -> {
            Button(
                onClick = button.onClick,
                enabled = isEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = modifier,
            ) {
                ButtonContent()
            }
        }
    }
}
