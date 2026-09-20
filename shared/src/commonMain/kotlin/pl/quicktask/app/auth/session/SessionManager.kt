package pl.quicktask.app.auth.session

import com.russhwolf.settings.Settings
import pl.quicktask.app.common.AppLoggerManager

expect fun createSettings(): Settings

class SessionManager(
    private val settings: Settings = createSecretSettings(),
) {
    private var savedEpoch = browserSessionEpoch()
    val changedInAnotherTab: Boolean
        get() = browserSessions && savedEpoch != browserSessionEpoch()

    var accessToken: String?
        get() = if (changedInAnotherTab) null else settings.getStringOrNull(KEY_ACCESS_TOKEN)
        set(value) {
            if (value != null) settings.putString(KEY_ACCESS_TOKEN, value)
            else settings.remove(KEY_ACCESS_TOKEN)
        }

    var refreshToken: String?
        get() = if (changedInAnotherTab) null else settings.getStringOrNull(KEY_REFRESH_TOKEN)
        set(value) {
            if (value != null) settings.putString(KEY_REFRESH_TOKEN, value)
            else settings.remove(KEY_REFRESH_TOKEN)
        }

    var userEmail: String?
        get() = if (changedInAnotherTab) null else settings.getStringOrNull(KEY_USER_EMAIL)
        set(value) {
            if (value != null) settings.putString(KEY_USER_EMAIL, value)
            else settings.remove(KEY_USER_EMAIL)
        }

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrBlank()

    fun saveSession(accessToken: String, refreshToken: String, email: String) {
        savedEpoch = browserSessionEpoch()
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.userEmail = email
        AppLoggerManager.logStateChange("SessionManager", "Zapisano nową sesję", "email=$email")
    }

    fun clearSession() {
        settings.remove(KEY_ACCESS_TOKEN)
        settings.remove(KEY_REFRESH_TOKEN)
        settings.remove(KEY_USER_EMAIL)
        AppLoggerManager.logStateChange("SessionManager", "Wyczyszczono sesję (wylogowanie)")
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "clearmind.accessToken"
        private const val KEY_REFRESH_TOKEN = "clearmind.refreshToken"
        private const val KEY_USER_EMAIL = "clearmind.userEmail"
    }
}
