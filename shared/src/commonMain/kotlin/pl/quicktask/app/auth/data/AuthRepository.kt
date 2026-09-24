package pl.quicktask.app.auth.data

import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import pl.quicktask.app.auth.crypto.*
import pl.quicktask.app.auth.model.*
import pl.quicktask.app.auth.session.*
import pl.quicktask.app.common.AppLogger
import pl.quicktask.app.common.LogLevel
import pl.quicktask.app.common.coroutineResult

class AuthRepository internal constructor(
    private val api: AuthApiClient,
    private val dPoPManager: DPoPManager,
    private val opaqueManager: OpaqueManager,
    private val sessionManager: SessionManager,
    private val keys: UserKeyService,
    private val refresher: SessionRefreshService,
    private val baseUrl: String,
    private val logger: AppLogger,
    private val dispatcher: CoroutineDispatcher,
) : AuthOperations, SessionRefresher {

    override val isLoggedIn: Boolean
        get() = sessionManager.isLoggedIn || (browserSessions && browserRememberedEmail().isNotBlank())

    override suspend fun register(email: String, password: String): Result<String> = withContext(dispatcher) {
        coroutineResult {
            refresher.reset()
            dPoPManager.clearKeyPair()
            val cleanEmail = email.trim().lowercase()
            val opaqueStart = opaqueManager.startRegistration(password)
            try {
            val startResponse = api.startRegistration(
                StartRegistrationRequestDto(cleanEmail, opaqueStart.registrationRequest),
            )
            val opaqueFinish = opaqueManager.finishRegistration(
                password = password,
                clientRegistrationState = opaqueStart.clientRegistrationState,
                registrationResponse = startResponse.registrationResponse,
                email = cleanEmail,
                serverOrigin = baseUrl.trimEnd('/'),
            )
            val userPair = generateUserKeyPair()
            val privateKey = userPair.privateKeyPkcs8 ?: error("Nie można wyeksportować klucza prywatnego")
            val wrapped = wrapPrivateKey(opaqueFinish.exportKey, privateKey)
            val pending = api.finishRegistration(
                FinishRegistrationRequestDto(
                    email = cleanEmail,
                    registrationRecord = opaqueFinish.registrationRecord,
                    publicKey = userPair.publicKeySpki.toBase64(),
                    encryptedPrivateKey = wrapped.ciphertext,
                    privateKeyNonce = wrapped.nonce,
                ),
            )
            pending.registrationId
            } finally { opaqueManager.discardState(opaqueStart.clientRegistrationState) }
        }
    }

    override suspend fun verifyRegistration(registrationId: String, code: String, email: String, password: String): Result<FinishLoginResponseDto> =
        withContext(dispatcher) {
            coroutineResult {
                api.verifyRegistration(VerifyRegistrationRequestDto(registrationId, code.trim().lowercase()))
                login(email, password).getOrThrow()
            }
        }

    override suspend fun login(email: String, password: String): Result<FinishLoginResponseDto> =
        withContext(dispatcher) {
            coroutineResult { withBrowserSessionLock {
                if (browserSessions) browserCall("migrate")
                refresher.reset()
                dPoPManager.clearKeyPair()
                val cleanEmail = email.trim().lowercase()
                val opaqueStart = opaqueManager.startLogin(password)
                try {
                val startResponse = startLogin(cleanEmail, opaqueStart.startLoginRequest)
                val opaqueFinish = opaqueManager.finishLogin(
                    password = password,
                    clientLoginState = opaqueStart.clientLoginState,
                    loginResponse = startResponse.loginResponse,
                    email = cleanEmail,
                    serverOrigin = baseUrl.trimEnd('/'),
                )
                val finishResponse = finishLogin(
                    startResponse.loginSessionId,
                    opaqueFinish.finishLoginRequest,
                )
                if (browserSessions) browserCall("email", "email" to cleanEmail)
                sessionManager.saveSession(finishResponse.accessToken, finishResponse.refreshToken, cleanEmail)
                recoverKeys(opaqueFinish.exportKey, finishResponse.accessToken)
                finishResponse
                } catch (error: Throwable) {
                clearLocalSession()
                throw error
                } finally { opaqueManager.discardState(opaqueStart.clientLoginState) }
            }
            }
        }

    suspend fun unlockKeys(password: String): Result<Unit> = withContext(dispatcher) {
        coroutineResult {
            val email = sessionManager.userEmail?.trim()?.lowercase()
                ?: error("Brak zapisanego adresu e-mail w sesji")
            val accessToken = sessionManager.accessToken ?: error("Brak aktywnego tokenu dostępu")
            val opaqueStart = opaqueManager.startLogin(password)
            try {
            val startResponse = startLogin(email, opaqueStart.startLoginRequest)
            val opaqueFinish = opaqueManager.finishLogin(
                password = password,
                clientLoginState = opaqueStart.clientLoginState,
                loginResponse = startResponse.loginResponse,
                email = email,
                serverOrigin = baseUrl.trimEnd('/'),
            )
            recoverKeys(opaqueFinish.exportKey, accessToken)
            } finally { opaqueManager.discardState(opaqueStart.clientLoginState) }
        }
    }

    suspend fun recoverKeys(exportKey: String, accessToken: String) = keys.recover(exportKey, accessToken)

    override suspend fun tryRestoreCachedKeys(): Boolean {
        if (!browserSessions) return keys.restoreCached()
        return withBrowserSessionLock {
            try {
                browserCall("migrate")
                val email = browserRememberedEmail().takeIf { it.isNotBlank() } ?: return@withBrowserSessionLock false
                if (!sessionManager.isLoggedIn) {
                    val tokens = api.refresh("")
                    sessionManager.saveSession(tokens.accessToken, "", email)
                }
                keys.restoreCached()
            } catch (error: CancellationException) { throw error }
              catch (_: Exception) { false }
        }
    }

    override suspend fun refreshSession(): Result<Unit> = refresher.refreshSession()

    override suspend fun logout() = withContext(dispatcher) { withBrowserSessionLock {
        val accessToken = sessionManager.accessToken
        val sendLogout = try {
            accessToken?.takeIf { it.isNotBlank() }?.let { api.prepareLogout(it) }
        } finally {
            withContext(NonCancellable) { clearLocalSession() }
        }
        if (sendLogout != null) {
            try {
                val response = sendLogout()
                if (!response.status.isSuccess()) {
                    logger.log(LogLevel.WARNING, "auth.logout", response.status.value, null)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.log(LogLevel.WARNING, "auth.logout", (error as? ApiException)?.statusCode, (error as? ApiException)?.code)
            }
        }
    }

    }

    suspend fun startLogin(email: String, startLoginRequest: String): StartLoginResponseDto =
        withContext(dispatcher) {
            api.startLogin(StartLoginRequestDto(email, startLoginRequest))
        }

    suspend fun finishLogin(loginSessionId: String, finishLoginRequest: String): FinishLoginResponseDto =
        withContext(dispatcher) {
            api.finishLogin(FinishLoginRequestDto(loginSessionId, finishLoginRequest))
        }

    private suspend fun clearLocalSession() {
        refresher.reset()
        sessionManager.clearSession()
        dPoPManager.clearKeyPair()
        keys.clear()
        if (browserSessions) browserCall("clear")
    }
}
