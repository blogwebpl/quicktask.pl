package pl.quicktask.app.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskDateRulesTest {
    @Test
    fun scheduledDatesAllowSameDayAndOptionalDates() {
        assertTrue(isScheduledDateOrderValid("2026-09-25", null, null))
        assertTrue(isScheduledDateOrderValid("2026-09-25T12:00:00.000Z", "2026-09-25", "2026-09-25"))
    }

    @Test
    fun scheduledDatesRejectInvertedAndInvalidDates() {
        assertFalse(isScheduledDateOrderValid("2026-09-25", null, "2026-09-24"))
        assertFalse(isScheduledDateOrderValid("2026-09-25", "2026-09-26", null))
        assertFalse(isScheduledDateOrderValid("2026-09-25", "2026-09-25", "2026-09-24"))
        assertFalse(isScheduledDateOrderValid("2026-02-30", null, null))
    }

    @Test
    fun rangesIncludeBoundsAndRejectOutsideDates() {
        assertTrue(isTaskDateInRange("2026-09-25", "2026-09-25", "2026-09-25"))
        assertFalse(isTaskDateInRange("2026-09-24", "2026-09-25", null))
        assertFalse(isTaskDateInRange("2026-09-26", null, "2026-09-25"))
    }

    @Test
    fun followUpCannotBeAfterDeadline() {
        assertTrue(isFollowUpDateOrderValid("2026-09-25", "2026-09-25"))
        assertFalse(isFollowUpDateOrderValid("2026-09-26", "2026-09-25"))
    }
}
