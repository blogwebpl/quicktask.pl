package pl.quicktask.app.auth.model

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import pl.quicktask.app.auth.crypto.DefaultOpaqueManager
import pl.quicktask.app.auth.crypto.generateUserKeyPair
import pl.quicktask.app.auth.crypto.OpaqueManager
import pl.quicktask.app.auth.crypto.OpaqueRegisterFinishResult
import pl.quicktask.app.auth.crypto.OpaqueRegisterStartResult
import pl.quicktask.app.auth.crypto.OpaqueStartResult
import pl.quicktask.app.auth.crypto.OpaqueFinishResult
import pl.quicktask.app.auth.crypto.UserKeyMaterialDto
import pl.quicktask.app.auth.crypto.toBase64
import pl.quicktask.app.auth.crypto.toBase64Url
import pl.quicktask.app.auth.crypto.wrapPrivateKey
import kotlinx.serialization.encodeToString
import pl.quicktask.app.auth.session.KeyCache
import pl.quicktask.app.auth.session.SessionManager
import pl.quicktask.app.common.AppLogger
import pl.quicktask.app.di.AppModule
import pl.quicktask.app.items.support.*
import pl.quicktask.app.testing.TestSettings
import kotlin.test.*

class ModuleIsolationAndLoggingTest {
    @Test
    fun registrationNeverSendsPasswordToServer() = runBlocking {
        val password = "client-only-password"
        val exportKey = ByteArray(64) { it.toByte() }.toBase64Url()
        val requestBodies = mutableListOf<String>()
        val opaque = object : OpaqueManager by DefaultOpaqueManager() {
            override suspend fun startRegistration(password: String): OpaqueRegisterStartResult {
                assertEquals("client-only-password", password)
                return OpaqueRegisterStartResult("opaque-registration-request", "client-state")
            }

            override suspend fun finishRegistration(
                password: String,
                clientRegistrationState: String,
                registrationResponse: String,
                email: String,
                serverOrigin: String,
            ): OpaqueRegisterFinishResult {
                assertEquals("client-only-password", password)
                return OpaqueRegisterFinishResult("opaque-registration-record", exportKey)
            }
        }
        val client = mockClient(MockEngine { request ->
            requestBodies += (request.body as TextContent).text
            when (request.url.encodedPath) {
                "/auth/register/start" -> respond(
                    """{"registrationResponse":"opaque-server-response"}""",
                    headers = jsonHeaders,
                )
                "/auth/register/finish" -> respond(
                    """{"registrationId":"registration-id"}""",
                    HttpStatusCode.Created,
                    jsonHeaders,
                )
                else -> error("Unexpected endpoint")
            }
        })
        try {
            val module = AppModule(
                client,
                SessionManager(TestSettings()),
                RecordingDPoP(),
                opaque,
                KeyCache(TestSettings()),
                "https://example.test",
            )

            assertEquals("registration-id", module.auth.register("user@example.test", password).getOrThrow())
            assertEquals(2, requestBodies.size)
            assertFalse(requestBodies.any { password in it })
            assertFalse(requestBodies.any { "\"password\"" in it })
        } finally {
            client.close()
        }
    }

    @Test
    fun loginRecoversKeysAndCacheThenLogoutClearsBoth() = runBlocking {
        val pair = generateUserKeyPair()
        val exportKey = ByteArray(64) { it.toByte() }.toBase64Url()
        val wrapped = wrapPrivateKey(exportKey, assertNotNull(pair.privateKeyPkcs8))
        val material = UserKeyMaterialDto(pair.publicKeySpki.toBase64(), wrapped.ciphertext, wrapped.nonce)
        val opaque = object : OpaqueManager by DefaultOpaqueManager() {
            override suspend fun startLogin(password: String) = OpaqueStartResult("request", "state")
            override suspend fun finishLogin(password: String, clientLoginState: String, loginResponse: String, email: String, serverOrigin: String): OpaqueFinishResult {
                assertEquals("user@example.test", email)
                assertEquals("https://example.test", serverOrigin)
                return OpaqueFinishResult("finish", exportKey)
            }
        }
        val client = mockClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/auth/login/start" -> respond("""{"loginResponse":"response","loginSessionId":"session"}""", headers = jsonHeaders)
                "/auth/login/finish" -> respond(testJson.encodeToString(FinishLoginResponseDto("access", "refresh", "DPoP", 45, 90)), headers = jsonHeaders)
                "/users/me/key" -> {
                    assertEquals("DPoP access", request.headers["Authorization"])
                    respond(testJson.encodeToString(material), headers = jsonHeaders)
                }
                "/auth/logout" -> respond("", HttpStatusCode.NoContent)
                else -> error("Unexpected endpoint")
            }
        })
        try {
            val settings = TestSettings()
            val session = SessionManager(settings)
            val cache = KeyCache(settings)
            val module = AppModule(client, session, RecordingDPoP(), opaque, cache, "https://example.test")
            module.auth.login(" User@Example.Test ", "password").getOrThrow()
            assertEquals("user@example.test", session.userEmail)
            assertContentEquals(pair.publicKeySpki, assertNotNull(module.keyStore.getSnapshot()).publicKeySpki)
            module.keyStore.set(null)
            assertTrue(module.auth.tryRestoreCachedKeys())
            assertContentEquals(pair.publicKeySpki, assertNotNull(module.keyStore.getSnapshot()).publicKeySpki)
            module.auth.logout()
            assertFalse(session.isLoggedIn)
            assertNull(module.keyStore.getSnapshot())
            assertFalse(module.auth.tryRestoreCachedKeys())
        } finally { client.close() }
    }

    @Test
    fun logoutClearsOnlyItsModuleAndLogsNoResponseOrToken() = runBlocking {
        val events = mutableListOf<String>()
        val logger = AppLogger { level, operation, status, code -> events.add("$level $operation $status $code") }
        val client = mockClient(MockEngine { respond("sensitive-response", HttpStatusCode.BadRequest) })
        try {
            fun module(email: String): AppModule {
                val settings = TestSettings()
                val session = SessionManager(settings).apply { saveSession("sensitive-token", "sensitive-refresh", email) }
                return AppModule(client, session, RecordingDPoP(), DefaultOpaqueManager(), KeyCache(settings), "https://example.test", logger = logger)
            }
            val first = module("first")
            val second = module("second")
            first.keyStore.set(generateUserKeyPair())
            val secondKeys = generateUserKeyPair()
            second.keyStore.set(secondKeys)
            first.auth.logout()
            assertNull(first.keyStore.getSnapshot())
            assertSame(secondKeys, second.keyStore.getSnapshot())
            assertTrue(second.sessionManager.isLoggedIn)
            assertFalse(first.sessionManager.isLoggedIn)
            assertEquals(listOf("WARNING auth.logout 400 null"), events)
            assertFalse(events.any { "sensitive" in it })
        } finally { client.close() }
    }
}
