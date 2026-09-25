package pl.quicktask.app.ui.components

import kotlin.time.Instant

/** Dates are compared as calendar days, regardless of the time stored by the API. */
fun taskDatePart(value: String?): String? {
    val date = value?.substringBefore('T')?.takeIf { it.isNotBlank() } ?: return null
    if (date.length != 10 || date[4] != '-' || date[7] != '-') return null
    return runCatching { Instant.parse("${date}T00:00:00Z") }.getOrNull()?.let { date }
}

fun isTaskDateInRange(value: String, minDate: String? = null, maxDate: String? = null): Boolean {
    val date = taskDatePart(value) ?: return false
    val min = taskDatePart(minDate)
    val max = taskDatePart(maxDate)
    return (min == null || date >= min) && (max == null || date <= max)
}

fun isScheduledDateOrderValid(scheduledAt: String, deferUntil: String?, dueAt: String?): Boolean {
    val scheduled = taskDatePart(scheduledAt) ?: return false
    val deferred = deferUntil?.takeIf { it.isNotBlank() }?.let(::taskDatePart)
    val due = dueAt?.takeIf { it.isNotBlank() }?.let(::taskDatePart)
    if (deferUntil?.isNotBlank() == true && deferred == null) return false
    if (dueAt?.isNotBlank() == true && due == null) return false
    return (deferred == null || deferred <= scheduled) &&
        (due == null || scheduled <= due) &&
        (deferred == null || due == null || deferred <= due)
}

fun isFollowUpDateOrderValid(followUpAt: String?, dueAt: String?): Boolean {
    val followUp = followUpAt?.takeIf { it.isNotBlank() }?.let(::taskDatePart)
    val due = dueAt?.takeIf { it.isNotBlank() }?.let(::taskDatePart)
    if (followUpAt?.isNotBlank() == true && followUp == null) return false
    if (dueAt?.isNotBlank() == true && due == null) return false
    return followUp == null || due == null || followUp <= due
}
