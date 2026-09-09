package pl.quicktask.todo.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class SyncLifecycleObserver(
    private val syncCoordinator: SyncCoordinator = sharedSyncCoordinator,
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        syncCoordinator.onAppForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        syncCoordinator.onAppBackground()
    }
}
