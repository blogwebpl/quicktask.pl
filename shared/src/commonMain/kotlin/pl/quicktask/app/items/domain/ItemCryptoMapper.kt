package pl.quicktask.app.items.domain

import pl.quicktask.app.scheduled.model.RecurrenceRule

import pl.quicktask.app.items.model.decryptItem

import pl.quicktask.app.items.model.AttachmentMetadataState
import pl.quicktask.app.items.model.CompletedInTwoMinutesItem
import pl.quicktask.app.items.model.CompletedInTwoMinutesItemDto
import pl.quicktask.app.items.model.CreateInboxItemRequestDto
import pl.quicktask.app.items.model.DecryptedAttachment
import pl.quicktask.app.items.model.FileMetadataDto
import pl.quicktask.app.items.model.InboxAttachmentDto
import pl.quicktask.app.items.model.InboxItem
import pl.quicktask.app.items.model.InboxItemDto
import pl.quicktask.app.items.model.SyncStateItemDto
import pl.quicktask.app.items.model.UpdateInboxItemRequestDto
import pl.quicktask.app.items.model.itemResult
import pl.quicktask.app.nextactions.model.CreateNextActionRequestDto
import pl.quicktask.app.nextactions.model.CreateProjectRequestDto
import pl.quicktask.app.nextactions.model.DecryptedProject
import pl.quicktask.app.nextactions.model.NextAction
import pl.quicktask.app.nextactions.model.NextActionContextDto
import pl.quicktask.app.nextactions.model.NextActionDto
import pl.quicktask.app.nextactions.model.NextActionProjectDto
import pl.quicktask.app.nextactions.model.UpdateNextActionRequestDto
import pl.quicktask.app.now.model.NowItem
import pl.quicktask.app.now.model.NowItemDto
import pl.quicktask.app.scheduled.model.CreateScheduledTaskRequestDto
import pl.quicktask.app.scheduled.model.ScheduledTask
import pl.quicktask.app.scheduled.model.ScheduledTaskDto
import pl.quicktask.app.scheduled.model.UpdateScheduledTaskRequestDto


import pl.quicktask.app.somedaymaybe.model.SomedayMaybeItem
import pl.quicktask.app.somedaymaybe.model.SomedayMaybeItemDto
import pl.quicktask.app.somedaymaybe.model.CreateSomedayMaybeRequestDto
import pl.quicktask.app.references.model.ReferenceItem
import pl.quicktask.app.references.model.ReferenceItemDto
import pl.quicktask.app.references.model.CreateReferenceRequestDto

import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.serialization.json.Json
import pl.quicktask.app.auth.crypto.createItemKey
import pl.quicktask.app.auth.crypto.decryptText
import pl.quicktask.app.auth.crypto.encryptText
import pl.quicktask.app.auth.crypto.unwrapItemKey
import pl.quicktask.app.auth.crypto.wrapItemKey
import pl.quicktask.app.nextactions.model.NewContextInput
import pl.quicktask.app.trash.model.TrashItem
import pl.quicktask.app.trash.model.TrashItemDto

