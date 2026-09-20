package pl.quicktask.app.auth.model

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.*
import pl.quicktask.app.auth.crypto.*
import pl.quicktask.app.auth.session.*
import pl.quicktask.app.di.AppModule
import pl.quicktask.app.items.support.*
import pl.quicktask.app.testing.TestSettings
import kotlin.test.*

class AuthCancellationAndCompositionTest {
    @Test
    fun loginAndRegistrationDoNotConvertCancellationToFailure(): Unit = runBlocking {
        val opaque = object : OpaqueManager by DefaultOpaqueManager() {
            override suspend fun startLogin(password: String): OpaqueStartResult = throw CancellationException("login cancelled")
            override suspend fun startRegistration(password: String): OpaqueRegisterStartResult = throw CancellationException("registration cancelled")
        }
        val client = mockClient(MockEngine { error("Must not send") })
        try {
            val settings = TestSettings()
            val auth = AppModule(client, SessionManager(settings), RecordingDPoP(), opaque, KeyCache(settings)).auth
            assertFailsWith<CancellationException> { auth.login("a@b.pl", "password") }
            assertFailsWith<CancellationException> { auth.register("a@b.pl", "password") }
        } finally { client.close() }
    }

    @Test
    fun refreshPropagatesCancellation(): Unit = runBlocking {
        val client = mockClient(MockEngine { throw CancellationException("refresh cancelled") })
        try {
            val settings = TestSettings()
            val session = SessionManager(settings).apply { refreshToken = "refresh" }
            val auth = AppModule(client, session, RecordingDPoP(), DefaultOpaqueManager(), KeyCache(settings)).auth
            assertFailsWith<CancellationException> { auth.refreshSession() }
        } finally { client.close() }
    }

    @Test
    fun applicationAndItemRequestsShareRefreshLockAndSession(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var refreshes = 0
        val client = mockClient(MockEngine { request ->
            if (request.url.encodedPath == "/auth/refresh") {
                refreshes++
                entered.complete(Unit)
                release.await()
                respond("""{"accessToken":"new","refreshToken":"new-refresh","tokenType":"DPoP","accessTokenExpiresIn":600,"refreshTokenExpiresIn":1000}""", headers = jsonHeaders)
            } else if (request.headers["Authorization"] == "DPoP old") {
                respond("expired", io.ktor.http.HttpStatusCode(498, "Expired"), jsonHeaders)
            } else {
                assertEquals("DPoP new", request.headers["Authorization"])
                respond("[]", headers = jsonHeaders)
            }
        })
        try {
            val settings = TestSettings()
            val session = SessionManager(settings).apply { saveSession("old", "refresh", "a@b.pl") }
            val module = AppModule(client, session, RecordingDPoP(), DefaultOpaqueManager(), KeyCache(settings), "https://example.test")
            val refresh = async { module.auth.refreshSession().getOrThrow() }
            entered.await()
            val request = async(start = CoroutineStart.UNDISPATCHED) { module.items.api.request(io.ktor.http.HttpMethod.Get, "inbox") }
            release.complete(Unit)
            refresh.await()
            request.await()
            assertEquals(1, refreshes)
            assertSame(session, module.sessionManager)
            assertEquals("new", session.accessToken)
        } finally { client.close() }
    }
}
