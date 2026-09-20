package pl.quicktask.app.common

import io.ktor.util.date.GMTDate
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LogLevel { INFO, WARNING, ERROR }

enum class LogCategory(val label: String) {
    API_REQUEST("API REQ"),
    API_RESPONSE("API RES"),
    STATE_CHANGE("STATE"),
    REFRESH("REFRESH"),
    FUNCTION("FUNC"),
    GENERAL("GENERAL"),
}

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val level: LogLevel,
    val category: LogCategory,
    val tag: String,
    val message: String,
    val details: String? = null,
)

object AppLoggerManager {
    /** Jedna zmienna wyłączająca/włączająca logowanie w całej aplikacji */
    var isLoggingEnabled: Boolean = true

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private var nextId = 1L
    private const val MAX_LOGS = 500

    fun log(
        level: LogLevel = LogLevel.INFO,
        category: LogCategory = LogCategory.GENERAL,
        tag: String = "App",
        message: String,
        details: String? = null,
    ) {
        if (!isLoggingEnabled) return

        val timestamp = formatTimestamp(getTimeMillis())
        val entry = LogEntry(
            id = nextId++,
            timestamp = timestamp,
            level = level,
            category = category,
            tag = tag,
            message = message,
            details = details,
        )

        println("[$timestamp] [${category.label}] [$tag] $message${details?.let { " ($it)" } ?: ""}")

        _logs.update { current ->
            if (current.size >= MAX_LOGS) {
                current.drop(current.size - MAX_LOGS + 1) + entry
            } else {
                current + entry
            }
        }
    }

    fun logApiRequest(tag: String, method: String, url: String, details: String? = null) {
        log(LogLevel.INFO, LogCategory.API_REQUEST, tag, "$method $url", details)
    }

    fun logApiResponse(tag: String, method: String, url: String, statusCode: Int, details: String? = null) {
        val level = if (statusCode in 200..299) LogLevel.INFO else LogLevel.ERROR
        log(level, LogCategory.API_RESPONSE, tag, "$method $url -> $statusCode", details)
    }

    fun logStateChange(tag: String, stateName: String, details: String? = null) {
        log(LogLevel.INFO, LogCategory.STATE_CHANGE, tag, "Zmiana stanu: $stateName", details)
    }

    fun logRefresh(tag: String, message: String, details: String? = null) {
        log(LogLevel.INFO, LogCategory.REFRESH, tag, "Odświeżenie: $message", details)
    }

    fun logFunction(tag: String, functionName: String, details: String? = null) {
        log(LogLevel.INFO, LogCategory.FUNCTION, tag, "Funkcja: $functionName", details)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun formatTimestamp(timeMs: Long): String {
        return try {
            val date = GMTDate(timeMs)
            val millis = (timeMs % 1000).toInt()
            fun pad(n: Int, len: Int = 2) = n.toString().padStart(len, '0')
            "${pad(date.hours)}:${pad(date.minutes)}:${pad(date.seconds)}.${pad(millis, 3)}"
        } catch (_: Exception) {
            timeMs.toString()
        }
    }
}

fun interface AppLogger {
    fun log(level: LogLevel, operation: String, status: Int?, code: String?)
}

object ConsoleAppLogger : AppLogger {
    override fun log(level: LogLevel, operation: String, status: Int?, code: String?) {
        AppLoggerManager.log(
            level = level,
            category = LogCategory.GENERAL,
            tag = "ConsoleAppLogger",
            message = operation,
            details = "status=${status ?: "none"} code=${code ?: "none"}",
        )
    }
}

object NoOpAppLogger : AppLogger {
    override fun log(level: LogLevel, operation: String, status: Int?, code: String?) = Unit
}
