package pl.quicktask.app.projects.data

import dev.whyoleg.cryptography.algorithms.AES
import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import pl.quicktask.app.auth.crypto.createItemKey
import pl.quicktask.app.auth.model.ApiException
import pl.quicktask.app.items.data.FileOperations
import pl.quicktask.app.items.data.ItemQueries
import pl.quicktask.app.items.domain.ItemCryptoMapper
import pl.quicktask.app.items.domain.ItemViewRefresher
import pl.quicktask.app.items.model.AttachmentLimitException
import pl.quicktask.app.items.model.CreateInboxItemResponseDto
import pl.quicktask.app.items.model.InputFile
import pl.quicktask.app.items.model.ItemSyncStateResponseDto
import pl.quicktask.app.items.model.MAX_ATTACHMENTS
import pl.quicktask.app.items.model.UncertainItemWriteException
import pl.quicktask.app.items.model.itemResult
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.network.client.AuthenticatedApiClient
import pl.quicktask.app.projects.model.ConvertInboxToWaitingRequestDto
import pl.quicktask.app.projects.model.ProjectsResult

interface ProjectsOperations {
    suspend fun getProjects(forceFetch: Boolean = false): Result<ProjectsResult>
    suspend fun createProject(
        title: String,
        note: String = "",
        files: List<InputFile> = emptyList(),
        onProgress: ((Float) -> Unit)? = null,
        itemKey: AES.GCM.Key? = null,
    ): Result<String>
    suspend fun convertFromInbox(itemId: String): Result<Unit>
    suspend fun convertInboxToWaiting(
        itemId: String,
        projectId: String? = null,
        dueAt: String? = null,
        waitingFor: String,
        followUpAt: String? = null,
    ): Result<Unit>
    suspend fun createWaitingTask(
        title: String,
        note: String = "",
        projectId: String? = null,
        dueAt: String? = null,
        waitingFor: String,
        followUpAt: String? = null,
        files: List<InputFile> = emptyList(),
        onProgress: ((Float) -> Unit)? = null,
        itemKey: AES.GCM.Key? = null,
    ): Result<String>
    suspend fun getProjectSyncState(projectId: String): Result<ItemSyncStateResponseDto>
}

class ProjectsRepository(
    private val api: AuthenticatedApiClient,
    private val mapper: ItemCryptoMapper,
    private val store: ItemStore,
    private val refresher: ItemViewRefresher,
    private val filesRepository: FileOperations,
    private val queries: ItemQueries,
) : ProjectsOperations {

    override suspend fun getProjects(forceFetch: Boolean): Result<ProjectsResult> = queries.getProjects(forceFetch)

    override suspend fun createProject(
        title: String,
        note: String,
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
            val request = mapper.createProjectRequest(
                title = title,
                note = note,
                itemKey = itemKey ?: createItemKey(),
            )
            requestSent = true
            val response = api.request(HttpMethod.Post, "items") {
                contentType(ContentType.Application.Json)
                setBody(request.copy(fileIds = uploadedIds.ifEmpty { null }))
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

    override suspend fun convertFromInbox(itemId: String): Result<Unit> = itemResult {
        api.request(HttpMethod.Post, "inbox/$itemId/project")
        store.invalidateCache()
        refresher.refreshViews(inbox = true)
    }

    override suspend fun convertInboxToWaiting(
        itemId: String,
        projectId: String?,
        dueAt: String?,
        waitingFor: String,
        followUpAt: String?,
    ): Result<Unit> = itemResult {
        val request = ConvertInboxToWaitingRequestDto(
            projectId = projectId,
            dueAt = dueAt,
            waitingFor = waitingFor,
            followUpAt = followUpAt,
        )
        api.request(HttpMethod.Post, "inbox/$itemId/waiting") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        store.invalidateCache()
        refresher.refreshViews(inbox = true)
    }

    override suspend fun createWaitingTask(
        title: String,
        note: String,
        projectId: String?,
        dueAt: String?,
        waitingFor: String,
        followUpAt: String?,
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
            val request = mapper.createWaitingTaskRequest(
                title = title,
                note = note,
                projectId = projectId,
                dueAt = dueAt,
                waitingFor = waitingFor,
                followUpAt = followUpAt,
                fileIds = uploadedIds.ifEmpty { null },
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

    override suspend fun getProjectSyncState(projectId: String): Result<ItemSyncStateResponseDto> = itemResult {
        api.request(HttpMethod.Get, "inbox/$projectId/sync-state").body<ItemSyncStateResponseDto>()
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
