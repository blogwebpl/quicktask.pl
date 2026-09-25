package pl.quicktask.app.references.data

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
import pl.quicktask.app.items.support.encryptedFixture
import pl.quicktask.app.references.model.CreateReferenceRequestDto
import pl.quicktask.app.items.support.testJson
import pl.quicktask.app.testing.TestSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReferenceRepositoryTest {
    @Test
    fun createReferenceSendsEncryptedReferencePayload(): Unit = runBlocking {
        val fixture = encryptedFixture()
        var requestBody = ""
        val client = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/items" -> {
                    assertEquals("POST", request.method.value)
                    requestBody = (request.body as TextContent).text
                    respond("{\"itemId\":\"550e8400-e29b-41d4-a716-446655440000\",\"createdAt\":\"2026-09-25T12:00:00.000Z\"}",
                        HttpStatusCode.Created, headersOf("Content-Type", ContentType.Application.Json.toString()))
                }
                "/inbox/references", "/inbox/trash" ->
                    respond("[]", HttpStatusCode.OK, headersOf("Content-Type", ContentType.Application.Json.toString()))
                "/inbox/projects" ->
                    respond("{}", HttpStatusCode.OK, headersOf("Content-Type", ContentType.Application.Json.toString()))
                else -> error("Unexpected URL: ${request.url.encodedPath}")
            }
        }) { install(ContentNegotiation) { json() } }
        try {
            val result = fixture.module(client).references.createReference("Private title", "Private note")
            assertEquals("550e8400-e29b-41d4-a716-446655440000", result.getOrThrow())
            val body = testJson.decodeFromString<CreateReferenceRequestDto>(requestBody)
            assertEquals("REFERENCE", body.type)
            assertTrue(body.encryptedTitle.isNotBlank())
            assertTrue(body.encryptedTitle != "Private title")
            assertTrue(body.encryptedNote != "Private note")
            assertTrue(body.encryptedItemKey.isNotBlank())
        } finally {
            client.close()
        }
    }

    @Test
    fun referenceMutationsUseSpecifiedEndpointsAndTagBodies(): Unit = runBlocking {
        val itemId = "550e8400-e29b-41d4-a716-446655440000"
        val tagId = "7b4e8a90-3c2d-4d5e-8f60-123456789abc"
        val requests = mutableListOf<Pair<String, String>>()
        val bodies = mutableMapOf<String, String>()
        val session = SessionManager(TestSettings()).apply { accessToken = "test-token" }
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            requests += request.method.value to path
            if (request.body is TextContent) bodies[path] = (request.body as TextContent).text
            when (path) {
                "/inbox/references", "/inbox", "/inbox/trash" ->
                    respond("[]", HttpStatusCode.OK, headersOf("Content-Type", ContentType.Application.Json.toString()))
                "/inbox/projects" ->
                    respond("{}", HttpStatusCode.OK, headersOf("Content-Type", ContentType.Application.Json.toString()))
                "/inbox/$itemId/reference", "/inbox/$itemId/reference/restore-to-inbox", "/inbox/$itemId/tags" ->
                    respond("", HttpStatusCode.NoContent)
                else -> error("Unexpected URL: $path")
            }
        }) { install(ContentNegotiation) { json() } }

        try {
            val references = AppModule(
                httpClient = client,
                sessionManager = session,
                dPoPManager = DefaultDPoPManager(TestSettings()),
                baseUrl = "https://example.test/",
            ).items.references

            assertTrue(references.convertFromInbox(itemId, listOf(tagId), listOf("Documentation")).isSuccess)
            assertTrue(references.updateTags(itemId, emptyList(), emptyList()).isSuccess)
            assertTrue(references.restoreToInbox(itemId).isSuccess)
            assertTrue(references.deleteReference(itemId).isSuccess)

            assertTrue(("POST" to "/inbox/$itemId/reference") in requests)
            assertTrue(("PUT" to "/inbox/$itemId/tags") in requests)
            assertTrue(("POST" to "/inbox/$itemId/reference/restore-to-inbox") in requests)
            assertTrue(("DELETE" to "/inbox/$itemId/reference") in requests)
            assertTrue(bodies["/inbox/$itemId/reference"].orEmpty().contains(tagId))
            assertTrue(bodies["/inbox/$itemId/reference"].orEmpty().contains("Documentation"))
            assertEquals(true, bodies["/inbox/$itemId/tags"].orEmpty().contains("\"tagIds\":[]"))
        } finally {
            client.close()
            session.clearSession()
        }
    }
}
