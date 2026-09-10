package pl.quicktask.todo.inbox

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.todo.ui.components.AppConfirmationDialog
import pl.quicktask.todo.ui.components.DialogButton
import pl.quicktask.todo.ui.components.DialogButtonStyle
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.app_name
import todo.shared.generated.resources.dialog_delete_attachments_confirm
import todo.shared.generated.resources.dialog_delete_attachments_question
import todo.shared.generated.resources.dialog_delete_attachments_title
import todo.shared.generated.resources.dialog_delete_confirm
import todo.shared.generated.resources.dialog_delete_item_question
import todo.shared.generated.resources.dialog_delete_item_title
import todo.shared.generated.resources.dialog_keep_attachments
import todo.shared.generated.resources.ic_attach_file
import todo.shared.generated.resources.ic_book
import todo.shared.generated.resources.ic_check_circle
import todo.shared.generated.resources.ic_date_range
import todo.shared.generated.resources.timer_cancel
import todo.shared.generated.resources.timer_done
import todo.shared.generated.resources.timer_title
import todo.shared.generated.resources.ic_delete
import todo.shared.generated.resources.ic_hourglass_empty
import todo.shared.generated.resources.ic_list
import todo.shared.generated.resources.ic_menu
import todo.shared.generated.resources.ic_play_arrow
import todo.shared.generated.resources.ic_process
import todo.shared.generated.resources.ic_star
import todo.shared.generated.resources.menu
import todo.shared.generated.resources.process_do_2min
import todo.shared.generated.resources.process_item
import todo.shared.generated.resources.screen_inbox
import todo.shared.generated.resources.screen_next_actions
import todo.shared.generated.resources.screen_projects
import todo.shared.generated.resources.screen_reference
import todo.shared.generated.resources.screen_scheduled
import todo.shared.generated.resources.screen_someday
import todo.shared.generated.resources.screen_trash
import todo.shared.generated.resources.screen_waiting
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
                                onDelete = { viewModel.requestDeleteItem(item) },
                                onProcess = { destination ->
                                    // Obsługa wybranego przeznaczenia GTD
                                    when (destination) {
                                        ProcessDestination.TWO_MINUTES -> {
                                            viewModel.startTwoMinuteTimer(item)
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

    if (uiState.activeTwoMinuteItem != null) {
        TwoMinuteTimerDialog(
            item = uiState.activeTwoMinuteItem!!,
            remainingSeconds = uiState.twoMinuteSecondsRemaining,
            isSubmitting = uiState.isCompletingTwoMinuteItem,
            onCancel = { viewModel.cancelTwoMinuteTimer() },
            onDone = { viewModel.onTwoMinuteDoneClicked() },
        )
    }

    // Okno dialogowe pytające o usunięcie załącznika przy kliknięciu "Zrobione"
    if (uiState.showDeleteAttachmentPrompt && uiState.activeTwoMinuteItem != null) {
        AppConfirmationDialog(
            title = stringResource(Res.string.dialog_delete_attachments_title),
            text = stringResource(Res.string.dialog_delete_attachments_question),
            iconPainter = painterResource(Res.drawable.ic_attach_file),
            buttons = listOf(
                DialogButton(
                    text = stringResource(Res.string.dialog_delete_attachments_confirm),
                    onClick = { viewModel.confirmCompleteTwoMinuteTimer(deleteAttachments = true) },
                    style = DialogButtonStyle.DESTRUCTIVE,
                ),
                DialogButton(
                    text = stringResource(Res.string.dialog_keep_attachments),
                    onClick = { viewModel.confirmCompleteTwoMinuteTimer(deleteAttachments = false) },
                    style = DialogButtonStyle.SECONDARY,
                ),
                DialogButton(
                    text = stringResource(Res.string.timer_cancel),
                    onClick = { viewModel.dismissDeleteAttachmentPrompt() },
                    style = DialogButtonStyle.TEXT,
                ),
            ),
            onDismissRequest = { viewModel.dismissDeleteAttachmentPrompt() },
        )
    }

    // Okno dialogowe potwierdzające usunięcie zadania
    if (uiState.itemToDelete != null) {
        AppConfirmationDialog(
            title = stringResource(Res.string.dialog_delete_item_title),
            text = stringResource(Res.string.dialog_delete_item_question, uiState.itemToDelete!!.title),
            iconPainter = painterResource(Res.drawable.ic_delete),
            buttons = listOf(
                DialogButton(
                    text = stringResource(Res.string.dialog_delete_confirm),
                    onClick = { viewModel.confirmDeleteItem() },
                    style = DialogButtonStyle.DESTRUCTIVE,
                ),
                DialogButton(
                    text = stringResource(Res.string.timer_cancel),
                    onClick = { viewModel.cancelDeleteItem() },
                    style = DialogButtonStyle.TEXT,
                ),
            ),
            onDismissRequest = { viewModel.cancelDeleteItem() },
        )
    }
}

@Composable
private fun InboxItemCard(
    item: InboxItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onProcess: (ProcessDestination) -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }

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

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_process),
                        contentDescription = stringResource(Res.string.process_item),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    // 1. Zrób w 2 minuty (Do it in 2 minutes)
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.process_do_2min)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_check_circle),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onProcess(ProcessDestination.TWO_MINUTES)
                        },
                    )

                    HorizontalDivider()

                    // 2. Następne działanie (Next Action)
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.screen_next_actions)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_play_arrow),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onProcess(ProcessDestination.NEXT_ACTION)
                        },
                    )

                    // 3. Projekt (Project)
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.screen_projects)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_list),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onProcess(ProcessDestination.PROJECT)
                        },
                    )

                    // 4. Oczekujące (Waiting)
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.screen_waiting)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_hourglass_empty),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onProcess(ProcessDestination.WAITING)
                        },
                    )

                    // 5. Zaplanowane (Scheduled)
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.screen_scheduled)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_date_range),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onProcess(ProcessDestination.SCHEDULED)
                        },
                    )

                    HorizontalDivider()

                    // 6. Kiedyś / Może (Someday/Maybe)
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.screen_someday)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_star),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onProcess(ProcessDestination.SOMEDAY)
                        },
                    )

                    // 7. Materiały / Referencje (Reference)
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.screen_reference)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_book),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onProcess(ProcessDestination.REFERENCE)
                        },
                    )

                    HorizontalDivider()

                    // 8. Usuń (Delete)
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.screen_trash)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(Res.drawable.ic_delete),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    )
                }
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
    var titleTextFieldValue by remember(itemToEdit) {
        val initialText = itemToEdit?.title ?: ""
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(initialText.length),
            ),
        )
    }
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
                    canSave = titleTextFieldValue.text.isNotBlank(),
                    onDismiss = onDismiss,
                    onSave = { onConfirm(titleTextFieldValue.text, note) },
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
                    value = titleTextFieldValue,
                    onValueChange = { titleTextFieldValue = it },
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
            Button(
                onClick = onSave,
                enabled = canSave && !isSubmitting,
                modifier = Modifier.padding(end = 8.dp),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    val buttonRes = if (isEditing) Res.string.task_save_button else Res.string.task_add_button
                    Text(
                        text = stringResource(buttonRes),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        },
    )
}

@Composable
private fun TitleTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    enabled: Boolean,
    focusRequester: FocusRequester,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = stringResource(Res.string.task_title_placeholder),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        textStyle = MaterialTheme.typography.titleLarge,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoMinuteTimerDialog(
    item: InboxItem,
    remainingSeconds: Int,
    isSubmitting: Boolean,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    val progress = (remainingSeconds.toFloat() / 120f).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500),
    )

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeFormatted = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"

    Dialog(
        onDismissRequest = {
            if (!isSubmitting) {
                onCancel()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.timer_title)) },
                    navigationIcon = {
                        IconButton(onClick = onCancel, enabled = !isSubmitting) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.timer_cancel),
                            )
                        }
                    },
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (item.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = item.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Box(
                    modifier = Modifier.size(260.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 14.dp,
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = timeFormatted,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "pozostało",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.timer_cancel),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    Button(
                        onClick = onDone,
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(
                                text = stringResource(Res.string.timer_done),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
