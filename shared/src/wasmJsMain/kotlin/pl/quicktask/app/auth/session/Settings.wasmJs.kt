package pl.quicktask.app.auth.session

import com.russhwolf.settings.Settings

actual fun createSettings(): Settings = Settings()

private val secretSettings = MemorySettings()
actual fun createSecretSettings(): Settings = secretSettings
