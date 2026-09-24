package pl.quicktask.app.projects.model

import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.serialization.Serializable
import pl.quicktask.app.items.model.DecryptedAttachment
import pl.quicktask.app.items.model.InboxAttachmentDto
import pl.quicktask.app.items.model.InboxTagDto
import pl.quicktask.app.nextactions.model.NextActionContextDto
import pl.quicktask.app.scheduled.model.RecurrenceRule

@Serializable
data class ProjectTaskDto(
    val itemId: String,
    val taskId: String,
    val encryptedTitle: String,
    val encryptedNote: String? = null,
    val encryptedItemKey: String,
    val createdAt: String,
    val updatedAt: String,
    val attachments: List<InboxAttachmentDto> = emptyList(),
    val tags: List<InboxTagDto> = emptyList(),
    val recurrence: RecurrenceRule? = null,
    val gtdState: String, // "NEXT", "WAITING", "SCHEDULED"
    val dueAt: String? = null,
    val deferUntil: String? = null,
    val scheduledAt: String? = null,
    val waitingFor: String? = null,
    val assignedByUserId: String? = null,
    val waitingSince: String? = null,
    val followUpAt: String? = null,
    val reviewedAt: String? = null,
    val contexts: List<NextActionContextDto> = emptyList(),
)

@Serializable
data class ProjectWithTasksDto(
    val itemId: String,
    val projectId: String,
    val encryptedTitle: String,
    val encryptedNote: String? = null,
    val encryptedItemKey: String,
    val createdAt: String,
    val updatedAt: String,
    val attachments: List<InboxAttachmentDto> = emptyList(),
    val tags: List<InboxTagDto> = emptyList(),
    val dueAt: String? = null,
    val tasks: List<ProjectTaskDto> = emptyList(),
)

@Serializable
data class ProjectsResponseDto(
    val projects: List<ProjectWithTasksDto> = emptyList(),
    val unassignedTasks: List<ProjectTaskDto> = emptyList(),
)

@Serializable
data class CreateWaitingTaskRequestDto(
    val assignedToUserId: String? = null,
    val recipientEncryptedItemKey: String? = null,
    val type: String = "WAITING",
    val encryptedTitle: String,
    val encryptedItemKey: String,
    val encryptedNote: String? = null,
    val fileIds: List<String>? = null,
    val projectId: String? = null,
    val dueAt: String? = null,
    val waitingFor: String,
    val followUpAt: String? = null,
)

@Serializable
data class ConvertInboxToWaitingRequestDto(
    val assignedToUserId: String? = null,
    val recipientEncryptedItemKey: String? = null,
    val projectId: String? = null,
    val dueAt: String? = null,
    val waitingFor: String,
    val followUpAt: String? = null,
)

data class DecryptedProjectTask(
    val itemId: String,
    val taskId: String,
    val title: String,
    val note: String,
    val itemKey: AES.GCM.Key,
    val createdAt: String,
    val updatedAt: String,
    val attachments: List<DecryptedAttachment> = emptyList(),
    val tags: List<InboxTagDto> = emptyList(),
    val recurrence: RecurrenceRule? = null,
    val gtdState: String, // "NEXT", "WAITING", "SCHEDULED"
    val dueAt: String? = null,
    val deferUntil: String? = null,
    val scheduledAt: String? = null,
    val waitingFor: String? = null,
    val waitingSince: String? = null,
    val followUpAt: String? = null,
    val reviewedAt: String? = null,
    val contexts: List<NextActionContextDto> = emptyList(),
)

data class ProjectWithTasks(
    val itemId: String,
    val projectId: String,
    val title: String,
    val note: String,
    val itemKey: AES.GCM.Key,
    val createdAt: String,
    val updatedAt: String,
    val attachments: List<DecryptedAttachment> = emptyList(),
    val tags: List<InboxTagDto> = emptyList(),
    val dueAt: String? = null,
    val tasks: List<DecryptedProjectTask> = emptyList(),
)

data class ProjectsResult(
    val projects: List<ProjectWithTasks> = emptyList(),
    val unassignedTasks: List<DecryptedProjectTask> = emptyList(),
)
