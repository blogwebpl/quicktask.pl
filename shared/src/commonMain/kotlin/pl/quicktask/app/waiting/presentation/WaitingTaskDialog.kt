package pl.quicktask.app.waiting.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.nextactions.model.DecryptedProject
import pl.quicktask.app.nextactions.model.NextActionOptions
import pl.quicktask.app.ui.components.DueDatePickerField
import pl.quicktask.app.ui.components.LabeledDatePickerField
import pl.quicktask.app.ui.components.ProjectFieldSection
import pl.quicktask.app.ui.components.ProjectSelectionBottomSheet
import pl.quicktask.app.ui.components.normalizeIsoDate
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.follow_up_at_label
import todo.shared.generated.resources.screen_waiting
import todo.shared.generated.resources.task_close_description
import todo.shared.generated.resources.task_note_placeholder
import todo.shared.generated.resources.task_save_button
import todo.shared.generated.resources.waiting_for_label
import todo.shared.generated.resources.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
import pl.quicktask.app.contacts.Contact
import pl.quicktask.app.contacts.ContactsRepository
import pl.quicktask.app.items.model.InboxItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaitingTaskDialog(
    title: String = "",
    note: String = "",
    options: NextActionOptions,
    editableContent: Boolean = true,
    isSubmitting: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        note: String,
        projectId: String?,
        dueAt: String?,
        waitingFor: String,
        followUpAt: String?,
    ) -> Unit,
    onCreateProject: (suspend (String) -> DecryptedProject?)? = null,
    contactsRepository: ContactsRepository? = null,
    inboxItem: InboxItem? = null,
    onDelegated: () -> Unit = onDismiss,
) {
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    var delegating by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(contactsRepository) {
        contactsRepository?.list()?.onSuccess { contacts = it.filter { c -> c.status == "ACCEPTED" } }?.onFailure { failed = true }
    }
    var currentTitle by remember(title) { mutableStateOf(title) }
    var currentNote by remember(note) { mutableStateOf(note) }
    var waitingFor by remember { mutableStateOf("") }
    var dueAt by remember { mutableStateOf("") }
    var followUpAt by remember { mutableStateOf("") }
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    var showProjectPicker by remember { mutableStateOf(false) }
    val extraProjects = remember { mutableStateListOf<DecryptedProject>() }
    val availableProjects = remember(options.projects, extraProjects.toList()) {
        val ids = options.projects.map { it.projectId }.toSet()
        options.projects + extraProjects.filter { it.projectId !in ids }
    }
    val canSave = currentTitle.isNotBlank() && (waitingFor.isNotBlank() || selectedContact != null) && !isSubmitting && !delegating

    Dialog(
        onDismissRequest = { if (!delegating && !isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(Res.string.screen_waiting)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss, enabled = !isSubmitting && !delegating) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.task_close_description))
                        }
                    },
                    actions = {
                        Button(
                            enabled = canSave,
                            onClick = {
                                val contact = selectedContact
                                if (contact != null && contactsRepository != null) {
                                    scope.launch {
                                        delegating = true
                                        failed = false
                                        try {
                                            contactsRepository.delegate(contact, currentTitle.trim(), currentNote.trim(), selectedProjectId,
                                                normalizeIsoDate(dueAt), normalizeIsoDate(followUpAt), inboxItem)
                                                .onSuccess { onDelegated() }.onFailure { failed = true }
                                        } finally { delegating = false }
                                    }
                                } else onConfirm(
                                    currentTitle.trim(),
                                    currentNote.trim(),
                                    selectedProjectId,
                                    normalizeIsoDate(dueAt),
                                    waitingFor.trim(),
                                    normalizeIsoDate(followUpAt),
                                )
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(stringResource(Res.string.task_save_button))
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.padding(top = 1.dp))
                if (editableContent) {
                    OutlinedTextField(
                        value = currentTitle,
                        onValueChange = { currentTitle = it },
                        label = { Text(stringResource(Res.string.screen_waiting)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = currentNote,
                        onValueChange = { currentNote = it },
                        placeholder = { Text(stringResource(Res.string.task_note_placeholder)) },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = currentTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (currentNote.isNotBlank()) Text(currentNote, style = MaterialTheme.typography.bodyMedium)
                }

                if (contactsRepository != null) {
                    Text(stringResource(Res.string.contacts_delegate), style = MaterialTheme.typography.titleMedium)
                    if (contacts.isEmpty()) Text(stringResource(Res.string.contacts_no_accepted))
                    TextButton(enabled = !delegating, onClick = { selectedContact = null }) {
                        Text((if (selectedContact == null) "✓ " else "") + stringResource(Res.string.contacts_no_delegate))
                    }
                    contacts.forEach { contact ->
                        TextButton(enabled = !delegating, onClick = { selectedContact = contact }) {
                            Text((if (selectedContact?.id == contact.id) "✓ " else "") + contact.label)
                        }
                    }
                    if (selectedContact != null) Text(stringResource(Res.string.contacts_share_notice))
                    if (failed) Text(stringResource(Res.string.contacts_error), color = MaterialTheme.colorScheme.error)
                }
                if (selectedContact == null) OutlinedTextField(
                    value = waitingFor,
                    onValueChange = { waitingFor = it },
                    label = { Text(stringResource(Res.string.waiting_for_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                ProjectFieldSection(
                    availableProjects = availableProjects,
                    selectedProjectId = selectedProjectId,
                    onOpenProjectPicker = { showProjectPicker = true },
                )

                LabeledDatePickerField(
                    value = followUpAt,
                    onValueChange = { followUpAt = it },
                    label = stringResource(Res.string.follow_up_at_label),
                )

                DueDatePickerField(value = dueAt, onValueChange = { dueAt = it })
                Spacer(Modifier.padding(bottom = 8.dp))
            }
        }

        if (showProjectPicker) {
            ProjectSelectionBottomSheet(
                availableProjects = availableProjects,
                selectedProjectId = selectedProjectId,
                onDismiss = { showProjectPicker = false },
                onConfirm = {
                    selectedProjectId = it
                    showProjectPicker = false
                },
                onCreateProject = { projectTitle ->
                    onCreateProject?.invoke(projectTitle)?.also { created -> extraProjects.add(created) }
                },
            )
        }
    }
}
