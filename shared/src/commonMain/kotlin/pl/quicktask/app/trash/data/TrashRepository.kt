package pl.quicktask.app.trash.data

import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import pl.quicktask.app.items.data.ItemQueries
import pl.quicktask.app.items.domain.ItemViewRefresher
import pl.quicktask.app.items.model.itemResult
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.items.store.TrashOperationKind
import pl.quicktask.app.network.client.AuthenticatedApiClient
import pl.quicktask.app.references.data.ReferenceOperations
import pl.quicktask.app.trash.model.TrashItem

interface TrashOperations {
    suspend fun getTrashItems(forceFetch: Boolean = false): Result<List<TrashItem>>
    suspend fun restoreFromTrash(itemId: String): Result<Unit>
    suspend fun permanentlyDeleteItem(itemId: String): Result<Unit>
    suspend fun emptyTrash(): Result<Unit>
}

class TrashRepository(
    private val api: AuthenticatedApiClient,
    private val store: ItemStore,
    private val queries: ItemQueries,
    private val refresher: ItemViewRefresher,
    private val references: ReferenceOperations? = null,
) : TrashOperations {
    override suspend fun getTrashItems(forceFetch: Boolean) = queries.getTrashItems(forceFetch)

    override suspend fun restoreFromTrash(itemId: String): Result<Unit> = updateTrash(
        itemId, TrashOperationKind.Restore, refreshInbox = true,
    ) { api.request(HttpMethod.Post, "inbox/$itemId/restore") }

    override suspend fun permanentlyDeleteItem(itemId: String): Result<Unit> = updateTrash(
        itemId, TrashOperationKind.PermanentDelete, refreshInbox = false,
    ) { api.request(HttpMethod.Delete, "inbox/$itemId/permanent") }

    override suspend fun emptyTrash(): Result<Unit> = updateTrash(null, null, refreshInbox = false) {
        api.request(HttpMethod.Delete, "inbox/trash")
    }

    private suspend fun updateTrash(
        itemId: String?,
        kind: TrashOperationKind?,
        refreshInbox: Boolean,
        request: suspend () -> Unit,
    ): Result<Unit> {
        val restoringReference = kind == TrashOperationKind.Restore &&
            store.trashItemsFlow.value.any { it.itemId == itemId && it.isReference }
        val operation = store.beginTrashOperation(itemId, kind)
            ?: return Result.failure(IllegalStateException("Trash operation already in progress"))
        try {
            val result = itemResult {
                request()
                if (itemId == null) store.setOptimisticTrash(emptyList()) else store.removeTrashItem(itemId)
                store.invalidateCache()
                refresher.refreshViews(inbox = refreshInbox)
                if (restoringReference) references?.getReferenceItems(forceFetch = true)
            }
            if (result.isFailure) {
                store.invalidateCache()
                queries.getTrashItems(forceFetch = true).onFailure { store.invalidateCache() }
            }
            return result
        } catch (error: CancellationException) {
            store.invalidateCache()
            throw error
        } finally {
            store.finishTrashOperation(operation)
        }
    }
}
