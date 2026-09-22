package pl.quicktask.app.scheduled.model

import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.serialization.Serializable
import pl.quicktask.app.items.model.DecryptedAttachment
import pl.quicktask.app.items.model.InboxAttachmentDto
import pl.quicktask.app.items.model.InboxTagDto
import pl.quicktask.app.nextactions.model.DecryptedProject
import pl.quicktask.app.nextactions.model.NextActionContextDto
import pl.quicktask.app.nextactions.model.NextActionProjectDto

@Serializable
data class ScheduledTaskDto(
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
    val scheduledAt: String,
    val recurrence: RecurrenceRule? = null,
    val deferUntil: String? = null,
)

@Serializable
data class CreateScheduledTaskRequestDto(
    val type: String = "SCHEDULED",
    val encryptedTitle: String,
    val encryptedItemKey: String,
    val encryptedNote: String? = null,
    val scheduledAt: String,
    val recurrence: RecurrenceRule? = null,
    val deferUntil: String? = null,
    val dueAt: String? = null,
    val projectId: String? = null,
    val contextIds: List<String> = emptyList(),
    val newContextNames: List<String> = emptyList(),
    val tagIds: List<String> = emptyList(),
    val newTagNames: List<String> = emptyList(),
    val fileIds: List<String>? = null,
)

@Serializable
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
data class UpdateScheduledTaskRequestDto(
    val encryptedTitle: String,
    val encryptedNote: String? = null,
    val scheduledAt: String,
    @kotlinx.serialization.EncodeDefault
    val recurrence: RecurrenceRule? = null,
    val deferUntil: String? = null,
    val dueAt: String? = null,
    val projectId: String? = null,
    val contextIds: List<String> = emptyList(),
    val newContextNames: List<String> = emptyList(),
    val tagIds: List<String> = emptyList(),
    val newTagNames: List<String> = emptyList(),
    val addedFileIds: List<String>? = null,
    val removedAttachmentIds: List<String>? = null,
)

typealias ConvertInboxToScheduledRequestDto = UpdateScheduledTaskRequestDto

@Serializable
data class ChangeDateRequestDto(
    val scheduledAt: String? = null,
    val dueAt: String? = null,
)

@Serializable
data class ChangeTagsRequestDto(
    val tagIds: List<String> = emptyList(),
    val newTagNames: List<String> = emptyList(),
)

data class ScheduledTask(
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
    val scheduledAt: String,
    val recurrence: RecurrenceRule? = null,
    val deferUntil: String? = null,
    val isPending: Boolean = false,
) {
    val isPendingConfirmation: Boolean
        get() = isPending || itemId.startsWith("temp_")
}
