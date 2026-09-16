package pl.quicktask.app.inbox

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import pl.quicktask.app.auth.AuthRepository
import pl.quicktask.app.auth.DPoPManager
import pl.quicktask.app.auth.sharedDPoPManager
import pl.quicktask.app.auth.KeyStore
import pl.quicktask.app.auth.UserKeyPair
import pl.quicktask.app.auth.SessionManager
import pl.quicktask.app.auth.createItemKey
import pl.quicktask.app.auth.decryptText
import pl.quicktask.app.auth.encryptText
import pl.quicktask.app.auth.unwrapItemKey
import pl.quicktask.app.auth.wrapItemKey
import pl.quicktask.app.network.ApiConfig
import pl.quicktask.app.network.sharedHttpClient

val sharedInboxRepository: InboxRepository by lazy { InboxRepository() }

open class InboxRepository(
    private val httpClient: HttpClient = sharedHttpClient,
    private val dPoPManager: DPoPManager = sharedDPoPManager,
    private val sessionManager: SessionManager = SessionManager(),
    private val authRepository: AuthRepository = AuthRepository(
        httpClient = httpClient,
        dPoPManager = dPoPManager,
        sessionManager = sessionManager,
    ),
    private val baseUrl: String = ApiConfig.BASE_URL,
    private val filesRepository: FilesRepository = FilesRepository(
        httpClient = httpClient,
        dPoPManager = dPoPManager,
        sessionManager = sessionManager,
        authRepository = authRepository,
        baseUrl = baseUrl,
    ),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    private fun getAccessToken(): String {
        val token = sessionManager.accessToken
        if (token.isNullOrBlank()) {
            error("Brak tokenu dostępu (brak aktywnej sesji)")
        }
        return token
    }

    private suspend fun executeAuthenticated(
        method: String,
        url: String,
        block: suspend (accessToken: String, dpopProof: String) -> HttpResponse,
    ): HttpResponse {
        val initialToken = getAccessToken()
        val initialDpop = dPoPManager.generateDPoPProof(method, url, initialToken)
        var response = block(initialToken, initialDpop)

        if (response.status.value == 498) {
            val refreshedResult = authRepository.refreshSession()
            if (refreshedResult.isSuccess) {
                val newToken = getAccessToken()
                val newDpop = dPoPManager.generateDPoPProof(method, url, newToken)
                response = block(newToken, newDpop)
            }
        }

        return response
    }

    private suspend fun getUserKeys(): UserKeyPair {
        KeyStore.getSnapshot()?.let { return it }
        if (authRepository.tryRestoreCachedKeys()) {
            KeyStore.getSnapshot()?.let { return it }
        }
        return KeyStore.requireUserKeys()
    }

    private suspend fun decryptInboxItem(dto: InboxItemDto): InboxItem {
        val userKeys = getUserKeys()
        val itemKey = unwrapItemKey(userKeys.privateKey, dto.encryptedItemKey)

        val title = decryptText(itemKey, dto.encryptedTitle)
        val note = if (!dto.encryptedNote.isNullOrBlank()) {
            decryptText(itemKey, dto.encryptedNote)
        } else {
            ""
        }

        val decryptedAttachments = dto.attachments.map { att ->
            val fileKey = unwrapItemKey(userKeys.privateKey, att.encryptedFileKey)
            var name = "załącznik"
            var type = ""
            try {
                val metadataJson = decryptText(fileKey, att.encryptedMetadata)
                val meta = json.decodeFromString(FileMetadataDto.serializer(), metadataJson)
                name = meta.name
                type = meta.type
            } catch (_: Exception) {
                name = "Nie można odszyfrować załącznika"
            }

            DecryptedAttachment(
                attachmentId = att.attachmentId,
                fileId = att.fileId,
                name = name,
                type = type,
                ciphertextSha256 = att.ciphertextSha256,
                fileKey = fileKey,
            )
        }

        return InboxItem(
            itemId = dto.itemId,
            title = title,
            note = note,
            itemKey = itemKey,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            attachments = decryptedAttachments,
            tags = dto.tags,
        )
    }

    private val _itemsFlow = MutableStateFlow<List<InboxItem>>(emptyList())
    val itemsFlow: StateFlow<List<InboxItem>> = _itemsFlow.asStateFlow()

    private val _trashItemsFlow = MutableStateFlow<List<TrashItem>>(emptyList())
    val trashItemsFlow: StateFlow<List<TrashItem>> = _trashItemsFlow.asStateFlow()

    private var isCacheValid = false
    private var isTrashCacheValid = false

    fun invalidateCache() {
        isCacheValid = false
        isTrashCacheValid = false
    }

    fun addOptimisticItem(item: InboxItem) {
        _itemsFlow.value = listOf(item) + _itemsFlow.value
    }

    fun updateOptimisticItem(item: InboxItem) {
        _itemsFlow.value = _itemsFlow.value.map { if (it.itemId == item.itemId) item else it }
    }

    fun removeOptimisticItem(itemId: String) {
        _itemsFlow.value = _itemsFlow.value.filterNot { it.itemId == itemId }
    }

    fun setOptimisticItems(items: List<InboxItem>) {
        _itemsFlow.value = items
    }

    suspend fun getItems(forceFetch: Boolean = false): Result<List<InboxItem>> = withContext(Dispatchers.Default) {
        if (!forceFetch && isCacheValid) {
            return@withContext Result.success(_itemsFlow.value)
        }
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/inbox"

            val response = executeAuthenticated("GET", url) { token, dpop ->
                httpClient.get(url) {
                    header("Authorization", "DPoP $token")
                    header("DPoP", dpop)
                }
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                error("Błąd serwera (${response.status.value}): $errorText")
            }

            val dtos: List<InboxItemDto> = response.body()
            val activeDtos = dtos.filter { it.deletedAt == null }
            val items = activeDtos.map { decryptInboxItem(it) }
            _itemsFlow.value = items
            isCacheValid = true
            items
        }
    }

    suspend fun getItem(itemId: String): Result<InboxItem> = withContext(Dispatchers.Default) {
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/inbox/$itemId"

            val response = executeAuthenticated("GET", url) { token, dpop ->
                httpClient.get(url) {
                    header("Authorization", "DPoP $token")
                    header("DPoP", dpop)
                }
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                error("Błąd serwera (${response.status.value}): $errorText")
            }

            val dto: InboxItemDto = response.body()
            decryptInboxItem(dto)
        }
    }

    open suspend fun fetchItemSyncState(itemId: String): Result<ItemSyncStateResponseDto> = withContext(Dispatchers.Default) {
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/inbox/$itemId/sync-state"

            val response = executeAuthenticated("GET", url) { token, dpop ->
                httpClient.get(url) {
                    header("Authorization", "DPoP $token")
                    header("DPoP", dpop)
                }
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                error("Błąd pobierania stanu synchronizacji ($itemId): (${response.status.value}) $errorText")
            }

            response.body<ItemSyncStateResponseDto>()
        }
    }

    open suspend fun handleSyncStateForItem(itemId: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val syncStateResult = fetchItemSyncState(itemId)
            if (syncStateResult.isSuccess) {
                val dto = syncStateResult.getOrThrow()
                applySyncState(dto, itemId)
            } else {
                refreshActiveViews()
            }
            Unit
        }
    }

    open suspend fun applySyncState(dto: ItemSyncStateResponseDto, itemIdParam: String) {
        val location = dto.location
        val targetItemId = dto.itemId ?: dto.item?.itemId ?: itemIdParam

        if (location == "removed") {
            if (isCacheValid) {
                _itemsFlow.value = _itemsFlow.value.filterNot { it.itemId == targetItemId }
            }
            if (isTrashCacheValid) {
                _trashItemsFlow.value = _trashItemsFlow.value.filterNot { it.itemId == targetItemId }
            }
            return
        }

        val itemDto = dto.item ?: return
        val userKeys = getUserKeys()
        val itemKey = unwrapItemKey(userKeys.privateKey, itemDto.encryptedItemKey)
        val title = decryptText(itemKey, itemDto.encryptedTitle)
        val note = if (!itemDto.encryptedNote.isNullOrBlank()) decryptText(itemKey, itemDto.encryptedNote) else ""
        val decryptedAttachments = itemDto.attachments.map { att ->
            val fileKey = unwrapItemKey(userKeys.privateKey, att.encryptedFileKey)
            var name = "załącznik"
            var type = ""
            try {
                val metadataJson = decryptText(fileKey, att.encryptedMetadata)
                val meta = json.decodeFromString(FileMetadataDto.serializer(), metadataJson)
                name = meta.name
                type = meta.type
            } catch (_: Exception) {
                name = "Nie można odszyfrować załącznika"
            }
            DecryptedAttachment(
                attachmentId = att.attachmentId,
                fileId = att.fileId,
                name = name,
                type = type,
                ciphertextSha256 = att.ciphertextSha256,
                fileKey = fileKey,
            )
        }

        when (location) {
            "inbox" -> {
                val updatedInboxItem = InboxItem(
                    itemId = itemDto.itemId,
                    title = title,
                    note = note,
                    itemKey = itemKey,
                    createdAt = itemDto.createdAt,
                    updatedAt = itemDto.updatedAt,
                    attachments = decryptedAttachments,
                    tags = itemDto.tags,
                )
                if (isCacheValid) {
                    val current = _itemsFlow.value
                    val existingIndex = current.indexOfFirst { it.itemId == targetItemId }
                    if (existingIndex >= 0) {
                        _itemsFlow.value = current.map { if (it.itemId == targetItemId) updatedInboxItem else it }
                    } else {
                        _itemsFlow.value = listOf(updatedInboxItem) + current
                    }
                }
                if (isTrashCacheValid) {
                    _trashItemsFlow.value = _trashItemsFlow.value.filterNot { it.itemId == targetItemId }
                }
            }
            "trash" -> {
                val updatedTrashItem = TrashItem(
                    itemId = itemDto.itemId,
                    title = title,
                    note = note,
                    itemKey = itemKey,
                    createdAt = itemDto.createdAt,
                    updatedAt = itemDto.updatedAt,
                    deletedAt = itemDto.deletedAt ?: itemDto.updatedAt,
                    purgeAfter = itemDto.purgeAfter,
                    isReference = itemDto.isReference,
                    isCompletedInTwoMinutes = itemDto.isCompletedInTwoMinutes,
                    isSomedayMaybe = itemDto.isSomedayMaybe,
                    isNextAction = itemDto.isNextAction,
                    isScheduled = itemDto.isScheduled,
                    attachments = decryptedAttachments,
                    tags = itemDto.tags,
                )
                if (isTrashCacheValid) {
                    val current = _trashItemsFlow.value
                    val existingIndex = current.indexOfFirst { it.itemId == targetItemId }
                    if (existingIndex >= 0) {
                        _trashItemsFlow.value = current.map { if (it.itemId == targetItemId) updatedTrashItem else it }
                    } else {
                        _trashItemsFlow.value = listOf(updatedTrashItem) + current
                    }
                }
                if (isCacheValid) {
                    _itemsFlow.value = _itemsFlow.value.filterNot { it.itemId == targetItemId }
                }
            }
            else -> {
                if (isCacheValid) {
                    _itemsFlow.value = _itemsFlow.value.filterNot { it.itemId == targetItemId }
                }
                if (isTrashCacheValid) {
                    _trashItemsFlow.value = _trashItemsFlow.value.filterNot { it.itemId == targetItemId }
                }
            }
        }
    }

    open suspend fun refreshActiveViews() = withContext(Dispatchers.Default) {
        invalidateCache()
        getItems(forceFetch = true)
        getTrashItems(forceFetch = true)
    }

    suspend fun createInboxItem(
        title: String,
        note: String,
        files: List<InputFile> = emptyList(),
        onProgress: ((Float) -> Unit)? = null,
    ): Result<CreateInboxItemResponseDto> = withContext(Dispatchers.Default) {
        runCatching {
            require(files.size <= 10) { "Maksymalna liczba załączników to 10" }
            val userKeys = getUserKeys()

            val itemKey = createItemKey()
            val encryptedTitle = encryptText(itemKey, title)
            val encryptedNote = if (note.isNotBlank()) encryptText(itemKey, note) else null
            val encryptedItemKey = wrapItemKey(userKeys.publicKey, itemKey)

            val uploadedFileIds = mutableListOf<String>()

            try {
                val totalFiles = files.size
                files.forEachIndexed { index, file ->
                    val uploadResult = filesRepository.uploadEncryptedFile(
                        fileName = file.fileName,
                        mimeType = file.mimeType,
                        fileBytes = file.bytes,
                        onProgress = { fileProgress ->
                            if (totalFiles > 0 && onProgress != null) {
                                val overallProgress = (index.toFloat() + fileProgress) / totalFiles.toFloat()
                                onProgress(overallProgress)
                            }
                        },
                    )
                    val fileId = uploadResult.getOrThrow()
                    uploadedFileIds.add(fileId)
                }
                if (totalFiles > 0) {
                    onProgress?.invoke(1.0f)
                }

                val url = "${baseUrl.trimEnd('/')}/inbox"

                val response = executeAuthenticated("POST", url) { token, dpop ->
                    httpClient.post(url) {
                        contentType(ContentType.Application.Json)
                        header("Authorization", "DPoP $token")
                        header("DPoP", dpop)
                        setBody(
                            CreateInboxItemRequestDto(
                                encryptedTitle = encryptedTitle,
                                encryptedNote = encryptedNote,
                                encryptedItemKey = encryptedItemKey,
                                fileIds = uploadedFileIds.ifEmpty { null },
                            ),
                        )
                    }
                }

                if (!response.status.isSuccess()) {
                    val errorText = response.bodyAsText()
                    error("Błąd tworzenia wpisu (${response.status.value}): $errorText")
                }

                invalidateCache()
                response.body()
            } catch (e: Exception) {
                for (fileId in uploadedFileIds) {
                    filesRepository.deleteUploadedFile(fileId)
                }
                throw e
            }
        }
    }

    suspend fun updateInboxItem(
        item: InboxItem,
        title: String,
        note: String,
        newFiles: List<InputFile> = emptyList(),
        removedAttachmentIds: List<String> = emptyList(),
    ): Result<UpdateInboxItemResponseDto> = withContext(Dispatchers.Default) {
        runCatching {
            val encryptedTitle = encryptText(item.itemKey, title)
            val encryptedNote = if (note.isNotBlank()) encryptText(item.itemKey, note) else null

            val url = "${baseUrl.trimEnd('/')}/inbox/${item.itemId}"

            val response = executeAuthenticated("PATCH", url) { token, dpop ->
                httpClient.patch(url) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "DPoP $token")
                    header("DPoP", dpop)
                    setBody(
                        UpdateInboxItemRequestDto(
                            encryptedTitle = encryptedTitle,
                            encryptedNote = encryptedNote,
                        ),
                    )
                }
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                error("Błąd aktualizacji wpisu (${response.status.value}): $errorText")
            }

            for (attachmentId in removedAttachmentIds) {
                val attUrl = "${baseUrl.trimEnd('/')}/inbox/${item.itemId}/attachments/$attachmentId"
                val attResponse = executeAuthenticated("DELETE", attUrl) { token, dpop ->
                    httpClient.delete(attUrl) {
                        header("Authorization", "DPoP $token")
                        header("DPoP", dpop)
                    }
                }
                if (!attResponse.status.isSuccess()) {
                    val errorText = attResponse.bodyAsText()
                    error("Błąd usuwania załącznika (${attResponse.status.value}): $errorText")
                }
            }

            for (file in newFiles) {
                val uploadResult = filesRepository.uploadEncryptedFile(
                    fileName = file.fileName,
                    mimeType = file.mimeType,
                    fileBytes = file.bytes,
                )
                val fileId = uploadResult.getOrThrow()

                try {
                    val attUrl = "${baseUrl.trimEnd('/')}/inbox/${item.itemId}/attachments"
                    val attResponse = executeAuthenticated("POST", attUrl) { token, dpop ->
                        httpClient.post(attUrl) {
                            contentType(ContentType.Application.Json)
                            header("Authorization", "DPoP $token")
                            header("DPoP", dpop)
                            setBody(mapOf("fileId" to fileId))
                        }
                    }
                    if (!attResponse.status.isSuccess()) {
                        val errorText = attResponse.bodyAsText()
                        error("Błąd dołączania pliku (${attResponse.status.value}): $errorText")
                    }
                } catch (e: Exception) {
                    filesRepository.deleteUploadedFile(fileId)
                    throw e
                }
            }

            invalidateCache()
            response.body()
        }
    }

    suspend fun deleteAttachment(itemId: String, attachmentId: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/inbox/$itemId/attachments/$attachmentId"

            val response = executeAuthenticated("DELETE", url) { token, dpop ->
                httpClient.delete(url) {
                    header("Authorization", "DPoP $token")
                    header("DPoP", dpop)
                }
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                error("Błąd usuwania załącznika (${response.status.value}): $errorText")
            }
            invalidateCache()
        }
    }

    suspend fun deleteItem(itemId: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/inbox/$itemId"

            val response = executeAuthenticated("DELETE", url) { token, dpop ->
                httpClient.delete(url) {
                    header("Authorization", "DPoP $token")
                    header("DPoP", dpop)
                }
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                error("Błąd serwera (${response.status.value}): $errorText")
            }
            invalidateCache()
            getItems(forceFetch = true)
            getTrashItems(forceFetch = true)
            Unit
        }
    }

    suspend fun permanentlyDeleteItem(itemId: String): Result<Unit> = withContext(Dispatchers.Default) {
        val previousTrashItems = _trashItemsFlow.value
        _trashItemsFlow.value = previousTrashItems.filterNot { it.itemId == itemId }

        runCatching {
            val url = "${baseUrl.trimEnd('/')}/inbox/$itemId/permanent"

            val response = executeAuthenticated("DELETE", url) { token, dpop ->
                httpClient.delete(url) {
                    header("Authorization", "DPoP $token")
                    header("DPoP", dpop)
                }
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                error("Błąd serwera (${response.status.value}): $errorText")
            }
            invalidateCache()
            getTrashItems(forceFetch = true)
            Unit
        }.onFailure {
            getTrashItems(forceFetch = true)
        }
    }

    open suspend fun emptyTrash(): Result<Unit> = withContext(Dispatchers.Default) {
        _trashItemsFlow.value = emptyList()

        runCatching {
            val url = "${baseUrl.trimEnd('/')}/inbox/trash"

            val response = executeAuthenticated("DELETE", url) { token, dpop ->
                httpClient.delete(url) {
                    header("Authorization", "DPoP $token")
                    header("DPoP", dpop)
                }
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                error("Błąd serwera (${response.status.value}): $errorText")
            }
            invalidateCache()
            getTrashItems(forceFetch = true)
            Unit
        }.onFailure {
            getTrashItems(forceFetch = true)
        }
    }

    suspend fun completeInTwoMinutes(itemId: String, deleteAttachments: Boolean = false): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/inbox/$itemId/complete-in-two-minutes"

            val response = executeAuthenticated("POST", url) { token, dpop ->
                httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "DPoP $token")
                    header("DPoP", dpop)
                    setBody(CompleteInTwoMinutesRequestDto(deleteAttachments = deleteAttachments))
                }
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                error("Błąd serwera (${response.status.value}): $errorText")
            }
            invalidateCache()
            getItems(forceFetch = true)
            if (deleteAttachments) {
                getTrashItems(forceFetch = true)
            }
            Unit
        }
    }

    suspend fun getCompletedInTwoMinutes(): Result<List<CompletedInTwoMinutesItem>> = withContext(Dispatchers.Default) {
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/inbox/completed-in-two-minutes"

            val response = executeAuthenticated("GET", url) { token, dpop ->
                httpClient.get(url) {
                    header("Authorization", "DPoP $token")
                    header("DPoP", dpop)
                }
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                error("Błąd serwera (${response.status.value}): $errorText")
            }

            val dtos: List<CompletedInTwoMinutesItemDto> = response.body()
            val userKeys = getUserKeys()
            dtos.map { dto ->
                val itemKey = unwrapItemKey(userKeys.privateKey, dto.encryptedItemKey)
                val title = decryptText(itemKey, dto.encryptedTitle)
                val note = if (!dto.encryptedNote.isNullOrBlank()) {
                    decryptText(itemKey, dto.encryptedNote)
                } else {
                    ""
                }
                val decryptedAttachments = dto.attachments.map { att ->
                    val fileKey = unwrapItemKey(userKeys.privateKey, att.encryptedFileKey)
                    var name = "załącznik"
                    var type = ""
                    try {
                        val metadataJson = decryptText(fileKey, att.encryptedMetadata)
                        val meta = json.decodeFromString(FileMetadataDto.serializer(), metadataJson)
                        name = meta.name
                        type = meta.type
                    } catch (_: Exception) {
                        name = "Nie można odszyfrować załącznika"
                    }

                    DecryptedAttachment(
                        attachmentId = att.attachmentId,
                        fileId = att.fileId,
                        name = name,
                        type = type,
                        ciphertextSha256 = att.ciphertextSha256,
                        fileKey = fileKey,
                    )
                }

                CompletedInTwoMinutesItem(
                    itemId = dto.itemId,
                    title = title,
                    note = note,
                    itemKey = itemKey,
                    createdAt = dto.createdAt,
                    updatedAt = dto.updatedAt,
                    processedAt = dto.processedAt,
                    attachments = decryptedAttachments,
                    tags = dto.tags,
                )
            }
        }
    }

    suspend fun restoreFromTwoMinutes(itemId: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/inbox/$itemId/restore-from-two-minutes"

            val response = executeAuthenticated("POST", url) { token, dpop ->
                httpClient.post(url) {
                    header("Authorization", "DPoP $token")
                    header("DPoP", dpop)
                }
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                error("Błąd serwera (${response.status.value}): $errorText")
            }
            invalidateCache()
            getItems(forceFetch = true)
            Unit
        }
    }

    suspend fun deleteCompletedInTwoMinutes(itemId: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/inbox/$itemId/completed-in-two-minutes"

            val response = executeAuthenticated("DELETE", url) { token, dpop ->
                httpClient.delete(url) {
                    header("Authorization", "DPoP $token")
                    header("DPoP", dpop)
                }
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                error("Błąd serwera (${response.status.value}): $errorText")
            }
            invalidateCache()
            getTrashItems(forceFetch = true)
            Unit
        }
    }

    suspend fun getTrashItems(forceFetch: Boolean = false): Result<List<TrashItem>> = withContext(Dispatchers.Default) {
        if (!forceFetch && isTrashCacheValid) {
            return@withContext Result.success(_trashItemsFlow.value)
        }
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/inbox/trash"

            val response = executeAuthenticated("GET", url) { token, dpop ->
                httpClient.get(url) {
                    header("Authorization", "DPoP $token")
                    header("DPoP", dpop)
                }
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                error("Błąd serwera (${response.status.value}): $errorText")
            }

            val dtos: List<TrashItemDto> = response.body()
            val userKeys = getUserKeys()
            val items = dtos.map { dto ->
                val itemKey = unwrapItemKey(userKeys.privateKey, dto.encryptedItemKey)
                val title = decryptText(itemKey, dto.encryptedTitle)
                val note = if (!dto.encryptedNote.isNullOrBlank()) {
                    decryptText(itemKey, dto.encryptedNote)
                } else {
                    ""
                }
                val decryptedAttachments = dto.attachments.map { att ->
                    val fileKey = unwrapItemKey(userKeys.privateKey, att.encryptedFileKey)
                    var name = "załącznik"
                    var type = ""
                    try {
                        val metadataJson = decryptText(fileKey, att.encryptedMetadata)
                        val meta = json.decodeFromString(FileMetadataDto.serializer(), metadataJson)
                        name = meta.name
                        type = meta.type
                    } catch (_: Exception) {
                        name = "Nie można odszyfrować załącznika"
                    }

                    DecryptedAttachment(
                        attachmentId = att.attachmentId,
                        fileId = att.fileId,
                        name = name,
                        type = type,
                        ciphertextSha256 = att.ciphertextSha256,
                        fileKey = fileKey,
                    )
                }

                TrashItem(
                    itemId = dto.itemId,
                    title = title,
                    note = note,
                    itemKey = itemKey,
                    createdAt = dto.createdAt,
                    updatedAt = dto.updatedAt,
                    deletedAt = dto.deletedAt,
                    purgeAfter = dto.purgeAfter,
                    isReference = dto.isReference,
                    isCompletedInTwoMinutes = dto.isCompletedInTwoMinutes,
                    isSomedayMaybe = dto.isSomedayMaybe,
                    isNextAction = dto.isNextAction,
                    isScheduled = dto.isScheduled,
                    attachments = decryptedAttachments,
                    tags = dto.tags,
                )
            }
            _trashItemsFlow.value = items
            isTrashCacheValid = true
            items
        }
    }

    suspend fun restoreFromTrash(itemId: String): Result<Unit> = withContext(Dispatchers.Default) {
        _trashItemsFlow.value = _trashItemsFlow.value.filterNot { it.itemId == itemId }

        runCatching {
            val url = "${baseUrl.trimEnd('/')}/inbox/$itemId/restore"

            val response = executeAuthenticated("POST", url) { token, dpop ->
                httpClient.post(url) {
                    header("Authorization", "DPoP $token")
                    header("DPoP", dpop)
                }
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                error("Błąd serwera (${response.status.value}): $errorText")
            }
            invalidateCache()
            getItems(forceFetch = true)
            getTrashItems(forceFetch = true)
            Unit
        }.onFailure {
            getTrashItems(forceFetch = true)
        }
    }

    suspend fun deleteNextAction(itemId: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/inbox/$itemId/next-action"

            val response = executeAuthenticated("DELETE", url) { token, dpop ->
                httpClient.delete(url) {
                    header("Authorization", "DPoP $token")
                    header("DPoP", dpop)
                }
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                error("Błąd serwera (${response.status.value}): $errorText")
            }
            invalidateCache()
            getTrashItems(forceFetch = true)
            Unit
        }
    }

    suspend fun deleteScheduled(itemId: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/inbox/$itemId/scheduled"

            val response = executeAuthenticated("DELETE", url) { token, dpop ->
                httpClient.delete(url) {
                    header("Authorization", "DPoP $token")
                    header("DPoP", dpop)
                }
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                error("Błąd serwera (${response.status.value}): $errorText")
            }
            invalidateCache()
            getTrashItems(forceFetch = true)
            Unit
        }
    }

    suspend fun deleteSomedayMaybe(itemId: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/inbox/$itemId/someday-maybe"

            val response = executeAuthenticated("DELETE", url) { token, dpop ->
                httpClient.delete(url) {
                    header("Authorization", "DPoP $token")
                    header("DPoP", dpop)
                }
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                error("Błąd serwera (${response.status.value}): $errorText")
            }
            invalidateCache()
            getTrashItems(forceFetch = true)
            Unit
        }
    }

    suspend fun deleteReference(itemId: String): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            val url = "${baseUrl.trimEnd('/')}/inbox/$itemId/reference"

            val response = executeAuthenticated("DELETE", url) { token, dpop ->
                httpClient.delete(url) {
                    header("Authorization", "DPoP $token")
                    header("DPoP", dpop)
                }
            }

            if (!response.status.isSuccess()) {
                val errorText = response.bodyAsText()
                error("Błąd serwera (${response.status.value}): $errorText")
            }
            invalidateCache()
            getTrashItems(forceFetch = true)
            Unit
        }
    }
}
