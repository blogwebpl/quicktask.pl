package pl.quicktask.todo.inbox

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.quicktask.todo.auth.AuthRepository
import pl.quicktask.todo.auth.DPoPManager
import pl.quicktask.todo.auth.sharedDPoPManager
import pl.quicktask.todo.auth.KeyStore
import pl.quicktask.todo.auth.UserKeyPair
import pl.quicktask.todo.auth.SessionManager
import pl.quicktask.todo.auth.createItemKey
import pl.quicktask.todo.auth.decryptBuffer
import pl.quicktask.todo.auth.decryptText
import pl.quicktask.todo.auth.encryptBuffer
import pl.quicktask.todo.auth.encryptText
import pl.quicktask.todo.auth.sha256Base64
import pl.quicktask.todo.auth.unwrapItemKey
import pl.quicktask.todo.auth.wrapItemKey
import pl.quicktask.todo.network.ApiConfig
import pl.quicktask.todo.network.sharedHttpClient

@Serializable
data class UploadedFileDto(
    val fileId: String,
)

@Serializable
data class StoredFileDto(
    val fileId: String,
    val encryptedMetadata: String,
    val encryptedFileKey: String,
    val ciphertextSha256: String,
)

@Serializable
data class FileMetadataDto(
    val name: String,
    val type: String,
)

data class DecryptedFile(
    val name: String,
    val type: String,
    val content: ByteArray,
)

private const val FILE_TIMEOUT_MS = 600_000L // 10 minut

