package pl.quicktask.app.somedaymaybe.model

import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.serialization.Serializable
import pl.quicktask.app.items.model.DecryptedAttachment
import pl.quicktask.app.items.model.InboxAttachmentDto
import pl.quicktask.app.items.model.InboxTagDto

@Serializable
data class SomedayMaybeItemDto(
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
data class CreateSomedayMaybeRequestDto(
    val type: String = "SOMEDAY_MAYBE",
    val encryptedTitle: String,
    val encryptedItemKey: String,
    val encryptedNote: String? = null,
    val tagIds: List<String> = emptyList(),
    val newTagNames: List<String> = emptyList(),
    val fileIds: List<String>? = null,
)

@Serializable
data class UpdateTagsRequestDto(
    val tagIds: List<String> = emptyList(),
    val newTagNames: List<String> = emptyList(),
)

data class SomedayMaybeItem(
    val itemId: String,
    val title: String,
    val note: String,
    val itemKey: AES.GCM.Key,
    val createdAt: String,
    val updatedAt: String,
    val attachments: List<DecryptedAttachment> = emptyList(),
    val tags: List<InboxTagDto> = emptyList(),
)
