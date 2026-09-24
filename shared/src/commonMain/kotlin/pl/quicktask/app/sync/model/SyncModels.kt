package pl.quicktask.app.sync.model

sealed class SyncEvent {
    data object SyncRequired : SyncEvent()
    data class ContactsChanged(
        val eventId: String,
        val changedAt: String? = null,
    ) : SyncEvent()
    data class ItemsChanged(
        val eventId: String,
        val itemId: String? = null,
        val changedAt: String? = null,
    ) : SyncEvent()
}

sealed class SyncState {
    data object Disconnected : SyncState()
    data object Connecting : SyncState()
    data object Connected : SyncState()
    data class Retrying(
        val attempt: Int,
        val nextRetryInMs: Long,
    ) : SyncState()
    data object AuthenticationRequired : SyncState()
}

sealed class SyncConnectionResult {
    data object Completed : SyncConnectionResult()
    data class HttpError(
        val statusCode: Int,
        val body: String,
    ) : SyncConnectionResult()
    data class NetworkError(
        val cause: Throwable,
    ) : SyncConnectionResult()
}
