package pl.quicktask.app.nextactions.data

import dev.whyoleg.cryptography.algorithms.AES
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import pl.quicktask.app.auth.model.ApiException
import pl.quicktask.app.items.data.FileOperations
import pl.quicktask.app.items.domain.ItemCryptoMapper
import pl.quicktask.app.items.domain.ItemViewRefresher
import pl.quicktask.app.items.model.AttachmentLimitException
import pl.quicktask.app.items.model.CreateInboxItemResponseDto
import pl.quicktask.app.items.model.InputFile
import pl.quicktask.app.items.model.MAX_ATTACHMENTS
import pl.quicktask.app.items.model.UncertainItemWriteException
import pl.quicktask.app.items.model.itemResult
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.network.client.AuthenticatedApiClient
import pl.quicktask.app.auth.crypto.createItemKey
import pl.quicktask.app.nextactions.model.ConvertInboxToNextActionRequestDto
import pl.quicktask.app.nextactions.model.DecryptedProject
import pl.quicktask.app.nextactions.model.NewContextInput
import pl.quicktask.app.nextactions.model.NextAction
import pl.quicktask.app.nextactions.model.NextActionContext
import pl.quicktask.app.nextactions.model.NextActionDto
import pl.quicktask.app.nextactions.model.NextActionOptions
import pl.quicktask.app.nextactions.model.NextActionOptionsResponseDto
import pl.quicktask.app.nextactions.model.NextActionTag

interface NextActionsOperations {
    suspend fun getNextActions(forceFetch: Boolean = false): Result<List<NextAction>>
    suspend fun getNextActionOptions(): Result<NextActionOptions>
    suspend fun createNextAction(
        title: String,
        note: String,
        projectId: String? = null,
        dueAt: String? = null,
        contextIds: List<String> = emptyList(),
        newContextNames: List<String> = emptyList(),
        newContexts: List<NewContextInput> = emptyList(),
        tagIds: List<String> = emptyList(),
        newTagNames: List<String> = emptyList(),
        files: List<InputFile> = emptyList(),
        onProgress: ((Float) -> Unit)? = null,
        itemKey: AES.GCM.Key? = null,
    ): Result<String>
    suspend fun convertFromInbox(
        itemId: String,
        projectId: String? = null,
        dueAt: String? = null,
        contextIds: List<String> = emptyList(),
        newContextNames: List<String> = emptyList(),
        newContexts: List<NewContextInput> = emptyList(),
        tagIds: List<String> = emptyList(),
        newTagNames: List<String> = emptyList(),
    ): Result<Unit>
    suspend fun updateNextAction(
        item: NextAction,
        title: String,
        note: String,
        projectId: String? = null,
        dueAt: String? = null,
        contextIds: List<String> = emptyList(),
        newContextNames: List<String> = emptyList(),
        newContexts: List<NewContextInput> = emptyList(),
        tagIds: List<String> = emptyList(),
        newTagNames: List<String> = emptyList(),
    ): Result<Unit>
    suspend fun deleteNextAction(itemId: String): Result<Unit>
    suspend fun restoreToInbox(itemId: String): Result<Unit>
    suspend fun createProject(title: String, note: String = ""): Result<DecryptedProject>
}

