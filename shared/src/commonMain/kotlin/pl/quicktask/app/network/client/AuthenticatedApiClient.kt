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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import pl.quicktask.app.auth.crypto.DPoPManager
import pl.quicktask.app.auth.data.SessionRefresher
import pl.quicktask.app.auth.model.ApiException
import pl.quicktask.app.auth.session.SessionManager
import pl.quicktask.app.common.AppLoggerManager
import pl.quicktask.app.common.LogCategory
import pl.quicktask.app.common.LogLevel
import pl.quicktask.app.items.model.MissingSessionException
import pl.quicktask.app.network.config.ApiConfig

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private val defaultJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

internal data class ParsedApiError(
    val code: String?,
    val message: String,
    val rawBody: String,
)

internal fun parseApiError(httpStatusCode: Int, responseText: String): ParsedApiError {
    if (responseText.isBlank()) {
        return ParsedApiError(code = null, message = "HTTP $httpStatusCode (brak treści odpowiedzi)", rawBody = "")
    }
    return try {
        val jsonElement = defaultJson.parseToJsonElement(responseText)
        if (jsonElement is JsonObject) {
            val code = jsonElement["code"]?.jsonPrimitive?.contentOrNull
            val errorStr = jsonElement["error"]?.jsonPrimitive?.contentOrNull

            val msgElement = jsonElement["message"]
            val msgStr = when (msgElement) {
                is JsonPrimitive -> msgElement.contentOrNull
                is JsonArray -> msgElement.mapNotNull {
                    if (it is JsonPrimitive) it.contentOrNull else it.toString()
                }.joinToString("; ")
                else -> msgElement?.toString()
            } ?: errorStr

            ParsedApiError(
                code = code ?: errorStr,
                message = msgStr ?: responseText,
                rawBody = responseText,
            )
        } else {
            ParsedApiError(code = null, message = responseText, rawBody = responseText)
        }
    } catch (_: Exception) {
        ParsedApiError(code = null, message = responseText, rawBody = responseText)
    }
}

class AuthenticatedApiClient(
    private val httpClient: HttpClient,
    private val dPoPManager: DPoPManager,
    private val sessionManager: SessionManager,
    private val refreshSession: suspend () -> Result<Unit>,
    private val baseUrl: String = ApiConfig.BASE_URL,
    private val json: Json = defaultJson,
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
            val parsedError = parseApiError(response.status.value, text)

            val logMsg = "Błąd API ${method.value} $path -> HTTP ${response.status.value}" +
                    (if (!parsedError.code.isNullOrBlank()) " [code=${parsedError.code}]" else "") +
                    ": ${parsedError.message}"

            AppLoggerManager.log(
                level = LogLevel.ERROR,
                category = LogCategory.API_RESPONSE,
                tag = "AuthenticatedApiClient",
                message = logMsg,
                details = "raw_body=$text",
            )
            throw ApiException(parsedError.code, response.status.value, parsedError.message)
        }
        return response
    }
}
