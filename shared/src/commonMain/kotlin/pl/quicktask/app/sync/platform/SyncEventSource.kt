package pl.quicktask.app.sync.platform

import pl.quicktask.app.sync.model.SyncConnectionResult
import pl.quicktask.app.sync.model.SyncEvent

interface SyncEventSource {
    suspend fun connectAndListen(
        url: String,
        accessToken: String,
        dpopProof: String,
        onEvent: (SyncEvent) -> Unit,
    ): SyncConnectionResult
}

internal object NoOpSyncEventSource : SyncEventSource {
    override suspend fun connectAndListen(
        url: String,
        accessToken: String,
        dpopProof: String,
        onEvent: (SyncEvent) -> Unit,
    ): SyncConnectionResult = SyncConnectionResult.Completed
}

expect fun createSyncEventSource(): SyncEventSource