class NextActionsRepository(
    private val api: AuthenticatedApiClient,
    private val mapper: ItemCryptoMapper,
    private val store: ItemStore,
    private val refresher: ItemViewRefresher,
    private val filesRepository: FileOperations,
) : NextActionsOperations {

    override suspend fun getNextActions(forceFetch: Boolean): Result<List<NextAction>> {
        if (!forceFetch && store.isNextActionsCacheValid) {
            return Result.success(store.nextActionsFlow.value)
        }
        return itemResult {
            val generation = store.generation
            val dtos = api.request(HttpMethod.Get, "inbox/next-actions").body<List<NextActionDto>>()
            val items = dtos.map { mapper.nextAction(it) }
            store.cacheNextActions(items, generation)
            store.nextActionsFlow.value
        }
    }

    override suspend fun getNextActionOptions(): Result<NextActionOptions> = itemResult {
        val dto = api.request(HttpMethod.Get, "inbox/next-action-options").body<NextActionOptionsResponseDto>()
        val projects = dto.projects.map { mapper.project(it) }
        val contexts = dto.contexts.map { NextActionContext(it.contextId, it.name, it.lat, it.lon, it.radius) }
        val tags = dto.tags.map { NextActionTag(it.tagId, it.name) }
        NextActionOptions(projects, contexts, tags)
    }

    override suspend fun createNextAction(
        title: String,
        note: String,
        projectId: String?,
        dueAt: String?,
        contextIds: List<String>,
        newContextNames: List<String>,
        newContexts: List<NewContextInput>,
        tagIds: List<String>,
        newTagNames: List<String>,
        files: List<InputFile>,
        onProgress: ((Float) -> Unit)?,
        itemKey: AES.GCM.Key?,
    ): Result<String> = itemResult {
        if (files.size > MAX_ATTACHMENTS) throw AttachmentLimitException()
        val uploadedIds = mutableListOf<String>()
        var requestSent = false
        try {
            files.forEachIndexed { index, file ->
                val id = filesRepository.uploadEncryptedFile(file.fileName, file.mimeType, file.bytes) { progress ->
                    onProgress?.invoke((index.toFloat() + progress) / files.size.toFloat())
                }.getOrThrow()
                uploadedIds.add(id)
            }
            if (files.isNotEmpty()) onProgress?.invoke(1f)
            val request = mapper.createNextActionRequest(
                title = title,
                note = note,
                projectId = projectId,
                dueAt = dueAt,
                contextIds = contextIds,
                newContextNames = newContextNames,
                newContexts = newContexts,
                tagIds = tagIds,
                newTagNames = newTagNames,
                fileIds = uploadedIds,
                itemKey = itemKey,
            )
            requestSent = true
            val response = api.request(HttpMethod.Post, "items") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            store.invalidateCache()
            refresher.refreshViews(inbox = false)
            response.body<CreateInboxItemResponseDto>().itemId
        } catch (error: Exception) {
            store.invalidateCache()
            cleanupUploads(uploadedIds)
            if (requestSent && error !is ApiException && error !is CancellationException) {
                throw UncertainItemWriteException(error)
            }
            throw error
        }
    }

    override suspend fun convertFromInbox(
        itemId: String,
        projectId: String?,
        dueAt: String?,
        contextIds: List<String>,
        newContextNames: List<String>,
        newContexts: List<NewContextInput>,
        tagIds: List<String>,
        newTagNames: List<String>,
    ): Result<Unit> = itemResult {
        val request = ConvertInboxToNextActionRequestDto(
            projectId = projectId,
            dueAt = dueAt,
            contextIds = contextIds,
            newContextNames = (newContextNames + newContexts.map { it.name }).distinct(),
            tagIds = tagIds,
            newTagNames = newTagNames,
        )
        api.request(HttpMethod.Post, "inbox/$itemId/next-action") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        store.invalidateCache()
        refresher.refreshViews(inbox = true)
    }

    override suspend fun updateNextAction(
        item: NextAction,
        title: String,
        note: String,
        projectId: String?,
        dueAt: String?,
        contextIds: List<String>,
        newContextNames: List<String>,
        newContexts: List<NewContextInput>,
        tagIds: List<String>,
        newTagNames: List<String>,
    ): Result<Unit> = itemResult {
        val request = mapper.updateNextActionRequest(
            itemKey = item.itemKey,
            title = title,
            note = note,
            projectId = projectId,
            dueAt = dueAt,
            contextIds = contextIds,
            newContextNames = newContextNames,
            newContexts = newContexts,
            tagIds = tagIds,
            newTagNames = newTagNames,
        )
        api.request(HttpMethod.Patch, "inbox/${item.itemId}/next-action") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        store.invalidateCache()
        refresher.refreshViews(inbox = false)
    }

    override suspend fun deleteNextAction(itemId: String): Result<Unit> = itemResult {
        api.request(HttpMethod.Delete, "inbox/$itemId/next-action")
        store.invalidateCache()
        refresher.refreshViews(inbox = false, trash = true)
    }

    override suspend fun restoreToInbox(itemId: String): Result<Unit> = itemResult {
        api.request(HttpMethod.Post, "inbox/$itemId/next-action/restore-to-inbox")
        store.invalidateCache()
        refresher.refreshViews(inbox = true)
    }

    override suspend fun createProject(title: String, note: String): Result<DecryptedProject> = itemResult {
        val itemKey = createItemKey()
        val request = mapper.createProjectRequest(title = title, note = note, itemKey = itemKey)
        val response = api.request(HttpMethod.Post, "items") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val itemId = response.body<CreateInboxItemResponseDto>().itemId
        store.invalidateCache()
        refresher.refreshViews(inbox = false)
        DecryptedProject(
            projectId = itemId,
            title = title,
            projectKey = itemKey,
        )
    }

    private suspend fun cleanupUploads(ids: List<String>) = withContext(NonCancellable) {
        for (id in ids) {
            try {
                filesRepository.deleteUploadedFile(id)
            } catch (_: Exception) {
            }
        }
    }
}
