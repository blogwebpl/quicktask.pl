package pl.quicktask.app.inbox.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.di.AppModule
import pl.quicktask.app.inbox.presentation.dialogs.*
import pl.quicktask.app.items.model.InboxItem
import pl.quicktask.app.items.model.ProcessDestination
import pl.quicktask.app.nextactions.model.NextActionOptions
import pl.quicktask.app.references.presentation.ReferenceTagsDialog
import pl.quicktask.app.ui.components.AppAddButton
import pl.quicktask.app.ui.components.AppTopBar
import pl.quicktask.app.waiting.presentation.WaitingTaskDialog
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.action_add_project
import todo.shared.generated.resources.action_close
import todo.shared.generated.resources.dialog_convert_to_project_question
import todo.shared.generated.resources.dialog_convert_to_project_title
import todo.shared.generated.resources.inbox_empty
import todo.shared.generated.resources.screen_inbox
import todo.shared.generated.resources.timer_cancel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    module: AppModule,
    onOpenDrawer: () -> Unit = {},
    viewModel: InboxViewModel = viewModel { InboxViewModel(module.items.inbox, module.items.store, module.items.lifecycle, module.items.completed) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val screenTitle = stringResource(Res.string.screen_inbox)

    val coroutineScope = rememberCoroutineScope()
    var itemToProcessToNextAction by remember { mutableStateOf<InboxItem?>(null) }
    var itemToProcessToWaiting by remember { mutableStateOf<InboxItem?>(null) }
    var itemToProcessToScheduled by remember { mutableStateOf<InboxItem?>(null) }
    var itemToConvertToProject by remember { mutableStateOf<InboxItem?>(null) }
    var itemToConvertToReference by remember { mutableStateOf<InboxItem?>(null) }
    var nextActionOptions by remember { mutableStateOf(NextActionOptions()) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = screenTitle,
                onOpenDrawer = onOpenDrawer,
            )
        },
        floatingActionButton = {
            AppAddButton(onClick = { viewModel.openAddDialog() }, contentDescription = null)
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
                    Text(
                        text = stringResource(Res.string.inbox_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.items, key = { it.itemId }) { item ->
                            InboxListItem(
                                item = item,
                                onClick = { viewModel.openEditDialog(item) },
                                onDelete = { viewModel.requestDeleteItem(item) },
                                onProcess = { destination ->
                                    // Obsługa wybranego przeznaczenia GTD
                                    when (destination) {
                                        ProcessDestination.TWO_MINUTES -> {
                                            viewModel.startTwoMinuteTimer(item)
                                        }
                                        ProcessDestination.NEXT_ACTION -> {
                                            itemToProcessToNextAction = item
                                            coroutineScope.launch {
                                                module.items.nextActions.getNextActionOptions().onSuccess { options ->
                                                    nextActionOptions = options
                                                }
                                            }
                                        }
                                        ProcessDestination.SCHEDULED -> {
                                            itemToProcessToScheduled = item
                                            coroutineScope.launch {
                                                module.items.scheduled.getNextActionOptions().onSuccess { options ->
                                                    nextActionOptions = options
                                                }
                                            }
                                        }
                                        ProcessDestination.WAITING -> {
                                            itemToProcessToWaiting = item
                                            coroutineScope.launch {
                                                module.items.nextActions.getNextActionOptions().onSuccess { options ->
                                                    nextActionOptions = options
                                                }
                                            }
                                        }
                                        ProcessDestination.PROJECT -> {
                                            itemToConvertToProject = item
                                        }
                                        ProcessDestination.REFERENCE -> {
                                            viewModel.clearError()
                                            itemToConvertToReference = item
                                            coroutineScope.launch {
                                                module.items.nextActions.getNextActionOptions().onSuccess { options ->
                                                    nextActionOptions = options
                                                }
                                            }
                                        }
                                        else -> {
                                            // Pozostałe kategorie
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            val errorText = uiState.errorMessageRes?.let { stringResource(it) }

            errorText?.let { errorMsg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.BottomCenter),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = errorMsg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text(stringResource(Res.string.action_close))
                        }
                    }
                }
            }
        }
    }

    if (uiState.showAddDialog) {
        AddEditInboxItemDialog(
            itemToEdit = uiState.editingItem,
            existingAttachments = uiState.existingAttachments,
            selectedFiles = uiState.selectedFiles,
            onAddFile = { file -> viewModel.addSelectedFile(file) },
            onRemoveExistingAttachment = { attachmentId -> viewModel.removeExistingAttachment(attachmentId) },
            onRemoveFile = { index -> viewModel.removeSelectedFile(index) },
            onDismiss = { viewModel.dismissEditor() },
            onConfirm = { title, note -> viewModel.saveItem(title, note) },
        )
    }

    uiState.activeTwoMinuteItem?.let { activeItem ->
        TwoMinuteTimerDialog(
            item = activeItem,
            remainingSeconds = uiState.twoMinuteSecondsRemaining,
            onCancel = { viewModel.cancelTwoMinuteTimer() },
            onDone = { viewModel.onTwoMinuteDoneClicked() },
        )
    }

    if (uiState.timer is TimerState.AwaitingAttachmentDecision) {
        DeleteAttachmentsConfirmationDialog(
            onDeleteAttachments = { viewModel.confirmCompleteTwoMinuteTimer(deleteAttachments = true) },
            onKeepAttachments = { viewModel.confirmCompleteTwoMinuteTimer(deleteAttachments = false) },
            onDismiss = viewModel::dismissDeleteAttachmentPrompt,
        )
    }

    uiState.itemToDelete?.let { item ->
        DeleteInboxItemConfirmationDialog(
            itemTitle = item.title,
            onDismiss = viewModel::cancelDeleteItem,
            onConfirm = viewModel::confirmDeleteItem,
        )
    }

    itemToProcessToNextAction?.let { item ->
        ProcessToNextActionDialog(
            item = item,
            options = nextActionOptions,
            onDismiss = { itemToProcessToNextAction = null },
            onConfirm = { projectId, dueAt, contextIds, newContextNames, newContexts, tagIds, newTagNames ->
                viewModel.convertToNextAction(
                    itemId = item.itemId,
                    projectId = projectId,
                    dueAt = dueAt,
                    contextIds = contextIds,
                    newContextNames = newContextNames,
                    newContexts = newContexts,
                    tagIds = tagIds,
                    newTagNames = newTagNames,
                    nextActionsOperations = module.items.nextActions,
                )
                itemToProcessToNextAction = null
            },
            onCreateProject = { title ->
                module.items.nextActions.createProject(title).getOrNull()
            },
        )
    }

    itemToProcessToScheduled?.let { item ->
        ProcessToScheduledDialog(
            item = item,
            options = nextActionOptions,
            onDismiss = { itemToProcessToScheduled = null },
            onConfirm = { title, note, scheduledAt, deferUntil, dueAt, projectId, contextIds, newContextNames, tagIds, newTagNames, recurrence, newFiles, removedAttachmentIds ->
                viewModel.convertToScheduled(
                    item = item,
                    title = title,
                    note = note,
                    recurrence = recurrence,
                    scheduledAt = scheduledAt,
                    deferUntil = deferUntil,
                    dueAt = dueAt,
                    projectId = projectId,
                    contextIds = contextIds,
                    newContextNames = newContextNames,
                    tagIds = tagIds,
                    newTagNames = newTagNames,
                    newFiles = newFiles,
                    removedAttachmentIds = removedAttachmentIds,
                    scheduledOperations = module.items.scheduled,
                )
                itemToProcessToScheduled = null
            },
            onCreateProject = { title ->
                module.items.nextActions.createProject(title).getOrNull()
            }
        )
    }

    itemToProcessToWaiting?.let { item ->
        WaitingTaskDialog(
            title = item.title,
            note = item.note,
            options = nextActionOptions,
            editableContent = false,
            contactsRepository = module.items.contacts,
            inboxItem = item,
            onDelegated = { itemToProcessToWaiting = null },
            onDismiss = { itemToProcessToWaiting = null },
            onConfirm = { _, _, projectId, dueAt, waitingFor, followUpAt ->
                viewModel.convertToWaiting(
                    itemId = item.itemId,
                    projectId = projectId,
                    dueAt = dueAt,
                    waitingFor = waitingFor,
                    followUpAt = followUpAt,
                    projectsOperations = module.items.projects,
                )
                itemToProcessToWaiting = null
            },
            onCreateProject = { projectTitle ->
                module.items.nextActions.createProject(projectTitle).getOrNull()
            },
        )
    }

    itemToConvertToProject?.let { item ->
        pl.quicktask.app.ui.components.AppConfirmationDialog(
            title = stringResource(Res.string.dialog_convert_to_project_title),
            text = stringResource(Res.string.dialog_convert_to_project_question, item.title),
            type = pl.quicktask.app.ui.components.DialogType.QUESTION,
            buttons = listOf(
                pl.quicktask.app.ui.components.DialogButton(
                    text = stringResource(Res.string.timer_cancel),
                    style = pl.quicktask.app.ui.components.DialogButtonStyle.TEXT,
                    onClick = { itemToConvertToProject = null },
                ),
                pl.quicktask.app.ui.components.DialogButton(
                    text = stringResource(Res.string.action_add_project),
                    style = pl.quicktask.app.ui.components.DialogButtonStyle.PRIMARY,
                    onClick = {
                        viewModel.convertToProject(item.itemId, module.items.projects)
                        itemToConvertToProject = null
                    },
                ),
            ),
            onDismissRequest = { itemToConvertToProject = null },
        )
    }

    itemToConvertToReference?.let { item ->
        ReferenceTagsDialog(
            isConversion = true,
            availableTags = nextActionOptions.tags,
            initialTags = item.tags,
            isSaving = item.isPendingConfirmation,
            errorMessageRes = uiState.errorMessageRes,
            onDismiss = { itemToConvertToReference = null },
            onSave = { tagIds, newTagNames ->
                viewModel.convertToReference(item.itemId, tagIds, newTagNames, module.items.references)
                itemToConvertToReference = null
            },
        )
    }
}
