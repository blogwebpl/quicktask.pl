package pl.quicktask.app.sync.platform

actual fun createSyncEventSource(): SyncEventSource = OkHttpSyncEventSource()
