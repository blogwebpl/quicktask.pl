package pl.quicktask.todo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class DialogButtonStyle {
    PRIMARY,
    SECONDARY,
    OUTLINED,
    TEXT,
    DESTRUCTIVE,
}

data class DialogButton(
    val text: String,
    val onClick: () -> Unit,
    val style: DialogButtonStyle = DialogButtonStyle.PRIMARY,
    val enabled: Boolean = true,
)

/**
 * Uniwersalne, eleganckie okno dialogowe dla całego programu z pytaniem
 * oraz dowolnie zdefiniowaną listą przycisków i ich stylów.
 */
@Composable
fun AppConfirmationDialog(
    title: String? = null,
    text: String,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    buttons: List<DialogButton>,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Ikona nagłówka (opcjonalna)
                if (icon != null || iconPainter != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (icon != null) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp),
                                )
                            } else if (iconPainter != null) {
                                Icon(
                                    painter = iconPainter,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Tytuł dialogu (opcjonalny)
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Treść pytania / wiadomości
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Definiowalne przyciski akcji
                if (buttons.size <= 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        buttons.forEach { button ->
                            RenderDialogButton(
                                button = button,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        buttons.forEach { button ->
                            RenderDialogButton(
                                button = button,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderDialogButton(
    button: DialogButton,
    modifier: Modifier = Modifier,
) {
    when (button.style) {
        DialogButtonStyle.PRIMARY -> {
            Button(
                onClick = button.onClick,
                enabled = button.enabled,
                modifier = modifier,
            ) {
                Text(text = button.text, fontWeight = FontWeight.SemiBold)
            }
        }
        DialogButtonStyle.SECONDARY -> {
            FilledTonalButton(
                onClick = button.onClick,
                enabled = button.enabled,
                modifier = modifier,
            ) {
                Text(text = button.text, fontWeight = FontWeight.SemiBold)
            }
        }
        DialogButtonStyle.OUTLINED -> {
            OutlinedButton(
                onClick = button.onClick,
                enabled = button.enabled,
                modifier = modifier,
            ) {
                Text(text = button.text, fontWeight = FontWeight.SemiBold)
            }
        }
        DialogButtonStyle.TEXT -> {
            TextButton(
                onClick = button.onClick,
                enabled = button.enabled,
                modifier = modifier,
            ) {
                Text(text = button.text, fontWeight = FontWeight.SemiBold)
            }
        }
        DialogButtonStyle.DESTRUCTIVE -> {
            Button(
                onClick = button.onClick,
                enabled = button.enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = modifier,
            ) {
                Text(text = button.text, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
