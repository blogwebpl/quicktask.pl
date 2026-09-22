package pl.quicktask.app.nextactions.presentation

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.di.AppModule
import pl.quicktask.app.items.model.InputFile
import pl.quicktask.app.nextactions.presentation.dialogs.AddEditNextActionDialog
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.ic_list
import todo.shared.generated.resources.ic_play_arrow
import pl.quicktask.app.ui.components.AppTopBar
import todo.shared.generated.resources.screen_next_actions

import todo.shared.generated.resources.filter_all
import todo.shared.generated.resources.next_actions_empty
import todo.shared.generated.resources.no_actions_in_context

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextActionsScreen(
    module: AppModule,
    onOpenDrawer: () -> Unit = {},
    viewModel: NextActionsViewModel = viewModel {
        NextActionsViewModel(module.items.nextActions, module.items.store)
    },
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val pullToRefreshState = rememberPullToRefreshState()

    val selectedFiles = remember { mutableStateListOf<InputFile>() }

    val errorMessage = uiState.errorMessageRes?.let { stringResource(it) }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.screen_next_actions),
                onOpenDrawer = onOpenDrawer,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedFiles.clear()
                    viewModel.openAddDialog()
                },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.loadNextActions(forceFetch = true) },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (uiState.isLoading && uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_play_arrow),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(Res.string.next_actions_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    val availableProjects = remember(uiState.items, uiState.options) {
                        val fromOptions = uiState.options.projects
                        val fromItems = uiState.items.mapNotNull { it.project }
                        val existingIds = fromOptions.map { it.projectId }.toSet()
                        fromOptions + fromItems.filter { it.projectId !in existingIds }
                    }

                    val availableContexts = remember(uiState.items, uiState.options) {
                        val fromOptions = uiState.options.contexts.map { it.name }
                        val fromItems = uiState.items.flatMap { item -> item.contexts.map { it.name } }
                        (fromOptions + fromItems).distinct()
                    }

                    if (availableProjects.isNotEmpty() || availableContexts.isNotEmpty()) {
                        Column(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (availableProjects.isNotEmpty()) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    item {
                                        FilterChip(
                                            selected = uiState.selectedProjectIdFilter == null,
                                            onClick = { viewModel.setProjectFilter(null) },
                                            label = { Text(stringResource(Res.string.filter_all)) },
                                        )
                                    }
                                    items(availableProjects, key = { it.projectId }) { proj ->
                                        val isSelected = uiState.selectedProjectIdFilter == proj.projectId
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.setProjectFilter(if (isSelected) null else proj.projectId)
                                            },
                                            label = { Text(proj.title) },
                                            leadingIcon = {
                                                Icon(
                                                    painter = painterResource(Res.drawable.ic_list),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            },
                                        )
                                    }
                                }
                            }

                            if (availableContexts.isNotEmpty()) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    item {
                                        FilterChip(
                                            selected = uiState.selectedContextFilter == null,
                                            onClick = { viewModel.setContextFilter(null) },
                                            label = { Text(stringResource(Res.string.filter_all)) },
                                        )
                                    }
                                    items(availableContexts) { ctxName ->
                                        val isSelected = uiState.selectedContextFilter == ctxName
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.setContextFilter(if (isSelected) null else ctxName)
                                            },
                                            label = { Text(ctxName) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val displayItems = uiState.filteredItems

                    if (displayItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (uiState.selectedContextFilter != null || uiState.selectedProjectIdFilter != null) {
                                    stringResource(Res.string.no_actions_in_context)
                                } else {
                                    stringResource(Res.string.next_actions_empty)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(
                                items = displayItems,
                                key = { it.itemId },
                            ) { item ->
                                NextActionListItem(
                                    item = item,
                                    onClick = { viewModel.openEditDialog(item) },
                                    onEdit = { viewModel.openEditDialog(item) },
                                    onRestoreToInbox = { viewModel.restoreToInbox(item.itemId) },
                                    onDelete = { viewModel.deleteNextAction(item.itemId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showAddEditDialog) {
        AddEditNextActionDialog(
            itemToEdit = uiState.selectedItemToEdit,
            options = uiState.options,
            existingAttachments = uiState.selectedItemToEdit?.attachments ?: emptyList(),
            selectedFiles = selectedFiles,
            onAddFile = { selectedFiles.add(it) },
            onRemoveExistingAttachment = {},
            onRemoveFile = { selectedFiles.removeAt(it) },
            onDismiss = { viewModel.closeAddEditDialog() },
            onConfirm = { title, note, projectId, dueAt, contextIds, newContextNames, newContexts, tagIds, newTagNames ->
                val currentEdit = uiState.selectedItemToEdit
                if (currentEdit != null) {
                    viewModel.updateNextAction(
                        item = currentEdit,
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
                } else {
                    viewModel.createNextAction(
                        title = title,
                        note = note,
                        projectId = projectId,
                        dueAt = dueAt,
                        contextIds = contextIds,
                        newContextNames = newContextNames,
                        newContexts = newContexts,
                        tagIds = tagIds,
                        newTagNames = newTagNames,
                        files = selectedFiles.toList(),
                    )
                }
            },
            onCreateProject = { title ->
                module.items.nextActions.createProject(title).getOrNull()
            },
        )
    }
}
