package pl.quicktask.app.inbox

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
import androidx.compose.material3.TopAppBar
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
import pl.quicktask.app.ui.components.AppConfirmationDialog
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
import todo.shared.generated.resources.ic_menu
import todo.shared.generated.resources.menu
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
    onOpenDrawer: () -> Unit = {},
    viewModel: TrashViewModel = viewModel { TrashViewModel() },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.itemToPermanentlyDelete != null) {
        val item = uiState.itemToPermanentlyDelete!!
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
                    enabled = !uiState.isOperating,
                ),
                DialogButton(
                    text = stringResource(Res.string.dialog_delete_confirm),
                    onClick = { viewModel.confirmPermanentDelete() },
                    style = DialogButtonStyle.DESTRUCTIVE,
                    isLoading = uiState.isOperating,
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
                    enabled = !uiState.isOperating,
                ),
                DialogButton(
                    text = stringResource(Res.string.dialog_empty_trash_confirm),
                    onClick = { viewModel.confirmEmptyTrash() },
                    style = DialogButtonStyle.DESTRUCTIVE,
                    isLoading = uiState.isOperating,
                ),
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.screen_trash)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_menu),
                            contentDescription = stringResource(Res.string.menu),
                        )
                    }
                },
                actions = {
                    if (uiState.items.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.requestEmptyTrash() },
                            enabled = !uiState.isOperating,
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
                                    enabled = !uiState.isOperating,
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
                                isOperating = uiState.isOperating,
                                onRestore = { viewModel.restoreItem(item.itemId) },
                                onDeletePermanently = { viewModel.requestPermanentDelete(item) },
                            )
                        }
                    }
                }
            }

            val errorText = when {
                uiState.errorMessageRes != null -> stringResource(uiState.errorMessageRes!!)
                uiState.errorMessage != null -> uiState.errorMessage!!
                else -> null
            }

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

@Composable
private fun TrashItemCard(
    item: TrashItem,
    isOperating: Boolean,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPending = item.isPendingConfirmation

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(alpha = if (isPending) 0.65f else 1.0f),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = stringResource(item.originListNameRes),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                if (item.attachments.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_attach_file),
                            contentDescription = stringResource(Res.string.trash_attachments_description),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = item.attachments.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (item.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onRestore,
                    enabled = !isOperating && !isPending,
                ) {
                    Text(stringResource(Res.string.action_restore))
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onDeletePermanently,
                    enabled = !isOperating && !isPending,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_delete),
                        contentDescription = stringResource(Res.string.trash_delete_permanently_description),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
