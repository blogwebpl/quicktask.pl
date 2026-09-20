@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package pl.quicktask.app.auth.crypto
import kotlin.js.*
import kotlinx.coroutines.await
@JsModule("@serenity-kit/opaque")
private external object TestOpaque : JsAny { val ready: Promise<JsAny?>; val server: JsAny }
@JsFun("(server, method, options) => JSON.stringify(server[method](JSON.parse(options)))")
private external fun invokeTestServer(server: JsAny, method: String, options: String): String
internal suspend fun opaqueTestServer(method: String, options: String): String {
    TestOpaque.ready.await<JsAny?>()
    return invokeTestServer(TestOpaque.server, method, options)
}
