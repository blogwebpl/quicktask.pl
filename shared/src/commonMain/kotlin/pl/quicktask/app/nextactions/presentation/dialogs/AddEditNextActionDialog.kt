package pl.quicktask.app.nextactions.presentation.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.inbox.platform.rememberFilePicker
import pl.quicktask.app.inbox.presentation.AttachmentCard
import pl.quicktask.app.inbox.presentation.ExistingAttachmentCard
import pl.quicktask.app.inbox.presentation.dialogs.NoteTextField
import pl.quicktask.app.inbox.presentation.dialogs.TaskTopBar
import pl.quicktask.app.inbox.presentation.dialogs.TitleTextField
import pl.quicktask.app.items.model.DecryptedAttachment
import pl.quicktask.app.items.model.InputFile
import pl.quicktask.app.items.model.MAX_ATTACHMENTS
import pl.quicktask.app.nextactions.model.DecryptedProject
import pl.quicktask.app.nextactions.model.NewContextInput
import pl.quicktask.app.nextactions.model.NextAction
import pl.quicktask.app.nextactions.model.NextActionOptions
import pl.quicktask.app.ui.components.ContextFieldSection
import pl.quicktask.app.ui.components.ContextSelectionBottomSheet
import pl.quicktask.app.ui.components.DueDatePickerField
import pl.quicktask.app.ui.components.ProjectFieldSection
import pl.quicktask.app.ui.components.ProjectSelectionBottomSheet
import pl.quicktask.app.ui.components.TagFieldSection
import pl.quicktask.app.ui.components.TagSelectionBottomSheet
import pl.quicktask.app.ui.components.normalizeIsoDate
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.ic_attach_file
import todo.shared.generated.resources.task_add_attachment
import todo.shared.generated.resources.task_attachments_label

