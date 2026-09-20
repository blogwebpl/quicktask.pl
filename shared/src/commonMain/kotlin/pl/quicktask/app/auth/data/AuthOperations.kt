package pl.quicktask.app.auth.data

import pl.quicktask.app.auth.model.FinishLoginResponseDto

fun interface SessionRefresher {
    suspend fun refreshSession(): Result<Unit>
}

interface AuthOperations {
    val isLoggedIn: Boolean
    suspend fun login(email: String, password: String): Result<FinishLoginResponseDto>
    suspend fun tryRestoreCachedKeys(): Boolean
    suspend fun logout()
}
