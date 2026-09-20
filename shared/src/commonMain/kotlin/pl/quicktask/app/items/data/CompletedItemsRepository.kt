package pl.quicktask.app.items.data

import io.ktor.client.call.body
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import pl.quicktask.app.items.domain.ItemCryptoMapper
import pl.quicktask.app.items.domain.ItemViewRefresher
import pl.quicktask.app.items.model.CompleteInTwoMinutesRequestDto
import pl.quicktask.app.items.model.CompletedInTwoMinutesItem
import pl.quicktask.app.items.model.CompletedInTwoMinutesItemDto
import pl.quicktask.app.items.model.itemResult
import pl.quicktask.app.network.client.AuthenticatedApiClient

interface CompletedItemsOperations {
    suspend fun getCompletedInTwoMinutes(): Result<List<CompletedInTwoMinutesItem>>
    suspend fun completeInTwoMinutes(itemId: String, deleteAttachments: Boolean = false): Result<Unit>
    suspend fun restoreFromTwoMinutes(itemId: String): Result<Unit>
    suspend fun deleteCompletedInTwoMinutes(itemId: String): Result<Unit>
}

class CompletedItemsRepository(
    private val api: AuthenticatedApiClient,
    private val mapper: ItemCryptoMapper,
    private val refresher: ItemViewRefresher,
) : CompletedItemsOperations {
    override suspend fun getCompletedInTwoMinutes(): Result<List<CompletedInTwoMinutesItem>> = itemResult {
        api.request(HttpMethod.Get, "inbox/completed-in-two-minutes").body<List<CompletedInTwoMinutesItemDto>>()
            .map { mapper.completed(it) }
    }

    override suspend fun completeInTwoMinutes(itemId: String, deleteAttachments: Boolean): Result<Unit> = itemResult {
        api.request(HttpMethod.Post, "inbox/$itemId/complete-in-two-minutes") {
            contentType(ContentType.Application.Json)
            setBody(CompleteInTwoMinutesRequestDto(deleteAttachments))
        }
        refresher.refreshViews(trash = deleteAttachments)
    }

    override suspend fun restoreFromTwoMinutes(itemId: String): Result<Unit> = itemResult {
        api.request(HttpMethod.Post, "inbox/$itemId/restore-from-two-minutes")
        refresher.refreshViews(trash = false)
    }

    override suspend fun deleteCompletedInTwoMinutes(itemId: String): Result<Unit> = itemResult {
        api.request(HttpMethod.Delete, "inbox/$itemId/completed-in-two-minutes")
        refresher.refreshViews(inbox = false)
    }
}
