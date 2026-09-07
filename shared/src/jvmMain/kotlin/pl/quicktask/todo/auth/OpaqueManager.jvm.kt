package pl.quicktask.todo.auth

import com.opaquekmp.Identifiers
import com.opaquekmp.LoginState
import com.opaquekmp.base64UrlDecode
import com.opaquekmp.base64UrlEncode
import com.opaquekmp.clientLoginFinish
import com.opaquekmp.clientLoginStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class JvmOpaqueManager : OpaqueManager {

    private val loginStates = ConcurrentHashMap<String, LoginState>()

    override suspend fun startLogin(password: String): OpaqueStartResult = withContext(Dispatchers.Default) {
        val startResult = clientLoginStart(password.encodeToByteArray())

        val startLoginRequestBase64 = base64UrlEncode(startResult.credentialRequest)
        val stateId = "state_" + System.currentTimeMillis() + "_" + (1..10000).random()
        loginStates[stateId] = startResult.clientLoginState

        OpaqueStartResult(
            startLoginRequest = startLoginRequestBase64,
            clientLoginState = stateId,
        )
    }

    override suspend fun finishLogin(
        password: String,
        clientLoginState: String,
        loginResponse: String,
        email: String,
        serverOrigin: String,
    ): OpaqueFinishResult = withContext(Dispatchers.Default) {
        val savedState = loginStates.remove(clientLoginState)
            ?: error("Client login state not found or expired")

        val responseBytes = base64UrlDecode(loginResponse)
        val identifiers = Identifiers(
            client = email.encodeToByteArray(),
            server = serverOrigin.encodeToByteArray(),
        )

        val finishResult = clientLoginFinish(
            password = password.encodeToByteArray(),
            clientLoginState = savedState,
            credentialResponse = responseBytes,
            identifiers = identifiers,
        )

        val finishLoginRequestBase64 = base64UrlEncode(finishResult.credentialFinalization)
        val exportKeyBase64 = base64UrlEncode(finishResult.exportKey.bytes())

        finishResult.destroy()

        OpaqueFinishResult(
            finishLoginRequest = finishLoginRequestBase64,
            exportKey = exportKeyBase64,
        )
    }
}

actual fun createOpaqueManager(): OpaqueManager = JvmOpaqueManager()
