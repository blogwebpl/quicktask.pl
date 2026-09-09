package pl.quicktask.todo.auth

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AndroidOpaqueManagerTest {

    @Test
    fun testAndroidOpaqueManagerStartLogin() = runBlocking {
        val manager = AndroidOpaqueManager()
        val startResult = manager.startLogin("testPassword123")
        assertNotNull(startResult.startLoginRequest)
        assertNotNull(startResult.clientLoginState)
        assertTrue(startResult.startLoginRequest.isNotEmpty(), "startLoginRequest should not be empty")
        assertTrue(startResult.clientLoginState.isNotEmpty(), "clientLoginState should not be empty")
    }
}
