@file:OptIn(dev.whyoleg.cryptography.CryptographyProviderApi::class)
package pl.quicktask.app.auth.session

import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.operations.SharedSecretGenerator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import pl.quicktask.app.auth.crypto.*

expect val browserSessions: Boolean
expect fun browserOrigin(): String
expect fun browserRememberedEmail(): String
expect fun browserSessionEpoch(): String
expect fun rememberBrowserUnlock(enabled: Boolean)
expect suspend fun browserSecurityCall(operation: String, input: String): String

private val browserMutex = Mutex()
suspend fun <T> withBrowserSessionLock(action: suspend () -> T): T {
    if (!browserSessions) return action()
    return browserMutex.withLock {
        // Do not abandon a queued Web Lock when its Kotlin waiter is cancelled.
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { browserCall("acquire") }
        try {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            action()
        } finally { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { browserCall("release") } }
    }
}
suspend fun browserCall(operation: String, vararg entries: Pair<String, String>): JsonObject =
    Json.parseToJsonElement(browserSecurityCall(operation, buildJsonObject { entries.forEach { put(it.first, it.second) } }.toString())).jsonObject

class BrowserDPoPManager : DPoPManager {
    override suspend fun generateDPoPProof(method: String, url: String, accessToken: String?): String {
        val input = buildJsonObject { put("method", method); put("url", url); put("token", accessToken ?: ""); put("create", accessToken == null && !url.endsWith("/refresh")) }
        return Json.parseToJsonElement(browserSecurityCall("proof", input.toString())).jsonObject.getValue("proof").jsonPrimitive.content
    }
    // Keep the origin's shared key while other browser tabs may still use their session.
    override suspend fun clearKeyPair() {}
}

internal class StoredBrowserPrivateKey(private val email: String) : ECDH.PrivateKey {
    override fun encodeToByteArrayBlocking(format: EC.PrivateKey.Format): ByteArray = error("Private key is non-exportable")
    override fun sharedSecretGenerator(): SharedSecretGenerator<ECDH.PublicKey> = object : SharedSecretGenerator<ECDH.PublicKey> {
        override fun generateSharedSecretToByteArrayBlocking(other: ECDH.PublicKey): ByteArray = error("Browser crypto requires suspension")
        override suspend fun generateSharedSecretToByteArray(other: ECDH.PublicKey): ByteArray = browserCall("derive", "email" to email,
            "publicKey" to other.encodeToByteArray(EC.PublicKey.Format.DER).toBase64()).getValue("secret").jsonPrimitive.content.fromBase64()
    }
}
