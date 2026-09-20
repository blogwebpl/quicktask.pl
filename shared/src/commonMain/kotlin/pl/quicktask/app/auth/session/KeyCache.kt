package pl.quicktask.app.auth.session

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CancellationException
import pl.quicktask.app.auth.crypto.*
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

class KeyCache(
    private val settings: Settings = createSecretSettings(),
    private val browserStore: Boolean = browserSessions && settings === createSecretSettings(),
) {
    suspend fun cacheUserKeys(email: String, pair: UserKeyPair) {
        val pkcs8 = pair.privateKeyPkcs8 ?: return
        try {
            if (browserStore) {
                browserCall("cache", "email" to email, "publicKey" to pair.publicKeySpki.toBase64(), "privateKey" to pkcs8.toBase64())
                return
            }
            settings.putString(KEY_CACHE_EMAIL, email)
            settings.putString(KEY_CACHE_PUBLIC_KEY, pair.publicKeySpki.toBase64())
            settings.putString(KEY_CACHE_PRIVATE_KEY, pkcs8.toBase64())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Cache jest udogodnieniem. W przypadku niepowodzenia odblokowanie następuje hasłem.
        }
    }

    suspend fun loadCachedUserKeys(email: String): UserKeyPair? {
        try {
            if (browserStore) {
                val publicBytes = browserCall("load", "email" to email)["publicKey"]?.jsonPrimitive?.contentOrNull?.fromBase64() ?: return null
                val publicKey = getCryptographyProvider().get(ECDH).publicKeyDecoder(EC.Curve.P256).decodeFromByteArray(EC.PublicKey.Format.DER, publicBytes)
                return UserKeyPair(publicKey, StoredBrowserPrivateKey(email), publicBytes)
            }
            val cachedEmail = settings.getStringOrNull(KEY_CACHE_EMAIL) ?: return null
            if (cachedEmail != email) return null

            val publicKeySpkiBase64 = settings.getStringOrNull(KEY_CACHE_PUBLIC_KEY) ?: return null
            val privateKeyPkcs8Base64 = settings.getStringOrNull(KEY_CACHE_PRIVATE_KEY) ?: return null

            val publicKeySpki = publicKeySpkiBase64.fromBase64()
            val privateKeyPkcs8 = privateKeyPkcs8Base64.fromBase64()

            return decodeUserKeyPair(publicKeySpki, privateKeyPkcs8)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return null
        }
    }

    suspend fun clearCachedUserKeys() {
        try {
            if (browserStore) browserCall("clearCache")
        } catch (error: CancellationException) { throw error }
          catch (_: Throwable) { /* Local logout must continue when IndexedDB is unavailable. */ }
        settings.remove(KEY_CACHE_EMAIL)
        settings.remove(KEY_CACHE_PUBLIC_KEY)
        settings.remove(KEY_CACHE_PRIVATE_KEY)
    }

    companion object {
        private const val KEY_CACHE_EMAIL = "clearmind.keycache.email"
        private const val KEY_CACHE_PUBLIC_KEY = "clearmind.keycache.publicKey"
        private const val KEY_CACHE_PRIVATE_KEY = "clearmind.keycache.privateKey"
    }
}
