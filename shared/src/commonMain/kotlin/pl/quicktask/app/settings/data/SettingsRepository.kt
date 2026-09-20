package pl.quicktask.app.settings.data

import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import pl.quicktask.app.common.coroutineResult
import pl.quicktask.app.network.client.AuthenticatedApiClient
import pl.quicktask.app.settings.model.UserSettings
import pl.quicktask.app.settings.model.UserSettingsDto
import pl.quicktask.app.settings.model.toDomain

interface SettingsOperations {
    suspend fun getUserSettings(): Result<UserSettings>
    suspend fun updateUserSettings(settings: UserSettings): Result<UserSettings>
}

class SettingsRepository(
    private val api: AuthenticatedApiClient,
) : SettingsOperations {
    override suspend fun getUserSettings(): Result<UserSettings> = coroutineResult {
        val response = api.request(HttpMethod.Get, "users/me/settings")
        response.body<UserSettingsDto>().toDomain()
    }

    override suspend fun updateUserSettings(settings: UserSettings): Result<UserSettings> = coroutineResult {
        val response = api.request(HttpMethod.Patch, "users/me/settings") {
            contentType(ContentType.Application.Json)
            setBody(UserSettingsDto(timeZone = settings.timeZone))
        }
        response.body<UserSettingsDto>().toDomain()
    }
}
