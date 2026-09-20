package pl.quicktask.app.auth.crypto
import kotlin.js.Promise
import kotlinx.coroutines.await
@JsModule("@serenity-kit/opaque")
@JsNonModule
private external object TestOpaque { val ready: Promise<Unit>; val server: dynamic }
internal suspend fun opaqueTestServer(method: String, options: String): String {
    TestOpaque.ready.await()
    val result = TestOpaque.server[method](JSON.parse<dynamic>(options))
    return JSON.stringify(result)
}
