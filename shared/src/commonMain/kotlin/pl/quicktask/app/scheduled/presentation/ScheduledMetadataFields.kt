package pl.quicktask.app.scheduled.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.inbox.platform.rememberFilePicker
import pl.quicktask.app.inbox.presentation.AttachmentCard
import pl.quicktask.app.inbox.presentation.ExistingAttachmentCard
import pl.quicktask.app.inbox.presentation.dialogs.NoteTextField
import pl.quicktask.app.inbox.presentation.dialogs.TitleTextField
import pl.quicktask.app.items.model.DecryptedAttachment
import pl.quicktask.app.items.model.InboxTagDto
import pl.quicktask.app.items.model.InputFile
import pl.quicktask.app.items.model.MAX_ATTACHMENTS
import pl.quicktask.app.nextactions.model.DecryptedProject
import pl.quicktask.app.nextactions.model.NewContextInput
import pl.quicktask.app.nextactions.model.NextActionContextDto
import pl.quicktask.app.nextactions.model.NextActionOptions
import pl.quicktask.app.ui.components.ContextFieldSection
import pl.quicktask.app.ui.components.ContextSelectionBottomSheet
import pl.quicktask.app.ui.components.DueDatePickerField
import pl.quicktask.app.ui.components.LabeledDatePickerField
import pl.quicktask.app.ui.components.ProjectFieldSection
import pl.quicktask.app.ui.components.ProjectSelectionBottomSheet
import pl.quicktask.app.ui.components.TagFieldSection
import pl.quicktask.app.ui.components.TagSelectionBottomSheet
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.defer_until_label
import todo.shared.generated.resources.ic_attach_file
import todo.shared.generated.resources.scheduled_at_label
import todo.shared.generated.resources.task_add_attachment
import todo.shared.generated.resources.task_edit_title

@Composable
internal fun ScheduledContentFields(
    title: TextFieldValue,
    onTitleChange: (TextFieldValue) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    titleEditable: Boolean,
    onEditTitle: (() -> Unit)? = null,
    focusRequester: FocusRequester,
    existingAttachments: List<DecryptedAttachment>,
    selectedFiles: List<InputFile>,
    onAddFile: (InputFile) -> Unit,
    onRemoveExistingAttachment: (String) -> Unit,
    onRemoveFile: (Int) -> Unit,
    enabled: Boolean,
) {
    val launchFilePicker = rememberFilePicker { pickedFile ->
        if (enabled && existingAttachments.size + selectedFiles.size < MAX_ATTACHMENTS) {
            pickedFile?.let(onAddFile)
        }
    }

    ScheduledEditorSection {
        if (titleEditable) {
            TitleTextField(
                value = title,
                onValueChange = onTitleChange,
                enabled = enabled,
                focusRequester = focusRequester,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title.text,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { onEditTitle?.invoke() }, enabled = enabled && onEditTitle != null) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(Res.string.task_edit_title),
                    )
                }
            }
        }

        NoteTextField(
            value = note,
            onValueChange = onNoteChange,
            enabled = enabled,
        )
    }

    ScheduledEditorSection {
        existingAttachments.forEach { attachment ->
            ExistingAttachmentCard(
                attachment = attachment,
                enabled = enabled,
                onRemove = { onRemoveExistingAttachment(attachment.attachmentId) },
            )
        }
        selectedFiles.forEachIndexed { index, file ->
            AttachmentCard(
                file = file,
                enabled = enabled,
                onRemove = { onRemoveFile(index) },
            )
        }
        TextButton(
            onClick = launchFilePicker,
            enabled = enabled && existingAttachments.size + selectedFiles.size < MAX_ATTACHMENTS,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_attach_file),
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(Res.string.task_add_attachment))
        }
    }
}

