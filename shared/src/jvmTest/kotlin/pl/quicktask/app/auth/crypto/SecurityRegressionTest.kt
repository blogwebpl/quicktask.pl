package pl.quicktask.app.auth.crypto

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import pl.quicktask.app.auth.session.*
import pl.quicktask.app.di.AppModule
import pl.quicktask.app.items.support.*
import pl.quicktask.app.testing.TestSettings
import kotlin.test.*

class SecurityRegressionTest {
    private fun jwk(proof: String) = Json.parseToJsonElement(proof.substringBefore('.').fromBase64Url().decodeToString()).jsonObject["jwk"]

    @Test fun parallelProofsUseOneKeyAndUniqueIds() = runBlocking {
        val manager = DefaultDPoPManager(TestSettings())
        val proofs = (1..40).map { async(Dispatchers.Default) { manager.generateDPoPProof("GET", "https://example.test/inbox") } }.awaitAll()
        assertEquals(1, proofs.map(::jwk).distinct().size)
        assertEquals(40, proofs.map { Json.parseToJsonElement(it.split('.')[1].fromBase64Url().decodeToString()).jsonObject["jti"] }.distinct().size)
    }

    @Test fun failedPersistenceKeepsSameMemoryKey() = runBlocking {
        val settings = object : com.russhwolf.settings.Settings by TestSettings() {
            override fun putString(key: String, value: String) { error("unavailable") }
        }
        val manager = DefaultDPoPManager(settings)
        assertEquals(jwk(manager.generateDPoPProof("GET", "https://example.test/a")), jwk(manager.generateDPoPProof("GET", "https://example.test/b")))
    }

    @Test fun logoutProofUsesSessionKeyAndClearsBeforeNetwork() = runBlocking {
        val settings = TestSettings()
        val session = SessionManager(settings).apply { saveSession("access", "refresh", "test@example.test") }
        val manager = DefaultDPoPManager(settings)
        val original = jwk(manager.generateDPoPProof("GET", "https://example.test/inbox", "access"))
        var sent = false
        val client = mockClient(MockEngine { request ->
            sent = true
            assertFalse(session.isLoggedIn)
            assertEquals(original, jwk(request.headers["DPoP"]!!))
            assertEquals("DPoP access", request.headers["Authorization"])
            respond("", io.ktor.http.HttpStatusCode.NoContent)
        })
        try {
            AppModule(client, session, manager, createOpaqueManager(), KeyCache(settings), "https://example.test").auth.logout()
            assertTrue(sent)
            assertNull(session.refreshToken)
            assertNotEquals(original, jwk(manager.generateDPoPProof("GET", "https://example.test/inbox")))
        } finally { client.close() }
    }
}
