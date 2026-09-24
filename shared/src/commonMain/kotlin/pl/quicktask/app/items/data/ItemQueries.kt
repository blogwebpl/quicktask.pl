package pl.quicktask.app.items.data

import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.quicktask.app.items.domain.ItemCryptoMapper
import pl.quicktask.app.items.model.InboxItem
import pl.quicktask.app.items.model.CompletedInTwoMinutesItem
import pl.quicktask.app.items.model.CompletedInTwoMinutesItemDto
import pl.quicktask.app.items.model.InboxItemDto
import pl.quicktask.app.items.model.itemResult
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.network.client.AuthenticatedApiClient
import pl.quicktask.app.trash.model.TrashItem
import pl.quicktask.app.trash.model.TrashItemDto
import pl.quicktask.app.projects.model.ProjectsResponseDto
import pl.quicktask.app.projects.model.ProjectsResult

class ItemQueries(
    private val api: AuthenticatedApiClient,
    private val mapper: ItemCryptoMapper,
    private val store: ItemStore,
) {
    private val inboxFlight = SingleFlight<List<InboxItem>>()
    private val trashFlight = SingleFlight<List<TrashItem>>()
    private val projectsFlight = SingleFlight<ProjectsResult>()
    private val completedFlight = SingleFlight<List<CompletedInTwoMinutesItem>>()

    suspend fun getCompletedInTwoMinutes(): Result<List<CompletedInTwoMinutesItem>> =
        completedFlight.run(store.generation) {
            itemResult {
                while (true) {
                    val generation = store.generation
                    val items = api.request(HttpMethod.Get, "inbox/completed-in-two-minutes")
                        .body<List<CompletedInTwoMinutesItemDto>>().map { mapper.completed(it) }
                        .sortedByDescending { it.processedAt }
                    if (store.cacheCompleted(items, generation)) break
                }
                store.completedItemsFlow.value
            }
        }

    suspend fun getProjects(forceFetch: Boolean = false): Result<ProjectsResult> {
        if (!forceFetch && store.isProjectsCacheValid) return Result.success(store.projectsOverviewFlow.value)
        return projectsFlight.run(store.generation) {
            itemResult {
                while (true) {
                    val generation = store.generation
                    val response = api.request(HttpMethod.Get, "inbox/projects").body<ProjectsResponseDto>()
                    val projects = response.projects.map { mapper.projectWithTasks(it) }
                    val unassigned = response.unassignedTasks.map { mapper.projectTask(it) }
                    if (store.cacheProjects(projects, generation, unassigned)) break
                }
                store.projectsOverviewFlow.value
            }
        }
    }

    suspend fun getItems(
        forceFetch: Boolean = false,
        completedOperation: ItemStore.PendingOperation? = null,
    ): Result<List<InboxItem>> {
        // Reconciliation owns its snapshot and must not join a fetch that keeps the overlay.
        if (completedOperation != null) return fetchInbox(completedOperation)
        if (!forceFetch && store.isCacheValid) return Result.success(store.itemsFlow.value)
        return inboxFlight.run(store.generation) {
            fetchInbox()
        }
    }

    private suspend fun fetchInbox(completedOperation: ItemStore.PendingOperation? = null): Result<List<InboxItem>> =
        itemResult {
            while (true) {
                val generation = store.generation
                val dtos = api.request(HttpMethod.Get, "inbox").body<List<InboxItemDto>>()
                val items = dtos.filter { it.deletedAt == null }.map { mapper.inbox(it) }
                if (store.cacheInbox(items, generation, completedOperation)) break
            }
            store.itemsFlow.value
        }

    suspend fun getTrashItems(forceFetch: Boolean = false): Result<List<TrashItem>> {
        if (!forceFetch && store.isTrashCacheValid) return Result.success(store.trashItemsFlow.value)
        return trashFlight.run(store.generation) {
            itemResult {
                while (true) {
                    val generation = store.generation
                    val items = api.request(HttpMethod.Get, "inbox/trash").body<List<TrashItemDto>>().map { mapper.trash(it) }
                    if (store.cacheTrash(items, generation)) break
                }
                store.trashItemsFlow.value
            }
        }
    }
}

/** The initiating caller owns the request; cancellation of an awaiting caller does not cancel it. */
private class SingleFlight<T> {
    private val mutex = Mutex()
    private var current: Pair<Long, Deferred<Result<T>>>? = null
    suspend fun run(generation: Long, fetch: suspend () -> Result<T>): Result<T> = coroutineScope {
        val request = mutex.withLock {
            current?.takeIf { it.first == generation && !it.second.isCompleted }?.second
                ?: async { fetch() }.also { current = generation to it }
        }
        request.await()
    }
}
