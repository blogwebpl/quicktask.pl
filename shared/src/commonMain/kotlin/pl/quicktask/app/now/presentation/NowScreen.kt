package pl.quicktask.app.now.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.di.AppModule
import pl.quicktask.app.nextactions.presentation.dialogs.AddEditNextActionDialog
import pl.quicktask.app.ui.components.AppTopBar
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.ic_sun
import todo.shared.generated.resources.now_available_next_actions
import todo.shared.generated.resources.now_empty
import todo.shared.generated.resources.now_overdue
import todo.shared.generated.resources.now_scheduled_today
import todo.shared.generated.resources.now_waiting_for_review
import todo.shared.generated.resources.screen_now

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowScreen(
    module: AppModule,
    onOpenDrawer: () -> Unit = {},
    viewModel: NowViewModel = viewModel {
        NowViewModel(module.items.now, module.items.nextActions)
    },
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val pullToRefreshState = rememberPullToRefreshState()

    val errorMessage = uiState.errorMessageRes?.let { stringResource(it) }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.screen_now),
                onOpenDrawer = onOpenDrawer,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.loadNowData() },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (uiState.isLoading && uiState.nowData.isEmpty) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.nowData.isEmpty) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_sun),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(Res.string.now_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (uiState.nowData.overdue.isNotEmpty()) {
                        item(key = "header_overdue") {
                            Text(
                                text = stringResource(Res.string.now_overdue),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                        items(
                            items = uiState.nowData.overdue,
                            key = { "overdue_${it.itemId}" },
                        ) { item ->
                            NowListItem(
                                item = item,
                                onClick = { viewModel.openEditDialog(item) },
                                onEdit = { viewModel.openEditDialog(item) },
                                onRestoreToInbox = { viewModel.restoreToInbox(item.itemId) },
                                onDelete = { viewModel.deleteItem(item.itemId) },
                            )
                        }
                    }

                    if (uiState.nowData.scheduledToday.isNotEmpty()) {
                        item(key = "header_scheduled_today") {
                            Text(
                                text = stringResource(Res.string.now_scheduled_today),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                        items(
                            items = uiState.nowData.scheduledToday,
                            key = { "scheduled_${it.itemId}" },
                        ) { item ->
                            NowListItem(
                                item = item,
                                onClick = { viewModel.openEditDialog(item) },
                                onEdit = { viewModel.openEditDialog(item) },
                                onRestoreToInbox = { viewModel.restoreToInbox(item.itemId) },
                                onDelete = { viewModel.deleteItem(item.itemId) },
                            )
                        }
                    }

                    if (uiState.nowData.availableNextActions.isNotEmpty()) {
                        item(key = "header_available_next_actions") {
                            Text(
                                text = stringResource(Res.string.now_available_next_actions),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                        items(
                            items = uiState.nowData.availableNextActions,
                            key = { "next_${it.itemId}" },
                        ) { item ->
                            NowListItem(
                                item = item,
                                onClick = { viewModel.openEditDialog(item) },
                                onEdit = { viewModel.openEditDialog(item) },
                                onRestoreToInbox = { viewModel.restoreToInbox(item.itemId) },
                                onDelete = { viewModel.deleteItem(item.itemId) },
                            )
                        }
                    }

                    if (uiState.nowData.waitingForReview.isNotEmpty()) {
                        item(key = "header_waiting_for_review") {
                            Text(
                                text = stringResource(Res.string.now_waiting_for_review),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                        items(
                            items = uiState.nowData.waitingForReview,
                            key = { "waiting_${it.itemId}" },
                        ) { item ->
                            NowListItem(
                                item = item,
                                onClick = { viewModel.openEditDialog(item) },
                                onEdit = { viewModel.openEditDialog(item) },
                                onRestoreToInbox = { viewModel.restoreToInbox(item.itemId) },
                                onDelete = { viewModel.deleteItem(item.itemId) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.showAddEditDialog && uiState.selectedItemToEdit != null) {
        val selectedItem = uiState.selectedItemToEdit!!
        AddEditNextActionDialog(
            itemToEdit = selectedItem.toNextAction(),
            options = uiState.options,
            existingAttachments = selectedItem.attachments,
            selectedFiles = emptyList(),
            onAddFile = {},
            onRemoveExistingAttachment = {},
            onRemoveFile = {},
            onDismiss = { viewModel.closeAddEditDialog() },
            onConfirm = { title, note, projectId, dueAt, contextIds, newContextNames, newContexts, tagIds, newTagNames ->
                viewModel.updateNextAction(
                    item = selectedItem,
                    title = title,
                    note = note,
                    projectId = projectId,
                    dueAt = dueAt,
                    contextIds = contextIds,
                    newContextNames = newContextNames,
                    newContexts = newContexts,
                    tagIds = tagIds,
                    newTagNames = newTagNames,
                )
            },
            onCreateProject = { title ->
                module.items.nextActions.createProject(title).getOrNull()
            },
        )
    }
}
