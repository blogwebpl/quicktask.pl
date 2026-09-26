package pl.quicktask.app.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.common.AppLoggerManager
import pl.quicktask.app.common.LogCategory
import pl.quicktask.app.common.LogEntry
import pl.quicktask.app.common.LogLevel
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.logs_clear_button
import todo.shared.generated.resources.logs_count
import todo.shared.generated.resources.logs_disabled_label
import todo.shared.generated.resources.logs_empty
import todo.shared.generated.resources.logs_enabled_label
import todo.shared.generated.resources.logs_filter_all
import todo.shared.generated.resources.logs_filter_api
import todo.shared.generated.resources.logs_filter_func
import todo.shared.generated.resources.logs_filter_refresh
import todo.shared.generated.resources.logs_filter_state
import todo.shared.generated.resources.logs_search_hint
import todo.shared.generated.resources.logs_section_title

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LogsCard(
    modifier: Modifier = Modifier,
) {
    val allLogs by AppLoggerManager.logs.collectAsStateWithLifecycle()
    var isEnabled by remember { mutableStateOf(AppLoggerManager.isLoggingEnabled) }
    var selectedCategoryFilter by remember { mutableStateOf<LogCategoryFilter?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredLogs = remember(allLogs, selectedCategoryFilter, searchQuery) {
        allLogs.filter { entry ->
            val categoryMatches = when (selectedCategoryFilter) {
                null -> true
                LogCategoryFilter.API -> ((entry.category == LogCategory.API_REQUEST) || (entry.category == LogCategory.API_RESPONSE))
                LogCategoryFilter.STATE -> entry.category == LogCategory.STATE_CHANGE
                LogCategoryFilter.REFRESH -> entry.category == LogCategory.REFRESH
                LogCategoryFilter.FUNC -> entry.category == LogCategory.FUNCTION
            }
            val queryMatches = searchQuery.isBlank() ||
                entry.message.contains(searchQuery, ignoreCase = true) ||
                entry.tag.contains(searchQuery, ignoreCase = true) ||
                (entry.details?.contains(searchQuery, ignoreCase = true) == true)

            categoryMatches && queryMatches
        }.reversed()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.logs_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (isEnabled) stringResource(Res.string.logs_enabled_label) else stringResource(Res.string.logs_disabled_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            isEnabled = checked
                            AppLoggerManager.isLoggingEnabled = checked
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.logs_count, filteredLogs.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedButton(
                    onClick = { AppLoggerManager.clearLogs() },
                    modifier = Modifier.height(36.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.logs_clear_button),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CategoryChip(
                    text = stringResource(Res.string.logs_filter_all),
                    selected = selectedCategoryFilter == null,
                    onClick = { selectedCategoryFilter = null },
                )
                CategoryChip(
                    text = stringResource(Res.string.logs_filter_api),
                    selected = selectedCategoryFilter == LogCategoryFilter.API,
                    onClick = { selectedCategoryFilter = LogCategoryFilter.API },
                )
                CategoryChip(
                    text = stringResource(Res.string.logs_filter_state),
                    selected = selectedCategoryFilter == LogCategoryFilter.STATE,
                    onClick = { selectedCategoryFilter = LogCategoryFilter.STATE },
                )
                CategoryChip(
                    text = stringResource(Res.string.logs_filter_refresh),
                    selected = selectedCategoryFilter == LogCategoryFilter.REFRESH,
                    onClick = { selectedCategoryFilter = LogCategoryFilter.REFRESH },
                )
                CategoryChip(
                    text = stringResource(Res.string.logs_filter_func),
                    selected = selectedCategoryFilter == LogCategoryFilter.FUNC,
                    onClick = { selectedCategoryFilter = LogCategoryFilter.FUNC },
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(Res.string.logs_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.logs_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filteredLogs.forEach { entry ->
                        LogItemView(entry = entry)
                    }
                }
            }
        }
    }
}

private enum class LogCategoryFilter {
    API,
    STATE,
    REFRESH,
    FUNC,
}

@Composable
private fun CategoryChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.height(32.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun LogItemView(
    entry: LogEntry,
    modifier: Modifier = Modifier,
) {
    val categoryBg = when (entry.category) {
        LogCategory.API_REQUEST -> MaterialTheme.colorScheme.tertiaryContainer
        LogCategory.API_RESPONSE -> MaterialTheme.colorScheme.secondaryContainer
        LogCategory.STATE_CHANGE -> MaterialTheme.colorScheme.primaryContainer
        LogCategory.REFRESH -> MaterialTheme.colorScheme.surfaceContainerHigh
        LogCategory.FUNCTION -> MaterialTheme.colorScheme.surfaceContainerHighest
        LogCategory.GENERAL -> MaterialTheme.colorScheme.surface
    }

    val categoryFg = when (entry.category) {
        LogCategory.API_REQUEST -> MaterialTheme.colorScheme.onTertiaryContainer
        LogCategory.API_RESPONSE -> MaterialTheme.colorScheme.onSecondaryContainer
        LogCategory.STATE_CHANGE -> MaterialTheme.colorScheme.onPrimaryContainer
        LogCategory.REFRESH -> MaterialTheme.colorScheme.onSurface
        LogCategory.FUNCTION -> MaterialTheme.colorScheme.onSurface
        LogCategory.GENERAL -> MaterialTheme.colorScheme.onSurface
    }

    val isError = entry.level == LogLevel.ERROR

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .background(categoryBg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = entry.category.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = categoryFg,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Text(
                        text = entry.tag,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                    )
                }

                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            )

            entry.details?.let { details ->
                Text(
                    text = details,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
