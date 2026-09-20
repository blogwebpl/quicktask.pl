package pl.quicktask.app.now.model

import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.serialization.Serializable
import pl.quicktask.app.items.model.DecryptedAttachment
import pl.quicktask.app.items.model.InboxAttachmentDto
import pl.quicktask.app.items.model.InboxTagDto
import pl.quicktask.app.nextactions.model.DecryptedProject
import pl.quicktask.app.nextactions.model.NextAction
import pl.quicktask.app.nextactions.model.NextActionContextDto
import pl.quicktask.app.nextactions.model.NextActionProjectDto

@Serializable
data class NowItemDto(
    val itemId: String,
    val taskId: String? = null,
    val gtdState: String? = null,
    val encryptedTitle: String,
    val encryptedNote: String? = null,
    val encryptedItemKey: String,
    val createdAt: String,
    val updatedAt: String,
    val attachments: List<InboxAttachmentDto> = emptyList(),
    val tags: List<InboxTagDto> = emptyList(),
    val contexts: List<NextActionContextDto> = emptyList(),
    val project: NextActionProjectDto? = null,
    val dueAt: String? = null,
    val deferUntil: String? = null,
    val scheduledAt: String? = null,
    val waitingFor: String? = null,
    val waitingSince: String? = null,
    val followUpAt: String? = null,
    val reviewedAt: String? = null,
)

@Serializable
data class NowResponseDto(
    val availableNextActions: List<NowItemDto> = emptyList(),
    val scheduledToday: List<NowItemDto> = emptyList(),
    val overdue: List<NowItemDto> = emptyList(),
    val waitingForReview: List<NowItemDto> = emptyList(),
)

data class NowItem(
    val itemId: String,
    val taskId: String? = null,
    val gtdState: String? = null,
    val title: String,
    val note: String,
    val itemKey: AES.GCM.Key,
    val createdAt: String,
    val updatedAt: String,
    val attachments: List<DecryptedAttachment> = emptyList(),
    val tags: List<InboxTagDto> = emptyList(),
    val contexts: List<NextActionContextDto> = emptyList(),
    val project: DecryptedProject? = null,
    val dueAt: String? = null,
    val deferUntil: String? = null,
    val scheduledAt: String? = null,
    val waitingFor: String? = null,
    val waitingSince: String? = null,
    val followUpAt: String? = null,
    val reviewedAt: String? = null,
    val isPending: Boolean = false,
) {
    val isPendingConfirmation: Boolean
        get() = isPending || itemId.startsWith("temp_")

    fun toNextAction(): NextAction = NextAction(
        itemId = itemId,
        title = title,
        note = note,
        itemKey = itemKey,
        createdAt = createdAt,
        updatedAt = updatedAt,
        attachments = attachments,
        tags = tags,
        project = project,
        dueAt = dueAt,
        contexts = contexts,
    )
}

data class NowData(
    val availableNextActions: List<NowItem> = emptyList(),
    val scheduledToday: List<NowItem> = emptyList(),
    val overdue: List<NowItem> = emptyList(),
    val waitingForReview: List<NowItem> = emptyList(),
) {
    val isEmpty: Boolean
        get() = availableNextActions.isEmpty() &&
                scheduledToday.isEmpty() &&
                overdue.isEmpty() &&
                waitingForReview.isEmpty()
}
