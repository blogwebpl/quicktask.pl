package pl.quicktask.app.nextactions.model

import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.serialization.Serializable
import pl.quicktask.app.items.model.DecryptedAttachment
import pl.quicktask.app.items.model.InboxAttachmentDto
import pl.quicktask.app.items.model.InboxTagDto

@Serializable
data class NextActionProjectDto(
    val projectId: String,
    val encryptedTitle: String,
    val encryptedItemKey: String,
)

@Serializable
data class NextActionContextDto(
    val contextId: String,
    val name: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val radius: Double? = null,
)

@Serializable
data class NewContextInput(
    val name: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val radius: Double? = null,
)

@Serializable
data class NextActionDto(
    val itemId: String,
    val encryptedTitle: String,
    val encryptedNote: String? = null,
    val encryptedItemKey: String,
    val createdAt: String,
    val updatedAt: String,
    val attachments: List<InboxAttachmentDto> = emptyList(),
    val tags: List<InboxTagDto> = emptyList(),
    val project: NextActionProjectDto? = null,
    val dueAt: String? = null,
    val contexts: List<NextActionContextDto> = emptyList(),
)

@Serializable
data class NextActionOptionsResponseDto(
    val projects: List<NextActionProjectDto> = emptyList(),
    val contexts: List<NextActionContextDto> = emptyList(),
    val tags: List<InboxTagDto> = emptyList(),
)

@Serializable
data class CreateNextActionRequestDto(
    val type: String = "NEXT_ACTION",
    val encryptedTitle: String,
    val encryptedItemKey: String,
    val encryptedNote: String? = null,
    val projectId: String? = null,
    val dueAt: String? = null,
    val contextIds: List<String> = emptyList(),
    val newContextNames: List<String> = emptyList(),
    val tagIds: List<String> = emptyList(),
    val newTagNames: List<String> = emptyList(),
    val fileIds: List<String>? = null,
)

@Serializable
data class CreateProjectRequestDto(
    val type: String = "PROJECT",
    val encryptedTitle: String,
    val encryptedItemKey: String,
    val encryptedNote: String? = null,
    val fileIds: List<String>? = null,
)

@Serializable
data class ConvertInboxToNextActionRequestDto(
    val projectId: String? = null,
    val dueAt: String? = null,
    val contextIds: List<String> = emptyList(),
    val newContextNames: List<String> = emptyList(),
    val tagIds: List<String> = emptyList(),
    val newTagNames: List<String> = emptyList(),
)

@Serializable
data class UpdateNextActionRequestDto(
    val encryptedTitle: String,
    val encryptedNote: String? = null,
    val projectId: String? = null,
    val dueAt: String? = null,
    val contextIds: List<String> = emptyList(),
    val newContextNames: List<String> = emptyList(),
    val tagIds: List<String> = emptyList(),
    val newTagNames: List<String> = emptyList(),
)

data class DecryptedProject(
    val projectId: String,
    val title: String,
    val projectKey: AES.GCM.Key,
)

data class NextActionContext(
    val contextId: String,
    val name: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val radius: Double? = null,
)

data class NextActionTag(
    val tagId: String,
    val name: String,
)

data class NextActionOptions(
    val projects: List<DecryptedProject> = emptyList(),
    val contexts: List<NextActionContext> = emptyList(),
    val tags: List<NextActionTag> = emptyList(),
)

data class NextAction(
    val itemId: String,
    val title: String,
    val note: String,
    val itemKey: AES.GCM.Key,
    val createdAt: String,
    val updatedAt: String,
    val attachments: List<DecryptedAttachment> = emptyList(),
    val tags: List<InboxTagDto> = emptyList(),
    val project: DecryptedProject? = null,
    val dueAt: String? = null,
    val contexts: List<NextActionContextDto> = emptyList(),
    val isPending: Boolean = false,
) {
    val isPendingConfirmation: Boolean
        get() = isPending || itemId.startsWith("temp_")
}
