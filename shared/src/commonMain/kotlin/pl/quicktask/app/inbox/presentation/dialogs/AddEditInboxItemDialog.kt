package pl.quicktask.app.inbox.presentation.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.inbox.platform.rememberFilePicker
import pl.quicktask.app.inbox.presentation.AttachmentCard
import pl.quicktask.app.inbox.presentation.ExistingAttachmentCard
import pl.quicktask.app.items.model.DecryptedAttachment
import pl.quicktask.app.items.model.InboxItem
import pl.quicktask.app.items.model.InputFile
import pl.quicktask.app.items.model.MAX_ATTACHMENTS
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.ic_attach_file
import todo.shared.generated.resources.ic_list
import todo.shared.generated.resources.task_add_attachment
import todo.shared.generated.resources.task_add_button
import todo.shared.generated.resources.task_attachments_label
import todo.shared.generated.resources.task_close_description
import todo.shared.generated.resources.task_edit_title
import todo.shared.generated.resources.task_new_title
import todo.shared.generated.resources.task_note_label
import todo.shared.generated.resources.task_optional_label
import todo.shared.generated.resources.task_save_button
import todo.shared.generated.resources.task_title_placeholder
import todo.shared.generated.resources.task_title_prompt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddEditInboxItemDialog(
    itemToEdit: InboxItem? = null,
    existingAttachments: List<DecryptedAttachment> = emptyList(),
    selectedFiles: List<InputFile>,
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
                    onDone = {
                        if (titleTextFieldValue.text.isNotBlank()) {
                            onConfirm(titleTextFieldValue.text, note)
                        }
                    },
                )

                NoteTextField(
                    value = note,
                    onValueChange = { note = it },
                    enabled = true,
                )

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
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
