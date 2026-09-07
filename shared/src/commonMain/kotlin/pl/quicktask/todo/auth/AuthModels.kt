package pl.quicktask.todo.auth

import kotlinx.serialization.Serializable

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
    val refreshToken: String,
    val tokenType: String,
    val accessTokenExpiresIn: Long,
    val refreshTokenExpiresIn: Long,
)
