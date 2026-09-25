package pl.quicktask.app.references.data

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
import pl.quicktask.app.references.model.ReferenceItem
import pl.quicktask.app.references.model.ReferenceItemDto
import pl.quicktask.app.somedaymaybe.model.UpdateTagsRequestDto

interface ReferenceOperations {
    suspend fun getReferenceItems(forceFetch: Boolean = false): Result<List<ReferenceItem>>
    suspend fun createReference(
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
    suspend fun restoreToInbox(itemId: String): Result<Unit>
    suspend fun deleteReference(itemId: String): Result<Unit>
    suspend fun updateTags(
        itemId: String,
        tagIds: List<String> = emptyList(),
        newTagNames: List<String> = emptyList(),
    ): Result<Unit>
}

class ReferenceRepository(
    private val api: AuthenticatedApiClient,
    private val mapper: ItemCryptoMapper,
    private val store: ItemStore,
    private val refresher: ItemViewRefresher,
    private val filesRepository: FileOperations,
) : ReferenceOperations {

    override suspend fun getReferenceItems(forceFetch: Boolean): Result<List<ReferenceItem>> {
        if (!forceFetch && store.isReferencesCacheValid) {
            return Result.success(store.referencesFlow.value)
        }
        return itemResult {
            val generation = store.generation
            val dtos = api.request(HttpMethod.Get, "inbox/references").body<List<ReferenceItemDto>>()
            val items = dtos.map { mapper.reference(it) }
            store.cacheReferences(items, generation)
            store.referencesFlow.value
        }
    }

    override suspend fun createReference(
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
            val request = mapper.createReferenceRequest(
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
            val createdId = response.body<CreateInboxItemResponseDto>().itemId
            refreshAfterWrite(inbox = false)
            createdId
        } catch (error: Exception) {
            store.invalidateCache()
            if (!requestSent) cleanupUploads(uploadedIds)
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
        api.request(HttpMethod.Post, "inbox/$itemId/reference") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        refreshAfterWrite(inbox = true)
    }

    override suspend fun restoreToInbox(itemId: String): Result<Unit> = itemResult {
        api.request(HttpMethod.Post, "inbox/$itemId/reference/restore-to-inbox")
        refreshAfterWrite(inbox = true)
    }

    override suspend fun deleteReference(itemId: String): Result<Unit> = itemResult {
        api.request(HttpMethod.Delete, "inbox/$itemId/reference")
        refreshAfterWrite(inbox = false, trash = true)
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
        refreshAfterWrite(inbox = false)
    }

    private suspend fun refreshAfterWrite(inbox: Boolean, trash: Boolean = false) {
        store.invalidateCache()
        try {
            refresher.refreshViews(inbox = inbox, trash = trash)
            getReferenceItems(forceFetch = true).getOrThrow()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The server has already confirmed the write. Leave lists invalid for the next load.
            store.invalidateCache()
        }
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
