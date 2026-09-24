package pl.quicktask.app.completed.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.di.AppModule
import pl.quicktask.app.items.model.AttachmentMetadataState
import pl.quicktask.app.ui.components.AppTopBar
import todo.shared.generated.resources.*

@Composable
fun CompletedScreen(
    module: AppModule,
    onOpenDrawer: () -> Unit = {},
    viewModel: CompletedViewModel = viewModel { CompletedViewModel(module.items.completed, module.items.store) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val opened = state.items.find { it.itemId == state.openedItemId }

    if (opened != null) {
        AlertDialog(
            onDismissRequest = viewModel::closeItem,
            title = { Text(opened.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.completed_at, opened.processedAt))
                    if (opened.note.isNotBlank()) Text(opened.note)
                    if (opened.tags.isNotEmpty()) Text(opened.tags.joinToString(" · ") { it.name })
                    if (opened.attachments.isNotEmpty()) {
                        Text(stringResource(Res.string.task_attachments_label), style = MaterialTheme.typography.titleSmall)
                        opened.attachments.forEach { attachment ->
                            Text(when (attachment.metadataState) {
                                AttachmentMetadataState.DECRYPTION_FAILED -> stringResource(Res.string.attachment_metadata_decryption_failed)
                                AttachmentMetadataState.AVAILABLE -> attachment.name ?: stringResource(Res.string.attachment_unnamed)
                            })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::closeItem) { Text(stringResource(Res.string.action_close)) }
            },
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
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.errorMessageRes?.let { error ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(error), Modifier.padding(16.dp))
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text(stringResource(Res.string.completed_subtitle), style = MaterialTheme.typography.titleMedium) }
                if (state.items.isEmpty() && !state.isLoading && state.errorMessageRes == null) {
                    item { Text(stringResource(Res.string.completed_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(state.items, key = { it.itemId }) { item ->
                    val enabled = item.itemId !in state.operatingIds && !item.isPendingConfirmation
                    Card(onClick = { viewModel.openItem(item.itemId) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            if (item.note.isNotBlank()) Text(item.note, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(Res.string.completed_at, item.processedAt), style = MaterialTheme.typography.bodySmall)
                            if (item.itemId in state.operatingIds) LinearProgressIndicator(Modifier.fillMaxWidth())
                            TextButton(onClick = { viewModel.restoreItem(item.itemId) }, enabled = enabled) {
                                Text(stringResource(Res.string.completed_restore))
                            }
                            TextButton(onClick = { viewModel.deleteItem(item.itemId) }, enabled = enabled) {
                                Text(stringResource(Res.string.completed_trash))
                            }
                        }
                    }
                }
            }
        }
    }
}
