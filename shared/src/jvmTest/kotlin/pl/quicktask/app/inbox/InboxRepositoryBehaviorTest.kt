package pl.quicktask.app.inbox



import pl.quicktask.app.items.di.ItemModule

import pl.quicktask.app.testing.TestSettings

import pl.quicktask.app.inbox.data.*
import pl.quicktask.app.trash.model.*

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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InboxRepositoryBehaviorTest {
    @Test
    fun emptyInboxUsesCacheUntilInvalidated(): Unit = runBlocking {
        var requests = 0
        val session = SessionManager(TestSettings()).apply { accessToken = "behavior-test" }
        val client = HttpClient(MockEngine { request ->
            requests++
            assertEquals("https://example.test/inbox", request.url.toString())
            assertEquals("GET", request.method.value)
            assertEquals("DPoP behavior-test", request.headers["Authorization"])
            assertTrue(!request.headers["DPoP"].isNullOrBlank())
            respond("[]", HttpStatusCode.OK, headersOf("Content-Type", ContentType.Application.Json.toString()))
        }) { install(ContentNegotiation) { json() } }
        try {
            val module = pl.quicktask.app.di.AppModule(
                httpClient = client, sessionManager = session,
                dPoPManager = DefaultDPoPManager(TestSettings()), baseUrl = "https://example.test/",
            ).items
            val repository = module.inbox
            assertEquals(emptyList(), repository.getItems().getOrThrow())
            repository.getItems().getOrThrow()
            assertEquals(1, requests)
            module.store.invalidateCache()
            repository.getItems().getOrThrow()
            assertEquals(2, requests)
            repository.getItems(forceFetch = true).getOrThrow()
            assertEquals(3, requests)
        } finally {
            client.close()
            session.clearSession()
        }
    }
}