class FilesRepository(
    private val httpClient: HttpClient = sharedHttpClient,
    private val dPoPManager: DPoPManager = sharedDPoPManager,
    private val sessionManager: SessionManager = SessionManager(),
    private val authRepository: AuthRepository = AuthRepository(
        httpClient = httpClient,
        dPoPManager = dPoPManager,
        sessionManager = sessionManager,
    ),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val baseUrl: String = ApiConfig.BASE_URL,
) {

    private fun getAccessToken(): String {
        val token = sessionManager.accessToken
        if (token.isNullOrBlank()) {
            error("Brak tokenu dostępu (brak aktywnej sesji)")
        }
        return token
    }

    private suspend fun executeAuthenticated(
        method: String,
        url: String,
        block: suspend (accessToken: String, dpopProof: String) -> HttpResponse,
    ): HttpResponse {
        val initialToken = getAccessToken()
        val initialDpop = dPoPManager.generateDPoPProof(method, url, initialToken)
        var response = block(initialToken, initialDpop)

        if (response.status.value == 498) {
            val refreshedResult = authRepository.refreshSession()
            if (refreshedResult.isSuccess) {
                val newToken = getAccessToken()
                val newDpop = dPoPManager.generateDPoPProof(method, url, newToken)
                response = block(newToken, newDpop)
            }
        }

        return response
    }

    private suspend fun getUserKeys(): UserKeyPair {
        KeyStore.getSnapshot()?.let { return it }
        if (authRepository.tryRestoreCachedKeys()) {
            KeyStore.getSnapshot()?.let { return it }
        }
        return KeyStore.requireUserKeys()
    }

    suspend fun uploadEncryptedFile(
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray,
        onProgress: ((Float) -> Unit)? = null,
    ): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            val userKeys = getUserKeys()

            val fileKey = createItemKey()
            val ciphertext = encryptBuffer(fileKey, fileBytes)

            val metadata = FileMetadataDto(name = fileName, type = mimeType)
            val metadataJson = json.encodeToString(FileMetadataDto.serializer(), metadata)
            val encryptedMetadata = encryptText(fileKey, metadataJson)

            val encryptedFileKey = wrapItemKey(userKeys.publicKey, fileKey)
            val ciphertextSha256 = sha256Base64(ciphertext)

            val url = "${baseUrl.trimEnd('/')}/files"

            val response = executeAuthenticated("POST", url) { accessToken, dpopProof ->
                httpClient.post(url) {
                    timeout {
                        requestTimeoutMillis = FILE_TIMEOUT_MS
                        socketTimeoutMillis = FILE_TIMEOUT_MS
                    }
                    if (onProgress != null) {
                        onUpload { bytesSentTotal, contentLength ->
                            val total = contentLength ?: ciphertext.size.toLong()
                            if (total > 0) {
                                val progress = (bytesSentTotal.toDouble() / total.toDouble()).coerceIn(0.0, 1.0).toFloat()
                                onProgress(progress)
                            }
                        }
                    }
                    contentType(ContentType.Application.OctetStream)
                    header("Authorization", "DPoP $accessToken")
                    header("DPoP", dpopProof)
                    header("X-Ciphertext-Length", ciphertext.size.toString())
                    header("X-Encrypted-Metadata", encryptedMetadata)
                    header("X-Encrypted-File-Key", encryptedFileKey)
                    header("X-Ciphertext-Sha256", ciphertextSha256)
                    setBody(ciphertext)
                }
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                error("Błąd przesłania pliku (${response.status.value}): $errorText")
            }

            val uploaded: UploadedFileDto = response.body()
            uploaded.fileId
        }
    }

    suspend fun deleteUploadedFile(fileId: String): Result<Unit> =
        withContext(Dispatchers.Default) {
            runCatching {
                val url = "${baseUrl.trimEnd('/')}/files/$fileId"

                val response = executeAuthenticated("DELETE", url) { accessToken, dpopProof ->
                    httpClient.delete(url) {
                        header("Authorization", "DPoP $accessToken")
                        header("DPoP", dpopProof)
                    }
                }

                if (!response.status.isSuccess()) {
                    val errorText = response.bodyAsText()
                    error("Błąd usunięcia pliku (${response.status.value}): $errorText")
                }
            }
        }

    suspend fun downloadEncryptedFile(fileId: String): Result<DecryptedFile> =
        withContext(Dispatchers.Default) {
            runCatching {
                val userKeys = getUserKeys()

                val metaUrl = "${baseUrl.trimEnd('/')}/files/$fileId"

                val metaResponse = executeAuthenticated("GET", metaUrl) { accessToken, dpopProof ->
                    httpClient.get(metaUrl) {
                        header("Authorization", "DPoP $accessToken")
                        header("DPoP", dpopProof)
                    }
                }

                if (!metaResponse.status.isSuccess()) {
                    val errorText = metaResponse.bodyAsText()
                    error("Błąd pobierania metadanych pliku (${metaResponse.status.value}): $errorText")
                }

                val stored: StoredFileDto = metaResponse.body()
                val fileKey = unwrapItemKey(userKeys.privateKey, stored.encryptedFileKey)

                val metadataJson = decryptText(fileKey, stored.encryptedMetadata)
                val metadata = json.decodeFromString(FileMetadataDto.serializer(), metadataJson)

                val contentUrl = "${baseUrl.trimEnd('/')}/files/$fileId/content"

                val contentResponse = executeAuthenticated("GET", contentUrl) { accessToken, dpopProof ->
                    httpClient.get(contentUrl) {
                        timeout {
                            requestTimeoutMillis = FILE_TIMEOUT_MS
                            socketTimeoutMillis = FILE_TIMEOUT_MS
                        }
                        header("Authorization", "DPoP $accessToken")
                        header("DPoP", dpopProof)
                    }
                }

                if (!contentResponse.status.isSuccess()) {
                    val errorText = contentResponse.bodyAsText()
                    error("Błąd pobierania zawartości pliku (${contentResponse.status.value}): $errorText")
                }

                val ciphertext: ByteArray = contentResponse.body()
                val actualSha256 = sha256Base64(ciphertext)

                if (actualSha256 != stored.ciphertextSha256) {
                    error("Błąd spójności danych: suma kontrolna SHA-256 pliku nie zgadza się z oczekiwaną")
                }

                val plaintext = decryptBuffer(fileKey, ciphertext)

                DecryptedFile(
                    name = metadata.name,
                    type = metadata.type,
                    content = plaintext,
                )
            }
        }
}
