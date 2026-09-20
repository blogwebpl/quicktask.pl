package pl.quicktask.app.auth.crypto

import io.ktor.util.date.getTimeMillis

/** Browser adapters run on one event loop. States are bounded and single-use. */
internal class BrowserOpaqueStates {
    private val pending = mutableMapOf<String, Long>()
    private fun prune() { val now = getTimeMillis(); pending.entries.removeAll { now - it.value >= 120_000L } }
    fun save(state: String): String {
        prune()
        check(pending.size < 32) { "Too many pending OPAQUE operations" }
        pending[state] = getTimeMillis()
        return state
    }
    fun consume(state: String) {
        prune()
        check(pending.remove(state) != null) { "OPAQUE state expired or already used" }
    }
    fun discard(state: String) { pending.remove(state) }
}
