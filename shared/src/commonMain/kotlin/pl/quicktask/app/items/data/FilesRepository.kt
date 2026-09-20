package pl.quicktask.app.items.data

import io.ktor.client.call.body
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import pl.quicktask.app.auth.crypto.createItemKey
import pl.quicktask.app.auth.crypto.decryptBuffer
import pl.quicktask.app.auth.crypto.decryptText
import pl.quicktask.app.auth.crypto.encryptBuffer
import pl.quicktask.app.auth.crypto.encryptText
import pl.quicktask.app.auth.crypto.sha256Base64
import pl.quicktask.app.auth.crypto.unwrapItemKey
import pl.quicktask.app.auth.crypto.wrapItemKey
import pl.quicktask.app.items.domain.UserKeysProvider
import pl.quicktask.app.items.model.DecryptedFile
import pl.quicktask.app.items.model.FileIntegrityException
import pl.quicktask.app.items.model.FileMetadataDto
import pl.quicktask.app.items.model.StoredFileDto
import pl.quicktask.app.items.model.UploadedFileDto
import pl.quicktask.app.items.model.decryptItem
import pl.quicktask.app.items.model.itemResult
import pl.quicktask.app.network.client.AuthenticatedApiClient

private const val FILE_TIMEOUT_MS = 600_000L

interface FileOperations {
    suspend fun uploadEncryptedFile(
        fileName: String, mimeType: String, fileBytes: ByteArray, onProgress: ((Float) -> Unit)? = null,
    ): Result<String>
    suspend fun deleteUploadedFile(fileId: String): Result<Unit>
    suspend fun downloadEncryptedFile(fileId: String): Result<DecryptedFile>
}

class FilesRepository(
    private val api: AuthenticatedApiClient,
    private val keysProvider: UserKeysProvider,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : FileOperations {

    override suspend fun uploadEncryptedFile(
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray,
        onProgress: ((Float) -> Unit)?,
    ): Result<String> = withContext(Dispatchers.Default) {
        itemResult {
            val userKeys = keysProvider.getUserKeys()

            val fileKey = createItemKey()
            val ciphertext = encryptBuffer(fileKey, fileBytes)

            val metadata = FileMetadataDto(name = fileName, type = mimeType)
            val metadataJson = json.encodeToString(FileMetadataDto.serializer(), metadata)
            val encryptedMetadata = encryptText(fileKey, metadataJson)

            val encryptedFileKey = wrapItemKey(userKeys.publicKey, fileKey)
            val ciphertextSha256 = sha256Base64(ciphertext)

            val url = "files"

            val response = api.request(HttpMethod.Post, url) {
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
                header("X-Ciphertext-Length", ciphertext.size.toString())
                header("X-Encrypted-Metadata", encryptedMetadata)
                header("X-Encrypted-File-Key", encryptedFileKey)
                header("X-Ciphertext-Sha256", ciphertextSha256)
                setBody(ciphertext)
            }

            val uploaded: UploadedFileDto = response.body()
            uploaded.fileId
        }
    }

    override suspend fun deleteUploadedFile(fileId: String): Result<Unit> =
        withContext(Dispatchers.Default) {
            itemResult {
                val url = "files/$fileId"

                api.request(HttpMethod.Delete, url)
                Unit
            }
        }

    override suspend fun downloadEncryptedFile(fileId: String): Result<DecryptedFile> =
        withContext(Dispatchers.Default) {
            itemResult {
                val userKeys = keysProvider.getUserKeys()

                val metaUrl = "files/$fileId"

                val metaResponse = api.request(HttpMethod.Get, metaUrl) {
                }

                val stored: StoredFileDto = metaResponse.body()
                val fileKey = decryptItem { unwrapItemKey(userKeys.privateKey, stored.encryptedFileKey) }

                val metadata = decryptItem {
                    json.decodeFromString<FileMetadataDto>(decryptText(fileKey, stored.encryptedMetadata))
                }

                val contentUrl = "files/$fileId/content"

                val contentResponse = api.request(HttpMethod.Get, contentUrl) {
                    timeout {
                        requestTimeoutMillis = FILE_TIMEOUT_MS
                        socketTimeoutMillis = FILE_TIMEOUT_MS
                    }
                }

                val ciphertext: ByteArray = contentResponse.body()
                val actualSha256 = sha256Base64(ciphertext)

                if (actualSha256 != stored.ciphertextSha256) {
                    throw FileIntegrityException()
                }

                val plaintext = decryptItem { decryptBuffer(fileKey, ciphertext) }

                DecryptedFile(
                    name = metadata.name,
                    type = metadata.type,
                    content = plaintext,
                )
            }
        }
}
