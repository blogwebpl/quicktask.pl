package pl.quicktask.app.scheduled.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import pl.quicktask.app.scheduled.model.*
import pl.quicktask.app.ui.components.LabeledDatePickerField
import todo.shared.generated.resources.*

internal data class RecurrenceEditorState(
    val enabled: Boolean = false,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.WEEKLY,
    val interval: String = "1",
    val mode: RecurrenceMode = RecurrenceMode.SCHEDULED,
    val until: String = "",
    // Preserve the exact server timestamp when an existing end date was not changed.
    val originalUntil: String? = null,
) {
    fun isValid(scheduledAt: String): Boolean {
        if (!enabled) return true
        if (interval.toIntOrNull() !in 1..366) return false
        if (until.isBlank()) return true
        val end = runCatching { Instant.parse(until + "T00:00:00Z") }.getOrNull() ?: return false
        val start = runCatching { Instant.parse(scheduledAt.substringBefore('T') + "T00:00:00Z") }.getOrNull() ?: return false
        return end >= start
    }

    fun toRule(): RecurrenceRule? = if (!enabled) null else RecurrenceRule(
        frequency, requireNotNull(interval.toIntOrNull()), mode,
        if (until.isBlank()) null else originalUntil?.takeIf { it.substringBefore('T') == until }
            ?: (until + "T23:59:59.999Z"),
    )

    companion object {
        fun from(rule: RecurrenceRule?) = rule?.let {
            RecurrenceEditorState(true, it.frequency, it.interval.toString(), it.mode, it.until?.substringBefore('T') ?: "", it.until)
        } ?: RecurrenceEditorState()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecurrenceEditor(state: RecurrenceEditorState, onChange: (RecurrenceEditorState) -> Unit, scheduledAt: String, enabled: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().toggleable(value = state.enabled, enabled = enabled, role = Role.Switch,
                onValueChange = { onChange(state.copy(enabled = it)) }), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(Res.string.recurrence_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Switch(checked = state.enabled, onCheckedChange = null, enabled = enabled)
            }
            if (state.enabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(Res.string.recurrence_frequency) + " *", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    RecurrenceFrequency.entries.forEachIndexed { index, frequency ->
                        SegmentedButton(
                            selected = state.frequency == frequency,
                            onClick = { onChange(state.copy(frequency = frequency)) },
                            enabled = enabled,
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = RecurrenceFrequency.entries.size),
                            icon = {},
                            label = { Text(stringResource(when (frequency) {
                                RecurrenceFrequency.DAILY -> Res.string.recurrence_daily
                                RecurrenceFrequency.WEEKLY -> Res.string.recurrence_weekly
                                RecurrenceFrequency.MONTHLY -> Res.string.recurrence_monthly
                                RecurrenceFrequency.YEARLY -> Res.string.recurrence_yearly
                            }), maxLines = 1) },
                        )
                    }
                }
                OutlinedTextField(value = state.interval, onValueChange = { text -> if (text.length <= 3 && text.all { it in '0'..'9' }) onChange(state.copy(interval = text)) },
                    label = { Text(stringResource(Res.string.recurrence_interval) + " *") },
                    suffix = { Text(stringResource(when (state.frequency) {
                        RecurrenceFrequency.DAILY -> Res.string.recurrence_unit_days
                        RecurrenceFrequency.WEEKLY -> Res.string.recurrence_unit_weeks
                        RecurrenceFrequency.MONTHLY -> Res.string.recurrence_unit_months
                        RecurrenceFrequency.YEARLY -> Res.string.recurrence_unit_years
                    })) },
                    supportingText = if (state.interval.toIntOrNull() !in 1..366) {{ Text(stringResource(Res.string.recurrence_interval_error)) }} else null,
                    shape = RoundedCornerShape(12.dp),
                    isError = state.interval.toIntOrNull() !in 1..366, enabled = enabled,
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Text(stringResource(Res.string.recurrence_mode_label) + " *", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
                    RecurrenceMode.entries.forEach { mode ->
                        Surface(shape = RoundedCornerShape(12.dp),
                            color = if (state.mode == mode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (state.mode == mode) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant)) {
                            Row(modifier = Modifier.fillMaxWidth().selectable(selected = state.mode == mode, enabled = enabled,
                                role = Role.RadioButton, onClick = { onChange(state.copy(mode = mode)) }).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RadioButton(selected = state.mode == mode, onClick = null, enabled = enabled)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(if (mode == RecurrenceMode.SCHEDULED) Res.string.recurrence_calendar else Res.string.recurrence_completion), style = MaterialTheme.typography.labelLarge)
                                    Text(stringResource(if (mode == RecurrenceMode.SCHEDULED) Res.string.recurrence_calendar_hint else Res.string.recurrence_completion_hint),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                LabeledDatePickerField(value = state.until, onValueChange = { if (enabled) onChange(state.copy(until = it, originalUntil = null)) }, label = stringResource(Res.string.recurrence_until), enabled = enabled, minDate = scheduledAt)
                if (state.until.isBlank()) Text(stringResource(Res.string.recurrence_no_end), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (!state.copy(interval = "1").isValid(scheduledAt)) Text(stringResource(Res.string.recurrence_end_error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
internal fun recurrenceSummary(rule: RecurrenceRule): String = if (rule.interval == 1) stringResource(
    when (rule.frequency) {
        RecurrenceFrequency.DAILY -> Res.string.recurrence_daily
        RecurrenceFrequency.WEEKLY -> Res.string.recurrence_weekly
        RecurrenceFrequency.MONTHLY -> Res.string.recurrence_monthly
        RecurrenceFrequency.YEARLY -> Res.string.recurrence_yearly
    },
) else stringResource(
    when (rule.frequency) {
        RecurrenceFrequency.DAILY -> Res.string.recurrence_days_summary
        RecurrenceFrequency.WEEKLY -> Res.string.recurrence_weeks_summary
        RecurrenceFrequency.MONTHLY -> Res.string.recurrence_months_summary
        RecurrenceFrequency.YEARLY -> Res.string.recurrence_years_summary
    }, rule.interval,
)
