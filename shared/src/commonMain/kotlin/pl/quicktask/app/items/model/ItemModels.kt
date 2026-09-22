package pl.quicktask.app.items.model

import pl.quicktask.app.scheduled.model.RecurrenceRule

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
    val deletedAt: String? = null,
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
    val addedFileIds: List<String>? = null,
    val removedAttachmentIds: List<String>? = null,
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

enum class AttachmentMetadataState { AVAILABLE, DECRYPTION_FAILED }

data class DecryptedAttachment(
    val attachmentId: String,
    val fileId: String,
    val name: String?,
    val type: String,
    val ciphertextSha256: String,
    val fileKey: AES.GCM.Key,
    val metadataState: AttachmentMetadataState = AttachmentMetadataState.AVAILABLE,
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
    val isPending: Boolean = false,
) {
    val isPendingConfirmation: Boolean
        get() = isPending || itemId.startsWith("temp_")
}

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
    val isPending: Boolean = false,
) {
    val isPendingConfirmation: Boolean
        get() = isPending || itemId.startsWith("temp_")
}

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

@Serializable
data class SyncStateContextDto(
    val contextId: String,
    val name: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val radius: Double? = null,
)

@Serializable
data class SyncStateProjectInfoDto(
    val projectId: String,
    val encryptedTitle: String,
    val encryptedItemKey: String,
)

@Serializable
data class SyncStateTaskDto(
    val taskId: String,
    val gtdState: String? = null,
    val dueAt: String? = null,
    val deferUntil: String? = null,
    val scheduledAt: String? = null,
    val recurrence: RecurrenceRule? = null,
    val waitingFor: String? = null,
    val waitingSince: String? = null,
    val followUpAt: String? = null,
    val reviewedAt: String? = null,
)

@Serializable
data class SyncStateItemDto(
    val itemId: String,
    val encryptedTitle: String,
    val encryptedNote: String? = null,
    val encryptedItemKey: String,
    val createdAt: String,
    val updatedAt: String,
    val attachments: List<InboxAttachmentDto> = emptyList(),
    val tags: List<InboxTagDto> = emptyList(),
    val contexts: List<SyncStateContextDto> = emptyList(),
    val project: SyncStateProjectInfoDto? = null,
    val deletedAt: String? = null,
    val purgeAfter: String? = null,
    val isReference: Boolean = false,
    val isCompletedInTwoMinutes: Boolean = false,
    val isSomedayMaybe: Boolean = false,
    val isNextAction: Boolean = false,
    val isScheduled: Boolean = false,
    val processedAt: String? = null,
    val scheduledAt: String? = null,
    val recurrence: RecurrenceRule? = null,
    val deferUntil: String? = null,
    val dueAt: String? = null,
    val taskId: String? = null,
    val gtdState: String? = null,
    val waitingFor: String? = null,
    val waitingSince: String? = null,
    val followUpAt: String? = null,
    val reviewedAt: String? = null,
)

@Serializable
data class ItemSyncStateResponseDto(
    val location: String,
    val itemId: String? = null,
    val item: SyncStateItemDto? = null,
    val task: SyncStateTaskDto? = null,
    val projectId: String? = null,
    val dueAt: String? = null,
)
