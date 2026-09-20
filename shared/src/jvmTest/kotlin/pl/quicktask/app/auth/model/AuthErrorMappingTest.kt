package pl.quicktask.app.auth.model

import kotlinx.serialization.json.Json
import pl.quicktask.app.auth.presentation.mapAuthErrorToState
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_access_token_expired
import todo.shared.generated.resources.error_authentication_failed
import todo.shared.generated.resources.error_invalid_dpop_proof
import todo.shared.generated.resources.error_invalid_login_request
import todo.shared.generated.resources.error_login_failed
import todo.shared.generated.resources.error_too_many_requests
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthErrorMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testApiErrorDtoParsing() {
        val jsonString = """
            {
              "statusCode": 401,
              "code": "INVALID_DPOP_PROOF",
              "message": "Authentication failed"
            }
        """.trimIndent()

        val parsed = json.decodeFromString<ApiErrorDto>(jsonString)
        assertEquals(401, parsed.statusCode)
        assertEquals("INVALID_DPOP_PROOF", parsed.code)
        assertEquals("Authentication failed", parsed.message)
    }

    @Test
    fun testApiExceptionMappingKnownCodes() {
        val codesToExpectedRes = mapOf(
            "AUTHENTICATION_FAILED" to Res.string.error_authentication_failed,
            "INVALID_DPOP_PROOF" to Res.string.error_invalid_dpop_proof,
            "INVALID_LOGIN_REQUEST" to Res.string.error_invalid_login_request,
            "ACCESS_TOKEN_EXPIRED" to Res.string.error_access_token_expired,
        )

        codesToExpectedRes.forEach { (code, expectedRes) ->
            val exception = ApiException(code = code, statusCode = 400, message = "Error")
            val (mappedRes, customMsg) = mapAuthErrorToState(exception)
            assertEquals(expectedRes, mappedRes, "Failed for code $code")
            assertEquals(null, customMsg)
        }
    }

    @Test
    fun testApiExceptionMappingFallbackStatus() {
        val statusToExpectedRes = mapOf(
            401 to Res.string.error_authentication_failed,
            400 to Res.string.error_invalid_login_request,
            429 to Res.string.error_too_many_requests,
        )

        statusToExpectedRes.forEach { (statusCode, expectedRes) ->
            val exception = ApiException(code = "UNKNOWN_CODE", statusCode = statusCode, message = "Error")
            val (mappedRes, customMsg) = mapAuthErrorToState(exception)
            assertEquals(expectedRes, mappedRes, "Failed for statusCode $statusCode")
            assertEquals(null, customMsg)
        }
    }

    @Test
    fun testUnknownErrorDetailedMessage() {
        val exception = RuntimeException("Connection refused")
        val (mappedRes, customMsg) = mapAuthErrorToState(exception)
        assertEquals(null, mappedRes)
        assertEquals("Wystąpił błąd logowania: Connection refused", customMsg)
    }

    @Test
    fun testTextStatusFallbacks() {
        val cases = mapOf(
            "HTTP 401" to Res.string.error_authentication_failed,
            "Unauthorized" to Res.string.error_authentication_failed,
            "HTTP 400" to Res.string.error_invalid_login_request,
            "HTTP 429" to Res.string.error_too_many_requests,
            "Too many requests" to Res.string.error_too_many_requests,
        )

        cases.forEach { (message, expectedRes) ->
            val (mappedRes, customMsg) = mapAuthErrorToState(RuntimeException(message))
            assertEquals(expectedRes, mappedRes, "Failed for message: $message")
            assertEquals(null, customMsg)
        }
    }

    @Test
    fun testBlankUnknownErrorUsesGenericResource() {
        listOf(null, "", "   ").forEach { message ->
            val (mappedRes, customMsg) = mapAuthErrorToState(RuntimeException(message))
            assertEquals(Res.string.error_login_failed, mappedRes)
            assertEquals(null, customMsg)
        }
    }
}
