package pl.quicktask.todo.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.app_name
import todo.shared.generated.resources.ic_attach_file
import todo.shared.generated.resources.ic_menu
import todo.shared.generated.resources.menu
import todo.shared.generated.resources.screen_inbox
import todo.shared.generated.resources.task_add_attachment
import todo.shared.generated.resources.task_add_button
import todo.shared.generated.resources.task_attachments_label
import todo.shared.generated.resources.task_close_description
import todo.shared.generated.resources.task_edit_title
import todo.shared.generated.resources.task_new_title
import todo.shared.generated.resources.task_note_placeholder
import todo.shared.generated.resources.task_remove_file_description
import todo.shared.generated.resources.task_save_button
import todo.shared.generated.resources.task_saving
import todo.shared.generated.resources.task_title_placeholder
import todo.shared.generated.resources.task_uploading_files

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onOpenDrawer: () -> Unit = {},
    viewModel: InboxViewModel = viewModel { InboxViewModel() },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appName = stringResource(Res.string.app_name)
    val screenTitle = stringResource(Res.string.screen_inbox)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$appName - $screenTitle") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_menu),
                            contentDescription = stringResource(Res.string.menu),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddDialog() },
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                )
            }
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
                        text = "Brak zadań w Skrzynce odbiorczej",
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
                            InboxItemCard(
                                item = item,
                                onClick = { viewModel.openEditDialog(item) },
                                onDelete = { viewModel.deleteItem(item.itemId) },
                            )
                        }
                    }
                }
            }

            uiState.errorMessage?.let { errorMsg ->
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
                            Text("Zamknij")
                        }
                    }
                }
            }
        }
    }

    if (uiState.showAddDialog) {
        InboxItemDialog(
            itemToEdit = uiState.editingItem,
            isSubmitting = uiState.isSubmitting,
            existingAttachments = uiState.existingAttachments,
            selectedFiles = uiState.selectedFiles,
            uploadProgress = uiState.uploadProgress,
            onAddFile = { file -> viewModel.addSelectedFile(file) },
            onRemoveExistingAttachment = { attachmentId -> viewModel.removeExistingAttachment(attachmentId) },
            onRemoveFile = { index -> viewModel.removeSelectedFile(index) },
            onDismiss = { viewModel.dismissAddDialog() },
            onConfirm = { title, note -> viewModel.addItem(title, note) },
        )
    }
}

@Composable
private fun InboxItemCard(
    item: InboxItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (item.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_attach_file),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = item.attachments.joinToString { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxItemDialog(
    itemToEdit: InboxItem? = null,
    isSubmitting: Boolean,
    existingAttachments: List<DecryptedAttachment> = emptyList(),
    selectedFiles: List<InputFile>,
    uploadProgress: Float? = null,
    onAddFile: (InputFile) -> Unit,
    onRemoveExistingAttachment: (String) -> Unit,
    onRemoveFile: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (title: String, note: String) -> Unit,
) {
    var title by remember(itemToEdit) { mutableStateOf(itemToEdit?.title ?: "") }
    var note by remember(itemToEdit) { mutableStateOf(itemToEdit?.note ?: "") }

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
                    isSubmitting = isSubmitting,
                    canSave = title.isNotBlank(),
                    onDismiss = onDismiss,
                    onSave = { onConfirm(title, note) },
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TitleTextField(
                    value = title,
                    onValueChange = { title = it },
                    enabled = !isSubmitting,
                    focusRequester = focusRequester,
                )

                NoteTextField(
                    value = note,
                    onValueChange = { note = it },
                    enabled = !isSubmitting,
                )

                if (existingAttachments.isNotEmpty() || selectedFiles.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.task_attachments_label),
                        style = MaterialTheme.typography.titleSmall,
                    )

                    existingAttachments.forEach { attachment ->
                        ExistingAttachmentCard(
                            attachment = attachment,
                            enabled = !isSubmitting,
                            onRemove = { onRemoveExistingAttachment(attachment.attachmentId) },
                        )
                    }

                    selectedFiles.forEachIndexed { index, file ->
                        AttachmentCard(
                            file = file,
                            enabled = !isSubmitting,
                            onRemove = { onRemoveFile(index) },
                        )
                    }
                }

                if (isSubmitting) {
                    UploadProgressSection(uploadProgress = uploadProgress)
                }

                OutlinedButton(
                    onClick = launchFilePicker,
                    enabled = !isSubmitting && (existingAttachments.size + selectedFiles.size) < 10,
                    modifier = Modifier.fillMaxWidth(),
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskTopBar(
    isEditing: Boolean,
    isSubmitting: Boolean,
    canSave: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    TopAppBar(
        title = {
            val titleRes = if (isEditing) Res.string.task_edit_title else Res.string.task_new_title
            Text(stringResource(titleRes))
        },
        navigationIcon = {
            IconButton(onClick = onDismiss, enabled = !isSubmitting) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.task_close_description),
                )
            }
        },
        actions = {
            TextButton(onClick = onSave, enabled = canSave && !isSubmitting) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    val buttonRes = if (isEditing) Res.string.task_save_button else Res.string.task_add_button
                    Text(stringResource(buttonRes))
                }
            }
        },
    )
}

@Composable
private fun TitleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    focusRequester: FocusRequester,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(stringResource(Res.string.task_title_placeholder)) },
        singleLine = true,
        enabled = enabled,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
}

@Composable
private fun NoteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(stringResource(Res.string.task_note_placeholder)) },
        enabled = enabled,
        minLines = 6,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ExistingAttachmentCard(
    attachment: DecryptedAttachment,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.task_remove_file_description),
                )
            }
        }
    }
}



@Composable
private fun AttachmentCard(
    file: InputFile,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${file.fileName} (${formatFileSize(file.bytes.size.toLong())})",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.task_remove_file_description),
                )
            }
        }
    }
}

@Composable
private fun UploadProgressSection(uploadProgress: Float?) {
    val progressText = if (uploadProgress != null) {
        stringResource(Res.string.task_uploading_files, (uploadProgress * 100).toInt())
    } else {
        stringResource(Res.string.task_saving)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = progressText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        LinearProgressIndicator(
            progress = { uploadProgress?.coerceIn(0f, 1f) ?: 0f },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun formatFileSize(sizeInBytes: Long): String {
    return when {
        sizeInBytes < 1024 -> "$sizeInBytes B"
        sizeInBytes < 1024 * 1024 -> "${sizeInBytes / 1024} KB"
        else -> "${sizeInBytes / (1024 * 1024)} MB"
    }
}
