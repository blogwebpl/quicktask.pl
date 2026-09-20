package pl.quicktask.app.now.data

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
import pl.quicktask.app.auth.session.SessionManager
import pl.quicktask.app.di.AppModule
import pl.quicktask.app.testing.TestSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NowRepositoryTest {
    @Test
    fun fetchesNowDataWithProperHeaders(): Unit = runBlocking {
        var requests = 0
        val session = SessionManager(TestSettings()).apply { accessToken = "now-test-token" }
        val responseJson = """
            {
              "availableNextActions": [],
              "scheduledToday": [],
              "overdue": [],
              "waitingForReview": []
            }
        """.trimIndent()

        val client = HttpClient(MockEngine { request ->
            requests++
            assertEquals("https://example.test/now", request.url.toString())
            assertEquals("GET", request.method.value)
            assertEquals("DPoP now-test-token", request.headers["Authorization"])
            assertTrue(!request.headers["DPoP"].isNullOrBlank())
            respond(responseJson, HttpStatusCode.OK, headersOf("Content-Type", ContentType.Application.Json.toString()))
        }) { install(ContentNegotiation) { json() } }

        try {
            val module = AppModule(
                httpClient = client,
                sessionManager = session,
                dPoPManager = DefaultDPoPManager(TestSettings()),
                baseUrl = "https://example.test/",
            ).items

            val repository = module.now
            val nowData = repository.getNowData().getOrThrow()
            assertEquals(1, requests)
            assertTrue(nowData.isEmpty)
        } finally {
            client.close()
            session.clearSession()
        }
    }
}
