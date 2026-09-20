package pl.quicktask.app.auth.crypto

import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

private val stateExpiry = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "opaque-state-expiry").apply { isDaemon = true } }

internal class OpaqueStates<T : AutoCloseable> {
    private data class Entry<T>(val state: T, val expiry: ScheduledFuture<*>)
    private val entries = linkedMapOf<String, Entry<T>>()
    @Synchronized fun add(state: T): String {
        if (entries.size >= 32) { state.close(); error("Too many pending OPAQUE operations") }
        val id = UUID.randomUUID().toString()
        val expiry = stateExpiry.schedule({ discard(id) }, 120, TimeUnit.SECONDS)
        entries[id] = Entry(state, expiry)
        return id
    }
    @Synchronized fun take(id: String): T {
        val entry = entries.remove(id) ?: error("OPAQUE state not found or expired")
        entry.expiry.cancel(false)
        return entry.state
    }
    @Synchronized fun discard(id: String) {
        entries.remove(id)?.let { it.expiry.cancel(false); it.state.close() }
    }
}
