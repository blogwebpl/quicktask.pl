package pl.quicktask.app.auth.model

import kotlinx.serialization.Serializable

@Serializable
data class StartRegistrationRequestDto(
    val email: String,
    val registrationRequest: String,
)

@Serializable
data class StartRegistrationResponseDto(
    val registrationResponse: String,
)

@Serializable
data class FinishRegistrationRequestDto(
    val email: String,
    val registrationRecord: String,
    val publicKey: String,
    val encryptedPrivateKey: String,
    val privateKeyNonce: String,
)

@Serializable
data class FinishRegistrationResponseDto(val registrationId: String)

@Serializable
data class VerifyRegistrationRequestDto(
    val registrationId: String,
    val verificationCode: String,
)

@Serializable
data class StartLoginRequestDto(
    val email: String,
    val startLoginRequest: String,
)

@Serializable
data class StartLoginResponseDto(
    val loginResponse: String,
    val loginSessionId: String,
)

@Serializable
data class FinishLoginRequestDto(
    val loginSessionId: String,
    val finishLoginRequest: String,
)

@Serializable
data class FinishLoginResponseDto(
    val accessToken: String,
    val refreshToken: String = "",
    val tokenType: String,
    val accessTokenExpiresIn: Long,
    val refreshTokenExpiresIn: Long,
)

@Serializable
data class RefreshTokenRequestDto(
    val refreshToken: String,
)

@Serializable
data class ApiErrorDto(
    val statusCode: Int? = null,
    val code: String? = null,
    val message: String? = null,
)

class ApiException(
    val code: String?,
    val statusCode: Int,
    override val message: String?,
) : Exception(message ?: "HTTP $statusCode (${code ?: "no_code"})")

@Serializable
data class OAuthProvidersDto(val google: Boolean = false, val apple: Boolean = false)

@Serializable
data class OAuthTicketRequestDto(val ticket: String)

@Serializable
data class OAuthTicketResponseDto(
    val email: String,
    val linked: Boolean = false,
    val linkTicket: String? = null,
    val accessToken: String? = null,
    val refreshToken: String = "",
)

@Serializable
data class OAuthLinkRequestDto(val ticket: String)

