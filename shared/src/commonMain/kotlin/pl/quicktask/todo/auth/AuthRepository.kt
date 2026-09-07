package pl.quicktask.todo.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import pl.quicktask.todo.network.ApiConfig
import pl.quicktask.todo.network.createHttpClient

class AuthRepository(
    private val httpClient: HttpClient = createHttpClient(),
    private val dPoPManager: DPoPManager = DefaultDPoPManager(),
    private val opaqueManager: OpaqueManager = createOpaqueManager(),
    private val sessionManager: SessionManager = SessionManager(),
    private val baseUrl: String = ApiConfig.BASE_URL,
) {

    val isLoggedIn: Boolean
        get() = sessionManager.isLoggedIn

    suspend fun login(email: String, password: String): Result<FinishLoginResponseDto> {
        return runCatching {
            // Etap 1 OPAQUE - wygenerowanie żądania start
            val opaqueStart = opaqueManager.startLogin(password)

            // Etap 1 HTTP - wysłanie POST /auth/login/start
            val startResponse = startLogin(
                email = email,
                startLoginRequest = opaqueStart.startLoginRequest,
            )

            // Etap 2 OPAQUE - dokończenie weryfikacji hasła lokalnie
            val opaqueFinish = opaqueManager.finishLogin(
                password = password,
                clientLoginState = opaqueStart.clientLoginState,
                loginResponse = startResponse.loginResponse,
                email = email,
                serverOrigin = baseUrl.trimEnd('/'),
            )

            // Etap 2 HTTP - wysłanie POST /auth/login/finish
            val finishResponse = finishLogin(
                loginSessionId = startResponse.loginSessionId,
                finishLoginRequest = opaqueFinish.finishLoginRequest,
            )

            // Zapisanie sesji w pamięci trwałej
            sessionManager.saveSession(
                accessToken = finishResponse.accessToken,
                refreshToken = finishResponse.refreshToken,
                email = email,
            )

            finishResponse
        }
    }

    suspend fun logout() {
        try {
            val accessToken = sessionManager.accessToken
            if (!accessToken.isNullOrBlank()) {
                val url = "$baseUrl/auth/logout"
                val dpopHeader = dPoPManager.generateDPoPProof("POST", url, accessToken)

                val response = httpClient.post(url) {
                    header("Authorization", "DPoP $accessToken")
                    header("DPoP", dpopHeader)
                }

                if (!response.status.isSuccess()) {
                    val errorText = response.bodyAsText()
                    println("Błąd wylogowania (${response.status.value}): $errorText")
                }
            }
        } catch (e: Exception) {
            println("Wyjątek podczas wylogowania: ${e.message}")
        } finally {
            sessionManager.clearSession()
        }
    }

    suspend fun startLogin(
        email: String,
        startLoginRequest: String,
    ): StartLoginResponseDto {
        val url = "$baseUrl/auth/login/start"
        val dpopHeader = dPoPManager.generateDPoPProof("POST", url)

        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            header("DPoP", dpopHeader)
            setBody(
                StartLoginRequestDto(
                    email = email,
                    startLoginRequest = startLoginRequest,
                ),
            )
        }

        if (!response.status.isSuccess()) {
            val errorText = response.bodyAsText()
            error("Błąd serwera (${response.status.value}): $errorText")
        }

        return response.body()
    }

    suspend fun finishLogin(
        loginSessionId: String,
        finishLoginRequest: String,
    ): FinishLoginResponseDto {
        val url = "$baseUrl/auth/login/finish"
        val dpopHeader = dPoPManager.generateDPoPProof("POST", url)

        val response = httpClient.post(url) {
            contentType(ContentType.Application.Json)
            header("DPoP", dpopHeader)
            setBody(
                FinishLoginRequestDto(
                    loginSessionId = loginSessionId,
                    finishLoginRequest = finishLoginRequest,
                ),
            )
        }

        if (!response.status.isSuccess()) {
            val errorText = response.bodyAsText()
            error("Błąd serwera (${response.status.value}): $errorText")
        }

        return response.body()
    }
}
