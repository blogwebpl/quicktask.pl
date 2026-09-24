package pl.quicktask.app.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.projects.model.DecryptedProjectTask
import todo.shared.generated.resources.*

@Composable
fun AssignedTasks(repo: ContactsRepository) {
    var tasks by remember { mutableStateOf<List<DecryptedProjectTask>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() { repo.tasks().onSuccess { tasks = it; failed = false }.onFailure { failed = true } }
    LaunchedEffect(repo) { while (true) { refresh(); delay(30_000) } }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.contacts_received_tasks), style = MaterialTheme.typography.titleMedium)
        TextButton(enabled = !busy, onClick = { scope.launch { refresh() } }) { Text(stringResource(Res.string.contacts_refresh)) }
        if (failed) Text(stringResource(Res.string.contacts_error), color = MaterialTheme.colorScheme.error)
        tasks.forEach { task ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium)
                    if (task.note.isNotBlank()) Text(task.note)
                    Text(stringResource(Res.string.contacts_from, task.waitingFor.orEmpty()))
                    task.dueAt?.let { Text(stringResource(Res.string.waiting_due_format, it.substringBefore('T'))) }
                    Button(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            try { repo.complete(task.itemId).onSuccess { tasks = tasks.filterNot { it.itemId == task.itemId } }.onFailure { failed = true } }
                            finally { busy = false }
                        }
                    }) { Text(stringResource(Res.string.contacts_complete)) }
                }
            }
        }
    }
}
