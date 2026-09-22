package pl.quicktask.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.nextactions.model.DecryptedProject
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.action_clear
import todo.shared.generated.resources.action_close
import todo.shared.generated.resources.ic_list
import todo.shared.generated.resources.new_project_format
import todo.shared.generated.resources.no_project
import todo.shared.generated.resources.project_label
import todo.shared.generated.resources.projects_header
import todo.shared.generated.resources.projects_title
import todo.shared.generated.resources.search_or_create_project
import todo.shared.generated.resources.task_save_button

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectSelectionBottomSheet(
    availableProjects: List<DecryptedProject>,
    selectedProjectId: String?,
    onDismiss: () -> Unit,
    onConfirm: (selectedProjectId: String?) -> Unit,
    onCreateProject: (suspend (title: String) -> DecryptedProject?)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    var searchQuery by remember { mutableStateOf("") }
    var currentSelectedProjectId by remember { mutableStateOf(selectedProjectId) }
    val createdProjects = remember { mutableStateListOf<DecryptedProject>() }
    var isCreatingProject by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val allProjects = remember(availableProjects, createdProjects.toList()) {
        val existingIds = availableProjects.map { it.projectId }.toSet()
        availableProjects + createdProjects.filter { it.projectId !in existingIds }
    }

    val trimmedQuery = searchQuery.trim()

    val filteredProjects = remember(allProjects, searchQuery) {
        if (searchQuery.isBlank()) allProjects
        else allProjects.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    val showCreateOption = trimmedQuery.isNotBlank() &&
        !allProjects.any { it.title.equals(trimmedQuery, ignoreCase = true) }

    val isNewProjectSelected = showCreateOption &&
        (currentSelectedProjectId == null || filteredProjects.none { it.projectId == currentSelectedProjectId })

    fun createAndSelectProject() {
        if (trimmedQuery.isNotBlank() && showCreateOption && !isCreatingProject) {
            isCreatingProject = true
            errorMessage = null
            coroutineScope.launch {
                try {
                    val created = onCreateProject?.invoke(trimmedQuery)
                    if (created != null) {
                        if (!createdProjects.any { it.projectId == created.projectId }) {
                            createdProjects.add(created)
                        }
                        currentSelectedProjectId = created.projectId
                        searchQuery = ""
                        onConfirm(created.projectId)
                        onDismiss()
                    } else {
                        errorMessage = "Nie udało się utworzyć projektu. Spróbuj ponownie."
                    }
                } catch (e: Exception) {
                    println("Error creating project: $e")
                    errorMessage = e.message ?: "Wystąpił błąd podczas tworzenia projektu."
                } finally {
                    isCreatingProject = false
                }
            }
        }
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
                .navigationBarsPadding(),
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.action_close),
                    )
                }

                Text(
                    text = stringResource(Res.string.projects_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )

                Button(
                    onClick = {
                        if (isNewProjectSelected) {
                            createAndSelectProject()
                        } else {
                            onConfirm(currentSelectedProjectId)
                            onDismiss()
                        }
                    },
                    enabled = !isCreatingProject,
                    shape = CircleShape,
                ) {
                    if (isCreatingProject) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(Res.string.task_save_button), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    errorMessage = null
                },
                placeholder = { Text(stringResource(Res.string.search_or_create_project)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = {
                            searchQuery = ""
                            errorMessage = null
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.action_clear),
                            )
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (isNewProjectSelected) {
                            createAndSelectProject()
                        } else {
                            onConfirm(currentSelectedProjectId)
                            onDismiss()
                        }
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focusRequester),
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section Header
            Text(
                text = stringResource(Res.string.projects_header),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Projects List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Option "No project"
                item {
                    ProjectRowItem(
                        title = stringResource(Res.string.no_project),
                        isSelected = !isNewProjectSelected && currentSelectedProjectId == null,
                        onClick = {
                            currentSelectedProjectId = null
                            onConfirm(null)
                            onDismiss()
                        },
                    )
                }

                items(filteredProjects, key = { it.projectId }) { proj ->
                    val isSelected = !isNewProjectSelected && proj.projectId == currentSelectedProjectId
                    ProjectRowItem(
                        title = proj.title,
                        isSelected = isSelected,
                        onClick = {
                            currentSelectedProjectId = proj.projectId
                            onConfirm(proj.projectId)
                            onDismiss()
                        },
                    )
                }

                if (showCreateOption) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isCreatingProject) {
                                    createAndSelectProject()
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isNewProjectSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (isNewProjectSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (isCreatingProject) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = if (isNewProjectSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = if (isNewProjectSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Text(
                                    text = stringResource(Res.string.new_project_format, trimmedQuery),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isNewProjectSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ProjectRowItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_list),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
fun ProjectFieldSection(
    availableProjects: List<DecryptedProject>,
    selectedProjectId: String?,
    onOpenProjectPicker: () -> Unit,
) {
    val selectedProject = remember(availableProjects, selectedProjectId) {
        availableProjects.find { it.projectId == selectedProjectId }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.project_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenProjectPicker),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_list),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = selectedProject?.title ?: stringResource(Res.string.no_project),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedProject != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
