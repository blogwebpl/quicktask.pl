@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package pl.quicktask.app.auth.crypto

import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.WebKit.WKContentWorld
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationActionPolicy.WKNavigationActionPolicyAllow
import platform.WebKit.WKNavigationActionPolicy.WKNavigationActionPolicyCancel
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject
import todo.shared.generated.resources.Res
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun createOpaqueManager(): OpaqueManager = AppleOpaqueManager()

/** Local Serenity/Wasm runs in WebKit, which supports WebAssembly on iOS and iPad apps on Mac. */
internal class AppleOpaqueManager : OpaqueManager {
    private val mutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var engine: AppleOpaqueEngine? = null

    override suspend fun startLogin(password: String): OpaqueStartResult {
        val stateId = newStateId()
        try {
            val result = call("startLogin", mapOf("password" to password, "stateId" to stateId))
            return OpaqueStartResult(result.text("startLoginRequest"), result.text("clientLoginState"))
        } catch (error: Exception) {
            // Also covers prompt cancellation while dispatching the result back to the caller.
            discardState(stateId)
            throw error
        }
    }

    override suspend fun startRegistration(password: String): OpaqueRegisterStartResult {
        val stateId = newStateId()
        try {
            val result = call("startRegistration", mapOf("password" to password, "stateId" to stateId))
            return OpaqueRegisterStartResult(result.text("registrationRequest"), result.text("clientRegistrationState"))
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
    ): OpaqueFinishResult {
        val result = call("finishLogin", mapOf(
            "password" to password, "clientLoginState" to clientLoginState,
            "loginResponse" to loginResponse, "email" to email, "serverOrigin" to serverOrigin,
        ))
        return OpaqueFinishResult(result.text("finishLoginRequest"), result.text("exportKey"))
    }

    override suspend fun finishRegistration(
        password: String,
        clientRegistrationState: String,
        registrationResponse: String,
        email: String,
        serverOrigin: String,
    ): OpaqueRegisterFinishResult {
        val result = call("finishRegistration", mapOf(
            "password" to password, "clientRegistrationState" to clientRegistrationState,
            "registrationResponse" to registrationResponse, "email" to email, "serverOrigin" to serverOrigin,
        ))
        return OpaqueRegisterFinishResult(result.text("registrationRecord"), result.text("exportKey"))
    }

    override fun discardState(state: String) {
        cleanupScope.launch {
            mutex.withLock {
                val current = engine ?: return@withLock
                try {
                    withTimeout(30_000) { current.call("discard", mapOf("stateId" to state)) }
                } catch (_: Exception) {
                    resetEngine()
                }
            }
        }
    }

    private suspend fun call(operation: String, payload: Map<String, String>): JsonObject =
        withContext(Dispatchers.Main) {
            mutex.withLock {
                try {
                    withTimeout(30_000) {
                        val current = engine ?: AppleOpaqueEngine().also {
                            engine = it
                            it.initialize()
                        }
                        current.call(operation, payload)
                    }
                } catch (error: Exception) {
                    // Cancellation, process loss, or a failed call must not leave pending secrets alive.
                    resetEngine()
                    throw error
                }
            }
        }

    private fun resetEngine() {
        engine?.close()
        engine = null
    }

    private fun newStateId(): String = secureRandomBytes(32).joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    private fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content
}

/** Accessed only from Dispatchers.Main. No remote page, persistent storage, or script message handler. */
private class AppleOpaqueEngine {
    private val world = WKContentWorld.worldWithName("ClearMindOpaque")
    private val webView = WKWebView(
        frame = CGRectZero.readValue(),
        configuration = WKWebViewConfiguration().apply {
            websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
        },
    )
    private var loading: CancellableContinuation<Unit>? = null
    // WebKit holds its navigation delegate weakly; retain it for the engine's lifetime.
    private val delegate = object : NSObject(), WKNavigationDelegateProtocol {
        override fun webView(
            webView: WKWebView,
            decidePolicyForNavigationAction: WKNavigationAction,
            decisionHandler: (WKNavigationActionPolicy) -> Unit,
        ) {
            val localInitialPage = loading != null &&
                decidePolicyForNavigationAction.request.URL?.absoluteString == "about:blank"
            decisionHandler(if (localInitialPage) WKNavigationActionPolicyAllow else WKNavigationActionPolicyCancel)
        }

        override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
            val continuation = loading
            loading = null
            if (continuation?.isActive == true) continuation.resume(Unit)
        }

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) = failLoading()

        @ObjCSignatureOverride
        override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) = failLoading()

        override fun webViewWebContentProcessDidTerminate(webView: WKWebView) = failLoading()
    }

    suspend fun initialize() {
        val bundle = Res.readBytes("files/opaque/opaque_bundle.js").decodeToString()
        val bridge = Res.readBytes("files/opaque/bridge.js").decodeToString()
        webView.navigationDelegate = delegate
        suspendCancellableCoroutine<Unit> { continuation ->
            loading = continuation
            webView.loadHTMLString(
                """<!doctype html><meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-eval' 'wasm-unsafe-eval'"><title>OPAQUE</title>""",
                baseURL = null,
            )
        }
        // Only bundled code is evaluated. Passwords and protocol messages are separate WebKit arguments.
        evaluate("""
            globalThis.serenityOpaque = (() => {
                const exports = {};
                $bundle
                return exports;
            })();
            $bridge
            await globalThis.serenityOpaque.ready;
            return true;
        """.trimIndent(), emptyMap())
    }

    suspend fun call(operation: String, payload: Map<String, String>): JsonObject {
        val result = evaluate(
            "return JSON.stringify(await globalThis.clearMindOpaque(operation, payload));",
            mapOf("operation" to operation, "payload" to payload),
        ) as? String ?: error("Nie udało się wykonać operacji OPAQUE.")
        return Json.parseToJsonElement(result).jsonObject
    }

    private suspend fun evaluate(script: String, arguments: Map<Any?, Any?>): Any? =
        suspendCancellableCoroutine { continuation ->
            webView.callAsyncJavaScript(script, arguments, null, world) { result, error ->
                if (continuation.isActive) {
                    if (error != null) continuation.resumeWithException(IllegalStateException("Nie udało się wykonać operacji OPAQUE."))
                    else continuation.resume(result)
                }
            }
        }

    private fun failLoading() {
        val continuation = loading
        loading = null
        if (continuation?.isActive == true) {
            continuation.resumeWithException(IllegalStateException("Nie udało się uruchomić biblioteki OPAQUE."))
        }
    }

    fun close() {
        loading = null
        webView.stopLoading()
        webView.navigationDelegate = null
        // Navigation releases the isolated JavaScript world, including unfinished protocol states.
        webView.loadHTMLString("", baseURL = null)
    }
}
