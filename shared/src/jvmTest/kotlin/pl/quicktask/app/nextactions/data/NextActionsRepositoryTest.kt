package pl.quicktask.app.nextactions.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import pl.quicktask.app.auth.crypto.DefaultDPoPManager
import pl.quicktask.app.auth.session.SessionManager
import pl.quicktask.app.di.AppModule
import pl.quicktask.app.nextactions.model.NewContextInput
import pl.quicktask.app.testing.TestSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NextActionsRepositoryTest {

    @Test
    fun convertFromInboxSendsWhitelistedFieldsOnly(): Unit = runBlocking {
        var requestBody = ""
        val session = SessionManager(TestSettings()).apply { accessToken = "test-token" }

        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/inbox/item-123/next-action" -> {
                    assertEquals("POST", request.method.value)
                    requestBody = (request.body as TextContent).text
                    respond("", HttpStatusCode.NoContent, headersOf("Content-Type", ContentType.Application.Json.toString()))
                }
                "/inbox" -> {
                    respond("[]", HttpStatusCode.OK, headersOf("Content-Type", ContentType.Application.Json.toString()))
                }
                "/inbox/trash" -> {
                    respond("[]", HttpStatusCode.OK, headersOf("Content-Type", ContentType.Application.Json.toString()))
                }
                else -> error("Unexpected URL: ${request.url.encodedPath}")
            }
        }) { install(ContentNegotiation) { json() } }

        try {
            val module = AppModule(
                httpClient = client,
                sessionManager = session,
                dPoPManager = DefaultDPoPManager(TestSettings()),
                baseUrl = "https://example.test/",
            ).items

            val repository = module.nextActions
            val result = repository.convertFromInbox(
                itemId = "item-123",
                projectId = "550e8400-e29b-41d4-a716-446655440000",
                dueAt = null,
                contextIds = emptyList(),
                newContextNames = listOf("Work"),
                newContexts = listOf(NewContextInput("Home")),
                tagIds = emptyList(),
                newTagNames = emptyList(),
            )

            if (result.isFailure) {
                println("EXCEPTION: ${result.exceptionOrNull()}")
            }
            assertTrue(result.isSuccess)
            // Ensure "newContexts" is NOT present in the JSON body, as NestJS ValidationPipe forbidNonWhitelisted rejects it.
            assertFalse(requestBody.contains("newContexts"))
            assertTrue(requestBody.contains("newContextNames"))
            assertTrue(requestBody.contains("Work"))
            assertTrue(requestBody.contains("Home"))
            assertTrue(requestBody.contains("550e8400-e29b-41d4-a716-446655440000"))
        } finally {
            client.close()
            session.clearSession()
        }
    }
}
