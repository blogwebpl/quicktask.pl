package pl.quicktask.app.settings.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import pl.quicktask.app.auth.crypto.DefaultDPoPManager
import pl.quicktask.app.auth.model.ApiException
import pl.quicktask.app.auth.session.SessionManager
import pl.quicktask.app.network.client.AuthenticatedApiClient
import pl.quicktask.app.settings.model.UserSettings
import pl.quicktask.app.testing.TestSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsRepositoryTest {

    @Test
    fun fetchesUserSettingsSuccessfully(): Unit = runBlocking {
        var requestCount = 0
        val session = SessionManager(TestSettings()).apply { accessToken = "test-token" }
        val responseJson = """{"timeZone": "Europe/Warsaw"}"""

        val client = HttpClient(MockEngine { request ->
            requestCount++
            assertEquals("https://example.test/users/me/settings", request.url.toString())
            assertEquals("GET", request.method.value)
            assertEquals("DPoP test-token", request.headers["Authorization"])
            assertTrue(!request.headers["DPoP"].isNullOrBlank())
            respond(responseJson, HttpStatusCode.OK, headersOf("Content-Type", ContentType.Application.Json.toString()))
        }) { install(ContentNegotiation) { json() } }

        try {
            val api = AuthenticatedApiClient(
                client,
                DefaultDPoPManager(TestSettings()),
                session,
                { Result.success(Unit) },
                "https://example.test/",
            )
            val repository = SettingsRepository(api)
            val result = repository.getUserSettings().getOrThrow()
            assertEquals(1, requestCount)
            assertEquals("Europe/Warsaw", result.timeZone)
        } finally {
            client.close()
            session.clearSession()
        }
    }

    @Test
    fun updatesUserSettingsSuccessfully(): Unit = runBlocking {
        var requestCount = 0
        val session = SessionManager(TestSettings()).apply { accessToken = "test-token" }
        val responseJson = """{"timeZone": "America/New_York"}"""

        val client = HttpClient(MockEngine { request ->
            requestCount++
            assertEquals("https://example.test/users/me/settings", request.url.toString())
            assertEquals("PATCH", request.method.value)
            assertEquals("DPoP test-token", request.headers["Authorization"])
            assertTrue(!request.headers["DPoP"].isNullOrBlank())
            respond(responseJson, HttpStatusCode.OK, headersOf("Content-Type", ContentType.Application.Json.toString()))
        }) { install(ContentNegotiation) { json() } }

        try {
            val api = AuthenticatedApiClient(
                client,
                DefaultDPoPManager(TestSettings()),
                session,
                { Result.success(Unit) },
                "https://example.test/",
            )
            val repository = SettingsRepository(api)
            val result = repository.updateUserSettings(UserSettings("America/New_York")).getOrThrow()
            assertEquals(1, requestCount)
            assertEquals("America/New_York", result.timeZone)
        } finally {
            client.close()
            session.clearSession()
        }
    }

    @Test
    fun handlesInvalidTimezoneError(): Unit = runBlocking {
        val session = SessionManager(TestSettings()).apply { accessToken = "test-token" }
        val errorJson = """{"statusCode": 400, "code": "INVALID_TIMEZONE", "message": "Invalid time zone"}"""

        val client = HttpClient(MockEngine {
            respond(errorJson, HttpStatusCode.BadRequest, headersOf("Content-Type", ContentType.Application.Json.toString()))
        }) { install(ContentNegotiation) { json() } }

        try {
            val api = AuthenticatedApiClient(
                client,
                DefaultDPoPManager(TestSettings()),
                session,
                { Result.success(Unit) },
                "https://example.test/",
            )
            val repository = SettingsRepository(api)
            val result = repository.updateUserSettings(UserSettings("+02:00"))
            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull() as ApiException
            assertEquals(400, exception.statusCode)
            assertEquals("INVALID_TIMEZONE", exception.code)
            assertEquals("Invalid time zone", exception.message)
        } finally {
            client.close()
            session.clearSession()
        }
    }
}
