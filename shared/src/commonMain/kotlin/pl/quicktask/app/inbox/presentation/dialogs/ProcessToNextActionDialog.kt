package pl.quicktask.app.inbox.presentation.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.items.model.InboxItem
import pl.quicktask.app.nextactions.model.DecryptedProject
import pl.quicktask.app.nextactions.model.NewContextInput
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
import todo.shared.generated.resources.no_project
import todo.shared.generated.resources.project_label
import todo.shared.generated.resources.screen_next_actions
import todo.shared.generated.resources.task_close_description
import todo.shared.generated.resources.task_save_button

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ProcessToNextActionDialog(
    item: InboxItem,
    options: NextActionOptions,
    onDismiss: () -> Unit,
    onConfirm: (
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
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var dueAt by remember { mutableStateOf("") }

    val extraProjects = remember { mutableStateListOf<DecryptedProject>() }
    val allAvailableProjects = remember(options.projects, extraProjects.toList()) {
        val existingIds = options.projects.map { it.projectId }.toSet()
        options.projects + extraProjects.filter { it.projectId !in existingIds }
    }

    val selectedContextIds = remember { mutableStateListOf<String>() }
    val newContextNames = remember { mutableStateListOf<String>() }
    val newContexts = remember { mutableStateListOf<NewContextInput>() }
    var showContextPicker by remember { mutableStateOf(false) }

    val selectedTagIds = remember {
        mutableStateListOf<String>().apply {
            item.tags.forEach { add(it.tagId) }
        }
    }
    val newTagNames = remember { mutableStateListOf<String>() }
    var showTagPicker by remember { mutableStateOf(false) }

    var showProjectPicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.screen_next_actions), style = MaterialTheme.typography.titleLarge) },
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
                                    selectedProjectId,
                                    normalizeIsoDate(dueAt),
                                    selectedContextIds.toList(),
                                    newContextNames.toList(),
                                    newContexts.toList(),
                                    selectedTagIds.toList(),
                                    newTagNames.toList(),
                                )
                            },
                            modifier = Modifier.padding(end = 8.dp),
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

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Project Selector
                ProjectFieldSection(
                    availableProjects = allAvailableProjects,
                    selectedProjectId = selectedProjectId,
                    onOpenProjectPicker = { showProjectPicker = true },
                )

                // Due Date
                DueDatePickerField(
                    value = dueAt,
                    onValueChange = { dueAt = it },
                )

                // Contexts
                ContextFieldSection(
                    availableContexts = options.contexts,
                    selectedContextIds = selectedContextIds,
                    newContexts = newContexts,
                    onOpenContextPicker = { showContextPicker = true },
                )

                // Tags
                TagFieldSection(
                    availableTags = options.tags,
                    selectedTagIds = selectedTagIds,
                    newTagNames = newTagNames,
                    onOpenTagPicker = { showTagPicker = true },
                    initialTags = item.tags,
                )

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
