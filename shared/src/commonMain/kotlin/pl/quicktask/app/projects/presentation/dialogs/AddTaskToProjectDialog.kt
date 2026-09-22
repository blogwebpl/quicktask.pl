package pl.quicktask.app.projects.presentation.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.nextactions.model.NewContextInput
import pl.quicktask.app.nextactions.model.NextActionOptions
import pl.quicktask.app.projects.model.ProjectWithTasks
import pl.quicktask.app.scheduled.model.RecurrenceRule
import pl.quicktask.app.ui.components.ContextSelectionBottomSheet
import pl.quicktask.app.ui.components.DueDatePickerField
import pl.quicktask.app.ui.components.TagSelectionBottomSheet
import pl.quicktask.app.ui.components.normalizeIsoDate
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.action_add
import todo.shared.generated.resources.action_close
import todo.shared.generated.resources.gtd_state_next
import todo.shared.generated.resources.gtd_state_scheduled
import todo.shared.generated.resources.gtd_state_waiting
import todo.shared.generated.resources.project_task_type_label
import todo.shared.generated.resources.task_note_placeholder
import todo.shared.generated.resources.task_save_button
import todo.shared.generated.resources.waiting_for_label

enum class ProjectTaskType { NEXT_ACTION, WAITING, SCHEDULED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskToProjectDialog(
    project: ProjectWithTasks?,
    options: NextActionOptions,
    onDismiss: () -> Unit,
    onConfirmNextAction: (
        title: String, note: String, projectId: String?, dueAt: String?,
        contextIds: List<String>, newContextNames: List<String>, newContexts: List<NewContextInput>,
        tagIds: List<String>, newTagNames: List<String>,
    ) -> Unit,
    onConfirmWaiting: (
        title: String, note: String, projectId: String?, dueAt: String?,
        waitingFor: String, followUpAt: String?,
    ) -> Unit,
    onConfirmScheduled: (
        title: String, note: String, scheduledAt: String, deferUntil: String?,
        dueAt: String?, projectId: String?, contextIds: List<String>, newContextNames: List<String>,
        tagIds: List<String>, newTagNames: List<String>, recurrence: RecurrenceRule?,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    var taskType by remember { mutableStateOf(ProjectTaskType.NEXT_ACTION) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var dueAt by remember { mutableStateOf("") }
    var scheduledAt by remember { mutableStateOf("") }
    var deferUntil by remember { mutableStateOf<String?>(null) }
    var waitingFor by remember { mutableStateOf("") }
    var followUpAt by remember { mutableStateOf("") }

    val selectedContextIds = remember { mutableStateListOf<String>() }
    val selectedNewContexts = remember { mutableStateListOf<NewContextInput>() }
    val selectedTagIds = remember { mutableStateListOf<String>() }
    val selectedNewTagNames = remember { mutableStateListOf<String>() }

    var showContextPicker by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val isFormValid = when (taskType) {
        ProjectTaskType.NEXT_ACTION -> title.isNotBlank()
        ProjectTaskType.WAITING -> title.isNotBlank() && waitingFor.isNotBlank()
        ProjectTaskType.SCHEDULED -> title.isNotBlank() && scheduledAt.isNotBlank()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.action_close),
                    )
                }

                Text(
                    text = project?.title ?: stringResource(Res.string.action_add),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )

                Button(
                    onClick = {
                        if (isFormValid) {
                            val pId = project?.projectId
                            val normalizedDue = normalizeIsoDate(dueAt)
                            when (taskType) {
                                ProjectTaskType.NEXT_ACTION -> onConfirmNextAction(
                                    title.trim(), note.trim(), pId, normalizedDue,
                                    selectedContextIds.toList(), emptyList(),
                                    selectedNewContexts.toList(), selectedTagIds.toList(),
                                    selectedNewTagNames.toList(),
                                )
                                ProjectTaskType.WAITING -> onConfirmWaiting(
                                    title.trim(), note.trim(), pId, normalizedDue,
                                    waitingFor.trim(), normalizeIsoDate(followUpAt),
                                )
                                ProjectTaskType.SCHEDULED -> onConfirmScheduled(
                                    title.trim(), note.trim(), normalizeIsoDate(scheduledAt) ?: scheduledAt, deferUntil,
                                    normalizedDue, pId, selectedContextIds.toList(),
                                    emptyList(), selectedTagIds.toList(),
                                    selectedNewTagNames.toList(), null,
                                )
                            }
                        }
                    },
                    enabled = isFormValid,
                    shape = CircleShape,
                ) {
                    Text(
                        text = stringResource(Res.string.task_save_button),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Task Type Selector
            Text(
                text = stringResource(Res.string.project_task_type_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = taskType == ProjectTaskType.NEXT_ACTION,
                    onClick = { taskType = ProjectTaskType.NEXT_ACTION },
                    label = { Text(stringResource(Res.string.gtd_state_next)) },
                )
                FilterChip(
                    selected = taskType == ProjectTaskType.WAITING,
                    onClick = { taskType = ProjectTaskType.WAITING },
                    label = { Text(stringResource(Res.string.gtd_state_waiting)) },
                )
                FilterChip(
                    selected = taskType == ProjectTaskType.SCHEDULED,
                    onClick = { taskType = ProjectTaskType.SCHEDULED },
                    label = { Text(stringResource(Res.string.gtd_state_scheduled)) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Tytuł zadania") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Note
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text(stringResource(Res.string.task_note_placeholder)) },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            if (taskType == ProjectTaskType.WAITING) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = waitingFor,
                    onValueChange = { waitingFor = it },
                    label = { Text(stringResource(Res.string.waiting_for_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                DueDatePickerField(
                    value = followUpAt,
                    onValueChange = { followUpAt = it },
                )
            }

            if (taskType == ProjectTaskType.SCHEDULED) {
                Spacer(modifier = Modifier.height(12.dp))

                DueDatePickerField(
                    value = scheduledAt,
                    onValueChange = { scheduledAt = it },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Due At Date
            DueDatePickerField(
                value = dueAt,
                onValueChange = { dueAt = it },
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showContextPicker) {
        ContextSelectionBottomSheet(
            availableContexts = options.contexts,
            selectedContextIds = selectedContextIds,
            newContexts = selectedNewContexts,
            onDismiss = { showContextPicker = false },
            onConfirm = { ids, newContexts ->
                selectedContextIds.clear()
                selectedContextIds.addAll(ids)
                selectedNewContexts.clear()
                selectedNewContexts.addAll(newContexts)
                showContextPicker = false
            },
        )
    }

    if (showTagPicker) {
        TagSelectionBottomSheet(
            availableTags = options.tags,
            selectedTagIds = selectedTagIds,
            newTagNames = selectedNewTagNames,
            onDismiss = { showTagPicker = false },
            onConfirm = { ids, newNames ->
                selectedTagIds.clear()
                selectedTagIds.addAll(ids)
                selectedNewTagNames.clear()
                selectedNewTagNames.addAll(newNames)
                showTagPicker = false
            },
        )
    }
}
