package pl.quicktask.app.scheduled.model

import kotlinx.serialization.Serializable

@Serializable
enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }
@Serializable
enum class RecurrenceMode { SCHEDULED, AFTER_COMPLETION }
@Serializable
data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int,
    val mode: RecurrenceMode,
    val until: String? = null,
)
