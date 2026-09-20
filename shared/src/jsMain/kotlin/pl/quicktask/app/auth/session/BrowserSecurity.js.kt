package pl.quicktask.app.auth.session
import kotlin.js.Promise
import kotlinx.coroutines.await
@JsModule("@quicktask/browser-security")
@JsNonModule
private external object BrowserSecurity {
    fun call(operation: String, input: String): Promise<String>
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
actual suspend fun browserSecurityCall(operation: String, input: String): String = BrowserSecurity.call(operation, input).await()
