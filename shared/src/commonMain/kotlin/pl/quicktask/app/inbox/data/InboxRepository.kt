package pl.quicktask.app.inbox.data

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
import pl.quicktask.app.items.data.ItemQueries
import pl.quicktask.app.items.domain.ItemCryptoMapper
import pl.quicktask.app.items.model.AttachmentLimitException
import pl.quicktask.app.items.model.CreateInboxItemResponseDto
import pl.quicktask.app.items.model.InboxItem
import pl.quicktask.app.items.model.InputFile
import pl.quicktask.app.items.model.MAX_ATTACHMENTS
import pl.quicktask.app.items.model.UncertainItemWriteException
import pl.quicktask.app.items.model.UpdateInboxItemResponseDto
import pl.quicktask.app.items.model.itemResult
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.network.client.AuthenticatedApiClient

interface InboxOperations {
    suspend fun getItems(
        forceFetch: Boolean = false,
        completedOperation: ItemStore.PendingOperation? = null,
    ): Result<List<InboxItem>>
    suspend fun getItem(itemId: String): Result<InboxItem>
    suspend fun createInboxItem(
        title: String, note: String, files: List<InputFile> = emptyList(), onProgress: ((Float) -> Unit)? = null,
        itemKey: AES.GCM.Key? = null,
    ): Result<String>
    suspend fun updateInboxItem(
        item: InboxItem, title: String, note: String, newFiles: List<InputFile> = emptyList(),
        removedAttachmentIds: List<String> = emptyList(),
    ): Result<Unit>
    suspend fun deleteAttachment(itemId: String, attachmentId: String): Result<Unit>
}

class InboxRepository(
    private val api: AuthenticatedApiClient,
    private val mapper: ItemCryptoMapper,
    private val store: ItemStore,
    private val queries: ItemQueries,
    private val filesRepository: FileOperations,
) : InboxOperations {
    override suspend fun getItems(forceFetch: Boolean, completedOperation: ItemStore.PendingOperation?) =
        queries.getItems(forceFetch, completedOperation)

    override suspend fun getItem(itemId: String): Result<InboxItem> = itemResult {
        mapper.inbox(api.request(HttpMethod.Get, "inbox/$itemId").body())
    }

    override suspend fun createInboxItem(
        title: String, note: String, files: List<InputFile>, onProgress: ((Float) -> Unit)?,
        itemKey: AES.GCM.Key?,
    ): Result<String> = itemResult {
        if (files.size > MAX_ATTACHMENTS) throw AttachmentLimitException()
        val request = mapper.createRequest(title, note, itemKey)
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
            requestSent = true
            val response = api.request(HttpMethod.Post, "inbox") {
                contentType(ContentType.Application.Json)
                setBody(request.copy(fileIds = uploadedIds.ifEmpty { null }))
            }
            store.invalidateCache()
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
    override suspend fun updateInboxItem(
        item: InboxItem, title: String, note: String, newFiles: List<InputFile>, removedAttachmentIds: List<String>,
    ): Result<Unit> = itemResult {
        val remaining = item.attachments.count { it.attachmentId !in removedAttachmentIds }
        if (remaining + newFiles.size > MAX_ATTACHMENTS) throw AttachmentLimitException()
        val request = mapper.updateRequest(item, title, note)
        val uploadedIds = mutableListOf<String>()
        var requestSent = false
        try {
            for (file in newFiles) {
                uploadedIds += filesRepository.uploadEncryptedFile(file.fileName, file.mimeType, file.bytes).getOrThrow()
            }
            requestSent = true
            val response = api.request(HttpMethod.Patch, "inbox/${item.itemId}") {
                contentType(ContentType.Application.Json)
                setBody(request.copy(
                    addedFileIds = uploadedIds.ifEmpty { null },
                    removedAttachmentIds = removedAttachmentIds.ifEmpty { null },
                ))
            }
            store.invalidateCache()
            response.body<UpdateInboxItemResponseDto>().let { Unit }
        } catch (error: Exception) {
            store.invalidateCache()
            cleanupUploads(uploadedIds)
            if (requestSent && error !is ApiException && error !is CancellationException) {
                throw UncertainItemWriteException(error)
            }
            throw error
        }
    }

    /** Deletion is ownership/reference checked by the server, including uncertain writes. */
    private suspend fun cleanupUploads(ids: List<String>) = withContext(NonCancellable) {
        for (id in ids) {
            try {
                filesRepository.deleteUploadedFile(id)
            } catch (_: Exception) {
                // The server's existing cleanup queue remains the fallback.
            }
        }
    }
    override suspend fun deleteAttachment(itemId: String, attachmentId: String): Result<Unit> = itemResult {
        api.request(HttpMethod.Delete, "inbox/$itemId/attachments/$attachmentId")
        store.invalidateCache()
    }
}