class ItemCryptoMapper(
    private val keysProvider: UserKeysProvider,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private data class Content(
        val key: AES.GCM.Key, val title: String, val note: String,
        val attachments: List<DecryptedAttachment>,
    )

    private suspend fun content(
        encryptedItemKey: String, encryptedTitle: String, encryptedNote: String?,
        attachments: List<InboxAttachmentDto>,
    ): Content {
        val keys = keysProvider.getUserKeys()
        return decryptItem {
            val itemKey = unwrapItemKey(keys.privateKey, encryptedItemKey)
            val decryptedAttachments = attachments.map { att ->
                val fileKey = unwrapItemKey(keys.privateKey, att.encryptedFileKey)
                val metadata = itemResult {
                    json.decodeFromString<FileMetadataDto>(decryptText(fileKey, att.encryptedMetadata))
                }.getOrNull()
                DecryptedAttachment(
                    attachmentId = att.attachmentId, fileId = att.fileId,
                    name = metadata?.name, type = metadata?.type.orEmpty(),
                    ciphertextSha256 = att.ciphertextSha256, fileKey = fileKey,
                    metadataState = if (metadata == null) AttachmentMetadataState.DECRYPTION_FAILED
                        else AttachmentMetadataState.AVAILABLE,
                )
            }
            Content(
                itemKey, decryptText(itemKey, encryptedTitle),
                if (encryptedNote.isNullOrBlank()) "" else decryptText(itemKey, encryptedNote),
                decryptedAttachments,
            )
        }
    }

    suspend fun inbox(dto: InboxItemDto): InboxItem {
        val c = content(dto.encryptedItemKey, dto.encryptedTitle, dto.encryptedNote, dto.attachments)
        return InboxItem(dto.itemId, c.title, c.note, c.key, dto.createdAt, dto.updatedAt, c.attachments, dto.tags)
    }

    suspend fun completed(dto: CompletedInTwoMinutesItemDto): CompletedInTwoMinutesItem {
        val c = content(dto.encryptedItemKey, dto.encryptedTitle, dto.encryptedNote, dto.attachments)
        return CompletedInTwoMinutesItem(
            dto.itemId, c.title, c.note, c.key, dto.createdAt, dto.updatedAt,
            dto.processedAt, c.attachments, dto.tags,
        )
    }

    suspend fun trash(dto: TrashItemDto): TrashItem {
        val c = content(dto.encryptedItemKey, dto.encryptedTitle, dto.encryptedNote, dto.attachments)
        return TrashItem(
            dto.itemId, c.title, c.note, c.key, dto.createdAt, dto.updatedAt, dto.deletedAt, dto.purgeAfter,
            dto.isReference, dto.isCompletedInTwoMinutes, dto.isSomedayMaybe, dto.isNextAction,
            dto.isScheduled, c.attachments, dto.tags,
        )
    }

    suspend fun syncInbox(dto: SyncStateItemDto): InboxItem = inbox(
        InboxItemDto(dto.itemId, dto.encryptedTitle, dto.encryptedNote, dto.encryptedItemKey,
            dto.createdAt, dto.updatedAt, dto.deletedAt, dto.attachments, dto.tags),
    )

    suspend fun syncCompleted(dto: SyncStateItemDto): CompletedInTwoMinutesItem = completed(
        CompletedInTwoMinutesItemDto(
            dto.itemId, dto.encryptedTitle, dto.encryptedNote, dto.encryptedItemKey,
            dto.createdAt, dto.updatedAt, requireNotNull(dto.processedAt), dto.attachments, dto.tags,
        ),
    )

    suspend fun syncTrash(dto: SyncStateItemDto): TrashItem = trash(
        TrashItemDto(dto.itemId, dto.encryptedTitle, dto.encryptedNote, dto.encryptedItemKey,
            dto.createdAt, dto.updatedAt, dto.deletedAt ?: dto.updatedAt, dto.purgeAfter,
            dto.isReference, dto.isCompletedInTwoMinutes, dto.isSomedayMaybe, dto.isNextAction,
            dto.isScheduled, dto.attachments, dto.tags),
    )

    suspend fun project(dto: NextActionProjectDto): DecryptedProject {
        val keys = keysProvider.getUserKeys()
        return decryptItem {
            val projectKey = unwrapItemKey(keys.privateKey, dto.encryptedItemKey)
            val title = decryptText(projectKey, dto.encryptedTitle)
            DecryptedProject(dto.projectId, title, projectKey)
        }
    }

    suspend fun nextAction(dto: NextActionDto): NextAction {
        val c = content(dto.encryptedItemKey, dto.encryptedTitle, dto.encryptedNote, dto.attachments)
        val decryptedProject = dto.project?.let { project(it) }
        return NextAction(
            itemId = dto.itemId,
            title = c.title,
            note = c.note,
            itemKey = c.key,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            attachments = c.attachments,
            tags = dto.tags,
            project = decryptedProject,
            dueAt = dto.dueAt,
            contexts = dto.contexts,
        )
    }

    suspend fun scheduledTask(dto: ScheduledTaskDto): ScheduledTask {
        val c = content(dto.encryptedItemKey, dto.encryptedTitle, dto.encryptedNote, dto.attachments)
        val decryptedProject = dto.project?.let { project(it) }
        return ScheduledTask(
            recurrence = dto.recurrence,
            itemId = dto.itemId,
            title = c.title,
            note = c.note,
            itemKey = c.key,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            attachments = c.attachments,
            tags = dto.tags,
            project = decryptedProject,
            dueAt = dto.dueAt,
            contexts = dto.contexts,
            scheduledAt = dto.scheduledAt,
            deferUntil = dto.deferUntil,
        )
    }

    suspend fun somedayMaybe(dto: SomedayMaybeItemDto): SomedayMaybeItem {
        val c = content(dto.encryptedItemKey, dto.encryptedTitle, dto.encryptedNote, dto.attachments)
        return SomedayMaybeItem(
            itemId = dto.itemId,
            title = c.title,
            note = c.note,
            itemKey = c.key,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            attachments = c.attachments,
            tags = dto.tags,
        )
    }

    suspend fun reference(dto: ReferenceItemDto): ReferenceItem {
        val c = content(dto.encryptedItemKey, dto.encryptedTitle, dto.encryptedNote, dto.attachments)
        return ReferenceItem(
            itemId = dto.itemId,
            title = c.title,
            note = c.note,
            itemKey = c.key,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            attachments = c.attachments,
            tags = dto.tags,
        )
    }

    suspend fun nowItem(dto: NowItemDto): NowItem {
        val c = content(dto.encryptedItemKey, dto.encryptedTitle, dto.encryptedNote, dto.attachments)
        val decryptedProject = dto.project?.let { project(it) }
        return NowItem(
            itemId = dto.itemId,
            taskId = dto.taskId,
            gtdState = dto.gtdState,
            title = c.title,
            note = c.note,
            itemKey = c.key,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            attachments = c.attachments,
            tags = dto.tags,
            contexts = dto.contexts,
            project = decryptedProject,
            dueAt = dto.dueAt,
            deferUntil = dto.deferUntil,
            scheduledAt = dto.scheduledAt,
            waitingFor = dto.waitingFor?.takeIf { it.isNotBlank() }?.let { decryptText(c.key, it) },
            waitingSince = dto.waitingSince,
            followUpAt = dto.followUpAt,
            reviewedAt = dto.reviewedAt,
        )
    }

    suspend fun syncNextAction(dto: SyncStateItemDto): NextAction {
        val c = content(dto.encryptedItemKey, dto.encryptedTitle, dto.encryptedNote, dto.attachments)
        val decryptedProject = dto.project?.let {
            project(NextActionProjectDto(it.projectId, it.encryptedTitle, it.encryptedItemKey))
        }
        return NextAction(
            itemId = dto.itemId,
            title = c.title,
            note = c.note,
            itemKey = c.key,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            attachments = c.attachments,
            tags = dto.tags,
            project = decryptedProject,
            dueAt = dto.dueAt,
            contexts = dto.contexts.map { NextActionContextDto(it.contextId, it.name, it.lat, it.lon, it.radius) },
        )
    }

    suspend fun syncScheduledTask(dto: SyncStateItemDto): ScheduledTask {
        val c = content(dto.encryptedItemKey, dto.encryptedTitle, dto.encryptedNote, dto.attachments)
        val decryptedProject = dto.project?.let {
            project(NextActionProjectDto(it.projectId, it.encryptedTitle, it.encryptedItemKey))
        }
        return ScheduledTask(
            recurrence = dto.recurrence,
            itemId = dto.itemId,
            title = c.title,
            note = c.note,
            itemKey = c.key,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            attachments = c.attachments,
            tags = dto.tags,
            project = decryptedProject,
            dueAt = dto.dueAt,
            contexts = dto.contexts.map { NextActionContextDto(it.contextId, it.name, it.lat, it.lon, it.radius) },
            scheduledAt = requireNotNull(dto.scheduledAt) { "Scheduled sync item is missing scheduledAt" },
            deferUntil = dto.deferUntil,
        )
    }

    suspend fun syncSomedayMaybe(dto: SyncStateItemDto): SomedayMaybeItem {
        val c = content(dto.encryptedItemKey, dto.encryptedTitle, dto.encryptedNote, dto.attachments)
        return SomedayMaybeItem(
            itemId = dto.itemId,
            title = c.title,
            note = c.note,
            itemKey = c.key,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            attachments = c.attachments,
            tags = dto.tags,
        )
    }

    suspend fun syncReference(dto: SyncStateItemDto): ReferenceItem {
        val c = content(dto.encryptedItemKey, dto.encryptedTitle, dto.encryptedNote, dto.attachments)
        return ReferenceItem(
            itemId = dto.itemId,
            title = c.title,
            note = c.note,
            itemKey = c.key,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            attachments = c.attachments,
            tags = dto.tags,
        )
    }

    suspend fun projectTask(dto: pl.quicktask.app.projects.model.ProjectTaskDto): pl.quicktask.app.projects.model.DecryptedProjectTask {
        val c = content(dto.encryptedItemKey, dto.encryptedTitle, dto.encryptedNote, dto.attachments)
        return pl.quicktask.app.projects.model.DecryptedProjectTask(
            itemId = dto.itemId,
            taskId = dto.taskId,
            title = c.title,
            note = c.note,
            itemKey = c.key,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            attachments = c.attachments,
            tags = dto.tags,
            recurrence = dto.recurrence,
            gtdState = dto.gtdState,
            dueAt = dto.dueAt,
            deferUntil = dto.deferUntil,
            scheduledAt = dto.scheduledAt,
            waitingFor = dto.waitingFor?.takeIf { it.isNotBlank() }?.let { decryptText(c.key, it) },
            waitingSince = dto.waitingSince,
            followUpAt = dto.followUpAt,
            reviewedAt = dto.reviewedAt,
            contexts = dto.contexts,
        )
    }

    suspend fun projectWithTasks(dto: pl.quicktask.app.projects.model.ProjectWithTasksDto): pl.quicktask.app.projects.model.ProjectWithTasks {
        val c = content(dto.encryptedItemKey, dto.encryptedTitle, dto.encryptedNote, dto.attachments)
        val decryptedTasks = dto.tasks.map { projectTask(it) }
        return pl.quicktask.app.projects.model.ProjectWithTasks(
            itemId = dto.itemId,
            projectId = dto.projectId,
            title = c.title,
            note = c.note,
            itemKey = c.key,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            attachments = c.attachments,
            tags = dto.tags,
            dueAt = dto.dueAt,
            tasks = decryptedTasks,
        )
    }

    suspend fun createWaitingTaskRequest(
        title: String,
        note: String = "",
        projectId: String? = null,
        dueAt: String? = null,
        waitingFor: String,
        followUpAt: String? = null,
        fileIds: List<String>? = null,
        itemKey: AES.GCM.Key? = null,
    ): pl.quicktask.app.projects.model.CreateWaitingTaskRequestDto {
        val keys = keysProvider.getUserKeys()
        val key = itemKey ?: createItemKey()
        return pl.quicktask.app.projects.model.CreateWaitingTaskRequestDto(
            type = "WAITING",
            encryptedTitle = encryptText(key, title),
            encryptedItemKey = wrapItemKey(keys.publicKey, key),
            encryptedNote = if (note.isNotBlank()) encryptText(key, note) else null,
            fileIds = fileIds?.ifEmpty { null },
            projectId = projectId,
            dueAt = dueAt,
            waitingFor = encryptText(key, waitingFor),
            followUpAt = followUpAt,
        )
    }

    suspend fun createRequest(title: String, note: String, itemKey: AES.GCM.Key? = null): CreateInboxItemRequestDto {
        val keys = keysProvider.getUserKeys()
        val key = itemKey ?: createItemKey()
        return CreateInboxItemRequestDto(
            encryptedTitle = encryptText(key, title),
            encryptedItemKey = wrapItemKey(keys.publicKey, key),
            encryptedNote = if (note.isNotBlank()) encryptText(key, note) else null,
        )
    }

    suspend fun updateRequest(item: InboxItem, title: String, note: String) = UpdateInboxItemRequestDto(
        encryptedTitle = encryptText(item.itemKey, title),
        encryptedNote = if (note.isNotBlank()) encryptText(item.itemKey, note) else null,
    )

    suspend fun createNextActionRequest(
        title: String, note: String, projectId: String? = null, dueAt: String? = null,
        contextIds: List<String> = emptyList(), newContextNames: List<String> = emptyList(),
        newContexts: List<NewContextInput> = emptyList(),
        tagIds: List<String> = emptyList(), newTagNames: List<String> = emptyList(),
        fileIds: List<String>? = null, itemKey: AES.GCM.Key? = null,
    ): CreateNextActionRequestDto {
        val keys = keysProvider.getUserKeys()
        val key = itemKey ?: createItemKey()
        val combinedNewContextNames = (newContextNames + newContexts.map { it.name }).distinct()
        return CreateNextActionRequestDto(
            type = "NEXT_ACTION",
            encryptedTitle = encryptText(key, title),
            encryptedItemKey = wrapItemKey(keys.publicKey, key),
            encryptedNote = if (note.isNotBlank()) encryptText(key, note) else null,
            projectId = projectId,
            dueAt = dueAt,
            contextIds = contextIds,
            newContextNames = combinedNewContextNames,
            tagIds = tagIds,
            newTagNames = newTagNames,
            fileIds = fileIds?.ifEmpty { null },
        )
    }

    suspend fun createProjectRequest(
        title: String,
        note: String = "",
        itemKey: AES.GCM.Key? = null,
    ): CreateProjectRequestDto {
        val keys = keysProvider.getUserKeys()
        val key = itemKey ?: createItemKey()
        return CreateProjectRequestDto(
            type = "PROJECT",
            encryptedTitle = encryptText(key, title),
            encryptedItemKey = wrapItemKey(keys.publicKey, key),
            encryptedNote = if (note.isNotBlank()) encryptText(key, note) else null,
        )
    }

    suspend fun createSomedayMaybeRequest(
        title: String, note: String,
        tagIds: List<String> = emptyList(), newTagNames: List<String> = emptyList(),
        fileIds: List<String>? = null, itemKey: AES.GCM.Key? = null,
    ): CreateSomedayMaybeRequestDto {
        val keys = keysProvider.getUserKeys()
        val key = itemKey ?: createItemKey()
        return CreateSomedayMaybeRequestDto(
            type = "SOMEDAY_MAYBE",
            encryptedTitle = encryptText(key, title),
            encryptedItemKey = wrapItemKey(keys.publicKey, key),
            encryptedNote = if (note.isNotBlank()) encryptText(key, note) else null,
            tagIds = tagIds,
            newTagNames = newTagNames,
            fileIds = fileIds?.ifEmpty { null },
        )
    }

    suspend fun createReferenceRequest(
        title: String, note: String,
        tagIds: List<String> = emptyList(), newTagNames: List<String> = emptyList(),
        fileIds: List<String>? = null, itemKey: AES.GCM.Key? = null,
    ): CreateReferenceRequestDto {
        val keys = keysProvider.getUserKeys()
        val key = itemKey ?: createItemKey()
        return CreateReferenceRequestDto(
            type = "REFERENCE",
            encryptedTitle = encryptText(key, title),
            encryptedItemKey = wrapItemKey(keys.publicKey, key),
            encryptedNote = if (note.isNotBlank()) encryptText(key, note) else null,
            tagIds = tagIds,
            newTagNames = newTagNames,
            fileIds = fileIds?.ifEmpty { null },
        )
    }

    suspend fun createScheduledTaskRequest(
        recurrence: RecurrenceRule? = null,
        title: String, note: String, scheduledAt: String, deferUntil: String? = null,
        dueAt: String? = null, projectId: String? = null,
        contextIds: List<String> = emptyList(), newContextNames: List<String> = emptyList(),
        tagIds: List<String> = emptyList(), newTagNames: List<String> = emptyList(),
        fileIds: List<String>? = null, itemKey: AES.GCM.Key? = null,
    ): CreateScheduledTaskRequestDto {
        val keys = keysProvider.getUserKeys()
        val key = itemKey ?: createItemKey()
        return CreateScheduledTaskRequestDto(
            type = "SCHEDULED",
            encryptedTitle = encryptText(key, title),
            encryptedItemKey = wrapItemKey(keys.publicKey, key),
            encryptedNote = if (note.isNotBlank()) encryptText(key, note) else null,
            recurrence = recurrence,
            scheduledAt = scheduledAt,
            deferUntil = deferUntil,
            projectId = projectId,
            dueAt = dueAt,
            contextIds = contextIds,
            newContextNames = newContextNames,
            tagIds = tagIds,
            newTagNames = newTagNames,
            fileIds = fileIds?.ifEmpty { null },
        )
    }

    suspend fun updateScheduledTaskRequest(
        recurrence: RecurrenceRule? = null,
        itemKey: AES.GCM.Key, title: String, note: String, scheduledAt: String, deferUntil: String? = null,
        projectId: String? = null, dueAt: String? = null,
        contextIds: List<String> = emptyList(), newContextNames: List<String> = emptyList(),
        tagIds: List<String> = emptyList(), newTagNames: List<String> = emptyList(),
    ) = UpdateScheduledTaskRequestDto(
        encryptedTitle = encryptText(itemKey, title),
        encryptedNote = if (note.isNotBlank()) encryptText(itemKey, note) else null,
        recurrence = recurrence,
        scheduledAt = scheduledAt,
        deferUntil = deferUntil,
        projectId = projectId,
        dueAt = dueAt,
        contextIds = contextIds,
        newContextNames = newContextNames,
        tagIds = tagIds,
        newTagNames = newTagNames,
    )

    suspend fun updateNextActionRequest(
        itemKey: AES.GCM.Key, title: String, note: String,
        projectId: String? = null, dueAt: String? = null,
        contextIds: List<String> = emptyList(), newContextNames: List<String> = emptyList(),
        newContexts: List<NewContextInput> = emptyList(),
        tagIds: List<String> = emptyList(), newTagNames: List<String> = emptyList(),
    ) = UpdateNextActionRequestDto(
        encryptedTitle = encryptText(itemKey, title),
        encryptedNote = if (note.isNotBlank()) encryptText(itemKey, note) else null,
        projectId = projectId,
        dueAt = dueAt,
        contextIds = contextIds,
        newContextNames = (newContextNames + newContexts.map { it.name }).distinct(),
        tagIds = tagIds,
        newTagNames = newTagNames,
    )
}
