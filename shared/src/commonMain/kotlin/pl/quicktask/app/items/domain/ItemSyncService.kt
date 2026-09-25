package pl.quicktask.app.items.domain

import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import pl.quicktask.app.common.AppLoggerManager
import pl.quicktask.app.common.LogCategory
import pl.quicktask.app.common.LogLevel
import pl.quicktask.app.items.data.ItemQueries
import pl.quicktask.app.items.model.ItemSyncStateResponseDto
import pl.quicktask.app.items.model.itemResult
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.network.client.AuthenticatedApiClient

interface ItemViewRefresher {
    suspend fun refreshViews(inbox: Boolean = true, trash: Boolean = true)
}

interface ItemSyncOperations : ItemViewRefresher {
    suspend fun handleSyncStateForItem(itemId: String): Result<Unit>
    suspend fun refreshActiveViews()
}

class ItemSyncService(
    private val api: AuthenticatedApiClient,
    private val mapper: ItemCryptoMapper,
    private val store: ItemStore,
    private val queries: ItemQueries,
) : ItemSyncOperations {
    suspend fun fetchItemSyncState(itemId: String): Result<ItemSyncStateResponseDto> = itemResult {
        api.request(HttpMethod.Get, "inbox/$itemId/sync-state").body()
    }

    override suspend fun handleSyncStateForItem(itemId: String): Result<Unit> = itemResult {
        val result = fetchItemSyncState(itemId)
        if (result.isSuccess) {
            applySyncState(result.getOrThrow(), itemId)
            queries.getProjects(forceFetch = true).getOrThrow()
        } else refreshActiveViews()
    }

    suspend fun applySyncState(dto: ItemSyncStateResponseDto, itemIdParam: String) {
        val targetId = dto.itemId ?: dto.item?.itemId ?: itemIdParam
        if (dto.location == "removed") {
            store.applyRemoval(targetId)
            return
        }
        val item = dto.item ?: return
        when {
            dto.location == "inbox" -> store.applyInbox(mapper.syncInbox(item), targetId)
            dto.location == "trash" -> store.applyTrash(mapper.syncTrash(item), targetId)
            dto.location == "completed-in-two-minutes" -> store.applyCompleted(mapper.syncCompleted(item), targetId)
            dto.location == "references" || dto.location == "reference" || item.isReference -> {
                store.applyReference(mapper.syncReference(item), targetId)
            }
            dto.location == "scheduled" || item.isScheduled -> {
                store.applyScheduledTask(mapper.syncScheduledTask(item), targetId)
            }
            dto.location == "next-actions" || dto.location == "next-action" || item.isNextAction -> {
                store.applyNextAction(mapper.syncNextAction(item), targetId)
            }
            else -> store.applyRemoval(targetId)
        }
    }

    override suspend fun refreshActiveViews() {
        try {
            refreshViews()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppLoggerManager.log(
                level = LogLevel.ERROR,
                category = LogCategory.REFRESH,
                tag = "ItemSyncService",
                message = "Błąd podczas refreshViews() w refreshActiveViews: ${e.message}",
            )
        }
        val generation = store.generation
        try {
            val nextActions = api.request(HttpMethod.Get, "inbox/next-actions")
                .body<List<pl.quicktask.app.nextactions.model.NextActionDto>>()
                .map { mapper.nextAction(it) }
            store.cacheNextActions(nextActions, generation)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppLoggerManager.log(
                level = LogLevel.ERROR,
                category = LogCategory.REFRESH,
                tag = "ItemSyncService",
                message = "Błąd podczas pobierania next-actions w refreshActiveViews: ${e.message}",
            )
        }
        try {
            val tasks = api.request(HttpMethod.Get, "inbox/scheduled")
                .body<List<pl.quicktask.app.scheduled.model.ScheduledTaskDto>>()
                .map { mapper.scheduledTask(it) }
            store.cacheScheduledTasks(tasks, generation)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppLoggerManager.log(
                level = LogLevel.ERROR,
                category = LogCategory.REFRESH,
                tag = "ItemSyncService",
                message = "Błąd podczas pobierania scheduled w refreshActiveViews: ${e.message}",
            )
        }
    }

    override suspend fun refreshViews(inbox: Boolean, trash: Boolean) {
        store.invalidateCache()
        if (store.hasLoadedCompletedItems) queries.getCompletedInTwoMinutes().getOrThrow()
        queries.getProjects(forceFetch = true).getOrThrow()
        if (inbox) queries.getItems(forceFetch = true).getOrThrow()
        if (trash) queries.getTrashItems(forceFetch = true).getOrThrow()
    }
}
