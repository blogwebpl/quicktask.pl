package pl.quicktask.todo.inbox

import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.serialization.Serializable

@Serializable
data class InboxAttachmentDto(
    val attachmentId: String,
    val fileId: String,
    val attachedAt: String,
    val encryptedMetadata: String,
    val encryptedFileKey: String,
    val ciphertextSha256: String,
    val encryptedSize: Long,
)

@Serializable
data class InboxTagDto(
    val tagId: String,
    val name: String,
)

@Serializable
data class InboxItemDto(
    val itemId: String,
    val encryptedTitle: String,
    val encryptedNote: String? = null,
    val encryptedItemKey: String,
    val createdAt: String,
    val updatedAt: String,
    val attachments: List<InboxAttachmentDto> = emptyList(),
    val tags: List<InboxTagDto> = emptyList(),
)

@Serializable
data class CreateInboxItemRequestDto(
    val encryptedTitle: String,
    val encryptedItemKey: String,
    val encryptedNote: String? = null,
    val fileIds: List<String>? = null,
)

@Serializable
data class CreateInboxItemResponseDto(
    val itemId: String,
    val createdAt: String,
)

@Serializable
data class UpdateInboxItemRequestDto(
    val encryptedTitle: String? = null,
    val encryptedNote: String? = null,
)

@Serializable
data class UpdateInboxItemResponseDto(
    val itemId: String,
    val updatedAt: String,
)

@Serializable
data class CompleteInTwoMinutesRequestDto(
    val deleteAttachments: Boolean,
)

@Serializable
data class CompletedInTwoMinutesItemDto(
    val itemId: String,
    val encryptedTitle: String,
    val encryptedNote: String? = null,
    val encryptedItemKey: String,
    val createdAt: String,
    val updatedAt: String,
    val processedAt: String,
    val attachments: List<InboxAttachmentDto> = emptyList(),
    val tags: List<InboxTagDto> = emptyList(),
)

data class DecryptedAttachment(
    val attachmentId: String,
    val fileId: String,
    val name: String,
    val type: String,
    val ciphertextSha256: String,
    val fileKey: AES.GCM.Key,
)

data class InboxItem(
    val itemId: String,
    val title: String,
    val note: String,
    val itemKey: AES.GCM.Key,
    val createdAt: String,
    val updatedAt: String,
    val attachments: List<DecryptedAttachment> = emptyList(),
    val tags: List<InboxTagDto> = emptyList(),
)

data class CompletedInTwoMinutesItem(
    val itemId: String,
    val title: String,
    val note: String,
    val itemKey: AES.GCM.Key,
    val createdAt: String,
    val updatedAt: String,
    val processedAt: String,
    val attachments: List<DecryptedAttachment> = emptyList(),
    val tags: List<InboxTagDto> = emptyList(),
)

enum class ProcessDestination {
    TWO_MINUTES,
    NEXT_ACTION,
    PROJECT,
    WAITING,
    SCHEDULED,
    SOMEDAY,
    REFERENCE,
}

data class InputFile(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)
