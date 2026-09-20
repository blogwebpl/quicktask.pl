package pl.quicktask.app.settings.model

import kotlinx.serialization.Serializable

@Serializable
data class UserSettingsDto(
    val timeZone: String,
)

data class UserSettings(
    val timeZone: String,
)

fun UserSettingsDto.toDomain(): UserSettings = UserSettings(timeZone = timeZone)
