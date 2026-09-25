package pl.quicktask.app.somedaymaybe.data

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
import pl.quicktask.app.testing.TestSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SomedayMaybeRepositoryTest {

    @Test
    fun convertFromInboxSendsCorrectData(): Unit = runBlocking {
        var requestBody = ""
        var method = ""
        var path = ""
        val session = SessionManager(TestSettings()).apply { accessToken = "test-token" }

        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/inbox/item-123/someday-maybe" -> {
                    method = request.method.value
                    path = request.url.encodedPath
                    requestBody = try { (request.body as TextContent).text } catch (e: Exception) { request.body.toString() }
                    println("REQUEST BODY: $requestBody")
                    respond("", HttpStatusCode.NoContent, headersOf("Content-Type", ContentType.Application.Json.toString()))
                }
                "/inbox", "/inbox/trash" -> {
                    respond("[]", HttpStatusCode.OK, headersOf("Content-Type", ContentType.Application.Json.toString()))
                }
                "/inbox/projects" -> {
                    respond("{}", HttpStatusCode.OK, headersOf("Content-Type", ContentType.Application.Json.toString()))
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

            val repository = module.somedayMaybe
            val result = repository.convertFromInbox(
                itemId = "item-123",
                tagIds = listOf("tag-1"),
                newTagNames = listOf("My Idea"),
            )

            assertTrue(result.isSuccess)
            assertEquals("POST", method)
            assertEquals("/inbox/item-123/someday-maybe", path)
            assertTrue(requestBody.contains("tagIds"))
            assertTrue(requestBody.contains("tag-1"))
            assertTrue(requestBody.contains("newTagNames"))
            assertTrue(requestBody.contains("My Idea"))
        } finally {
            client.close()
            session.clearSession()
        }
    }
}