@Composable
internal fun ScheduledMetadataFields(
    scheduledAt: String,
    onScheduledAtChange: (String) -> Unit,
    deferUntil: String,
    onDeferUntilChange: (String) -> Unit,
    dueAt: String,
    onDueAtChange: (String) -> Unit,
    recurrenceState: RecurrenceEditorState,
    onRecurrenceStateChange: (RecurrenceEditorState) -> Unit,
    options: NextActionOptions,
    selectedProjectId: String?,
    onSelectedProjectIdChange: (String?) -> Unit,
    selectedContextIds: MutableList<String>,
    newContexts: MutableList<NewContextInput>,
    selectedTagIds: MutableList<String>,
    newTagNames: MutableList<String>,
    enabled: Boolean,
    initialProject: DecryptedProject? = null,
    initialContexts: List<NextActionContextDto> = emptyList(),
    initialTags: List<InboxTagDto> = emptyList(),
    onCreateProject: (suspend (String) -> DecryptedProject?)? = null,
) {
    val extraProjects = remember { mutableStateListOf<DecryptedProject>() }
    val allAvailableProjects = remember(options.projects, extraProjects.toList(), initialProject) {
        val existingIds = options.projects.mapTo(mutableSetOf()) { it.projectId }
        buildList {
            addAll(options.projects)
            addAll(extraProjects.filter { it.projectId !in existingIds })
            if (initialProject != null && initialProject.projectId !in existingIds) {
                add(initialProject)
            }
        }
    }
    var showProjectPicker by remember { mutableStateOf(false) }
    var showContextPicker by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }

    ScheduledEditorSection {
        LabeledDatePickerField(
            value = scheduledAt,
            onValueChange = { if (enabled) onScheduledAtChange(it) },
            label = stringResource(Res.string.scheduled_at_label) + " *",
            allowClear = false,
        )
        LabeledDatePickerField(
            value = deferUntil,
            onValueChange = { if (enabled) onDeferUntilChange(it) },
            label = stringResource(Res.string.defer_until_label),
        )
        DueDatePickerField(
            value = dueAt,
            onValueChange = { if (enabled) onDueAtChange(it) },
        )
    }

    RecurrenceEditor(
        state = recurrenceState,
        onChange = onRecurrenceStateChange,
        scheduledAt = scheduledAt,
        enabled = enabled,
    )

    ScheduledEditorSection {
        ProjectFieldSection(
            availableProjects = allAvailableProjects,
            selectedProjectId = selectedProjectId,
            onOpenProjectPicker = { if (enabled) showProjectPicker = true },
        )
        ContextFieldSection(
            availableContexts = options.contexts,
            selectedContextIds = selectedContextIds,
            newContexts = newContexts,
            onOpenContextPicker = { if (enabled) showContextPicker = true },
            initialContexts = initialContexts,
        )
        TagFieldSection(
            availableTags = options.tags,
            selectedTagIds = selectedTagIds,
            newTagNames = newTagNames,
            onOpenTagPicker = { if (enabled) showTagPicker = true },
            initialTags = initialTags,
        )
    }

    if (showProjectPicker) {
        ProjectSelectionBottomSheet(
            availableProjects = allAvailableProjects,
            selectedProjectId = selectedProjectId,
            onDismiss = { showProjectPicker = false },
            onConfirm = onSelectedProjectIdChange,
            onCreateProject = { title ->
                val created = onCreateProject?.invoke(title)
                if (created != null && extraProjects.none { it.projectId == created.projectId }) {
                    extraProjects.add(created)
                }
                created
            },
        )
    }

    if (showContextPicker) {
        ContextSelectionBottomSheet(
            availableContexts = options.contexts,
            selectedContextIds = selectedContextIds,
            newContexts = newContexts,
            onDismiss = { showContextPicker = false },
            onConfirm = { updatedContextIds, updatedNewContexts ->
                selectedContextIds.replaceWith(updatedContextIds)
                newContexts.replaceWith(updatedNewContexts)
            },
        )
    }

    if (showTagPicker) {
        TagSelectionBottomSheet(
            availableTags = options.tags,
            selectedTagIds = selectedTagIds,
            newTagNames = newTagNames,
            onDismiss = { showTagPicker = false },
            onConfirm = { updatedTagIds, updatedNewTagNames ->
                selectedTagIds.replaceWith(updatedTagIds)
                newTagNames.replaceWith(updatedNewTagNames)
            },
        )
    }
}

private fun <T> MutableList<T>.replaceWith(values: Collection<T>) {
    clear()
    addAll(values)
}

@Composable
internal fun ScheduledEditorSection(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
    }
}
