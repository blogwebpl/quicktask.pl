package pl.quicktask.app.inbox

import pl.quicktask.app.items.store.ItemStore
import pl.quicktask.app.items.model.InboxItem
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import pl.quicktask.app.auth.crypto.createItemKey

class VisibleInboxTest {
    @Test
    fun testVisibleInboxDropsNull() = runBlocking {
        val store = ItemStore()
        val key = createItemKey()
        val item = InboxItem("1", "Task 1", "", key, "", "")
        store.cacheInbox(listOf(item), store.generation)
        
        var visible = store.itemsFlow.value
        assertEquals(1, visible.size)
        
        val op = store.beginOperation("1", null)
        assertTrue(op != null)
        
        visible = store.itemsFlow.value
        assertEquals(0, visible.size, "Item should be dropped but was ${visible.map { it.itemId }}")
    }
}
