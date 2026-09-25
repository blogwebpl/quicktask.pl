package pl.quicktask.app.references.model

import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.serialization.Serializable
import pl.quicktask.app.items.model.DecryptedAttachment
import pl.quicktask.app.items.model.InboxAttachmentDto
import pl.quicktask.app.items.model.InboxTagDto

@Serializable
data class ReferenceItemDto(
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
data class CreateReferenceRequestDto(
    val type: String = "REFERENCE",
    val encryptedTitle: String,
    val encryptedItemKey: String,
    val encryptedNote: String? = null,
    val tagIds: List<String> = emptyList(),
    val newTagNames: List<String> = emptyList(),
    val fileIds: List<String>? = null,
)

data class ReferenceItem(
    val itemId: String,
    val title: String,
    val note: String,
    val itemKey: AES.GCM.Key,
    val createdAt: String,
    val updatedAt: String,
    val attachments: List<DecryptedAttachment> = emptyList(),
    val tags: List<InboxTagDto> = emptyList(),
)
