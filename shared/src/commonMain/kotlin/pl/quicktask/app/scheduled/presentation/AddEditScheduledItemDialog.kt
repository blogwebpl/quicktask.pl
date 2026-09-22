package pl.quicktask.app.scheduled.presentation

import pl.quicktask.app.scheduled.model.RecurrenceRule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import pl.quicktask.app.inbox.presentation.dialogs.TaskTopBar
import pl.quicktask.app.items.model.DecryptedAttachment
import pl.quicktask.app.items.model.InputFile
import pl.quicktask.app.nextactions.model.DecryptedProject
import pl.quicktask.app.nextactions.model.NewContextInput
import pl.quicktask.app.nextactions.model.NextActionOptions
import pl.quicktask.app.scheduled.model.ScheduledTask
import pl.quicktask.app.ui.components.normalizeIsoDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AddEditScheduledItemDialog(
    itemToEdit: ScheduledTask? = null,
    options: NextActionOptions,
    isSubmitting: Boolean = false,
    errorMessage: String? = null,
    existingAttachments: List<DecryptedAttachment> = emptyList(),
    selectedFiles: List<InputFile>,
    onAddFile: (InputFile) -> Unit,
    onRemoveExistingAttachment: (String) -> Unit,
    onRemoveFile: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        note: String,
        scheduledAt: String,
        deferUntil: String?,
        projectId: String?,
        dueAt: String?,
        contextIds: List<String>,
        newContextNames: List<String>,
        tagIds: List<String>,
        newTagNames: List<String>,
        recurrence: RecurrenceRule?,
    ) -> Unit,
    onCreateProject: (suspend (String) -> DecryptedProject?)? = null,
) {
    var titleTextFieldValue by remember(itemToEdit) {
        val initialText = itemToEdit?.title ?: ""
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(initialText.length)))
    }
    var note by remember(itemToEdit) { mutableStateOf(itemToEdit?.note ?: "") }
    var recurrenceState by remember(itemToEdit) { mutableStateOf(RecurrenceEditorState.from(itemToEdit?.recurrence)) }
    var scheduledAt by remember(itemToEdit) { mutableStateOf(itemToEdit?.scheduledAt?.substringBefore('T') ?: "") }
    var deferUntil by remember(itemToEdit) { mutableStateOf(itemToEdit?.deferUntil?.substringBefore('T') ?: "") }
    var selectedProjectId by remember(itemToEdit) { mutableStateOf(itemToEdit?.project?.projectId) }
    var dueAt by remember(itemToEdit) { mutableStateOf(itemToEdit?.dueAt?.substringBefore('T') ?: "") }

    val selectedContextIds = remember(itemToEdit) {
        mutableStateListOf<String>().apply {
            itemToEdit?.contexts?.forEach { add(it.contextId) }
        }
    }
    val newContexts = remember { mutableStateListOf<NewContextInput>() }

    val selectedTagIds = remember(itemToEdit) {
        mutableStateListOf<String>().apply {
            itemToEdit?.tags?.forEach { add(it.tagId) }
        }
    }
    val newTagNames = remember { mutableStateListOf<String>() }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TaskTopBar(
                    isEditing = itemToEdit != null,
                    canSave = !isSubmitting && titleTextFieldValue.text.isNotBlank() && scheduledAt.isNotBlank() && recurrenceState.isValid(scheduledAt),
                    onDismiss = { if (!isSubmitting) onDismiss() },
                    onSave = {
                        onConfirm(
                            titleTextFieldValue.text,
                            note,
                            normalizeIsoDate(scheduledAt) ?: "",
                            normalizeIsoDate(deferUntil),
                            selectedProjectId,
                            normalizeIsoDate(dueAt),
                            selectedContextIds.toList(),
                            newContexts.map { it.name },
                            selectedTagIds.toList(),
                            newTagNames.toList(),
                            recurrenceState.toRule(),
                        )
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

                if (isSubmitting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                ScheduledContentFields(
                    title = titleTextFieldValue,
                    onTitleChange = { titleTextFieldValue = it },
                    note = note,
                    onNoteChange = { note = it },
                    titleEditable = true,
                    focusRequester = focusRequester,
                    existingAttachments = existingAttachments,
                    selectedFiles = selectedFiles,
                    onAddFile = onAddFile,
                    onRemoveExistingAttachment = onRemoveExistingAttachment,
                    onRemoveFile = onRemoveFile,
                    enabled = !isSubmitting,
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
                    enabled = !isSubmitting,
                    initialProject = itemToEdit?.project,
                    initialContexts = itemToEdit?.contexts ?: emptyList(),
                    initialTags = itemToEdit?.tags ?: emptyList(),
                    onCreateProject = onCreateProject,
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

    }
}
