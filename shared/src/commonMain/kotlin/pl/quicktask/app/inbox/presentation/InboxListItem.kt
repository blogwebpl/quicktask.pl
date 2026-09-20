package pl.quicktask.app.inbox.presentation

import pl.quicktask.app.items.model.InboxItem
import pl.quicktask.app.items.model.ProcessDestination


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.ic_book
import todo.shared.generated.resources.ic_check_circle
import todo.shared.generated.resources.ic_date_range
import todo.shared.generated.resources.ic_delete
import todo.shared.generated.resources.action_projects
import todo.shared.generated.resources.ic_hourglass_empty
import todo.shared.generated.resources.ic_list
import todo.shared.generated.resources.ic_play_arrow
import todo.shared.generated.resources.ic_process
import todo.shared.generated.resources.ic_star
import todo.shared.generated.resources.process_do_2min
import todo.shared.generated.resources.process_item
import todo.shared.generated.resources.screen_next_actions
import todo.shared.generated.resources.screen_reference
import todo.shared.generated.resources.screen_scheduled
import todo.shared.generated.resources.screen_someday
import todo.shared.generated.resources.screen_trash
import todo.shared.generated.resources.screen_waiting

@Composable
internal fun InboxListItem(
    item: InboxItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onProcess: (ProcessDestination) -> Unit = {},
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(4f)
                    .clickable(enabled = !isPending, onClick = onClick)
                    .padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                )

            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !isPending) { showMenu = true }
                    .padding(top = 16.dp, bottom = 16.dp, end = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isPending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Icon(
                        painter = painterResource(Res.drawable.ic_process),
                        contentDescription = stringResource(Res.string.process_item),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    // 1. Zrób w 2 minuty (Do it in 2 minutes)
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.process_do_2min)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_check_circle),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onProcess(ProcessDestination.TWO_MINUTES)
                        },
                    )

                    HorizontalDivider()

                    // 2. Następne działanie (Next Action)
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.screen_next_actions)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_play_arrow),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onProcess(ProcessDestination.NEXT_ACTION)
                        },
                    )

                    // 3. Projekt (Project)
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.action_projects)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_list),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onProcess(ProcessDestination.PROJECT)
                        },
                    )

                    // 4. Oczekujące (Waiting)
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.screen_waiting)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_hourglass_empty),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onProcess(ProcessDestination.WAITING)
                        },
                    )

                    // 5. Zaplanowane (Scheduled)
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.screen_scheduled)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_date_range),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onProcess(ProcessDestination.SCHEDULED)
                        },
                    )

                    HorizontalDivider()

                    // 6. Kiedyś / Może (Someday/Maybe)
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.screen_someday)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_star),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onProcess(ProcessDestination.SOMEDAY)
                        },
                    )

                    // 7. Materiały / Referencje (Reference)
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.screen_reference)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_book),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onProcess(ProcessDestination.REFERENCE)
                        },
                    )

                    HorizontalDivider()

                    // 8. Usuń (Delete)
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
