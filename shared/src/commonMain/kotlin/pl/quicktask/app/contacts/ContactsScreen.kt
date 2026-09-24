package pl.quicktask.app.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.di.AppModule
import todo.shared.generated.resources.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactsSettingsContent(module: AppModule, modifier: Modifier = Modifier) {
    val repo = module.items.contacts
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var email by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var selectedStatus by remember { mutableStateOf("RECEIVED") }
    var message by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf(false) }
    val genericError = stringResource(Res.string.contacts_error)
    val notFound = stringResource(Res.string.contacts_not_found)
    val selfError = stringResource(Res.string.contacts_self)
    val saved = stringResource(Res.string.contacts_name_saved)
    val invited = stringResource(Res.string.contacts_invited)
    fun failure(error: Throwable) {
        errorMessage = true
        message = when ((error as? pl.quicktask.app.auth.model.ApiException)?.code) {
            "CONTACT_USER_NOT_FOUND" -> notFound
            "CONTACT_SELF" -> selfError
            else -> genericError
        }
    }
    suspend fun reload() {
        repo.list().onSuccess { contacts = it; loaded = true }.onFailure(::failure)
    }
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        scope.launch { try { action() } finally { busy = false } }
    }
    LaunchedEffect(repo, module.sync) {
        try { reload() } finally { busy = false }
        module.sync.contactChanges.collect {
            snapshotFlow { busy }.first { isBusy -> !isBusy }
            busy = true
            try { reload() } finally { busy = false }
        }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 760.dp).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ContactPanel(emphasized = true) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(Res.string.contacts_add_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(stringResource(Res.string.contacts_invite_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(email, { email = it }, label = { Text(stringResource(Res.string.contacts_email)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !busy,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Button(enabled = !busy && email.isNotBlank(), onClick = {
                        perform { repo.invite(email).onSuccess { contacts = it; email = ""; message = invited; errorMessage = false; selectedStatus = "SENT" }.onFailure(::failure) }
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.contacts_invite)) }
                }
            }
            message?.let { text -> item {
                Surface(shape = RoundedCornerShape(12.dp), color = if (errorMessage) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
                    Text(text, Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            } }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.contacts_people_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                val statusOptions = listOf(
                    "RECEIVED" to Res.string.contacts_tab_incoming,
                    "ACCEPTED" to Res.string.contacts_tab_accepted,
                    "SENT" to Res.string.contacts_tab_sent
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    statusOptions.forEachIndexed { index, (status, labelRes) ->
                        SegmentedButton(
                            selected = selectedStatus == status,
                            onClick = { selectedStatus = status },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = statusOptions.size)
                        ) {
                            Text(stringResource(labelRes))
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            val group = contacts.filter { it.status == selectedStatus }
            if (loaded && group.isEmpty()) item {
                ContactPanel {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.align(Alignment.CenterHorizontally).size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(when (selectedStatus) {
                            "RECEIVED" -> Res.string.contacts_empty_incoming
                            "SENT" -> Res.string.contacts_empty_sent
                            else -> Res.string.contacts_empty_accepted
                        }),
                        modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            items(group, key = { it.id }) { contact ->
                var editedName by remember(contact.id, contact.displayName) { mutableStateOf(contact.displayName.orEmpty()) }
                ContactPanel {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ContactAvatar(contact.displayName?.takeIf { it.isNotBlank() } ?: contact.email)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(contact.displayName?.takeIf { it.isNotBlank() } ?: contact.email, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (!contact.displayName.isNullOrBlank()) Text(contact.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (contact.status == "RECEIVED") FlowRow(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !busy, onClick = { perform { repo.respond(contact.id, false).onSuccess { contacts = it }.onFailure(::failure) } }) { Text(stringResource(Res.string.contacts_reject)) }
                        Button(enabled = !busy, onClick = { perform { repo.respond(contact.id, true).onSuccess { contacts = it }.onFailure(::failure) } }) { Text(stringResource(Res.string.contacts_accept)) }
                    }
                    if (contact.status == "ACCEPTED") {
                        Text(stringResource(Res.string.contacts_name_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = editedName,
                            onValueChange = { if (it.length <= 80) editedName = it },
                            label = { Text(stringResource(Res.string.contacts_name)) },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        )
                        OutlinedButton(
                            enabled = !busy && editedName.trim() != contact.displayName.orEmpty(),
                            onClick = {
                                perform {
                                    repo.saveContactName(contact, editedName).onSuccess { updated ->
                                        contacts = contacts.map { if (it.id == updated.id) updated else it }
                                        editedName = updated.displayName.orEmpty()
                                        message = saved
                                        errorMessage = false
                                    }.onFailure(::failure)
                                }
                            },
                            modifier = Modifier.align(Alignment.End),
                        ) { Text(stringResource(Res.string.task_save_button)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactPanel(emphasized: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(
        containerColor = if (emphasized) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
    )) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun ContactAvatar(label: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) {
            if (label.isBlank()) Icon(Icons.Default.Person, contentDescription = null)
            else Text(label.trim().take(1).uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
