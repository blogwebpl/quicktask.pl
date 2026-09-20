package pl.quicktask.app.auth.crypto

import kotlinx.coroutines.await
import kotlin.js.Promise

@JsModule("@serenity-kit/opaque")
@JsNonModule
external object SerenityOpaque {
    val ready: Promise<Unit>
    val client: SerenityOpaqueClient
}

external interface SerenityOpaqueClient {
    fun startRegistration(options: dynamic): dynamic
    fun finishRegistration(options: dynamic): dynamic
    fun startLogin(options: dynamic): dynamic
    fun finishLogin(options: dynamic): dynamic
}

class JsOpaqueManager : OpaqueManager {
    private val states = BrowserOpaqueStates()
    override fun discardState(state: String) = states.discard(state)

    override suspend fun startRegistration(password: String): OpaqueRegisterStartResult {
        SerenityOpaque.ready.await()
        val opts = js("{}")
        opts.password = password
        val res = Promise.resolve<dynamic>(SerenityOpaque.client.startRegistration(opts)).await()
        return OpaqueRegisterStartResult(
            registrationRequest = res.registrationRequest.unsafeCast<String>(),
            clientRegistrationState = states.save(res.clientRegistrationState.unsafeCast<String>())
        )
    }

    override suspend fun finishRegistration(
        password: String,
        clientRegistrationState: String,
        registrationResponse: String,
        email: String,
        serverOrigin: String,
    ): OpaqueRegisterFinishResult {
        states.consume(clientRegistrationState)
        SerenityOpaque.ready.await()
        val opts = js("{}")
        opts.password = password
        opts.clientRegistrationState = clientRegistrationState
        opts.registrationResponse = registrationResponse
        val ids = js("{}")
        ids.client = email
        ids.server = serverOrigin
        opts.identifiers = ids
        opts.keyStretching = "memory-constrained"

        val res = Promise.resolve<dynamic>(SerenityOpaque.client.finishRegistration(opts)).await()
        return OpaqueRegisterFinishResult(
            registrationRecord = res.registrationRecord.unsafeCast<String>(),
            exportKey = res.exportKey.unsafeCast<String>()
        )
    }

    override suspend fun startLogin(password: String): OpaqueStartResult {
        SerenityOpaque.ready.await()
        val opts = js("{}")
        opts.password = password
        val res = Promise.resolve<dynamic>(SerenityOpaque.client.startLogin(opts)).await()
        return OpaqueStartResult(
            startLoginRequest = res.startLoginRequest.unsafeCast<String>(),
            clientLoginState = states.save(res.clientLoginState.unsafeCast<String>())
        )
    }

    override suspend fun finishLogin(
        password: String,
        clientLoginState: String,
        loginResponse: String,
        email: String,
        serverOrigin: String,
    ): OpaqueFinishResult {
        states.consume(clientLoginState)
        SerenityOpaque.ready.await()
        val opts = js("{}")
        opts.password = password
        opts.clientLoginState = clientLoginState
        opts.loginResponse = loginResponse
        val ids = js("{}")
        ids.client = email
        ids.server = serverOrigin
        opts.identifiers = ids
        opts.keyStretching = "memory-constrained"

        val res = Promise.resolve<dynamic>(SerenityOpaque.client.finishLogin(opts)).await()
        if (res == null) error("finishLogin returned null")
        return OpaqueFinishResult(
            finishLoginRequest = res.finishLoginRequest.unsafeCast<String>(),
            exportKey = res.exportKey.unsafeCast<String>()
        )
    }
}

actual fun createOpaqueManager(): OpaqueManager = JsOpaqueManager()
