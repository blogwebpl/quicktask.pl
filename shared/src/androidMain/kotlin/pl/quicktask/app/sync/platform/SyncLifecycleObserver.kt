package pl.quicktask.app.sync.platform

import pl.quicktask.app.sync.domain.SyncCoordinator

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class SyncLifecycleObserver(
    private val syncCoordinator: SyncCoordinator,
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        syncCoordinator.onAppForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        syncCoordinator.onAppBackground()
    }
}
