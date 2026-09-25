package pl.quicktask.app.somedaymaybe.data

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
import pl.quicktask.app.somedaymaybe.model.SomedayMaybeItem
import pl.quicktask.app.somedaymaybe.model.SomedayMaybeItemDto
import pl.quicktask.app.somedaymaybe.model.UpdateTagsRequestDto

interface SomedayMaybeOperations {
    suspend fun getSomedayMaybeItems(forceFetch: Boolean = false): Result<List<SomedayMaybeItem>>
    suspend fun createSomedayMaybe(
        title: String,
        note: String,
        tagIds: List<String> = emptyList(),
        newTagNames: List<String> = emptyList(),
        files: List<InputFile> = emptyList(),
        onProgress: ((Float) -> Unit)? = null,
        itemKey: AES.GCM.Key? = null,
    ): Result<String>
    suspend fun convertFromInbox(
        itemId: String,
        tagIds: List<String> = emptyList(),
        newTagNames: List<String> = emptyList(),
    ): Result<Unit>
    suspend fun convertTaskToSomedayMaybe(itemId: String): Result<Unit>
    suspend fun restoreToInbox(itemId: String): Result<Unit>
    suspend fun convertToNextAction(itemId: String): Result<Unit>
    suspend fun deleteSomedayMaybe(itemId: String): Result<Unit>
    suspend fun updateTags(
        itemId: String,
        tagIds: List<String> = emptyList(),
        newTagNames: List<String> = emptyList(),
    ): Result<Unit>
}

class SomedayMaybeRepository(
    private val api: AuthenticatedApiClient,
    private val mapper: ItemCryptoMapper,
    private val store: ItemStore,
    private val refresher: ItemViewRefresher,
    private val filesRepository: FileOperations,
) : SomedayMaybeOperations {

    override suspend fun getSomedayMaybeItems(forceFetch: Boolean): Result<List<SomedayMaybeItem>> {
        if (!forceFetch && store.isSomedayMaybeCacheValid) {
            return Result.success(store.somedayMaybeFlow.value)
        }
        return itemResult {
            val generation = store.generation
            val dtos = api.request(HttpMethod.Get, "inbox/someday-maybe").body<List<SomedayMaybeItemDto>>()
            val items = dtos.map { mapper.somedayMaybe(it) }
            store.cacheSomedayMaybe(items, generation)
            store.somedayMaybeFlow.value
        }
    }

    override suspend fun createSomedayMaybe(
        title: String,
        note: String,
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
            val request = mapper.createSomedayMaybeRequest(
                title = title,
                note = note,
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
            refresher.refreshViews(inbox = false) // Add true for somedayMaybe if we have a flag
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
        tagIds: List<String>,
        newTagNames: List<String>,
    ): Result<Unit> = itemResult {
        val request = UpdateTagsRequestDto(
            tagIds = tagIds,
            newTagNames = newTagNames,
        )
        api.request(HttpMethod.Post, "inbox/$itemId/someday-maybe") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        store.invalidateCache()
        refresher.refreshViews(inbox = true)
    }

    override suspend fun convertTaskToSomedayMaybe(itemId: String): Result<Unit> = itemResult {
        api.request(HttpMethod.Post, "inbox/$itemId/task/someday-maybe")
        store.invalidateCache()
        refresher.refreshViews(inbox = false) 
    }

    override suspend fun restoreToInbox(itemId: String): Result<Unit> = itemResult {
        api.request(HttpMethod.Post, "inbox/$itemId/someday-maybe/restore-to-inbox")
        store.invalidateCache()
        refresher.refreshViews(inbox = true)
    }

    override suspend fun convertToNextAction(itemId: String): Result<Unit> = itemResult {
        api.request(HttpMethod.Post, "inbox/$itemId/someday-maybe/next-action")
        store.invalidateCache()
        refresher.refreshViews(inbox = false)
    }

    override suspend fun deleteSomedayMaybe(itemId: String): Result<Unit> = itemResult {
        api.request(HttpMethod.Delete, "inbox/$itemId/someday-maybe")
        store.invalidateCache()
        refresher.refreshViews(inbox = false, trash = true)
    }

    override suspend fun updateTags(
        itemId: String,
        tagIds: List<String>,
        newTagNames: List<String>,
    ): Result<Unit> = itemResult {
        val request = UpdateTagsRequestDto(
            tagIds = tagIds,
            newTagNames = newTagNames,
        )
        api.request(HttpMethod.Put, "inbox/$itemId/tags") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        store.invalidateCache()
        refresher.refreshViews(inbox = false)
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
