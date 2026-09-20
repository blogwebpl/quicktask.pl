package pl.quicktask.app.auth.crypto

import com.opaquekmp.Identifiers
import com.opaquekmp.LoginState
import com.opaquekmp.RegistrationState
import com.opaquekmp.base64UrlDecode
import com.opaquekmp.base64UrlEncode
import com.opaquekmp.clientLoginFinish
import com.opaquekmp.clientLoginStart
import com.opaquekmp.clientRegistrationFinish
import com.opaquekmp.clientRegistrationStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NativeOpaqueManager : OpaqueManager {

    private val loginStates = OpaqueStates<LoginState>()
    private val registrationStates = OpaqueStates<RegistrationState>()

    override fun discardState(state: String) { loginStates.discard(state); registrationStates.discard(state) }

    override suspend fun startRegistration(password: String): OpaqueRegisterStartResult = withContext(Dispatchers.Default) {
        val startResult = clientRegistrationStart(password.encodeToByteArray())
        val reqBase64 = base64UrlEncode(startResult.registrationRequest)
        val stateId = registrationStates.add(startResult.clientRegistrationState)

        OpaqueRegisterStartResult(
            registrationRequest = reqBase64,
            clientRegistrationState = stateId,
        )
    }

    override suspend fun finishRegistration(
        password: String,
        clientRegistrationState: String,
        registrationResponse: String,
        email: String,
        serverOrigin: String,
    ): OpaqueRegisterFinishResult = withContext(Dispatchers.Default) {
        val savedState = registrationStates.take(clientRegistrationState)
        try {

        val responseBytes = base64UrlDecode(registrationResponse)
        val identifiers = Identifiers(
            client = email.encodeToByteArray(),
            server = serverOrigin.encodeToByteArray(),
        )

        val finishResult = clientRegistrationFinish(
            password = password.encodeToByteArray(),
            clientRegistrationState = savedState,
            registrationResponse = responseBytes,
            identifiers = identifiers,
        )

        try {
        val recordBase64 = base64UrlEncode(finishResult.registrationRecord)
        val exportKeyBase64 = base64UrlEncode(finishResult.exportKey.bytes())


        OpaqueRegisterFinishResult(
            registrationRecord = recordBase64,
            exportKey = exportKeyBase64,
        )
        } finally { finishResult.destroy() }
        } finally { savedState.close() }
    }

    override suspend fun startLogin(password: String): OpaqueStartResult = withContext(Dispatchers.Default) {
        val startResult = clientLoginStart(password.encodeToByteArray())

        val startLoginRequestBase64 = base64UrlEncode(startResult.credentialRequest)
        val stateId = loginStates.add(startResult.clientLoginState)

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
        val savedState = loginStates.take(clientLoginState)
        try {

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

        try {
        val finishLoginRequestBase64 = base64UrlEncode(finishResult.credentialFinalization)
        val exportKeyBase64 = base64UrlEncode(finishResult.exportKey.bytes())


        OpaqueFinishResult(
            finishLoginRequest = finishLoginRequestBase64,
            exportKey = exportKeyBase64,
        )
        } finally { finishResult.destroy() }
        } finally { savedState.close() }
    }
}
