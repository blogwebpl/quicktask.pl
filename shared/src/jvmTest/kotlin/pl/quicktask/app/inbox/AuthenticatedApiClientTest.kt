package pl.quicktask.app.inbox

import pl.quicktask.app.items.support.jsonHeaders
import pl.quicktask.app.items.support.RecordingDPoP
import pl.quicktask.app.items.support.mockClient

import pl.quicktask.app.items.model.CreateInboxItemRequestDto
import pl.quicktask.app.items.model.MissingSessionException

import pl.quicktask.app.testing.TestSettings

import pl.quicktask.app.trash.model.*

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import pl.quicktask.app.auth.model.ApiException
import pl.quicktask.app.auth.session.SessionManager
import pl.quicktask.app.network.client.AuthenticatedApiClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AuthenticatedApiClientTest {
    @Test
    fun expiredTokenIsRetriedOnceWithNewTokenProofAndSamePayload(): Unit = runBlocking {
        val session = SessionManager(TestSettings()).apply { accessToken = "old" }
        val proofs = RecordingDPoP()
        var requests = 0
        var refreshes = 0
        val client = mockClient(MockEngine { request ->
            requests++
            assertEquals("https://example.test/inbox", request.url.toString())
            assertEquals("POST", request.method.value)
            assertEquals("DPoP ${if (requests == 1) "old" else "new"}", request.headers["Authorization"])
            assertEquals("proof-$requests", request.headers["DPoP"])
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            assertTrue(body.contains("encryptedTitle"))
            respond("{}", HttpStatusCode.fromValue(498), jsonHeaders)
        })
        try {
            val api = AuthenticatedApiClient(client, proofs, session, {
                refreshes++
                session.accessToken = "new"
                Result.success(Unit)
            }, "https://example.test/")
            val error = assertFailsWith<ApiException> {
                api.request(io.ktor.http.HttpMethod.Post, "/inbox") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateInboxItemRequestDto("encryptedTitle", "encryptedKey"))
                }
            }
            assertEquals(498, error.statusCode)
            assertEquals(2, requests)
            assertEquals(1, refreshes)
            assertEquals(listOf("old", "new"), proofs.calls.map { it.third })
        } finally { client.close() }
    }

    @Test
    fun successfulRetryReturnsResponse(): Unit = runBlocking {
        var requests = 0
        val client = mockClient(MockEngine {
            requests++
            respond("{}", if (requests == 1) HttpStatusCode.fromValue(498) else HttpStatusCode.OK, jsonHeaders)
        })
        try {
            val session = SessionManager(TestSettings()).apply { accessToken = "old" }
            val api = AuthenticatedApiClient(client, RecordingDPoP(), session, {
                session.accessToken = "new"
                Result.success(Unit)
            }, "https://example.test")
            assertEquals(HttpStatusCode.OK, api.request(io.ktor.http.HttpMethod.Get, "inbox").status)
            assertEquals(2, requests)
        } finally { client.close() }
    }

    @Test
    fun failedRefreshDoesNotRetryAndCancellationIsPropagated(): Unit = runBlocking {
        var requests = 0
        val client = mockClient(MockEngine {
            requests++
            respond("{}", HttpStatusCode.fromValue(498), jsonHeaders)
        })
        try {
            val session = SessionManager(TestSettings()).apply { accessToken = "token" }
            val api = AuthenticatedApiClient(client, RecordingDPoP(), session, { Result.failure(IllegalStateException()) })
            assertFailsWith<ApiException> { api.request(io.ktor.http.HttpMethod.Get, "inbox") }
            assertEquals(1, requests)
            val cancelling = AuthenticatedApiClient(client, RecordingDPoP(), session, { Result.failure(CancellationException("cancel")) })
            assertFailsWith<CancellationException> { cancelling.request(io.ktor.http.HttpMethod.Get, "inbox") }
            assertEquals(2, requests)
        } finally { client.close() }
    }

    @Test
    fun serverCodesAndPlainErrorsBecomeApiExceptions(): Unit = runBlocking {
        var requests = 0
        val client = mockClient(MockEngine {
            requests++
            respond(if (requests == 1) """{"code":"INVALID_DPOP_PROOF","message":"server detail"}""" else "plain error",
                HttpStatusCode.BadRequest, jsonHeaders)
        })
        try {
            val session = SessionManager(TestSettings()).apply { accessToken = "token" }
            val api = AuthenticatedApiClient(client, RecordingDPoP(), session, { error("Must not refresh") })
            val structured = assertFailsWith<ApiException> { api.request(io.ktor.http.HttpMethod.Get, "inbox") }
            assertEquals("INVALID_DPOP_PROOF", structured.code)
            assertEquals("server detail", structured.message)
            val plain = assertFailsWith<ApiException> { api.request(io.ktor.http.HttpMethod.Delete, "inbox/item") }
            assertEquals(400, plain.statusCode)
            assertEquals("plain error", plain.message)
        } finally { client.close() }
    }

    @Test
    fun missingSessionDoesNotSendRequest(): Unit = runBlocking {
        val client = mockClient(MockEngine { error("Must not send") })
        try {
            val api = AuthenticatedApiClient(client, RecordingDPoP(), SessionManager(TestSettings()), { error("Must not refresh") })
            assertFailsWith<MissingSessionException> { api.request(io.ktor.http.HttpMethod.Get, "inbox") }
        } finally { client.close() }
    }
}
