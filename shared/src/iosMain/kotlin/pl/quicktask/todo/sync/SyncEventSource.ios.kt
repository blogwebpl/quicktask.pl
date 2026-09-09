package pl.quicktask.todo.sync

actual fun createSyncEventSource(): SyncEventSource = object : SyncEventSource {
    override suspend fun connectAndListen(
        url: String,
        accessToken: String,
        dpopProof: String,
        onEvent: (SyncEvent) -> Unit,
    ): SyncConnectionResult {
        return SyncConnectionResult.Completed
    }
}
