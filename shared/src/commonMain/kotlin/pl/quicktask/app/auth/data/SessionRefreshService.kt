package pl.quicktask.app.auth.data

import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.quicktask.app.auth.model.ApiException
import pl.quicktask.app.auth.model.FinishLoginResponseDto
import pl.quicktask.app.auth.session.SessionManager
import pl.quicktask.app.common.AppLoggerManager
import pl.quicktask.app.common.coroutineResult
import pl.quicktask.app.auth.session.withBrowserSessionLock

private const val REFRESH_REUSE_WINDOW_MS = 2_000L

class SessionRefreshService(
    private val session: SessionManager,
    private val refresh: suspend (String) -> FinishLoginResponseDto,
    private val clearSession: suspend () -> Unit,
    private val clock: () -> Long = ::getTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SessionRefresher {
    private val mutex = Mutex()
    private val commitMutex = Mutex()
    private data class Reused(val access: String?, val refresh: String, val email: String?, val at: Long, val epoch: Long)
    private var reused: Reused? = null
    private val epoch = MutableStateFlow(0L)

    suspend fun reset() = commitMutex.withLock { epoch.update { it + 1 }; reused = null }

    override suspend fun refreshSession(): Result<Unit> = withContext(dispatcher) { withBrowserSessionLock {
        mutex.withLock {
            coroutineResult {
                AppLoggerManager.logRefresh("SessionRefreshService", "Rozpoczęcie odświeżania sesji")
                val refreshToken = session.refreshToken ?: error("Missing refresh token")
                val access = session.accessToken
                val email = session.userEmail
                val version = epoch.value
                val now = clock()
                val previous = reused
                if (previous != null && previous.epoch == version && previous.access == access &&
                    previous.refresh == refreshToken && previous.email == email &&
                    now - previous.at in 0 until REFRESH_REUSE_WINDOW_MS) {
                    AppLoggerManager.logRefresh("SessionRefreshService", "Użyto ponownie ostatnio odświeżonego tokena")
                    return@coroutineResult Unit
                }
                val tokens = try {
                    refresh(refreshToken)
                } catch (error: ApiException) {
                    if (epoch.value == version && session.accessToken == access &&
                        session.refreshToken == refreshToken && session.userEmail == email) clearSession()
                    AppLoggerManager.logRefresh("SessionRefreshService", "Błąd odświeżania sesji: ${error.message}")
                    throw error
                }
                commitMutex.withLock {
                    check(epoch.value == version && session.accessToken == access &&
                        session.refreshToken == refreshToken && session.userEmail == email) { "Session changed during refresh" }
                    session.saveSession(tokens.accessToken, tokens.refreshToken, email.orEmpty())
                    reused = Reused(tokens.accessToken, tokens.refreshToken, email, clock(), version)
                }
                AppLoggerManager.logRefresh("SessionRefreshService", "Odświeżenie sesji zakończone sukcesem")
                Unit
            }
        }
    }
    }
}