import todo.shared.generated.resources.no_project
import todo.shared.generated.resources.project_label

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AddEditNextActionDialog(
    itemToEdit: NextAction? = null,
    options: NextActionOptions,
    existingAttachments: List<DecryptedAttachment> = emptyList(),
    selectedFiles: List<InputFile>,
    onAddFile: (InputFile) -> Unit,
    onRemoveExistingAttachment: (String) -> Unit,
    onRemoveFile: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        note: String,
        projectId: String?,
        dueAt: String?,
        contextIds: List<String>,
        newContextNames: List<String>,
        newContexts: List<NewContextInput>,
        tagIds: List<String>,
        newTagNames: List<String>,
    ) -> Unit,
    onCreateProject: (suspend (String) -> DecryptedProject?)? = null,
) {
    var titleTextFieldValue by remember(itemToEdit) {
        val initialText = itemToEdit?.title ?: ""
        mutableStateOf(TextFieldValue(initialText, selection = TextRange(initialText.length)))
    }
    var note by remember(itemToEdit) { mutableStateOf(itemToEdit?.note ?: "") }
    var selectedProjectId by remember(itemToEdit) { mutableStateOf(itemToEdit?.project?.projectId) }
    var dueAt by remember(itemToEdit) { mutableStateOf(itemToEdit?.dueAt?.substringBefore('T') ?: "") }

    val extraProjects = remember { mutableStateListOf<DecryptedProject>() }
    val allAvailableProjects = remember(options.projects, extraProjects.toList(), itemToEdit) {
        val existingIds = options.projects.map { it.projectId }.toSet()
        val base = options.projects + extraProjects.filter { it.projectId !in existingIds }
        if (itemToEdit?.project != null && itemToEdit.project.projectId !in existingIds) {
            base + itemToEdit.project
        } else {
            base
        }
    }

    val selectedContextIds = remember(itemToEdit) {
        mutableStateListOf<String>().apply {
            itemToEdit?.contexts?.forEach { add(it.contextId) }
        }
    }
    val newContextNames = remember { mutableStateListOf<String>() }
    val newContexts = remember { mutableStateListOf<NewContextInput>() }
    var showContextPicker by remember { mutableStateOf(false) }

    val selectedTagIds = remember(itemToEdit) {
        mutableStateListOf<String>().apply {
            itemToEdit?.tags?.forEach { add(it.tagId) }
        }
    }
    val newTagNames = remember { mutableStateListOf<String>() }
    var showTagPicker by remember { mutableStateOf(false) }

    var showProjectPicker by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val launchFilePicker = rememberFilePicker { pickedFile ->
        pickedFile?.let { onAddFile(it) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TaskTopBar(
                    isEditing = itemToEdit != null,
                    canSave = titleTextFieldValue.text.isNotBlank(),
                    onDismiss = onDismiss,
                    onSave = {
                        onConfirm(
                            titleTextFieldValue.text,
                            note,
                            selectedProjectId,
                            normalizeIsoDate(dueAt),
                            selectedContextIds.toList(),
                            newContextNames.toList(),
                            newContexts.toList(),
                            selectedTagIds.toList(),
                            newTagNames.toList(),
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

                TitleTextField(
                    value = titleTextFieldValue,
                    onValueChange = { titleTextFieldValue = it },
                    enabled = true,
                    focusRequester = focusRequester,
                )

                NoteTextField(
                    value = note,
                    onValueChange = { note = it },
                    enabled = true,
                )

                // Project Selector
                ProjectFieldSection(
                    availableProjects = allAvailableProjects,
                    selectedProjectId = selectedProjectId,
                    onOpenProjectPicker = { showProjectPicker = true },
                )

                // Due Date Input
                DueDatePickerField(
                    value = dueAt,
                    onValueChange = { dueAt = it },
                )

                // Contexts Section
                ContextFieldSection(
                    availableContexts = options.contexts,
                    selectedContextIds = selectedContextIds,
                    newContexts = newContexts,
                    onOpenContextPicker = { showContextPicker = true },
                    initialContexts = itemToEdit?.contexts ?: emptyList(),
                )

                // Tags Section
                TagFieldSection(
                    availableTags = options.tags,
                    selectedTagIds = selectedTagIds,
                    newTagNames = newTagNames,
                    onOpenTagPicker = { showTagPicker = true },
                    initialTags = itemToEdit?.tags ?: emptyList(),
                )

                // Attachments (for new items)
                if (itemToEdit == null) {
                    if (existingAttachments.isNotEmpty() || selectedFiles.isNotEmpty()) {
                        Text(
                            text = stringResource(Res.string.task_attachments_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        existingAttachments.forEach { attachment ->
                            ExistingAttachmentCard(
                                attachment = attachment,
                                enabled = true,
                                onRemove = { onRemoveExistingAttachment(attachment.attachmentId) },
                            )
                        }

                        selectedFiles.forEachIndexed { index, file ->
                            AttachmentCard(
                                file = file,
                                enabled = true,
                                onRemove = { onRemoveFile(index) },
                            )
                        }
                    }

                    TextButton(
                        onClick = launchFilePicker,
                        enabled = (existingAttachments.size + selectedFiles.size) < MAX_ATTACHMENTS,
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

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (showProjectPicker) {
            ProjectSelectionBottomSheet(
                availableProjects = allAvailableProjects,
                selectedProjectId = selectedProjectId,
                onDismiss = { showProjectPicker = false },
                onConfirm = { updatedProjectId ->
                    selectedProjectId = updatedProjectId
                },
                onCreateProject = { title ->
                    val created = onCreateProject?.invoke(title)
                    if (created != null && !extraProjects.any { it.projectId == created.projectId }) {
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
                    selectedContextIds.clear()
                    selectedContextIds.addAll(updatedContextIds)
                    newContexts.clear()
                    newContexts.addAll(updatedNewContexts)
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
                    selectedTagIds.clear()
                    selectedTagIds.addAll(updatedTagIds)
                    newTagNames.clear()
                    newTagNames.addAll(updatedNewTagNames)
                },
            )
        }
    }
}
