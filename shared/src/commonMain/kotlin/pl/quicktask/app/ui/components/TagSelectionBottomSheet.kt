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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.nextactions.model.NextActionTag
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.action_clear
import todo.shared.generated.resources.action_close
import todo.shared.generated.resources.new_tag_format
import todo.shared.generated.resources.search_or_create_tag
import todo.shared.generated.resources.tags_header
import todo.shared.generated.resources.tags_title
import todo.shared.generated.resources.task_save_button

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagSelectionBottomSheet(
    availableTags: List<NextActionTag>,
    selectedTagIds: List<String>,
    newTagNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (selectedTagIds: List<String>, newTagNames: List<String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    var searchQuery by remember { mutableStateOf("") }
    val currentSelectedTagIds = remember { mutableStateListOf<String>().apply { addAll(selectedTagIds) } }
    val currentNewTagNames = remember { mutableStateListOf<String>().apply { addAll(newTagNames) } }

    val trimmedQuery = searchQuery.trim()

    val filteredAvailableTags = remember(availableTags, searchQuery) {
        if (searchQuery.isBlank()) availableTags
        else availableTags.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val filteredNewTags = if (searchQuery.isBlank()) {
        currentNewTagNames.toList()
    } else {
        currentNewTagNames.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    val showCreateOption = trimmedQuery.isNotBlank() &&
        !availableTags.any { it.name.equals(trimmedQuery, ignoreCase = true) } &&
        !currentNewTagNames.any { it.equals(trimmedQuery, ignoreCase = true) }

    fun addCurrentQueryAsNewTag() {
        if (trimmedQuery.isNotBlank()) {
            val tagMatch = availableTags.find { it.name.equals(trimmedQuery, ignoreCase = true) }
            if (tagMatch != null) {
                if (tagMatch.tagId !in currentSelectedTagIds) {
                    currentSelectedTagIds.add(tagMatch.tagId)
                }
            } else {
                if (!currentNewTagNames.any { it.equals(trimmedQuery, ignoreCase = true) }) {
                    currentNewTagNames.add(trimmedQuery)
                }
            }
            searchQuery = ""
        }
    }

    fun confirmSelection() {
        if (trimmedQuery.isNotBlank()) addCurrentQueryAsNewTag()
        onConfirm(currentSelectedTagIds.toList(), currentNewTagNames.toList())
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
                    text = stringResource(Res.string.tags_title),
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
                placeholder = { Text(stringResource(Res.string.search_or_create_tag)) },
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

            Spacer(modifier = Modifier.height(16.dp))

            // Section Label
            Text(
                text = stringResource(Res.string.tags_header),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Tags List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filteredAvailableTags, key = { it.tagId }) { tag ->
                    val isSelected = tag.tagId in currentSelectedTagIds
                    TagRowItem(
                        tagName = tag.name,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelected) {
                                currentSelectedTagIds.remove(tag.tagId)
                            } else {
                                currentSelectedTagIds.add(tag.tagId)
                            }
                        },
                    )
                }

                items(filteredNewTags) { newName ->
                    TagRowItem(
                        tagName = newName,
                        isSelected = true,
                        onClick = {
                            currentNewTagNames.remove(newName)
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
                                Text(
                                    text = stringResource(Res.string.new_tag_format, trimmedQuery),
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
