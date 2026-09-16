package pl.quicktask.app.inbox

import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import todo.shared.generated.resources.Res
import todo.shared.generated.resources.screen_inbox
import todo.shared.generated.resources.screen_next_actions
import todo.shared.generated.resources.screen_reference
import todo.shared.generated.resources.screen_scheduled
import todo.shared.generated.resources.screen_someday
import todo.shared.generated.resources.trash_origin_2min

@Serializable
data class TrashItemDto(
    val itemId: String,
    val encryptedTitle: String,
    val encryptedNote: String? = null,
    val encryptedItemKey: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String,
    val purgeAfter: String? = null,
    val isReference: Boolean = false,
    val isCompletedInTwoMinutes: Boolean = false,
    val isSomedayMaybe: Boolean = false,
    val isNextAction: Boolean = false,
    val isScheduled: Boolean = false,
    val attachments: List<InboxAttachmentDto> = emptyList(),
    val tags: List<InboxTagDto> = emptyList(),
)

data class TrashItem(
    val itemId: String,
    val title: String,
    val note: String,
    val itemKey: AES.GCM.Key,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String,
    val purgeAfter: String?,
    val isReference: Boolean = false,
    val isCompletedInTwoMinutes: Boolean = false,
    val isSomedayMaybe: Boolean = false,
    val isNextAction: Boolean = false,
    val isScheduled: Boolean = false,
    val attachments: List<DecryptedAttachment> = emptyList(),
    val tags: List<InboxTagDto> = emptyList(),
    val isPending: Boolean = false,
) {
    val isPendingConfirmation: Boolean
        get() = isPending || itemId.startsWith("temp_")

    val originListNameRes: StringResource
        get() = when {
            isNextAction -> Res.string.screen_next_actions
            isScheduled -> Res.string.screen_scheduled
            isSomedayMaybe -> Res.string.screen_someday
            isReference -> Res.string.screen_reference
            isCompletedInTwoMinutes -> Res.string.trash_origin_2min
            else -> Res.string.screen_inbox
        }
}
