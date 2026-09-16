package pl.quicktask.app.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import pl.quicktask.app.network.ApiConfig
import pl.quicktask.app.network.sharedHttpClient

val sharedAuthRepository: AuthRepository by lazy { AuthRepository() }

open class AuthRepository(
    private val httpClient: HttpClient = sharedHttpClient,
    private val dPoPManager: DPoPManager = sharedDPoPManager,
    private val opaqueManager: OpaqueManager = createOpaqueManager(),
    private val sessionManager: SessionManager = SessionManager(),
    private val keyCache: KeyCache = KeyCache(),
    private val baseUrl: String = ApiConfig.BASE_URL,
) {

    private val refreshMutex = Mutex()
    private var lastRefreshTime: Long = 0

    val isLoggedIn: Boolean
        get() = sessionManager.isLoggedIn

    suspend fun register(email: String, password: String): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                dPoPManager.clearKeyPair()
                val cleanEmail = email.trim().lowercase()
                val opaqueStart = opaqueManager.startRegistration(password)

                val startUrl = "$baseUrl/auth/register/start"
                val dpopStart = dPoPManager.generateDPoPProof("POST", startUrl)
                val startResponse = httpClient.post(startUrl) {
                    contentType(ContentType.Application.Json)
                    header("DPoP", dpopStart)
                    setBody(
                        StartRegistrationRequestDto(
                            email = cleanEmail,
                            registrationRequest = opaqueStart.registrationRequest,
                        ),
                    )
                }

                startResponse.ensureSuccessOrThrow()

                val startResultDto: StartRegistrationResponseDto = startResponse.body()

                val opaqueFinish = opaqueManager.finishRegistration(
                    password = password,
                    clientRegistrationState = opaqueStart.clientRegistrationState,
                    registrationResponse = startResultDto.registrationResponse,
                    email = cleanEmail,
                    serverOrigin = baseUrl.trimEnd('/'),
                )

                val userPair = generateUserKeyPair()
                val pkcs8 = userPair.privateKeyPkcs8
                    ?: error("Nie można wyeksportować klucza prywatnego")

                val wrapped = wrapPrivateKey(
                    exportKey = opaqueFinish.exportKey,
                    privateKeyPkcs8 = pkcs8,
                )

                val finishUrl = "$baseUrl/auth/register/finish"
                val dpopFinish = dPoPManager.generateDPoPProof("POST", finishUrl)
                val finishResponse = httpClient.post(finishUrl) {
                    contentType(ContentType.Application.Json)
                    header("DPoP", dpopFinish)
                    setBody(
                        FinishRegistrationRequestDto(
                            email = cleanEmail,
                            registrationRecord = opaqueFinish.registrationRecord,
                            publicKey = userPair.publicKeySpki.toBase64(),
                            encryptedPrivateKey = wrapped.ciphertext,
                            privateKeyNonce = wrapped.nonce,
                        ),
                    )
                }

                finishResponse.ensureSuccessOrThrow()

                val loginResult = login(cleanEmail, password)
                loginResult.getOrThrow()
                Unit
            }
        }

    suspend fun login(email: String, password: String): Result<FinishLoginResponseDto> =
        withContext(Dispatchers.Default) {
            runCatching {
                dPoPManager.clearKeyPair()
                val cleanEmail = email.trim().lowercase()
                val opaqueStart = opaqueManager.startLogin(password)

                val startResponse = startLogin(
                    email = cleanEmail,
                    startLoginRequest = opaqueStart.startLoginRequest,
                )

                val opaqueFinish = opaqueManager.finishLogin(
                    password = password,
                    clientLoginState = opaqueStart.clientLoginState,
                    loginResponse = startResponse.loginResponse,
                    email = cleanEmail,
                    serverOrigin = baseUrl.trimEnd('/'),
                )

                val finishResponse = finishLogin(
                    loginSessionId = startResponse.loginSessionId,
                    finishLoginRequest = opaqueFinish.finishLoginRequest,
                )

                sessionManager.saveSession(
                    accessToken = finishResponse.accessToken,
                    refreshToken = finishResponse.refreshToken,
                    email = cleanEmail,
                )

                recoverKeys(opaqueFinish.exportKey, finishResponse.accessToken)

                finishResponse
            }
        }

    suspend fun unlockKeys(password: String): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                val rawEmail = sessionManager.userEmail
                    ?: error("Brak zapisanego adresu e-mail w sesji")
                val cleanEmail = rawEmail.trim().lowercase()
                val accessToken = sessionManager.accessToken
                    ?: error("Brak aktywnego tokenu dostępu")

                val opaqueStart = opaqueManager.startLogin(password)
                val startResponse = startLogin(
                    email = cleanEmail,
                    startLoginRequest = opaqueStart.startLoginRequest,
                )

                val opaqueFinish = opaqueManager.finishLogin(
                    password = password,
                    clientLoginState = opaqueStart.clientLoginState,
                    loginResponse = startResponse.loginResponse,
                    email = cleanEmail,
                    serverOrigin = baseUrl.trimEnd('/'),
                )

                recoverKeys(opaqueFinish.exportKey, accessToken)
            }
        }

    suspend fun recoverKeys(exportKey: String, accessToken: String) {
        val email = sessionManager.userEmail ?: error("Brak adresu email w sesji")
        val url = "$baseUrl/users/me/key"
        val dpopProof = dPoPManager.generateDPoPProof("GET", url, accessToken)

        val response = httpClient.get(url) {
            header("Authorization", "DPoP $accessToken")
            header("DPoP", dpopProof)
        }

        response.ensureSuccessOrThrow()

        val material: UserKeyMaterialDto = response.body()
        val restoredPair = restoreUserKeyPair(exportKey, material)

        KeyStore.set(restoredPair)
        keyCache.cacheUserKeys(email, restoredPair)
    }

    suspend fun tryRestoreCachedKeys(): Boolean = withContext(Dispatchers.Default) {
        val email = sessionManager.userEmail ?: return@withContext false
        val cachedPair = keyCache.loadCachedUserKeys(email) ?: return@withContext false
        KeyStore.set(cachedPair)
        true
    }

    open suspend fun refreshSession(): Result<FinishLoginResponseDto> = withContext(Dispatchers.Default) {
        refreshMutex.withLock {
            runCatching {
                val currentRefreshToken = sessionManager.refreshToken
                    ?: error("Brak tokenu odświeżania w sesji")

                val now = getTimeMillis()
                if (now - lastRefreshTime < 2000) {
                    val currentAccessToken = sessionManager.accessToken ?: ""
                    return@runCatching FinishLoginResponseDto(
                        accessToken = currentAccessToken,
                        refreshToken = currentRefreshToken,
                        tokenType = "DPoP",
                        accessTokenExpiresIn = 600,
                        refreshTokenExpiresIn = 2592000,
                    )
                }

                val email = sessionManager.userEmail ?: ""
                val url = "$baseUrl/auth/refresh"
                val dpopProof = dPoPManager.generateDPoPProof("POST", url)

                val response = httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    header("DPoP", dpopProof)
                    setBody(RefreshTokenRequestDto(refreshToken = currentRefreshToken))
                }

                if (!response.status.isSuccess()) {
                    sessionManager.clearSession()
                    dPoPManager.clearKeyPair()
                    KeyStore.set(null)
                    keyCache.clearCachedUserKeys()
                    response.ensureSuccessOrThrow()
                }

                val tokens: FinishLoginResponseDto = response.body()
                sessionManager.saveSession(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    email = email,
                )
                lastRefreshTime = getTimeMillis()
                tokens
            }
        }
    }

    open suspend fun logout() = withContext(NonCancellable + Dispatchers.Default) {
        val accessToken = sessionManager.accessToken
        sessionManager.clearSession()
        dPoPManager.clearKeyPair()
        KeyStore.set(null)
        keyCache.clearCachedUserKeys()

        if (!accessToken.isNullOrBlank()) {
            try {
                val url = "$baseUrl/auth/logout"
                val dpopProof = dPoPManager.generateDPoPProof("POST", url, accessToken)

                val response = httpClient.post(url) {
                    header("Authorization", "DPoP $accessToken")
                    header("DPoP", dpopProof)
                }

                if (!response.status.isSuccess()) {
                    val errorText = response.bodyAsText()
                    println("Błąd wylogowania (${response.status.value}): $errorText")
                }
            } catch (e: Throwable) {
                println("Wyjątek podczas wylogowania: ${e.message}")
            }
        }
    }

    suspend fun startLogin(
        email: String,
        startLoginRequest: String,
    ): StartLoginResponseDto = withContext(Dispatchers.Default) {
        val url = "$baseUrl/auth/login/start"
        val dpopProof = dPoPManager.generateDPoPProof("POST", url)
        val requestDto = StartLoginRequestDto(
            email = email,
            startLoginRequest = startLoginRequest,
        )

        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            header("DPoP", dpopProof)
            setBody(requestDto)
        }

        response.ensureSuccessOrThrow()

        response.body()
    }

    suspend fun finishLogin(
        loginSessionId: String,
        finishLoginRequest: String,
    ): FinishLoginResponseDto = withContext(Dispatchers.Default) {
        val url = "$baseUrl/auth/login/finish"
        val dpopProof = dPoPManager.generateDPoPProof("POST", url)

        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            header("DPoP", dpopProof)
            setBody(
                FinishLoginRequestDto(
                    loginSessionId = loginSessionId,
                    finishLoginRequest = finishLoginRequest,
                ),
            )
        }

        response.ensureSuccessOrThrow()

        response.body()
    }
}

private val jsonParser = Json { ignoreUnknownKeys = true }

private suspend fun HttpResponse.ensureSuccessOrThrow() {
    if (!status.isSuccess()) {
        val errorText = bodyAsText()
        val parsedError = runCatching {
            jsonParser.decodeFromString<ApiErrorDto>(errorText)
        }.getOrNull()

        throw ApiException(
            code = parsedError?.code,
            statusCode = parsedError?.statusCode ?: status.value,
            message = parsedError?.message ?: errorText,
        )
    }
}

