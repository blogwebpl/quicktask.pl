package pl.quicktask.app.now.data

import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import pl.quicktask.app.items.domain.ItemCryptoMapper
import pl.quicktask.app.items.domain.ItemViewRefresher
import pl.quicktask.app.items.model.itemResult
import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.network.client.AuthenticatedApiClient
import pl.quicktask.app.now.model.NowData
import pl.quicktask.app.now.model.NowResponseDto

interface NowOperations {
    suspend fun getNowData(): Result<NowData>
    suspend fun deleteItem(itemId: String): Result<Unit>
    suspend fun restoreToInbox(itemId: String): Result<Unit>
}

class NowRepository(
    private val api: AuthenticatedApiClient,
    private val mapper: ItemCryptoMapper,
    private val refresher: ItemViewRefresher,
    private val store: ItemStore,
) : NowOperations {

    override suspend fun getNowData(): Result<NowData> = itemResult {
        val dto = api.request(HttpMethod.Get, "now").body<NowResponseDto>()
        NowData(
            availableNextActions = dto.availableNextActions.map { mapper.nowItem(it) },
            scheduledToday = dto.scheduledToday.map { mapper.nowItem(it) },
            overdue = dto.overdue.map { mapper.nowItem(it) },
            waitingForReview = dto.waitingForReview.map { mapper.nowItem(it) },
        )
    }

    override suspend fun deleteItem(itemId: String): Result<Unit> = itemResult {
        api.request(HttpMethod.Delete, "inbox/$itemId/next-action")
        store.invalidateCache()
        refresher.refreshViews(inbox = false, trash = true)
    }

    override suspend fun restoreToInbox(itemId: String): Result<Unit> = itemResult {
        api.request(HttpMethod.Post, "inbox/$itemId/next-action/restore-to-inbox")
        store.invalidateCache()
        refresher.refreshViews(inbox = true)
    }
}
