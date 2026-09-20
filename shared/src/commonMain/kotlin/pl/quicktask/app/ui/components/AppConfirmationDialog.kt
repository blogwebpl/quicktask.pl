package pl.quicktask.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Uniwersalne, nowoczesne okno dialogowe potwierdzenia / zapytania zgodne z Material Design 3.
 *
 * Wspiera:
 * - Opcjonalny tytuł (`title`), treść tekstową (`text`) oraz własny widok (`content`).
 * - Typy dialogów (`QUESTION`, `DANGER`, `WARNING`, `INFO`) z automatycznymi lub własnymi ikonami i paletami.
 * - Wybalansowane układy przycisków (`AUTO`, `HORIZONTAL`, `VERTICAL`) z poprawnymi kolorami spinnera ładowania.
 * - Automatyczną blokadę zamykania okna podczas wykonywania operacji (`isLoading`).
 */
@Composable
fun AppConfirmationDialog(
    title: String? = null,
    text: String? = null,
    type: DialogType = DialogType.QUESTION,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    showIcon: Boolean = true,
    iconContainerColor: Color? = null,
    iconContentColor: Color? = null,
    buttons: List<DialogButton>,
    onDismissRequest: () -> Unit,
    buttonLayout: DialogButtonLayout = DialogButtonLayout.AUTO,
    maxWidth: Dp = 440.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape: Shape = RoundedCornerShape(28.dp),
    properties: DialogProperties? = null,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    val isAnyLoading = buttons.any { it.isLoading }
    val hasDestructiveButton = buttons.any { it.style == DialogButtonStyle.DESTRUCTIVE }
    val effectiveType = if ((type == DialogType.QUESTION) && hasDestructiveButton) DialogType.DANGER else type

    val resolvedIconContainerColor = iconContainerColor ?: when (effectiveType) {
        DialogType.DANGER -> MaterialTheme.colorScheme.errorContainer
        DialogType.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        DialogType.INFO -> MaterialTheme.colorScheme.secondaryContainer
        DialogType.QUESTION -> MaterialTheme.colorScheme.primaryContainer
    }

    val resolvedIconContentColor = iconContentColor ?: when (effectiveType) {
        DialogType.DANGER -> MaterialTheme.colorScheme.onErrorContainer
        DialogType.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
        DialogType.INFO -> MaterialTheme.colorScheme.onSecondaryContainer
        DialogType.QUESTION -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    val defaultIconVector: ImageVector? = if (showIcon && (icon == null) && (iconPainter == null)) {
        when (effectiveType) {
            DialogType.DANGER -> Icons.Default.Delete
            DialogType.WARNING -> Icons.Default.Warning
            DialogType.INFO -> Icons.Default.Info
            DialogType.QUESTION -> Icons.Default.Info
        }
    } else null

    val resolvedProperties = properties ?: DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = !isAnyLoading,
        dismissOnClickOutside = !isAnyLoading,
    )

    Dialog(
        onDismissRequest = {
            if (!isAnyLoading) {
                onDismissRequest()
            }
        },
        properties = resolvedProperties,
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = maxWidth)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            shape = shape,
            color = containerColor,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Ikona nagłówka (opcjonalna lub domyślna)
                if (showIcon && ((icon != null) || (iconPainter != null) || (defaultIconVector != null))) {
                    Surface(
                        shape = CircleShape,
                        color = resolvedIconContainerColor,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when {
                                icon != null -> {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = resolvedIconContentColor,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                                iconPainter != null -> {
                                    Icon(
                                        painter = iconPainter,
                                        contentDescription = null,
                                        tint = resolvedIconContentColor,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                                defaultIconVector != null -> {
                                    Icon(
                                        imageVector = defaultIconVector,
                                        contentDescription = null,
                                        tint = resolvedIconContentColor,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
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
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Treść tekstowa (opcjonalna)
                if (!text.isNullOrBlank()) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = if (text.length > 120) TextAlign.Start else TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Własny slot treści (opcjonalny)
                if (content != null) {
                    if (!text.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    content()
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Układ przycisków z wybalansowaną hierarchią M3
                val isHorizontal = when (buttonLayout) {
                    DialogButtonLayout.HORIZONTAL -> true
                    DialogButtonLayout.VERTICAL -> false
                    DialogButtonLayout.AUTO -> buttons.size <= 2
                }

                if (isHorizontal) {
                    val hasTextOrOutlined = buttons.any {
                        (it.style == DialogButtonStyle.TEXT) || (it.style == DialogButtonStyle.OUTLINED)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        buttons.forEach { button ->
                            RenderDialogButton(
                                button = button,
                                modifier = if (hasTextOrOutlined) {
                                    Modifier.defaultMinSize(minWidth = 80.dp)
                                } else {
                                    Modifier.weight(1f)
                                },
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
