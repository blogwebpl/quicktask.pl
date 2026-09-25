package pl.quicktask.app.references.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.di.AppModule
import pl.quicktask.app.nextactions.model.NextActionTag
import pl.quicktask.app.references.model.ReferenceItem
import pl.quicktask.app.ui.components.AppAddButton
import pl.quicktask.app.ui.components.AppConfirmationDialog
import pl.quicktask.app.ui.components.AppTopBar
import pl.quicktask.app.ui.components.DialogButton
import pl.quicktask.app.ui.components.DialogButtonStyle
import pl.quicktask.app.ui.components.DialogType
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.action_close
import todo.shared.generated.resources.action_restore
import todo.shared.generated.resources.dialog_delete_confirm
import todo.shared.generated.resources.ic_book
import todo.shared.generated.resources.ic_delete
import todo.shared.generated.resources.ic_tag
import todo.shared.generated.resources.reference_delete_question
import todo.shared.generated.resources.reference_empty
import todo.shared.generated.resources.reference_tags_edit
import todo.shared.generated.resources.screen_reference
import todo.shared.generated.resources.screen_trash
import todo.shared.generated.resources.timer_cancel

@Composable
fun ReferenceScreen(
    module: AppModule,
    onOpenDrawer: () -> Unit = {},
    viewModel: ReferenceViewModel = viewModel { ReferenceViewModel(module.items.references, module.items.store) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var itemToEditTags by remember { mutableStateOf<ReferenceItem?>(null) }
    var itemToDelete by remember { mutableStateOf<ReferenceItem?>(null) }
    var itemToView by remember { mutableStateOf<ReferenceItem?>(null) }
    var availableTags by remember { mutableStateOf<List<NextActionTag>>(emptyList()) }

    LaunchedEffect(module) {
        module.items.nextActions.getNextActionOptions().onSuccess { availableTags = it.tags }
    }

    Scaffold(
        topBar = { AppTopBar(title = stringResource(Res.string.screen_reference), onOpenDrawer = onOpenDrawer) },
        floatingActionButton = { AppAddButton(onClick = { viewModel.clearError(); showCreate = true }, contentDescription = null) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading && state.items.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.items.isEmpty() -> Text(
                    stringResource(Res.string.reference_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.items, key = { it.itemId }) { item ->
                        ReferenceCard(item, state.busyItemId == item.itemId,
                            onView = { itemToView = item },
                            onTags = { viewModel.clearError(); itemToEditTags = item },
                            onRestore = { viewModel.restoreToInbox(item.itemId) },
                            onDelete = { itemToDelete = item })
                    }
                }
            }
            state.errorMessageRes?.let { error ->
                Card(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                        Text(stringResource(error), modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::clearError) { Text(stringResource(Res.string.action_close)) }
                    }
                }
            }
        }
    }

    if (showCreate) {
        ReferenceEditorDialog(availableTags, state.isSaving, state.uploadProgress, state.errorMessageRes,
            onDismiss = { showCreate = false },
            onSave = { title, note, tagIds, newTagNames, files ->
                viewModel.create(title, note, tagIds, newTagNames, files) { showCreate = false }
            })
    }

    itemToEditTags?.let { item ->
        ReferenceTagsDialog(false, availableTags, item.tags, state.busyItemId == item.itemId, state.errorMessageRes,
            onDismiss = { itemToEditTags = null },
            onSave = { ids, names ->
                viewModel.updateTags(item.itemId, ids, names) { itemToEditTags = null }
            })
    }

    itemToDelete?.let { item ->
        AppConfirmationDialog(
            title = stringResource(Res.string.screen_trash),
            text = stringResource(Res.string.reference_delete_question, item.title),
            type = DialogType.DANGER,
            buttons = listOf(
                DialogButton(stringResource(Res.string.timer_cancel), onClick = { itemToDelete = null }, style = DialogButtonStyle.TEXT),
                DialogButton(stringResource(Res.string.dialog_delete_confirm), onClick = {
                    viewModel.moveToTrash(item.itemId)
                    itemToDelete = null
                }, style = DialogButtonStyle.DESTRUCTIVE),
            ),
            onDismissRequest = { itemToDelete = null },
        )
    }

    itemToView?.let { item ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { itemToView = null },
            title = { Text(item.title) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (item.note.isNotBlank()) Text(item.note)
                    if (item.tags.isNotEmpty()) Text(item.tags.joinToString("  ") { "#${it.name}" })
                    item.attachments.forEach { attachment -> Text(attachment.name ?: "…") }
                }
            },
            confirmButton = { TextButton(onClick = { itemToView = null }) { Text(stringResource(Res.string.action_close)) } },
        )
    }
}

@Composable
private fun ReferenceCard(
    item: ReferenceItem,
    isBusy: Boolean,
    onView: () -> Unit,
    onTags: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(onClick = onView, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                if (item.note.isNotBlank()) Text(item.note, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                if (item.tags.isNotEmpty()) Text(item.tags.joinToString("  ") { "#${it.name}" },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (item.attachments.isNotEmpty()) Text(item.attachments.joinToString(" · ") { it.name ?: "…" },
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { showMenu = true }, enabled = !isBusy) {
                    if (isBusy) CircularProgressIndicator()
                    else Icon(painterResource(Res.drawable.ic_book), contentDescription = stringResource(Res.string.screen_reference))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(Res.string.reference_tags_edit)) },
                        leadingIcon = { Icon(painterResource(Res.drawable.ic_tag), null) },
                        onClick = { showMenu = false; onTags() })
                    DropdownMenuItem(text = { Text(stringResource(Res.string.action_restore)) },
                        onClick = { showMenu = false; onRestore() })
                    DropdownMenuItem(text = { Text(stringResource(Res.string.screen_trash)) },
                        leadingIcon = { Icon(painterResource(Res.drawable.ic_delete), null) },
                        onClick = { showMenu = false; onDelete() })
                }
            }
        }
    }
}
