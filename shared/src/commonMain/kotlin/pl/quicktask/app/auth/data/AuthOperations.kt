package pl.quicktask.app.auth.data

import pl.quicktask.app.auth.model.FinishLoginResponseDto
import pl.quicktask.app.auth.model.OAuthProvidersDto
import pl.quicktask.app.auth.model.OAuthTicketResponseDto

fun interface SessionRefresher {
    suspend fun refreshSession(): Result<Unit>
}

interface AuthOperations {
    val isLoggedIn: Boolean
    suspend fun register(email: String, password: String): Result<String> = Result.failure(UnsupportedOperationException())
    suspend fun verifyRegistration(registrationId: String, code: String, email: String, password: String): Result<FinishLoginResponseDto> = Result.failure(UnsupportedOperationException())
    suspend fun login(email: String, password: String): Result<FinishLoginResponseDto>
    suspend fun oauthProviders(): OAuthProvidersDto = OAuthProvidersDto()
    suspend fun redeemOAuthTicket(ticket: String): Result<OAuthTicketResponseDto> = Result.failure(UnsupportedOperationException())
    suspend fun linkOAuthIdentity(ticket: String): Result<Unit> = Result.failure(UnsupportedOperationException())
    suspend fun unlockOAuthKeys(password: String): Result<Unit> = Result.failure(UnsupportedOperationException())
    suspend fun tryRestoreCachedKeys(): Boolean
    suspend fun logout()
}
