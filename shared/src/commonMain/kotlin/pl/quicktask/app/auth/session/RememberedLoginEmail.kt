package pl.quicktask.app.auth.session

import com.russhwolf.settings.Settings

/** The address shown on the login form is independent of the active session. */
class RememberedLoginEmail(private val settings: Settings = createSettings()) {
    fun load(): String = runCatching { settings.getStringOrNull(KEY)?.trim().orEmpty() }.getOrDefault("")

    fun save(email: String) {
        val address = email.trim()
        if (address.isEmpty()) clear()
        else runCatching { settings.putString(KEY, address) }
    }

    fun clear() {
        runCatching { settings.remove(KEY) }
    }

    private companion object {
        const val KEY = "clearmind.login.rememberedEmail"
    }
}
