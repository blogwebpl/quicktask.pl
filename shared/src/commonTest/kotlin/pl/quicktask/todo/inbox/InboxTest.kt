package pl.quicktask.todo.inbox

import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json
import pl.quicktask.todo.auth.SessionManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private class FakeSettings : Settings {
    private val map = mutableMapOf<String, Any>()
    override val keys: Set<String> get() = map.keys
    override val size: Int get() = map.size
    override fun clear() = map.clear()
    override fun remove(key: String) { map.remove(key) }
    override fun hasKey(key: String): Boolean = map.containsKey(key)
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun getInt(key: String, defaultValue: Int): Int = map[key] as? Int ?: defaultValue
    override fun getIntOrNull(key: String): Int? = map[key] as? Int
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun getLong(key: String, defaultValue: Long): Long = map[key] as? Long ?: defaultValue
    override fun getLongOrNull(key: String): Long? = map[key] as? Long
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getString(key: String, defaultValue: String): String = map[key] as? String ?: defaultValue
    override fun getStringOrNull(key: String): String? = map[key] as? String
    override fun putFloat(key: String, value: Float) { map[key] = value }
    override fun getFloat(key: String, defaultValue: Float): Float = map[key] as? Float ?: defaultValue
    override fun getFloatOrNull(key: String): Float? = map[key] as? Float
    override fun putDouble(key: String, value: Double) { map[key] = value }
    override fun getDouble(key: String, defaultValue: Double): Double = map[key] as? Double ?: defaultValue
    override fun getDoubleOrNull(key: String): Double? = map[key] as? Double
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = map[key] as? Boolean ?: defaultValue
    override fun getBooleanOrNull(key: String): Boolean? = map[key] as? Boolean
}

class InboxTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testInboxItemDtoDeserialization() {
        val jsonString = """
            {
              "itemId": "2f6065ae-ec88-4ee4-b0d0-888989698e73",
              "encryptedTitle": "ZW5jcnlwdGVkLXRpdGxl",
              "encryptedNote": "ZW5jcnlwdGVkLW5vdGU=",
              "encryptedItemKey": "ZW5jcnlwdGVkLWl0ZW0ta2V5",
              "createdAt": "2026-09-08T10:15:30.000Z",
              "updatedAt": "2026-09-08T10:15:30.000Z",
              "attachments": [
                {
                  "attachmentId": "ae61b711-ff22-46b1-ab32-8d803a3b22a0",
                  "fileId": "93465abc-b021-4ab6-b097-c17ca48b47e1",
                  "attachedAt": "2026-09-08T10:16:00.000Z",
                  "encryptedMetadata": "ZW5jcnlwdGVkLW1ldGFkYXRh",
                  "encryptedFileKey": "ZW5jcnlwdGVkLWZpbGUta2V5",
                  "ciphertextSha256": "c2hhMjU2LWhhc2g=",
                  "encryptedSize": 24576
                }
              ],
              "tags": [
                {
                  "tagId": "bfece685-eb45-4d29-9f85-c9037bb375ac",
                  "name": "praca"
                }
              ]
            }
        """.trimIndent()

        val item = json.decodeFromString<InboxItemDto>(jsonString)
        assertEquals("2f6065ae-ec88-4ee4-b0d0-888989698e73", item.itemId)
        assertEquals("ZW5jcnlwdGVkLXRpdGxl", item.encryptedTitle)
        assertEquals("ZW5jcnlwdGVkLW5vdGU=", item.encryptedNote)
        assertEquals("ZW5jcnlwdGVkLWl0ZW0ta2V5", item.encryptedItemKey)
        assertEquals(1, item.attachments.size)
        assertEquals("ae61b711-ff22-46b1-ab32-8d803a3b22a0", item.attachments[0].attachmentId)
        assertEquals(24576L, item.attachments[0].encryptedSize)
        assertEquals(1, item.tags.size)
        assertEquals("praca", item.tags[0].name)
    }

    @Test
    fun testEmptyInboxListDeserialization() {
        val jsonString = "[]"
        val items = json.decodeFromString<List<InboxItemDto>>(jsonString)
        assertEquals(0, items.size)
    }

    @Test
    fun testCreateInboxItemResponseDeserialization() {
        val jsonString = """
            {
              "itemId": "2f6065ae-ec88-4ee4-b0d0-888989698e73",
              "createdAt": "2026-09-08T10:15:30.000Z"
            }
        """.trimIndent()

        val response = json.decodeFromString<CreateInboxItemResponseDto>(jsonString)
        assertEquals("2f6065ae-ec88-4ee4-b0d0-888989698e73", response.itemId)
        assertEquals("2026-09-08T10:15:30.000Z", response.createdAt)
    }

    @Test
    fun testUpdateInboxItemResponseDeserialization() {
        val jsonString = """
            {
              "itemId": "2f6065ae-ec88-4ee4-b0d0-888989698e73",
              "updatedAt": "2026-09-08T11:30:45.000Z"
            }
        """.trimIndent()

        val response = json.decodeFromString<UpdateInboxItemResponseDto>(jsonString)
        assertEquals("2f6065ae-ec88-4ee4-b0d0-888989698e73", response.itemId)
        assertEquals("2026-09-08T11:30:45.000Z", response.updatedAt)
    }

    @Test
    fun testRepositoryInstantiable() {
        val sessionManager = SessionManager(FakeSettings())
        val repository = InboxRepository(sessionManager = sessionManager)
        assertNotNull(repository)
    }
}
