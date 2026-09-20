package pl.quicktask.app.items.model

import kotlinx.serialization.Serializable

@Serializable
data class UploadedFileDto(val fileId: String)

@Serializable
data class StoredFileDto(
    val fileId: String,
    val encryptedMetadata: String,
    val encryptedFileKey: String,
    val ciphertextSha256: String,
)

@Serializable
data class FileMetadataDto(val name: String, val type: String)

data class DecryptedFile(
    val name: String,
    val type: String,
    val content: ByteArray,
)
