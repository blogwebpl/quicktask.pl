package pl.quicktask.app.completed.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.ktor.util.date.GMTDate
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.di.AppModule
import pl.quicktask.app.items.model.AttachmentMetadataState
import pl.quicktask.app.items.model.CompletedInTwoMinutesItem
import pl.quicktask.app.ui.components.AppConfirmationDialog
import pl.quicktask.app.ui.components.AppTopBar
import pl.quicktask.app.ui.components.DialogButton
import pl.quicktask.app.ui.components.DialogButtonLayout
import pl.quicktask.app.ui.components.DialogButtonStyle
import pl.quicktask.app.ui.components.DialogType
import todo.shared.generated.resources.*
import kotlin.time.Instant

@Composable
fun CompletedScreen(
    module: AppModule,
    onOpenDrawer: () -> Unit = {},
    viewModel: CompletedViewModel = viewModel { CompletedViewModel(module.items.completed, module.items.store) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val opened = state.items.find { it.itemId == state.openedItemId }
    var itemToTrashId by remember { mutableStateOf<String?>(null) }
    val itemToTrash = state.items.find { it.itemId == itemToTrashId }

    if (opened != null) {
        AlertDialog(
            onDismissRequest = viewModel::closeItem,
            title = { Text(opened.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(Res.string.completed_at, completedDate(opened.processedAt)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (opened.note.isNotBlank()) Text(opened.note)
                    if (opened.tags.isNotEmpty()) {
                        Text(opened.tags.joinToString("  ") { "#${it.name}" }, color = MaterialTheme.colorScheme.primary)
                    }
                    if (opened.attachments.isNotEmpty()) {
                        Text(stringResource(Res.string.task_attachments_label), style = MaterialTheme.typography.titleSmall)
                        opened.attachments.forEach { attachment ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(Res.drawable.ic_attach_file), null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(when (attachment.metadataState) {
                                    AttachmentMetadataState.DECRYPTION_FAILED -> stringResource(Res.string.attachment_metadata_decryption_failed)
                                    AttachmentMetadataState.AVAILABLE -> attachment.name ?: stringResource(Res.string.attachment_unnamed)
                                })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::closeItem) { Text(stringResource(Res.string.action_close)) }
            },
        )
    }

    if (itemToTrash != null) {
        AppConfirmationDialog(
            title = stringResource(Res.string.completed_trash),
            text = stringResource(Res.string.completed_trash_question, itemToTrash.title),
            type = DialogType.DANGER,
            buttonLayout = DialogButtonLayout.VERTICAL,
            onDismissRequest = { itemToTrashId = null },
            buttons = listOf(
                DialogButton(stringResource(Res.string.timer_cancel), onClick = { itemToTrashId = null }, style = DialogButtonStyle.TEXT),
                DialogButton(stringResource(Res.string.completed_trash), onClick = {
                    viewModel.deleteItem(itemToTrash.itemId)
                    itemToTrashId = null
                }, style = DialogButtonStyle.DESTRUCTIVE),
            ),
        )
    }

    Scaffold(topBar = {
        AppTopBar(
            title = stringResource(Res.string.screen_completed),
            onOpenDrawer = onOpenDrawer,
            actions = {
                TextButton(onClick = viewModel::refresh, enabled = !state.isLoading) {
                    Text(stringResource(Res.string.completed_refresh))
                }
            },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading && state.items.isNotEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.errorMessageRes?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(error), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = viewModel::refresh, enabled = !state.isLoading) {
                            Text(stringResource(Res.string.completed_refresh))
                        }
                    }
                }
            }
            when {
                state.isLoading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.items.isEmpty() && state.errorMessageRes == null -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_check_circle),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(Res.string.completed_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                state.items.isNotEmpty() -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(Res.string.completed_subtitle),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ) {
                                Text(
                                    state.items.size.toString(),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                    items(state.items, key = { it.itemId }) { item ->
                        CompletedItemCard(
                            item = item,
                            isOperating = item.itemId in state.operatingIds,
                            onOpen = { viewModel.openItem(item.itemId) },
                            onRestore = { viewModel.restoreItem(item.itemId) },
                            onTrash = { itemToTrashId = item.itemId },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedItemCard(
    item: CompletedInTwoMinutesItem,
    isOperating: Boolean,
    onOpen: () -> Unit,
    onRestore: () -> Unit,
    onTrash: () -> Unit,
) {
    val enabled = !isOperating && !item.isPendingConfirmation
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().graphicsLayer(alpha = if (item.isPendingConfirmation) 0.65f else 1f),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    painterResource(Res.drawable.ic_check_circle),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(Res.string.completed_at, completedDate(item.processedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.note.isNotBlank()) Text(
                item.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.tags.isNotEmpty() || item.attachments.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (item.tags.isNotEmpty()) Text(
                        item.tags.joinToString("  ") { "#${it.name}" },
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.attachments.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(Res.drawable.ic_attach_file), null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(item.attachments.size.toString(), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (isOperating) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onRestore, enabled = enabled) {
                    Text(stringResource(Res.string.completed_restore))
                }
                IconButton(onClick = onTrash, enabled = enabled) {
                    Icon(
                        painterResource(Res.drawable.ic_delete),
                        contentDescription = stringResource(Res.string.completed_trash),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** Show UTC explicitly so a server timestamp is never mistaken for local time. */
internal fun completedDate(value: String): String {
    val date = runCatching { GMTDate(Instant.parse(value).toEpochMilliseconds()) }.getOrNull()
    if (date != null) {
        val day = date.dayOfMonth.toString().padStart(2, '0')
        val month = (date.month.ordinal + 1).toString().padStart(2, '0')
        val hour = date.hours.toString().padStart(2, '0')
        val minute = date.minutes.toString().padStart(2, '0')
        return "$day.$month.${date.year}, $hour:$minute UTC"
    }
    val match = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").matchEntire(value)
    return match?.let { "${it.groupValues[3]}.${it.groupValues[2]}.${it.groupValues[1]}" } ?: value
}
