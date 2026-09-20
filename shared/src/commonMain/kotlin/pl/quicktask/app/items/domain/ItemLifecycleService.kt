package pl.quicktask.app.items.domain

import io.ktor.http.HttpMethod
import pl.quicktask.app.items.data.ItemQueries
import pl.quicktask.app.items.model.itemResult
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.network.client.AuthenticatedApiClient

interface ItemLifecycleOperations {
    suspend fun deleteItem(itemId: String): Result<Unit>
    suspend fun deleteNextAction(itemId: String): Result<Unit>
    suspend fun deleteScheduled(itemId: String): Result<Unit>
    suspend fun deleteSomedayMaybe(itemId: String): Result<Unit>
    suspend fun deleteReference(itemId: String): Result<Unit>
}

class ItemLifecycleService(
    private val api: AuthenticatedApiClient,
    private val refresher: ItemViewRefresher,
) : ItemLifecycleOperations {
    override suspend fun deleteItem(itemId: String): Result<Unit> = itemResult {
        api.request(HttpMethod.Delete, "inbox/$itemId")
        refresher.refreshViews()
    }

    override suspend fun deleteNextAction(itemId: String) = deleteFromCategory(itemId, "next-action")
    override suspend fun deleteScheduled(itemId: String) = deleteFromCategory(itemId, "scheduled")
    override suspend fun deleteSomedayMaybe(itemId: String) = deleteFromCategory(itemId, "someday-maybe")
    override suspend fun deleteReference(itemId: String) = deleteFromCategory(itemId, "reference")

    private suspend fun deleteFromCategory(itemId: String, category: String): Result<Unit> = itemResult {
        api.request(HttpMethod.Delete, "inbox/$itemId/$category")
        refresher.refreshViews(inbox = false)
    }
}
