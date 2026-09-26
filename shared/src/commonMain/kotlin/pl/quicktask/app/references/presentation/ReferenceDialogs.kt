package pl.quicktask.app.references.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.StringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.inbox.platform.rememberFilePicker
import pl.quicktask.app.inbox.presentation.AttachmentCard
import pl.quicktask.app.inbox.presentation.dialogs.NoteTextField
import pl.quicktask.app.inbox.presentation.dialogs.TitleTextField
import pl.quicktask.app.items.model.InboxTagDto
import pl.quicktask.app.items.model.InputFile
import pl.quicktask.app.items.model.MAX_ATTACHMENTS
import pl.quicktask.app.nextactions.model.NextActionTag
import pl.quicktask.app.ui.components.TagFieldSection
import pl.quicktask.app.ui.components.TagSelectionBottomSheet
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.action_close
import todo.shared.generated.resources.ic_attach_file
import todo.shared.generated.resources.reference_add
import todo.shared.generated.resources.reference_move_from_inbox
import todo.shared.generated.resources.reference_tags_edit
import todo.shared.generated.resources.reference_tag_limit
import todo.shared.generated.resources.task_add_attachment
import todo.shared.generated.resources.task_save_button
import todo.shared.generated.resources.timer_cancel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReferenceEditorDialog(
    availableTags: List<NextActionTag>,
    isSaving: Boolean,
    uploadProgress: Float?,
    errorMessageRes: StringResource?,
    onDismiss: () -> Unit,
    onSave: (String, String, List<String>, List<String>, List<InputFile>) -> Unit,
) {
    var title by remember { mutableStateOf(TextFieldValue()) }
    var note by remember { mutableStateOf("") }
    val selectedTagIds = remember { mutableStateListOf<String>() }
    val newTagNames = remember { mutableStateListOf<String>() }
    val files = remember { mutableStateListOf<InputFile>() }
    var showTagPicker by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val launchFilePicker = rememberFilePicker { file ->
        if (file != null && files.size < MAX_ATTACHMENTS) files.add(file)
    }
    val save = {
        if (title.text.isNotBlank() && !isSaving && selectedTagIds.size <= 50 && newTagNames.size <= 50 && newTagNames.all { it.length <= 100 }) {
            onSave(title.text.trim(), note, selectedTagIds.toList(), newTagNames.toList(), files.toList())
        }
    }

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.reference_add)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, enabled = !isSaving) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_close))
                        }
                    },
                    actions = {
                        Button(onClick = save, enabled = title.text.isNotBlank() && !isSaving &&
                            selectedTagIds.size <= 50 && newTagNames.size <= 50 && newTagNames.all { it.length <= 100 },
                            modifier = Modifier.padding(end = 8.dp)) {
                            Text(stringResource(Res.string.task_save_button))
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).imePadding()
                    .verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                TitleTextField(title, { title = it }, !isSaving, focusRequester, onDone = save)
                NoteTextField(note, { note = it }, !isSaving)
                TagFieldSection(availableTags, selectedTagIds, newTagNames,
                    onOpenTagPicker = { if (!isSaving) showTagPicker = true })
                if (selectedTagIds.size > 50 || newTagNames.size > 50 || newTagNames.any { it.length > 100 }) {
                    Text(stringResource(Res.string.reference_tag_limit), color = MaterialTheme.colorScheme.error)
                }
                files.forEachIndexed { index, file ->
                    AttachmentCard(file, enabled = !isSaving, onRemove = { files.removeAt(index) })
                }
                TextButton(onClick = launchFilePicker, enabled = !isSaving && files.size < MAX_ATTACHMENTS) {
                    Icon(painterResource(Res.drawable.ic_attach_file), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.task_add_attachment))
                }
                if (isSaving) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        uploadProgress?.let { Text("${(it * 100).toInt()}%") }
                    }
                }
                errorMessageRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (showTagPicker) {
        TagSelectionBottomSheet(availableTags, selectedTagIds, newTagNames,
            onDismiss = { showTagPicker = false },
            onConfirm = { ids, names ->
                selectedTagIds.clear(); selectedTagIds.addAll(ids)
                newTagNames.clear(); newTagNames.addAll(names)
            })
    }
}

@Composable
internal fun ReferenceTagsDialog(
    isConversion: Boolean,
    availableTags: List<NextActionTag>,
    initialTags: List<InboxTagDto>,
    isSaving: Boolean,
    errorMessageRes: StringResource?,
    onDismiss: () -> Unit,
    onSave: (List<String>, List<String>) -> Unit,
    itemTitle: String? = null,
) {
    val selectedTagIds = remember(initialTags) { mutableStateListOf<String>().apply { addAll(initialTags.map { it.tagId }) } }
    val newTagNames = remember { mutableStateListOf<String>() }
    var showTagPicker by remember { mutableStateOf(false) }
    val validTags = selectedTagIds.size <= 50 && newTagNames.size <= 50 && newTagNames.all { it.length <= 100 }
    val saveTags = { onSave(selectedTagIds.toList(), newTagNames.toList()) }
    if (isConversion) {
        Dialog(
            onDismissRequest = { if (!isSaving) onDismiss() },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(Res.string.reference_move_from_inbox)) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss, enabled = !isSaving) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_close))
                            }
                        },
                        actions = {
                            Button(onClick = saveTags, enabled = !isSaving && validTags,
                                modifier = Modifier.padding(end = 8.dp)) {
                                Text(stringResource(Res.string.task_save_button))
                            }
                        },
                    )
                },
            ) { paddingValues ->
                Column(
                    modifier = Modifier.fillMaxSize()
                        .padding(paddingValues)
                        .consumeWindowInsets(paddingValues)
                        .imePadding()
                        .padding(horizontal = 20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    itemTitle?.let {
                        Text(it, style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TagFieldSection(availableTags, selectedTagIds, newTagNames,
                        onOpenTagPicker = { if (!isSaving) showTagPicker = true }, initialTags = initialTags)
                    if (!validTags) Text(stringResource(Res.string.reference_tag_limit), color = MaterialTheme.colorScheme.error)
                    errorMessageRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = { if (!isSaving) onDismiss() },
            title = { Text(stringResource(Res.string.reference_tags_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TagFieldSection(availableTags, selectedTagIds, newTagNames,
                        onOpenTagPicker = { if (!isSaving) showTagPicker = true }, initialTags = initialTags)
                    if (!validTags) Text(stringResource(Res.string.reference_tag_limit), color = MaterialTheme.colorScheme.error)
                    errorMessageRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(onClick = saveTags, enabled = !isSaving && validTags) {
                    Text(stringResource(Res.string.task_save_button))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text(stringResource(Res.string.timer_cancel)) } },
        )
    }
    if (showTagPicker) {
        TagSelectionBottomSheet(availableTags, selectedTagIds, newTagNames,
            onDismiss = { showTagPicker = false },
            onConfirm = { ids, names ->
                selectedTagIds.clear(); selectedTagIds.addAll(ids)
                newTagNames.clear(); newTagNames.addAll(names)
            })
    }
}
