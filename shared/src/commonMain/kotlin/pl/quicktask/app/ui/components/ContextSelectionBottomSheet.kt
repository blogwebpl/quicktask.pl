package pl.quicktask.app.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.nextactions.model.NewContextInput
import pl.quicktask.app.nextactions.model.NextActionContext
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.action_clear
import todo.shared.generated.resources.action_close
import todo.shared.generated.resources.contexts_header
import todo.shared.generated.resources.contexts_title
import todo.shared.generated.resources.hide_gps_fields
import todo.shared.generated.resources.latitude_label
import todo.shared.generated.resources.longitude_label
import todo.shared.generated.resources.new_context_format
import todo.shared.generated.resources.new_context_with_gps_format
import todo.shared.generated.resources.radius_label
import todo.shared.generated.resources.show_gps_fields
import todo.shared.generated.resources.task_save_button

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextSelectionBottomSheet(
    availableContexts: List<NextActionContext>,
    selectedContextIds: List<String>,
    newContexts: List<NewContextInput>,
    onDismiss: () -> Unit,
    onConfirm: (selectedContextIds: List<String>, newContexts: List<NewContextInput>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    var searchQuery by remember { mutableStateOf("") }
    val currentSelectedContextIds = remember { mutableStateListOf<String>().apply { addAll(selectedContextIds) } }
    val currentNewContexts = remember { mutableStateListOf<NewContextInput>().apply { addAll(newContexts) } }

    var showGpsFields by remember { mutableStateOf(false) }
    var latInput by remember { mutableStateOf("") }
    var lonInput by remember { mutableStateOf("") }
    var radiusInput by remember { mutableStateOf("") }

    val trimmedQuery = searchQuery.trim()

    val filteredAvailableContexts = remember(availableContexts, searchQuery) {
        if (searchQuery.isBlank()) availableContexts
        else availableContexts.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val filteredNewContexts = if (searchQuery.isBlank()) {
        currentNewContexts.toList()
    } else {
        currentNewContexts.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val showCreateOption = trimmedQuery.isNotBlank() &&
        !availableContexts.any { it.name.equals(trimmedQuery, ignoreCase = true) } &&
        !currentNewContexts.any { it.name.equals(trimmedQuery, ignoreCase = true) }

    fun addCurrentQueryAsNewContext() {
        if (trimmedQuery.isNotBlank()) {
            val ctxMatch = availableContexts.find { it.name.equals(trimmedQuery, ignoreCase = true) }
            if (ctxMatch != null) {
                if (ctxMatch.contextId !in currentSelectedContextIds) {
                    currentSelectedContextIds.add(ctxMatch.contextId)
                }
            } else {
                if (!currentNewContexts.any { it.name.equals(trimmedQuery, ignoreCase = true) }) {
                    val lat = latInput.toDoubleOrNull()
                    val lon = lonInput.toDoubleOrNull()
                    val radius = radiusInput.toDoubleOrNull()
                    currentNewContexts.add(
                        NewContextInput(
                            name = trimmedQuery,
                            lat = lat,
                            lon = lon,
                            radius = radius,
                        )
                    )
                }
            }
            searchQuery = ""
            latInput = ""
            lonInput = ""
            radiusInput = ""
            showGpsFields = false
        }
    }

    fun confirmSelection() {
        if (trimmedQuery.isNotBlank()) addCurrentQueryAsNewContext()
        onConfirm(currentSelectedContextIds.toList(), currentNewContexts.toList())
        onDismiss()
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
            // Header
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
                    text = stringResource(Res.string.contexts_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )

                Button(
                    onClick = { confirmSelection() },
                    shape = CircleShape,
                ) {
                    Text(stringResource(Res.string.task_save_button), style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
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
                    onDone = { confirmSelection() },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focusRequester),
            )

            // Optional GPS Section
            if (trimmedQuery.isNotBlank() && showCreateOption) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    TextButton(
                        onClick = { showGpsFields = !showGpsFields },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showGpsFields) stringResource(Res.string.hide_gps_fields) else stringResource(Res.string.show_gps_fields),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = if (showGpsFields) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                        )
                    }

                    AnimatedVisibility(visible = showGpsFields) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = latInput,
                                    onValueChange = { latInput = it },
                                    label = { Text(stringResource(Res.string.latitude_label)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = lonInput,
                                    onValueChange = { lonInput = it },
                                    label = { Text(stringResource(Res.string.longitude_label)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            OutlinedTextField(
                                value = radiusInput,
                                onValueChange = { radiusInput = it },
                                label = { Text(stringResource(Res.string.radius_label)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section Label
            Text(
                text = stringResource(Res.string.contexts_header),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Contexts List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filteredAvailableContexts, key = { it.contextId }) { ctx ->
                    val isSelected = ctx.contextId in currentSelectedContextIds
                    ContextRowItem(
                        contextName = ctx.name,
                        lat = ctx.lat,
                        lon = ctx.lon,
                        radius = ctx.radius,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelected) {
                                currentSelectedContextIds.remove(ctx.contextId)
                            } else {
                                currentSelectedContextIds.add(ctx.contextId)
                            }
                        },
                    )
                }

                items(filteredNewContexts) { newCtx ->
                    ContextRowItem(
                        contextName = newCtx.name,
                        lat = newCtx.lat,
                        lon = newCtx.lon,
                        radius = newCtx.radius,
                        isSelected = true,
                        onClick = {
                            currentNewContexts.remove(newCtx)
                        },
                    )
                }

                if (showCreateOption) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { confirmSelection() },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                val hasGps = latInput.isNotBlank() && lonInput.isNotBlank()
                                Text(
                                    text = if (hasGps) stringResource(Res.string.new_context_with_gps_format, trimmedQuery) else stringResource(Res.string.new_context_format, trimmedQuery),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
