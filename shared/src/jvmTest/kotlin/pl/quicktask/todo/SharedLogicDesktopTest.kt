package pl.quicktask.todo

import kotlinx.coroutines.runBlocking
import pl.quicktask.todo.auth.AuthRepository
import pl.quicktask.todo.auth.JvmOpaqueManager
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SharedLogicDesktopTest {

    @Test
    fun testJvmOpaqueManagerStartLogin() {
        runBlocking {
            val manager = JvmOpaqueManager()
            val startResult = manager.startLogin("testPassword123")
            println("Start login request: ${startResult.startLoginRequest}")
            println("Client login state: ${startResult.clientLoginState}")
            assertNotNull(startResult.startLoginRequest)
            assertNotNull(startResult.clientLoginState)
            assertTrue(startResult.startLoginRequest.isNotEmpty(), "startLoginRequest should not be empty")
            assertTrue(startResult.clientLoginState.isNotEmpty(), "clientLoginState should not be empty")
        }
    }

    @Test
    fun testJvmOpaqueManagerFinishLoginInvalidState() {
        runBlocking {
            val manager = JvmOpaqueManager()
            assertFailsWith<IllegalStateException> {
                manager.finishLogin(
                    password = "testPassword123",
                    clientLoginState = "non_existent_state_id",
                    loginResponse = "fake_login_response",
                    email = "test@example.com",
                    serverOrigin = "http://localhost",
                )
            }
        }
    }

    @Test
    fun testJvmOpaqueManagerFinishLoginInvalidResponse() {
        runBlocking {
            val manager = JvmOpaqueManager()
            val startResult = manager.startLogin("testPassword123")

            assertFailsWith<Throwable> {
                manager.finishLogin(
                    password = "testPassword123",
                    clientLoginState = startResult.clientLoginState,
                    loginResponse = "invalid_opaque_response_bytes",
                    email = "test@example.com",
                    serverOrigin = "http://localhost",
                )
            }
        }
    }


}
