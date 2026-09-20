package pl.quicktask.app.platform

import kotlinx.coroutines.test.runTest
import pl.quicktask.app.inbox.platform.unsupportedFilePicker
import pl.quicktask.app.sync.model.SyncConnectionResult
import pl.quicktask.app.sync.platform.NoOpSyncEventSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NeutralPlatformHelpersTest {
    @Test
    fun unsupportedPickerReportsNullOnlyWhenInvoked() {
        var calls = 0
        val picker = unsupportedFilePicker { file ->
            assertNull(file)
            calls++
        }
        assertEquals(0, calls)
        picker()
        picker()
        assertEquals(2, calls)
    }

    @Test
    fun noOpSourceCompletesWithoutEvents() = runTest {
        var events = 0
        repeat(2) {
            val result = NoOpSyncEventSource.connectAndListen("url", "token", "proof") { events++ }
            assertEquals(SyncConnectionResult.Completed, result)
        }
        assertEquals(0, events)
    }
}
