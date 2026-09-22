package pl.quicktask.app.auth.presentation

import org.jetbrains.compose.resources.StringResource
import pl.quicktask.app.auth.model.ApiException
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.error_access_token_expired
import todo.shared.generated.resources.error_authentication_failed
import todo.shared.generated.resources.error_invalid_dpop_proof
import todo.shared.generated.resources.error_invalid_login_request
import todo.shared.generated.resources.error_login_failed
import todo.shared.generated.resources.error_too_many_requests

internal fun mapAuthErrorToState(error: Throwable): Pair<StringResource?, String?> {
    val apiException = error as? ApiException

    when (apiException?.code) {
        "AUTHENTICATION_FAILED" -> return Pair(Res.string.error_authentication_failed, null)
        "INVALID_DPOP_PROOF" -> return Pair(Res.string.error_invalid_dpop_proof, null)
        "INVALID_LOGIN_REQUEST" -> return Pair(Res.string.error_invalid_login_request, null)
        "ACCESS_TOKEN_EXPIRED" -> return Pair(Res.string.error_access_token_expired, null)
    }

    val statusCode = apiException?.statusCode ?: error.message.toStatusCode()
    return when (statusCode) {
        401 -> Pair(Res.string.error_authentication_failed, null)
        400 -> Pair(Res.string.error_invalid_login_request, null)
        429 -> Pair(Res.string.error_too_many_requests, null)
        else -> {
            val details = error.message?.trim().orEmpty()
            if (details.isNotEmpty()) {
                Pair(null, "Wystąpił błąd logowania: $details")
            } else {
                Pair(Res.string.error_login_failed, null)
            }
        }
    }
}

private fun String?.toStatusCode(): Int? = when {
    this == null -> null
    contains("401") || contains("Unauthorized") -> 401
    contains("400") -> 400
    contains("429") || contains("Too many requests") -> 429
    else -> null
}
