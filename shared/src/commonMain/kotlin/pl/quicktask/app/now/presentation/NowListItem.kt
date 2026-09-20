package pl.quicktask.app.now.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.now.model.NowItem
import pl.quicktask.app.ui.components.todayIso
import pl.quicktask.app.ui.components.tomorrowIso
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.ic_date_range
import todo.shared.generated.resources.ic_delete
import todo.shared.generated.resources.ic_tag
import todo.shared.generated.resources.ic_email
import todo.shared.generated.resources.ic_hourglass_empty
import todo.shared.generated.resources.ic_list
import todo.shared.generated.resources.screen_inbox
import todo.shared.generated.resources.screen_trash
import todo.shared.generated.resources.task_edit_title

import todo.shared.generated.resources.date_overdue_format
import todo.shared.generated.resources.date_today
import todo.shared.generated.resources.date_tomorrow
import todo.shared.generated.resources.scheduled_date_format
import todo.shared.generated.resources.waiting_for_format

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NowListItem(
    item: NowItem,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onRestoreToInbox: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val isPending = item.isPendingConfirmation

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = if (isPending) 0.65f else 1.0f),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !isPending, onClick = onClick)
                    .padding(16.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                )

                if (item.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }

                if (item.project != null || item.dueAt != null || item.scheduledAt != null ||
                    item.waitingFor != null || item.contexts.isNotEmpty() || item.tags.isNotEmpty()
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item.project?.let { project ->
                            AssistChip(
                                onClick = {},
                                label = { Text(project.title) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_list),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }

                        item.dueAt?.let { due ->
                            val datePart = due.substringBefore('T')
                            val todayPart = todayIso()
                            val tomorrowPart = tomorrowIso()
                            val todayText = stringResource(Res.string.date_today)
                            val tomorrowText = stringResource(Res.string.date_tomorrow)
                            val overdueText = stringResource(Res.string.date_overdue_format, datePart)
                            val (labelText, containerColor, contentColor) = when {
                                datePart == todayPart -> Triple(todayText, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                                datePart == tomorrowPart -> Triple(tomorrowText, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                                datePart < todayPart -> Triple(overdueText, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                                else -> Triple(datePart, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface)
                            }
                            AssistChip(
                                onClick = {},
                                label = { Text(labelText) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = containerColor,
                                    labelColor = contentColor,
                                ),
                            )
                        }

                        item.scheduledAt?.let { scheduled ->
                            val datePart = scheduled.substringBefore('T')
                            AssistChip(
                                onClick = {},
                                label = { Text(stringResource(Res.string.scheduled_date_format, datePart)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_date_range),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            )
                        }

                        item.waitingFor?.let { waiting ->
                            AssistChip(
                                onClick = {},
                                label = { Text(stringResource(Res.string.waiting_for_format, waiting)) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_hourglass_empty),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }

                        item.contexts.forEach { ctx ->
                            val displayName = if (ctx.name.startsWith("@")) ctx.name else "@${ctx.name}"
                            val hasGps = ctx.lat != null && ctx.lon != null
                            AssistChip(
                                onClick = {},
                                label = { Text(displayName) },
                                leadingIcon = if (hasGps) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                } else null,
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            )
                        }

                        item.tags.forEach { tag ->
                            AssistChip(
                                onClick = {},
                                label = { Text("#${tag.name}") },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_tag),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.padding(top = 8.dp, end = 4.dp),
            ) {
                if (isPending) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(12.dp)
                            .size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                        )
                    }
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.task_edit_title)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.screen_inbox)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_email),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onRestoreToInbox()
                        },
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.screen_trash)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_delete),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
