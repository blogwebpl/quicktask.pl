package pl.quicktask.app.auth.session
actual val browserSessions = false
actual fun browserOrigin(): String = error("Not a browser")
actual fun browserRememberedEmail(): String = ""
actual fun browserSessionEpoch(): String = ""
actual fun rememberBrowserUnlock(enabled: Boolean) {}
actual suspend fun browserSecurityCall(operation: String, input: String): String = error("Not a browser")
