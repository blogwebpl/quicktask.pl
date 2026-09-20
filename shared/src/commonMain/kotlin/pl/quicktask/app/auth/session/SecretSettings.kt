package pl.quicktask.app.auth.session

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SecretStorageAvailability { PERSISTENT, MEMORY_ONLY }
object SecretStorageStatus {
    private val state = MutableStateFlow(SecretStorageAvailability.PERSISTENT)
    val availability = state.asStateFlow()
    fun memoryOnly() { state.value = SecretStorageAvailability.MEMORY_ONLY }
}
expect fun createSecretSettings(): Settings

/** Never falls back to ordinary persistent preferences. */
class ResilientSecretSettings(private val persistent: Settings?, private val revocations: Settings? = null) : Settings by MemorySettings() {
    private val memory = MemorySettings()
    private var available = persistent != null
    init {
        try {
            if (revocations?.getBoolean("requireLogin", false) == true) {
                check(persistent != null)
                secretNames.forEach { persistent.remove(it) }
                revocations.remove("requireLogin")
            }
            if (available) secretNames.forEach { key -> persistent!!.getStringOrNull(key)?.let { memory.putString(key, it) } }
        } catch (_: Exception) { memory.clear(); unavailable() }
        if (!available) unavailable()
    }
    private fun unavailable() {
        available = false
        SecretStorageStatus.memoryOnly()
        // The marker contains no secret. A locked preferences store must not
        // prevent local logout or turn a memory-only session into an exception.
        runCatching { revocations?.putBoolean("requireLogin", true) }
    }
    override fun getStringOrNull(key: String): String? {
        if (!available) return memory.getStringOrNull(key)
        return try { persistent!!.getStringOrNull(key).also { if (it != null) memory.putString(key, it) } }
        catch (_: Exception) { unavailable(); memory.getStringOrNull(key) }
    }
    override fun getString(key: String, defaultValue: String) = getStringOrNull(key) ?: defaultValue
    override fun putString(key: String, value: String) {
        memory.putString(key, value)
        if (available) try { persistent!!.putString(key, value) }
        catch (_: Exception) { unavailable() }
    }
    override fun remove(key: String) {
        memory.remove(key)
        try { persistent?.remove(key) } catch (_: Exception) { unavailable() }
    }
    private companion object {
        val secretNames = listOf("clearmind.accessToken", "clearmind.refreshToken", "clearmind.userEmail", "clearmind.dpop.privateKey", "clearmind.dpop.publicKey", "clearmind.keycache.email", "clearmind.keycache.publicKey", "clearmind.keycache.privateKey")
    }
}
