package pl.quicktask.app.ui.theme

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.quicktask.app.auth.createSettings

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

object ThemeManager {
    private val settings: Settings by lazy { createSettings() }
    private const val KEY_THEME_MODE = "app_theme_mode"

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private fun loadThemeMode(): ThemeMode {
        val saved = runCatching { settings.getStringOrNull(KEY_THEME_MODE) }.getOrNull() ?: return ThemeMode.SYSTEM
        return try {
            ThemeMode.valueOf(saved)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        runCatching { settings.putString(KEY_THEME_MODE, mode.name) }
        _themeMode.value = mode
    }
}
