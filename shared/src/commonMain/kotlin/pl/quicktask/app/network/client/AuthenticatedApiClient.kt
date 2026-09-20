package pl.quicktask.app.network.client

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.util.date.getTimeMillis
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import pl.quicktask.app.auth.crypto.DPoPManager
import pl.quicktask.app.auth.data.AuthRepository
import pl.quicktask.app.auth.data.SessionRefresher
import pl.quicktask.app.auth.model.ApiErrorDto
import pl.quicktask.app.auth.model.ApiException
import pl.quicktask.app.auth.session.SessionManager
import pl.quicktask.app.common.AppLoggerManager
import pl.quicktask.app.items.model.MissingSessionException
import pl.quicktask.app.network.config.ApiConfig

class AuthenticatedApiClient(
    private val httpClient: HttpClient,
    private val dPoPManager: DPoPManager,
    private val sessionManager: SessionManager,
    private val refreshSession: suspend () -> Result<Unit>,
    private val baseUrl: String = ApiConfig.BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    constructor(
        httpClient: HttpClient, dPoPManager: DPoPManager, sessionManager: SessionManager,
        authRepository: SessionRefresher, baseUrl: String = ApiConfig.BASE_URL,
    ) : this(httpClient, dPoPManager, sessionManager, refreshSession = authRepository::refreshSession, baseUrl = baseUrl)

    fun url(path: String): String = "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"

    suspend fun request(
        method: HttpMethod,
        path: String,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        val url = url(path)
        val requestMethod = method
        val startTime = getTimeMillis()
        AppLoggerManager.logApiRequest("AuthenticatedApiClient", method.value, path)
        suspend fun send(): HttpResponse {
            val token = sessionManager.accessToken?.takeIf { it.isNotBlank() } ?: throw MissingSessionException()
            val proof = dPoPManager.generateDPoPProof(method.value, url, token)
            return httpClient.request(url) {
                this.method = requestMethod
                configure()
                header("Authorization", "DPoP $token")
                header("DPoP", proof)
            }
        }
        var response = send()
        if (response.status.value == SESSION_EXPIRED_STATUS) {
            AppLoggerManager.logRefresh("AuthenticatedApiClient", "Sesja wygasła (${response.status.value}), próba odświeżenia sesji...")
            val refreshed = refreshSession()
            (refreshed.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (refreshed.isSuccess) {
                AppLoggerManager.logRefresh("AuthenticatedApiClient", "Odświeżenie sesji udane, ponowne wysłanie żądania $path")
                response = send()
            }
        }
        val duration = getTimeMillis() - startTime
        AppLoggerManager.logApiResponse("AuthenticatedApiClient", method.value, path, response.status.value, "${duration}ms")
        if (!response.status.isSuccess()) {
            val text = response.bodyAsText()
            val error = try {
                json.decodeFromString<ApiErrorDto>(text)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            AppLoggerManager.log(
                tag = "AuthenticatedApiClient",
                message = "Błąd API $path: ${response.status.value} - ${error?.message ?: text}"
            )
            throw ApiException(error?.code, response.status.value, error?.message ?: text)
        }
        return response
    }
}
