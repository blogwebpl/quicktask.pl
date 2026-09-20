package pl.quicktask.app.auth.crypto

data class OpaqueRegisterStartResult(val registrationRequest: String, val clientRegistrationState: String)
data class OpaqueRegisterFinishResult(val registrationRecord: String, val exportKey: String)
data class OpaqueStartResult(val startLoginRequest: String, val clientLoginState: String)
data class OpaqueFinishResult(val finishLoginRequest: String, val exportKey: String)

interface OpaqueManager {
    fun discardState(state: String) {}
    suspend fun startRegistration(password: String): OpaqueRegisterStartResult
    suspend fun finishRegistration(password: String, clientRegistrationState: String, registrationResponse: String, email: String, serverOrigin: String): OpaqueRegisterFinishResult
    suspend fun startLogin(password: String): OpaqueStartResult
    suspend fun finishLogin(password: String, clientLoginState: String, loginResponse: String, email: String, serverOrigin: String): OpaqueFinishResult
}

/** Explicit absence of a compatible adapter. Never substitutes another protocol. */
class DefaultOpaqueManager(private val reason: String = "Zgodna biblioteka OPAQUE jest niedostępna na tej platformie.") : OpaqueManager {
    override suspend fun startRegistration(password: String): OpaqueRegisterStartResult = error(reason)
    override suspend fun finishRegistration(password: String, clientRegistrationState: String, registrationResponse: String, email: String, serverOrigin: String): OpaqueRegisterFinishResult = error(reason)
    override suspend fun startLogin(password: String): OpaqueStartResult = error(reason)
    override suspend fun finishLogin(password: String, clientLoginState: String, loginResponse: String, email: String, serverOrigin: String): OpaqueFinishResult = error(reason)
}

expect fun createOpaqueManager(): OpaqueManager
