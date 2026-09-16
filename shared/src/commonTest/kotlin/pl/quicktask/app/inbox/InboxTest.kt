package pl.quicktask.app.inbox

import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json
import pl.quicktask.app.auth.DefaultDPoPManager
import pl.quicktask.app.auth.SessionManager
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
    fun testCompletedInTwoMinutesItemDtoDeserialization() {
        val jsonString = """
            [
              {
                "itemId": "550e8400-e29b-41d4-a716-446655440000",
                "encryptedTitle": "BASE64_ENCRYPTED_TITLE",
                "encryptedNote": "BASE64_ENCRYPTED_NOTE",
                "encryptedItemKey": "BASE64_ENCRYPTED_ITEM_KEY",
                "createdAt": "2026-09-10T08:00:00.000Z",
                "updatedAt": "2026-09-10T08:02:00.000Z",
                "processedAt": "2026-09-10T08:02:00.000Z",
                "attachments": [],
                "tags": [
                  {
                    "tagId": "660e8400-e29b-41d4-a716-446655440001",
                    "name": "dom"
                  }
                ]
              }
            ]
        """.trimIndent()

        val items = json.decodeFromString<List<CompletedInTwoMinutesItemDto>>(jsonString)
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals("550e8400-e29b-41d4-a716-446655440000", item.itemId)
        assertEquals("BASE64_ENCRYPTED_TITLE", item.encryptedTitle)
        assertEquals("2026-09-10T08:02:00.000Z", item.processedAt)
        assertEquals(1, item.tags.size)
        assertEquals("dom", item.tags[0].name)
    }

    @Test
    fun testCompleteInTwoMinutesRequestDtoSerialization() {
        val request = CompleteInTwoMinutesRequestDto(deleteAttachments = false)
        val jsonString = json.encodeToString(CompleteInTwoMinutesRequestDto.serializer(), request)
        val decoded = json.decodeFromString<CompleteInTwoMinutesRequestDto>(jsonString)
        assertEquals(false, decoded.deleteAttachments)
    }

    @Test
    fun testCompleteInTwoMinutesRequestDtoWithDeleteAttachmentsTrue() {
        val request = CompleteInTwoMinutesRequestDto(deleteAttachments = true)
        val jsonString = json.encodeToString(CompleteInTwoMinutesRequestDto.serializer(), request)
        val decoded = json.decodeFromString<CompleteInTwoMinutesRequestDto>(jsonString)
        assertEquals(true, decoded.deleteAttachments)
    }

    @Test
    fun testRepositoryInstantiable() {
        val sessionManager = SessionManager(FakeSettings())
        val dPoPManager = DefaultDPoPManager(settings = FakeSettings())
        val repository = InboxRepository(sessionManager = sessionManager, dPoPManager = dPoPManager)
        assertNotNull(repository)
    }

    @Test
    fun testInboxItemDtoFilterSoftDeleted() {
        val jsonString = """
            [
              {
                "itemId": "active-id-1",
                "encryptedTitle": "Y3p5bm5l",
                "encryptedItemKey": "a2V5MQ==",
                "createdAt": "2026-09-01T08:00:00.000Z",
                "updatedAt": "2026-09-01T08:00:00.000Z",
                "deletedAt": null
              },
              {
                "itemId": "deleted-id-2",
                "encryptedTitle": "dXN1bmlvdGU=",
                "encryptedItemKey": "a2V5Mg==",
                "createdAt": "2026-09-01T08:00:00.000Z",
                "updatedAt": "2026-09-02T10:00:00.000Z",
                "deletedAt": "2026-09-03T12:00:00.000Z"
              }
            ]
        """.trimIndent()

        val allDtos = json.decodeFromString<List<InboxItemDto>>(jsonString)
        assertEquals(2, allDtos.size)

        val activeDtos = allDtos.filter { it.deletedAt == null }
        assertEquals(1, activeDtos.size)
        assertEquals("active-id-1", activeDtos[0].itemId)
    }

    @Test
    fun testTrashItemDtoDeserializationAndOriginListName() {
        val jsonString = """
            [
              {
                "itemId": "trash-id-100",
                "encryptedTitle": "dGl0bGU=",
                "encryptedNote": null,
                "encryptedItemKey": "a2V5",
                "createdAt": "2026-09-01T08:00:00.000Z",
                "updatedAt": "2026-09-02T10:30:00.000Z",
                "deletedAt": "2026-09-03T12:00:00.000Z",
                "purgeAfter": "2026-10-03T12:00:00.000Z",
                "isReference": false,
                "isCompletedInTwoMinutes": false,
                "isSomedayMaybe": false,
                "isNextAction": true,
                "isScheduled": false,
                "attachments": [],
                "tags": []
              }
            ]
        """.trimIndent()

        val trashDtos = json.decodeFromString<List<TrashItemDto>>(jsonString)
        assertEquals(1, trashDtos.size)

        val dto = trashDtos[0]
        assertEquals("trash-id-100", dto.itemId)
        assertEquals("2026-09-03T12:00:00.000Z", dto.deletedAt)
        assertEquals(true, dto.isNextAction)
    }

    @Test
    fun testSyncStateResponseRemovedDeserialization() {
        val jsonString = """{"location":"removed","itemId":"3f1c8d2a-7b64-4e0f-9c2a-1b8e6d4f0a21"}"""
        val response = json.decodeFromString<ItemSyncStateResponseDto>(jsonString)
        assertEquals("removed", response.location)
        assertEquals("3f1c8d2a-7b64-4e0f-9c2a-1b8e6d4f0a21", response.itemId)
    }

    @Test
    fun testSyncStateResponseInboxDeserialization() {
        val jsonString = """
            {
              "location": "inbox",
              "item": {
                "itemId": "3f1c8d2a-7b64-4e0f-9c2a-1b8e6d4f0a21",
                "encryptedTitle": "ZW5jcnlwdGVkLXRpdGxl",
                "encryptedNote": "ZW5jcnlwdGVkLW5vdGU=",
                "encryptedItemKey": "ZW5jcnlwdGVkLWl0ZW0ta2V5",
                "createdAt": "2026-09-11T17:00:00.000Z",
                "updatedAt": "2026-09-11T17:00:00.000Z",
                "attachments": [],
                "tags": []
              }
            }
        """.trimIndent()
        val response = json.decodeFromString<ItemSyncStateResponseDto>(jsonString)
        assertEquals("inbox", response.location)
        assertNotNull(response.item)
        assertEquals("3f1c8d2a-7b64-4e0f-9c2a-1b8e6d4f0a21", response.item?.itemId)
    }

    @Test
    fun testSyncStateResponseNextActionsDeserialization() {
        val jsonString = """
            {
              "location": "next-actions",
              "item": {
                "itemId": "3f1c8d2a-7b64-4e0f-9c2a-1b8e6d4f0a21",
                "encryptedTitle": "ZW5jY29kZWQ=",
                "encryptedItemKey": "a2V5",
                "createdAt": "2026-09-11T17:00:00.000Z",
                "updatedAt": "2026-09-11T17:00:00.000Z",
                "project": {
                  "projectId": "proj-1",
                  "encryptedTitle": "cHJvajE=",
                  "encryptedItemKey": "cHJvajE="
                },
                "dueAt": "2026-10-01T00:00:00.000Z",
                "contexts": [
                  {
                    "contextId": "ctx-1",
                    "name": "@home"
                  }
                ]
              },
              "task": {
                "taskId": "task-10",
                "gtdState": "NEXT",
                "dueAt": "2026-10-01T00:00:00.000Z"
              },
              "projectId": "proj-1"
            }
        """.trimIndent()
        val response = json.decodeFromString<ItemSyncStateResponseDto>(jsonString)
        assertEquals("next-actions", response.location)
        assertEquals("proj-1", response.projectId)
        assertNotNull(response.task)
        assertEquals("NEXT", response.task?.gtdState)
        assertNotNull(response.item)
        assertEquals(1, response.item?.contexts?.size)
        assertEquals("@home", response.item?.contexts?.get(0)?.name)
    }

    @Test
    fun testTrashViewModelEmptyTrashState() {
        val repository = InboxRepository(sessionManager = SessionManager(FakeSettings()), dPoPManager = DefaultDPoPManager(settings = FakeSettings()))
        val viewModel = TrashViewModel(repository = repository)
        assertEquals(false, viewModel.uiState.value.showEmptyTrashConfirmation)

        viewModel.requestEmptyTrash()
        assertEquals(true, viewModel.uiState.value.showEmptyTrashConfirmation)

        viewModel.cancelEmptyTrash()
        assertEquals(false, viewModel.uiState.value.showEmptyTrashConfirmation)
    }
}
