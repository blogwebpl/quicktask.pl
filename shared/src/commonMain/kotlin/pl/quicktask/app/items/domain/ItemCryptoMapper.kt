package pl.quicktask.app.items.domain

import dev.whyoleg.cryptography.algorithms.AES
import kotlinx.serialization.json.Json
import pl.quicktask.app.auth.crypto.createItemKey
import pl.quicktask.app.auth.crypto.encryptText
import pl.quicktask.app.auth.crypto.wrapItemKey
import pl.quicktask.app.items.model.CompletedInTwoMinutesItemDto
import pl.quicktask.app.items.model.CreateInboxItemRequestDto
import pl.quicktask.app.items.model.InboxItem
import pl.quicktask.app.items.model.InboxItemDto
import pl.quicktask.app.items.model.SyncStateItemDto
import pl.quicktask.app.items.model.UpdateInboxItemRequestDto
import pl.quicktask.app.nextactions.model.CreateNextActionRequestDto
import pl.quicktask.app.nextactions.model.CreateProjectRequestDto
import pl.quicktask.app.nextactions.model.NewContextInput
import pl.quicktask.app.nextactions.model.NextActionDto
import pl.quicktask.app.nextactions.model.NextActionProjectDto
import pl.quicktask.app.nextactions.model.UpdateNextActionRequestDto
import pl.quicktask.app.now.model.NowItemDto
import pl.quicktask.app.references.model.CreateReferenceRequestDto
import pl.quicktask.app.references.model.ReferenceItemDto
import pl.quicktask.app.scheduled.model.CreateScheduledTaskRequestDto
import pl.quicktask.app.scheduled.model.RecurrenceRule
import pl.quicktask.app.scheduled.model.ScheduledTaskDto
import pl.quicktask.app.scheduled.model.UpdateScheduledTaskRequestDto
import pl.quicktask.app.somedaymaybe.model.CreateSomedayMaybeRequestDto
import pl.quicktask.app.somedaymaybe.model.SomedayMaybeItemDto
import pl.quicktask.app.trash.model.TrashItemDto

class ItemCryptoMapper(
    private val keysProvider: UserKeysProvider,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val reader = ItemReadMapper(keysProvider, json)

    suspend fun inbox(dto: InboxItemDto) = reader.inbox(dto)
    suspend fun completed(dto: CompletedInTwoMinutesItemDto) = reader.completed(dto)
    suspend fun trash(dto: TrashItemDto) = reader.trash(dto)
    suspend fun syncInbox(dto: SyncStateItemDto) = reader.syncInbox(dto)
    suspend fun syncCompleted(dto: SyncStateItemDto) = reader.syncCompleted(dto)
    suspend fun syncTrash(dto: SyncStateItemDto) = reader.syncTrash(dto)
    suspend fun project(dto: NextActionProjectDto) = reader.project(dto)
    suspend fun nextAction(dto: NextActionDto) = reader.nextAction(dto)
    suspend fun scheduledTask(dto: ScheduledTaskDto) = reader.scheduledTask(dto)
    suspend fun somedayMaybe(dto: SomedayMaybeItemDto) = reader.somedayMaybe(dto)
    suspend fun reference(dto: ReferenceItemDto) = reader.reference(dto)
    suspend fun nowItem(dto: NowItemDto) = reader.nowItem(dto)
    suspend fun syncNextAction(dto: SyncStateItemDto) = reader.syncNextAction(dto)
    suspend fun syncScheduledTask(dto: SyncStateItemDto) = reader.syncScheduledTask(dto)
    suspend fun syncSomedayMaybe(dto: SyncStateItemDto) = reader.syncSomedayMaybe(dto)
    suspend fun syncReference(dto: SyncStateItemDto) = reader.syncReference(dto)
    suspend fun projectTask(dto: pl.quicktask.app.projects.model.ProjectTaskDto) = reader.projectTask(dto)
    suspend fun projectWithTasks(dto: pl.quicktask.app.projects.model.ProjectWithTasksDto) = reader.projectWithTasks(dto)

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
