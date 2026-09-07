package pl.quicktask.todo.auth

import com.russhwolf.settings.Settings

expect fun createSettings(): Settings

class SessionManager(
    private val settings: Settings = createSettings(),
) {
    var accessToken: String?
        get() = settings.getStringOrNull(KEY_ACCESS_TOKEN)
        set(value) {
            if (value != null) settings.putString(KEY_ACCESS_TOKEN, value)
            else settings.remove(KEY_ACCESS_TOKEN)
        }

    var refreshToken: String?
        get() = settings.getStringOrNull(KEY_REFRESH_TOKEN)
        set(value) {
            if (value != null) settings.putString(KEY_REFRESH_TOKEN, value)
            else settings.remove(KEY_REFRESH_TOKEN)
        }

    var userEmail: String?
        get() = settings.getStringOrNull(KEY_USER_EMAIL)
        set(value) {
            if (value != null) settings.putString(KEY_USER_EMAIL, value)
            else settings.remove(KEY_USER_EMAIL)
        }

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrBlank()

    fun saveSession(accessToken: String, refreshToken: String, email: String) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.userEmail = email
    }

    fun clearSession() {
        settings.remove(KEY_ACCESS_TOKEN)
        settings.remove(KEY_REFRESH_TOKEN)
        settings.remove(KEY_USER_EMAIL)
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "clearmind.accessToken"
        private const val KEY_REFRESH_TOKEN = "clearmind.refreshToken"
        private const val KEY_USER_EMAIL = "clearmind.userEmail"
    }
}
