package pl.quicktask.app.auth

import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.StringResource
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
            val (mappedRes, customMsg) = mapErrorToState(exception)
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
            val (mappedRes, customMsg) = mapErrorToState(exception)
            assertEquals(expectedRes, mappedRes, "Failed for statusCode $statusCode")
            assertEquals(null, customMsg)
        }
    }

    @Test
    fun testUnknownErrorDetailedMessage() {
        val exception = RuntimeException("Connection refused")
        val (mappedRes, customMsg) = mapErrorToState(exception)
        assertEquals(null, mappedRes)
        assertEquals("Wystąpił błąd logowania: Connection refused", customMsg)
    }

    private fun mapErrorToState(error: Throwable): Pair<StringResource?, String?> {
        val apiException = error as? ApiException
        val code = apiException?.code

        when (code) {
            "AUTHENTICATION_FAILED" -> return Pair(Res.string.error_authentication_failed, null)
            "INVALID_DPOP_PROOF" -> return Pair(Res.string.error_invalid_dpop_proof, null)
            "INVALID_LOGIN_REQUEST" -> return Pair(Res.string.error_invalid_login_request, null)
            "ACCESS_TOKEN_EXPIRED" -> return Pair(Res.string.error_access_token_expired, null)
        }

        val statusCode = apiException?.statusCode ?: run {
            val msg = error.message ?: ""
            when {
                msg.contains("401") || msg.contains("Unauthorized") -> 401
                msg.contains("400") -> 400
                msg.contains("429") || msg.contains("Too many requests") -> 429
                else -> null
            }
        }

        return when (statusCode) {
            401 -> Pair(Res.string.error_authentication_failed, null)
            400 -> Pair(Res.string.error_invalid_login_request, null)
            429 -> Pair(Res.string.error_too_many_requests, null)
            else -> {
                val detail = error.message?.takeIf { it.isNotBlank() }
                if (detail != null) {
                    Pair(null, "Wystąpił błąd logowania: $detail")
                } else {
                    Pair(Res.string.error_login_failed, null)
                }
            }
        }
    }
}
