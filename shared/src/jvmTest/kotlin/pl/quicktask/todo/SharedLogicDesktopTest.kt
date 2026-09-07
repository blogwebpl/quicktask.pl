package pl.quicktask.todo

import pl.quicktask.todo.auth.AuthRepository
import pl.quicktask.todo.auth.JvmOpaqueManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
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
    fun testAdminLoginOnLiveServer() {
        runBlocking {
            val repository = AuthRepository()
            val result = repository.login("admin@example.com", "admin")
            assertTrue(result.isSuccess, "Login for admin@example.com should succeed")
        }
    }
}