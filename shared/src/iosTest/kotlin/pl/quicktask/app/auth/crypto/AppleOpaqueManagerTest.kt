package pl.quicktask.app.auth.crypto

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppleOpaqueManagerTest {
    @Test
    fun passesExactIdentifiersAndConsumesStateAfterFinishing() = runTest {
        val bridge = RecordingBridge()
        val manager = AppleOpaqueManager(bridge, StandardTestDispatcher(testScheduler))
        val password = "Zażółć 🧠 <script>"
        val login = manager.startLogin(password)
        val registration = manager.startRegistration(password)
        assertNotEquals(login.clientLoginState, registration.clientRegistrationState)
        assertEquals(64, login.clientLoginState.length)
        assertEquals("login-request", login.startLoginRequest)
        assertEquals("registration-request", registration.registrationRequest)
        assertEquals(password, bridge.lastPassword)

        val finished = manager.finishLogin(password, login.clientLoginState, "challenge", "A@example.test", "https://example.test/")
        assertEquals(OpaqueFinishResult("finalization", "export-key"), finished)
        assertEquals(listOf(password, login.clientLoginState, "challenge", "A@example.test", "https://example.test/"), bridge.lastFinish)
        assertTrue(login.clientLoginState in bridge.discarded)

        manager.finishRegistration(password, registration.clientRegistrationState, "response", "A@example.test", "https://example.test/")
        assertTrue(registration.clientRegistrationState in bridge.discarded)
    }

    @Test
    fun failedFinishAlsoDiscardsTheNativeState() = runTest {
        val bridge = RecordingBridge().apply { failFinish = true }
        val manager = AppleOpaqueManager(bridge, StandardTestDispatcher(testScheduler))
        assertFailsWith<IllegalStateException> {
            manager.finishLogin("password", "login-state", "invalid", "email", "origin")
        }
        assertFailsWith<IllegalStateException> {
            manager.finishRegistration("password", "registration-state", "invalid", "email", "origin")
        }
        assertEquals(listOf("login-state", "registration-state"), bridge.discarded)
    }

    @Test
    fun cancellationBeforeDeliveringStartResultDiscardsNativeState() = runTest {
        for (registration in listOf(false, true)) {
            val bridge = RecordingBridge()
            val manager = AppleOpaqueManager(bridge, StandardTestDispatcher(testScheduler))
            lateinit var operation: Job
            bridge.afterStart = { operation.cancel() }
            operation = launch {
                if (registration) manager.startRegistration("password") else manager.startLogin("password")
            }
            runCurrent()
            assertTrue(operation.isCancelled)
            assertEquals(listOf(bridge.lastState), bridge.discarded)
        }
    }

    private class RecordingBridge : AppleOpaqueBridge {
        var lastPassword = ""
        var lastState = ""
        var lastFinish = emptyList<String>()
        var failFinish = false
        var afterStart: () -> Unit = {}
        val discarded = mutableListOf<String>()

        override fun startLogin(password: String, stateId: String): String {
            lastPassword = password
            lastState = stateId
            afterStart()
            return "login-request"
        }

        override fun startRegistration(password: String, stateId: String): String {
            startLogin(password, stateId)
            return "registration-request"
        }

        override fun finishLogin(password: String, stateId: String, loginResponse: String, email: String, serverOrigin: String): OpaqueFinishResult {
            lastFinish = listOf(password, stateId, loginResponse, email, serverOrigin)
            check(!failFinish) { "Native operation failed" }
            return OpaqueFinishResult("finalization", "export-key")
        }

        override fun finishRegistration(password: String, stateId: String, registrationResponse: String, email: String, serverOrigin: String): OpaqueRegisterFinishResult {
            check(!failFinish) { "Native operation failed" }
            return OpaqueRegisterFinishResult("record", "export-key")
        }

        override fun discardState(stateId: String) { discarded += stateId }
    }
}
