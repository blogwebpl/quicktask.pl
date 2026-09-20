package pl.quicktask.app.auth.session

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings

@OptIn(ExperimentalSettingsImplementation::class)
actual fun createSettings(): Settings = KeychainSettings("clearmind_keychain")

private val secretSettings by lazy { ResilientSecretSettings(runCatching { createSettings() }.getOrNull()) }
actual fun createSecretSettings(): Settings = secretSettings
