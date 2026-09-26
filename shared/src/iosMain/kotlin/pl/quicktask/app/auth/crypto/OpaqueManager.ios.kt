package pl.quicktask.app.auth.crypto

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Implemented by the Swift host using the generated bindings to our Rust opaque-ke wrapper. */
interface AppleOpaqueBridge {
    @Throws(Exception::class)
    fun startLogin(password: String, stateId: String): String

    @Throws(Exception::class)
    fun startRegistration(password: String, stateId: String): String

    @Throws(Exception::class)
    fun finishLogin(
        password: String, stateId: String, loginResponse: String, email: String, serverOrigin: String,
    ): OpaqueFinishResult

    @Throws(Exception::class)
    fun finishRegistration(
        password: String, stateId: String, registrationResponse: String, email: String, serverOrigin: String,
    ): OpaqueRegisterFinishResult

    fun discardState(stateId: String)
}

// MainViewController requires and injects the Swift implementation. Previews/tests without
// the native host can still construct AppModule, but cannot accidentally use another protocol.
actual fun createOpaqueManager(): OpaqueManager = DefaultOpaqueManager(
    "Natywna biblioteka OPAQUE nie została podłączona przez aplikację Apple.",
)

internal class AppleOpaqueManager(
    private val bridge: AppleOpaqueBridge,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : OpaqueManager {
    private val cleanupScope = CoroutineScope(SupervisorJob() + dispatcher)

    override suspend fun startLogin(password: String): OpaqueStartResult {
        val stateId = newStateId()
        try {
            return withContext(dispatcher) {
                OpaqueStartResult(bridge.startLogin(password, stateId), stateId)
            }
        } catch (error: Exception) {
            discardState(stateId)
            throw error
        }
    }

    override suspend fun startRegistration(password: String): OpaqueRegisterStartResult {
        val stateId = newStateId()
        try {
            return withContext(dispatcher) {
                OpaqueRegisterStartResult(bridge.startRegistration(password, stateId), stateId)
            }
        } catch (error: Exception) {
            discardState(stateId)
            throw error
        }
    }

    override suspend fun finishLogin(
        password: String,
        clientLoginState: String,
        loginResponse: String,
        email: String,
        serverOrigin: String,
    ): OpaqueFinishResult = withContext(dispatcher) {
        try {
            bridge.finishLogin(password, clientLoginState, loginResponse, email, serverOrigin)
        } finally {
            bridge.discardState(clientLoginState)
        }
    }

    override suspend fun finishRegistration(
        password: String,
        clientRegistrationState: String,
        registrationResponse: String,
        email: String,
        serverOrigin: String,
    ): OpaqueRegisterFinishResult = withContext(dispatcher) {
        try {
            bridge.finishRegistration(password, clientRegistrationState, registrationResponse, email, serverOrigin)
        } finally {
            bridge.discardState(clientRegistrationState)
        }
    }

    override fun discardState(state: String) {
        // A finish operation may be doing Argon2 on Swift's serial queue. Never wait for it on the UI thread.
        cleanupScope.launch { bridge.discardState(state) }
    }

    private fun newStateId(): String = secureRandomBytes(32).joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}
