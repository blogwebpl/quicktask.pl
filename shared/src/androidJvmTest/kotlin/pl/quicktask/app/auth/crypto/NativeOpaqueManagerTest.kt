package pl.quicktask.app.auth.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NativeOpaqueManagerTest {
    @Test
    fun registrationStartsWithIndependentStates() = runTest {
        val manager = createOpaqueManager()
        val first = manager.startRegistration("testPassword123")
        val second = manager.startRegistration("testPassword123")
        assertTrue(first.registrationRequest.isNotEmpty())
        assertTrue(first.clientRegistrationState.isNotEmpty())
        assertNotEquals(first.clientRegistrationState, second.clientRegistrationState)
    }

    @Test
    fun unknownLoginAndRegistrationStatesAreRejected() = runTest {
        val manager = createOpaqueManager()
        assertFailsWith<IllegalStateException> {
            manager.finishLogin("password", "missing", "response", "user@example.com", "https://example.com")
        }
        assertFailsWith<IllegalStateException> {
            manager.finishRegistration("password", "missing", "response", "user@example.com", "https://example.com")
        }
    }
}
