@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package pl.quicktask.app.auth.session
import kotlin.js.*
import kotlinx.coroutines.await
@JsModule("@quicktask/browser-security")
private external object BrowserSecurity : JsAny {
    fun call(operation: String, input: String): Promise<JsString>
    fun origin(): String
    fun rememberedEmail(): String
    fun epoch(): String
    fun rememberUnlock(enabled: Boolean)
}
actual val browserSessions = true
actual fun browserOrigin(): String = BrowserSecurity.origin()
actual fun browserRememberedEmail(): String = BrowserSecurity.rememberedEmail()
actual fun browserSessionEpoch(): String = BrowserSecurity.epoch()
actual fun rememberBrowserUnlock(enabled: Boolean) = BrowserSecurity.rememberUnlock(enabled)
@JsFun("async (bridge, operation, input) => { try { return await bridge.call(operation, input); } catch (_) { return JSON.stringify({ browserSecurityError: true }); } }")
private external fun safeBrowserCall(bridge: JsAny, operation: String, input: String): Promise<JsString>
actual suspend fun browserSecurityCall(operation: String, input: String): String {
    val result = safeBrowserCall(BrowserSecurity, operation, input).await<JsString>().toString()
    check(result != "{\"browserSecurityError\":true}") { "Bezpieczny magazyn sesji jest niedostępny. Zaloguj się ponownie." }
    return result
}
