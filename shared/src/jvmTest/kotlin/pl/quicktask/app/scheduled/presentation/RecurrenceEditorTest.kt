package pl.quicktask.app.scheduled.presentation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import pl.quicktask.app.scheduled.model.*
import kotlin.test.*

class RecurrenceEditorTest {
    @Test fun validatesIntervalAndEndDate() {
        val state = RecurrenceEditorState(enabled = true)
        for (interval in listOf("", "0", "367", "abc")) assertFalse(state.copy(interval = interval).isValid("2026-09-20"))
        assertTrue(state.copy(interval = "366", until = "2026-09-20").isValid("2026-09-20"))
        assertFalse(state.copy(until = "2026-09-19").isValid("2026-09-20"))
        assertFalse(state.copy(until = "2026-02-30").isValid("2026-01-01"))
        assertTrue(state.copy(enabled = false, interval = "").isValid("2026-09-20"))
    }
    @Test fun preservesBackendRuleAndExactEndTime() {
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 2, RecurrenceMode.AFTER_COMPLETION, "2027-01-31T09:00:00Z")
        assertEquals(rule, RecurrenceEditorState.from(rule).toRule())
        assertNull(RecurrenceEditorState.from(rule).copy(enabled = false).toRule())
        assertEquals("2027-02-28T23:59:59.999Z", RecurrenceEditorState.from(rule).copy(until = "2027-02-28", originalUntil = null).toRule()?.until)
    }
    @Test fun disablingAlwaysSendsExplicitNullWithProductionJsonDefaults() {
        val dto = UpdateScheduledTaskRequestDto(encryptedTitle = "title", scheduledAt = "2026-09-20T12:00:00Z", recurrence = null)
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val body = json.parseToJsonElement(json.encodeToString(dto)).jsonObject
        assertTrue(body.containsKey("recurrence"))
        assertEquals(JsonNull, body["recurrence"])
    }
    @Test fun encodesBackendEnumsAndOmitsAbsentUntil() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, 3, RecurrenceMode.SCHEDULED)
        val body = Json.parseToJsonElement(Json.encodeToString(rule)).jsonObject
        assertEquals("WEEKLY", body.getValue("frequency").jsonPrimitive.content)
        assertEquals("SCHEDULED", body.getValue("mode").jsonPrimitive.content)
        assertFalse(body.containsKey("until"))
    }
    @Test fun encodesYearlyFrequencyForBackend() {
        val rule = RecurrenceRule(RecurrenceFrequency.YEARLY, 1, RecurrenceMode.SCHEDULED)
        val body = Json.parseToJsonElement(Json.encodeToString(rule)).jsonObject
        assertEquals("YEARLY", body.getValue("frequency").jsonPrimitive.content)
    }
}
