package pl.quicktask.app.trash.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.di.AppModule
import pl.quicktask.app.trash.model.TrashItem
import pl.quicktask.app.ui.components.AppConfirmationDialog
import pl.quicktask.app.ui.components.AppTopBar
import pl.quicktask.app.ui.components.DialogButton
import pl.quicktask.app.ui.components.DialogButtonStyle
import pl.quicktask.app.ui.components.DialogType
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.action_restore
import todo.shared.generated.resources.dialog_delete_confirm
import todo.shared.generated.resources.dialog_empty_trash_confirm
import todo.shared.generated.resources.dialog_empty_trash_question
import todo.shared.generated.resources.dialog_empty_trash_title
import todo.shared.generated.resources.dialog_permanent_delete_question
import todo.shared.generated.resources.dialog_permanent_delete_title
import todo.shared.generated.resources.ic_attach_file
import todo.shared.generated.resources.ic_delete
import todo.shared.generated.resources.screen_trash
import todo.shared.generated.resources.timer_cancel
import todo.shared.generated.resources.trash_attachments_description
import todo.shared.generated.resources.trash_delete_permanently_description
import todo.shared.generated.resources.trash_empty
import todo.shared.generated.resources.trash_empty_all
import todo.shared.generated.resources.trash_items_count

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    module: AppModule,
    onOpenDrawer: () -> Unit = {},
    viewModel: TrashViewModel = viewModel { TrashViewModel(module.items.trash, module.items.store) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    uiState.itemToPermanentlyDelete?.let { item ->
        AppConfirmationDialog(
            title = stringResource(Res.string.dialog_permanent_delete_title),
            text = stringResource(Res.string.dialog_permanent_delete_question, item.title),
            type = DialogType.DANGER,
            onDismissRequest = { viewModel.cancelPermanentDelete() },
            buttons = listOf(
                DialogButton(
                    text = stringResource(Res.string.timer_cancel),
                    onClick = { viewModel.cancelPermanentDelete() },
                    style = DialogButtonStyle.TEXT,
                    enabled = !uiState.isOperating(item.itemId),
                ),
                DialogButton(
                    text = stringResource(Res.string.dialog_delete_confirm),
                    onClick = { viewModel.confirmPermanentDelete() },
                    style = DialogButtonStyle.DESTRUCTIVE,
                    isLoading = uiState.isOperating(item.itemId),
                ),
            ),
        )
    }

    if (uiState.showEmptyTrashConfirmation) {
        AppConfirmationDialog(
            title = stringResource(Res.string.dialog_empty_trash_title),
            text = stringResource(Res.string.dialog_empty_trash_question),
            type = DialogType.DANGER,
            onDismissRequest = { viewModel.cancelEmptyTrash() },
            buttons = listOf(
                DialogButton(
                    text = stringResource(Res.string.timer_cancel),
                    onClick = { viewModel.cancelEmptyTrash() },
                    style = DialogButtonStyle.TEXT,
                    enabled = uiState.canEmptyTrash,
                ),
                DialogButton(
                    text = stringResource(Res.string.dialog_empty_trash_confirm),
                    onClick = { viewModel.confirmEmptyTrash() },
                    style = DialogButtonStyle.DESTRUCTIVE,
                    isLoading = uiState.isEmptyingTrash,
                ),
            ),
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.screen_trash),
                onOpenDrawer = onOpenDrawer,
                actions = {
                    if (uiState.items.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.requestEmptyTrash() },
                            enabled = uiState.canEmptyTrash,
                        ) {
                            Text(
                                text = stringResource(Res.string.trash_empty_all),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                uiState.isLoading && uiState.items.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                uiState.items.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_delete),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(Res.string.trash_empty),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(Res.string.trash_items_count, uiState.items.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedButton(
                                    onClick = { viewModel.requestEmptyTrash() },
                                    enabled = uiState.canEmptyTrash,
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_delete),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(Res.string.trash_empty_all),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }

                        items(
                            items = uiState.items,
                            key = { it.itemId },
                        ) { item ->
                            TrashItemCard(
                                item = item,
                                isOperating = uiState.isOperating(item.itemId),
                                onRestore = { viewModel.restoreItem(item.itemId) },
                                onDeletePermanently = { viewModel.requestPermanentDelete(item) },
                            )
                        }
                    }
                }
            }

            val errorText = uiState.errorMessageRes?.let { stringResource(it) }

            errorText?.let { error ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
