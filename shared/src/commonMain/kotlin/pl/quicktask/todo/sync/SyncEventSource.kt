package pl.quicktask.todo.sync

interface SyncEventSource {
    suspend fun connectAndListen(
        url: String,
        accessToken: String,
        dpopProof: String,
        onEvent: (SyncEvent) -> Unit,
    ): SyncConnectionResult
}

expect fun createSyncEventSource(): SyncEventSource
