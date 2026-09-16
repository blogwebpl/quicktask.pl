package pl.quicktask.app.sync

class EventDeduplicator(
    private val maxSize: Int = 100,
) {
    private val seenIds = LinkedHashSet<String>()

    fun isDuplicate(eventId: String): Boolean {
        if (eventId.isBlank()) return false
        if (seenIds.contains(eventId)) {
            return true
        }
        if (seenIds.size >= maxSize) {
            val oldest = seenIds.iterator().next()
            seenIds.remove(oldest)
        }
        seenIds.add(eventId)
        return false
    }

    fun clear() {
        seenIds.clear()
    }
}
