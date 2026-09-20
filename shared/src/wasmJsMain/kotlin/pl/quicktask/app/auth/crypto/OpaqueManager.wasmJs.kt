@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package pl.quicktask.app.auth.crypto

import kotlin.js.*
import kotlinx.coroutines.await
import kotlinx.serialization.json.*

@JsModule("@serenity-kit/opaque")
private external object WasmSerenity : JsAny {
    val ready: Promise<JsAny?>
    val client: JsAny
}

@JsFun("async (client, method, options) => { try { return JSON.stringify((await client[method](JSON.parse(options))) ?? null); } catch (_) { return JSON.stringify({ opaqueError: true }); } }")
private external fun invokeOpaque(client: JsAny, method: String, options: String): Promise<JsString>

internal class WasmOpaqueManager : OpaqueManager {
    private val states = BrowserOpaqueStates()
    override fun discardState(state: String) = states.discard(state)
    private suspend fun call(method: String, options: JsonObject): JsonObject {
        WasmSerenity.ready.await<JsAny?>()
        val result = invokeOpaque(WasmSerenity.client, method, options.toString()).await<JsString>().toString()
        require(result != "null") { "OPAQUE authentication failed" }
        return Json.parseToJsonElement(result).jsonObject.also { require(!it.containsKey("opaqueError")) { "OPAQUE authentication failed" } }
    }
    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
    override suspend fun startRegistration(password: String): OpaqueRegisterStartResult {
        val result = call("startRegistration", buildJsonObject { put("password", password) })
        return OpaqueRegisterStartResult(result.text("registrationRequest"), states.save(result.text("clientRegistrationState")))
    }
    override suspend fun startLogin(password: String): OpaqueStartResult {
        val result = call("startLogin", buildJsonObject { put("password", password) })
        return OpaqueStartResult(result.text("startLoginRequest"), states.save(result.text("clientLoginState")))
    }
    override suspend fun finishRegistration(password: String, clientRegistrationState: String, registrationResponse: String, email: String, serverOrigin: String): OpaqueRegisterFinishResult {
        states.consume(clientRegistrationState)
        val result = call("finishRegistration", options(password, email, serverOrigin) {
            put("clientRegistrationState", clientRegistrationState); put("registrationResponse", registrationResponse)
        })
        return OpaqueRegisterFinishResult(result.text("registrationRecord"), result.text("exportKey"))
    }
    override suspend fun finishLogin(password: String, clientLoginState: String, loginResponse: String, email: String, serverOrigin: String): OpaqueFinishResult {
        states.consume(clientLoginState)
        val result = call("finishLogin", options(password, email, serverOrigin) {
            put("clientLoginState", clientLoginState); put("loginResponse", loginResponse)
        })
        return OpaqueFinishResult(result.text("finishLoginRequest"), result.text("exportKey"))
    }
    private fun options(password: String, email: String, origin: String, extra: JsonObjectBuilder.() -> Unit) = buildJsonObject {
        put("password", password); put("keyStretching", "memory-constrained")
        put("identifiers", buildJsonObject { put("client", email); put("server", origin) }); extra()
    }
}

actual fun createOpaqueManager(): OpaqueManager = WasmOpaqueManager()
