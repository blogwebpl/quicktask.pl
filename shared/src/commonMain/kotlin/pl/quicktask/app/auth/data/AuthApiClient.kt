package pl.quicktask.app.auth.data

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
import kotlinx.serialization.json.Json
import pl.quicktask.app.auth.crypto.DPoPManager
import pl.quicktask.app.auth.crypto.UserKeyMaterialDto
import pl.quicktask.app.auth.model.*
import pl.quicktask.app.auth.session.browserSessions
import pl.quicktask.app.common.AppLoggerManager

internal class AuthApiClient(
    private val httpClient: HttpClient,
    private val dPoPManager: DPoPManager,
    private val baseUrl: String,
) {
    suspend fun startRegistration(request: StartRegistrationRequestDto): StartRegistrationResponseDto =
        post("auth/register/start", request)

    suspend fun finishRegistration(request: FinishRegistrationRequestDto) {
        postWithoutResponse("auth/register/finish", request)
    }

    suspend fun startLogin(request: StartLoginRequestDto): StartLoginResponseDto =
        post("auth/login/start", request)

    suspend fun finishLogin(request: FinishLoginRequestDto): FinishLoginResponseDto =
        post(if (browserSessions) "auth/browser/login/finish" else "auth/login/finish", request)

    suspend fun refresh(refreshToken: String): FinishLoginResponseDto {
        if (!browserSessions) return post("auth/refresh", RefreshTokenRequestDto(refreshToken))
        val url = "$baseUrl/auth/browser/refresh"
        AppLoggerManager.logApiRequest("AuthApiClient", "POST", "auth/browser/refresh")
        val response = httpClient.post(url) {
            header("X-Clearmind-CSRF", "1")
            header("DPoP", dPoPManager.generateDPoPProof("POST", url))
        }
        AppLoggerManager.logApiResponse("AuthApiClient", "POST", "auth/browser/refresh", response.status.value)
        response.ensureSuccessOrThrow()
        return response.body()
    }

    suspend fun getUserKey(accessToken: String): UserKeyMaterialDto {
        val url = "$baseUrl/users/me/key"
        AppLoggerManager.logApiRequest("AuthApiClient", "GET", "users/me/key")
        val response = httpClient.get(url) {
            header("Authorization", "DPoP $accessToken")
            header("DPoP", dPoPManager.generateDPoPProof("GET", url, accessToken))
        }
        AppLoggerManager.logApiResponse("AuthApiClient", "GET", "users/me/key", response.status.value)
        response.ensureSuccessOrThrow()
        return response.body()
    }

    suspend fun prepareLogout(accessToken: String): suspend () -> HttpResponse {
        val url = "$baseUrl/auth/${if (browserSessions) "browser/" else ""}logout"
        val proof = dPoPManager.generateDPoPProof("POST", url, accessToken)
        return {
            AppLoggerManager.logApiRequest("AuthApiClient", "POST", "logout")
            val resp = httpClient.post(url) {
                if (browserSessions) header("X-Clearmind-CSRF", "1")
                header("Authorization", "DPoP $accessToken")
                header("DPoP", proof)
            }
            AppLoggerManager.logApiResponse("AuthApiClient", "POST", "logout", resp.status.value)
            resp
        }
    }

    private suspend inline fun <reified Request : Any, reified Response> post(
        path: String,
        request: Request,
    ): Response {
        val response = postResponse(path, request)
        response.ensureSuccessOrThrow()
        return response.body()
    }

    private suspend inline fun <reified Request : Any> postWithoutResponse(path: String, request: Request) {
        postResponse(path, request).ensureSuccessOrThrow()
    }

    private suspend inline fun <reified Request : Any> postResponse(path: String, request: Request): HttpResponse {
        val url = "$baseUrl/$path"
        AppLoggerManager.logApiRequest("AuthApiClient", "POST", path)
        val response = httpClient.post(url) {
            if (browserSessions && path.startsWith("auth/browser/")) header("X-Clearmind-CSRF", "1")
            contentType(ContentType.Application.Json)
            header("DPoP", dPoPManager.generateDPoPProof("POST", url))
            setBody(request)
        }
        AppLoggerManager.logApiResponse("AuthApiClient", "POST", path, response.status.value)
        return response
    }
}

private val authJson = Json { ignoreUnknownKeys = true }

internal suspend fun HttpResponse.ensureSuccessOrThrow() {
    if (status.isSuccess()) return
    val errorText = bodyAsText()
    val parsedError = runCatching { authJson.decodeFromString<ApiErrorDto>(errorText) }.getOrNull()
    throw ApiException(
        code = parsedError?.code,
        statusCode = parsedError?.statusCode ?: status.value,
        message = parsedError?.message ?: errorText,
    )
}
