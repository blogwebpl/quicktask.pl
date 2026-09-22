package pl.quicktask.app.projects.presentation

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import pl.quicktask.app.projects.presentation.dialogs.AddEditProjectDialog
import pl.quicktask.app.projects.presentation.dialogs.AddTaskToProjectDialog
import pl.quicktask.app.ui.components.AppTopBar
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.ic_list
import todo.shared.generated.resources.projects_empty
import todo.shared.generated.resources.screen_projects

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    module: AppModule,
    onOpenDrawer: () -> Unit = {},
    viewModel: ProjectsViewModel = viewModel {
        ProjectsViewModel(
            projectsRepository = module.items.projects,
            nextActionsRepository = module.items.nextActions,
            scheduledRepository = module.items.scheduled,
            store = module.items.store,
        )
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
                title = stringResource(Res.string.screen_projects),
                onOpenDrawer = onOpenDrawer,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddProjectDialog() },
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
            onRefresh = { viewModel.loadProjects(forceFetch = true) },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            if (uiState.isLoading && uiState.projects.isEmpty() && uiState.unassignedTasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.projects.isEmpty() && uiState.unassignedTasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_list),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(Res.string.projects_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(
                        items = uiState.projects,
                        key = { it.projectId },
                    ) { project ->
                        ProjectListItem(
                            project = project,
                            onAddTask = { viewModel.openAddTaskDialog(project) },
                            onDeleteTask = { itemId, gtdState ->
                                if (gtdState.uppercase() == "SCHEDULED") {
                                    viewModel.deleteScheduledTask(itemId)
                                } else {
                                    viewModel.deleteNextAction(itemId)
                                }
                            },
                        )
                    }

                    if (uiState.unassignedTasks.isNotEmpty()) {
                        item(key = "unassigned_section") {
                            UnassignedTasksSection(
                                tasks = uiState.unassignedTasks,
                                onDeleteTask = { itemId, gtdState ->
                                    if (gtdState.uppercase() == "SCHEDULED") {
                                        viewModel.deleteScheduledTask(itemId)
                                    } else {
                                        viewModel.deleteNextAction(itemId)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.showAddProjectDialog) {
        AddEditProjectDialog(
            onDismiss = { viewModel.closeAddProjectDialog() },
            onConfirm = { title, note ->
                viewModel.createProject(title, note)
            },
        )
    }

    if (uiState.showAddTaskDialog) {
        AddTaskToProjectDialog(
            project = uiState.selectedProjectForNewTask,
            options = uiState.options,
            onDismiss = { viewModel.closeAddTaskDialog() },
            onConfirmNextAction = { title, note, projectId, dueAt, contextIds, newContextNames, newContexts, tagIds, newTagNames ->
                viewModel.createNextActionInProject(
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
            onConfirmWaiting = { title, note, projectId, dueAt, waitingFor, followUpAt ->
                viewModel.createWaitingInProject(
                    title = title,
                    note = note,
                    projectId = projectId,
                    dueAt = dueAt,
                    waitingFor = waitingFor,
                    followUpAt = followUpAt,
                )
            },
            onConfirmScheduled = { title, note, scheduledAt, deferUntil, dueAt, projectId, contextIds, newContextNames, tagIds, newTagNames, recurrence ->
                viewModel.createScheduledInProject(
                    recurrence = recurrence,
                    title = title,
                    note = note,
                    scheduledAt = scheduledAt,
                    deferUntil = deferUntil,
                    dueAt = dueAt,
                    projectId = projectId,
                    contextIds = contextIds,
                    newContextNames = newContextNames,
                    tagIds = tagIds,
                    newTagNames = newTagNames,
                )
            },
        )
    }
}
