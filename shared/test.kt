class InboxItem(val itemId: String, val title: String, val isPending: Boolean)
class PendingOperation(val id: Long, val itemId: String, val realId: String? = null)
class Change(val operation: PendingOperation, val item: InboxItem?)

fun main() {
    val inbox = listOf(InboxItem("1", "Task 1", false), InboxItem("2", "Task 2", false))
    val pending = mapOf("1" to Change(PendingOperation(1, "1"), null))
    val added = pending.values.mapNotNull { it.item }.filter { pendingItem ->
        val change = pending[pendingItem.itemId]
        val realId = change?.operation?.realId
        inbox.none { inboxItem ->
            inboxItem.itemId == pendingItem.itemId ||
            (realId != null && inboxItem.itemId == realId)
        }
    }
    val visible = added + inbox.mapNotNull { item ->
        val change = pending[item.itemId]
        if (change == null) item else change.item
    }
    println("Visible items: " + visible.map { it.itemId })
}