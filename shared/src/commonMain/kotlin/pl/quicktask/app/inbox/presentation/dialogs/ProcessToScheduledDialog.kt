package pl.quicktask.app.inbox.presentation.dialogs

import pl.quicktask.app.scheduled.presentation.RecurrenceEditorState
import pl.quicktask.app.scheduled.presentation.ScheduledContentFields
import pl.quicktask.app.scheduled.presentation.ScheduledMetadataFields

import pl.quicktask.app.scheduled.model.RecurrenceRule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.items.model.InboxItem
import pl.quicktask.app.items.model.InputFile
import pl.quicktask.app.nextactions.model.DecryptedProject
import pl.quicktask.app.nextactions.model.NewContextInput
import pl.quicktask.app.nextactions.model.NextActionOptions
import pl.quicktask.app.ui.components.normalizeIsoDate
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.screen_scheduled
import todo.shared.generated.resources.task_close_description
import todo.shared.generated.resources.task_save_button

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProcessToScheduledDialog(
    item: InboxItem,
    options: NextActionOptions,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        note: String,
        scheduledAt: String,
        deferUntil: String?,
        dueAt: String?,
        projectId: String?,
        contextIds: List<String>,
        newContextNames: List<String>,
        tagIds: List<String>,
        newTagNames: List<String>,
        recurrence: RecurrenceRule?,
        newFiles: List<InputFile>,
        removedAttachmentIds: List<String>,
    ) -> Unit,
    onCreateProject: (suspend (String) -> DecryptedProject?)? = null,
) {
    var title by remember(item.itemId) {
        mutableStateOf(TextFieldValue(item.title, selection = TextRange(item.title.length)))
    }
    var note by remember(item.itemId) { mutableStateOf(item.note) }
    var titleEditable by remember(item.itemId) { mutableStateOf(false) }
    var recurrenceState by remember(item.itemId) { mutableStateOf(RecurrenceEditorState.from(null)) }
    var scheduledAt by remember { mutableStateOf("") }
    var deferUntil by remember { mutableStateOf("") }
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var dueAt by remember { mutableStateOf("") }

    val selectedContextIds = remember { mutableStateListOf<String>() }
    val newContexts = remember { mutableStateListOf<NewContextInput>() }

    val selectedTagIds = remember {
        mutableStateListOf<String>().apply {
            item.tags.forEach { add(it.tagId) }
        }
    }
    val newTagNames = remember { mutableStateListOf<String>() }
    val selectedFiles = remember(item.itemId) { mutableStateListOf<InputFile>() }
    val removedAttachmentIds = remember(item.itemId) { mutableStateListOf<String>() }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(titleEditable) {
        if (titleEditable) focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.screen_scheduled), style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.task_close_description),
                            )
                        }
                    },
                    actions = {
                        Button(
                            onClick = {
                                onConfirm(
                                    title.text,
                                    note,
                                    normalizeIsoDate(scheduledAt) ?: "",
                                    normalizeIsoDate(deferUntil),
                                    normalizeIsoDate(dueAt),
                                    selectedProjectId,
                                    selectedContextIds.toList(),
                                    newContexts.map { it.name },
                                    selectedTagIds.toList(),
                                    newTagNames.toList(),
                                    recurrenceState.toRule(),
                                    selectedFiles.toList(),
                                    removedAttachmentIds.toList(),
                                )
                            },
                            modifier = Modifier.padding(end = 8.dp),
                            enabled = title.text.isNotBlank() && scheduledAt.isNotBlank() && recurrenceState.isValid(scheduledAt)
                        ) {
                            Text(stringResource(Res.string.task_save_button), style = MaterialTheme.typography.labelLarge)
                        }
                    },
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .consumeWindowInsets(paddingValues)
                    .imePadding()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                ScheduledContentFields(
                    title = title,
                    onTitleChange = { title = it },
                    note = note,
                    onNoteChange = { note = it },
                    titleEditable = titleEditable,
                    onEditTitle = { titleEditable = true },
                    focusRequester = focusRequester,
                    existingAttachments = item.attachments.filterNot {
                        it.attachmentId in removedAttachmentIds
                    },
                    selectedFiles = selectedFiles,
                    onAddFile = { selectedFiles.add(it) },
                    onRemoveExistingAttachment = { removedAttachmentIds.add(it) },
                    onRemoveFile = { selectedFiles.removeAt(it) },
                    enabled = true,
                )

                ScheduledMetadataFields(
                    scheduledAt = scheduledAt,
                    onScheduledAtChange = { scheduledAt = it },
                    deferUntil = deferUntil,
                    onDeferUntilChange = { deferUntil = it },
                    dueAt = dueAt,
                    onDueAtChange = { dueAt = it },
                    recurrenceState = recurrenceState,
                    onRecurrenceStateChange = { recurrenceState = it },
                    options = options,
                    selectedProjectId = selectedProjectId,
                    onSelectedProjectIdChange = { selectedProjectId = it },
                    selectedContextIds = selectedContextIds,
                    newContexts = newContexts,
                    selectedTagIds = selectedTagIds,
                    newTagNames = newTagNames,
                    enabled = true,
                    initialTags = item.tags,
                    onCreateProject = onCreateProject,
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

    }
}
