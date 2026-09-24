package pl.quicktask.app.waiting.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.di.AppModule
import pl.quicktask.app.ui.components.AppAddButton
import pl.quicktask.app.ui.components.AppTopBar
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.screen_waiting
import todo.shared.generated.resources.waiting_due_format
import todo.shared.generated.resources.waiting_empty
import todo.shared.generated.resources.waiting_follow_up_format
import todo.shared.generated.resources.waiting_for_format
import todo.shared.generated.resources.waiting_project_format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaitingScreen(
    module: AppModule,
    onOpenDrawer: () -> Unit = {},
    viewModel: WaitingViewModel = viewModel {
        WaitingViewModel(module.items.projects, module.items.nextActions, module.items.store)
    },
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val refreshState = rememberPullToRefreshState()
    val error = uiState.errorMessageRes?.let { stringResource(it) }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = { AppTopBar(stringResource(Res.string.screen_waiting), onOpenDrawer) },
        floatingActionButton = { AppAddButton(onClick = viewModel::openAddDialog, contentDescription = null) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isLoading,
            onRefresh = { viewModel.loadWaiting(forceFetch = true) },
            state = refreshState,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { pl.quicktask.app.contacts.AssignedTasks(module.items.contacts) }
                if (uiState.isLoading && uiState.items.isEmpty()) item { CircularProgressIndicator() }
                if (!uiState.isLoading && uiState.items.isEmpty()) item { Text(
                    stringResource(Res.string.waiting_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ) }
                items(uiState.items, key = { it.task.taskId }) { entry ->
                    WaitingListItem(entry)
                }
            }
        }
    }

    if (uiState.showAddDialog) {
        WaitingTaskDialog(
            options = uiState.options,
            contactsRepository = module.items.contacts,
            onDelegated = { viewModel.closeAddDialog(); viewModel.loadWaiting(forceFetch = true) },
            isSubmitting = uiState.isSubmitting,
            onDismiss = viewModel::closeAddDialog,
            onConfirm = viewModel::createWaiting,
            onCreateProject = { module.items.nextActions.createProject(it).getOrNull() },
        )
    }
}

@Composable
private fun WaitingListItem(entry: WaitingListEntry) {
    val task = entry.task
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (task.note.isNotBlank()) {
                Text(task.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            task.waitingFor?.let {
                Text(stringResource(Res.string.waiting_for_format, it), color = MaterialTheme.colorScheme.tertiary)
            }
            entry.projectTitle?.let {
                Text(stringResource(Res.string.waiting_project_format, it), style = MaterialTheme.typography.bodySmall)
            }
            task.followUpAt?.let {
                Text(stringResource(Res.string.waiting_follow_up_format, it.substringBefore('T')), style = MaterialTheme.typography.bodySmall)
            }
            task.dueAt?.let {
                Text(stringResource(Res.string.waiting_due_format, it.substringBefore('T')), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
