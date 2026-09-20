package pl.quicktask.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ktor.util.date.GMTDate
import io.ktor.util.date.getTimeMillis

import org.jetbrains.compose.resources.stringResource
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.action_select
import todo.shared.generated.resources.clear_due_date
import todo.shared.generated.resources.date_calendar
import todo.shared.generated.resources.date_next_week
import todo.shared.generated.resources.date_placeholder
import todo.shared.generated.resources.date_today
import todo.shared.generated.resources.date_tomorrow
import todo.shared.generated.resources.due_date_optional
import todo.shared.generated.resources.open_calendar
import todo.shared.generated.resources.timer_cancel

fun formatIsoDate(timestamp: Long): String {
    val date = GMTDate(timestamp)
    val year = date.year
    val month = (date.month.ordinal + 1).toString().padStart(2, '0')
    val day = date.dayOfMonth.toString().padStart(2, '0')
    return "$year-$month-$day"
}

fun todayIso(): String = formatIsoDate(getTimeMillis())
fun tomorrowIso(): String = formatIsoDate(getTimeMillis() + 86_400_000L)
fun nextWeekIso(): String = formatIsoDate(getTimeMillis() + 7 * 86_400_000L)

fun normalizeIsoDate(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return null
    if (trimmed.contains('T')) return trimmed
    if (trimmed.length == 10 && trimmed.count { it == '-' } == 2) {
        return "${trimmed}T12:00:00.000Z"
    }
    return "${trimmed}T12:00:00.000Z"
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DueDatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePickerDialog by remember { mutableStateOf(false) }

    val datePart = if (value.contains('T')) value.substringBefore('T') else value

    val isToday = datePart.isNotEmpty() && datePart == todayIso()
    val isTomorrow = datePart.isNotEmpty() && datePart == tomorrowIso()
    val isNextWeek = datePart.isNotEmpty() && datePart == nextWeekIso()
    val isCustomDate = datePart.isNotEmpty() && !isToday && !isTomorrow && !isNextWeek

    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.due_date_optional),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth(),
        ) {
            SegmentedButton(
                selected = isToday,
                onClick = {
                    if (isToday) onValueChange("") else onValueChange(todayIso())
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4),
                icon = {},
                label = {
                    Text(
                        text = stringResource(Res.string.date_today),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )

            SegmentedButton(
                selected = isTomorrow,
                onClick = {
                    if (isTomorrow) onValueChange("") else onValueChange(tomorrowIso())
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4),
                icon = {},
                label = {
                    Text(
                        text = stringResource(Res.string.date_tomorrow),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )

            SegmentedButton(
                selected = isNextWeek,
                onClick = {
                    if (isNextWeek) onValueChange("") else onValueChange(nextWeekIso())
                },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4),
                icon = {},
                label = {
                    Text(
                        text = stringResource(Res.string.date_next_week),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )

            SegmentedButton(
                selected = isCustomDate || showDatePickerDialog,
                onClick = { showDatePickerDialog = true },
                shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4),
                icon = {},
                label = {
                    Text(
                        text = stringResource(Res.string.date_calendar),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = datePart,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.date_placeholder)) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (datePart.isNotBlank()) {
                        IconButton(onClick = { onValueChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.clear_due_date))
                        }
                    }
                    IconButton(onClick = { showDatePickerDialog = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = stringResource(Res.string.open_calendar))
                    }
                }
            },
        )
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onValueChange(formatIsoDate(millis))
                        }
                        showDatePickerDialog = false
                    },
                ) {
                    Text(stringResource(Res.string.action_select))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text(stringResource(Res.string.timer_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
