package pl.quicktask.app.sync.domain

import pl.quicktask.app.sync.model.SyncEvent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SyncEventParser {

    private val json = Json { ignoreUnknownKeys = true }

    private var currentEventName: String? = null
    private var currentEventId: String? = null
    private val currentDataBuffer = StringBuilder()
    private var partialLineBuffer = StringBuilder()

    fun feedChunk(chunk: String): List<SyncEvent> {
        val events = mutableListOf<SyncEvent>()
        partialLineBuffer.append(chunk)

        val fullText = partialLineBuffer.toString()
        val lines = fullText.split("\n")

        // The last element after split might be a partial line if fullText doesn't end with '\n'
        for (i in 0 until lines.size - 1) {
            val line = lines[i].removeSuffix("\r")
            events.addAll(feedLine(line))
        }

        partialLineBuffer = StringBuilder(lines.last())
        return events
    }

    fun feedLine(line: String): List<SyncEvent> {
        val cleanLine = line.removeSuffix("\r")

        // Comment line (e.g. : heartbeat) -> ignore
        if (cleanLine.startsWith(":")) {
            return emptyList()
        }

        // Empty line -> dispatch accumulated event
        if (cleanLine.isEmpty()) {
            val event = dispatchCurrentEvent()
            return if (event != null) listOf(event) else emptyList()
        }

        val field: String
        val value: String
        val colonIndex = cleanLine.indexOf(':')
        if (colonIndex != -1) {
            field = cleanLine.substring(0, colonIndex).trim()
            val rawValue = cleanLine.substring(colonIndex + 1)
            value = if (rawValue.startsWith(" ")) rawValue.substring(1) else rawValue
        } else {
            field = cleanLine.trim()
            value = ""
        }

        when (field) {
            "event" -> currentEventName = value
            "id" -> currentEventId = value
            "data" -> {
                if (currentDataBuffer.isNotEmpty()) {
                    currentDataBuffer.append("\n")
                }
                currentDataBuffer.append(value)
            }
        }

        return emptyList()
    }

    private fun dispatchCurrentEvent(): SyncEvent? {
        val eventName = currentEventName
        val sseId = currentEventId
        val dataStr = currentDataBuffer.toString()

        // Reset buffers for next event
        currentEventName = null
        currentEventId = null
        currentDataBuffer.clear()

        var jsonEventName: String? = null
        var jsonEventId: String? = null
        var jsonChangedAt: String? = null
        var jsonItemId: String? = null

        if (dataStr.isNotBlank()) {
            try {
                val jsonObj = json.parseToJsonElement(dataStr).jsonObject
                jsonEventName = jsonObj["event"]?.jsonPrimitive?.content
                jsonEventId = jsonObj["eventId"]?.jsonPrimitive?.content
                jsonChangedAt = jsonObj["changedAt"]?.jsonPrimitive?.content
                jsonItemId = jsonObj["itemId"]?.jsonPrimitive?.content
            } catch (_: Exception) {
                // Ignore JSON parsing errors
            }
        }

        val finalEventName = eventName ?: jsonEventName

        return when (finalEventName) {
            "sync.required" -> SyncEvent.SyncRequired
            "contacts.changed" -> {
                val finalEventId = sseId?.takeIf { it.isNotBlank() } ?: jsonEventId
                if (!finalEventId.isNullOrBlank()) {
                    SyncEvent.ContactsChanged(
                        eventId = finalEventId,
                        changedAt = jsonChangedAt,
                    )
                } else {
                    null
                }
            }
            "items.changed" -> {
                val finalEventId = sseId?.takeIf { it.isNotBlank() } ?: jsonEventId
                if (!finalEventId.isNullOrBlank()) {
                    SyncEvent.ItemsChanged(
                        eventId = finalEventId,
                        itemId = jsonItemId?.takeIf { it.isNotBlank() },
                        changedAt = jsonChangedAt,
                    )
                } else {
                    null
                }
            }
            else -> null
        }
    }

    fun reset() {
        currentEventName = null
        currentEventId = null
        currentDataBuffer.clear()
        partialLineBuffer.clear()
    }
}
