package pl.quicktask.todo.auth

import com.russhwolf.settings.Settings
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH

class KeyCache(
    private val settings: Settings = createSettings(),
) {
    suspend fun cacheUserKeys(email: String, pair: UserKeyPair) {
        val pkcs8 = pair.privateKeyPkcs8 ?: return
        try {
            settings.putString(KEY_CACHE_EMAIL, email)
            settings.putString(KEY_CACHE_PUBLIC_KEY, pair.publicKeySpki.toBase64())
            settings.putString(KEY_CACHE_PRIVATE_KEY, pkcs8.toBase64())
        } catch (_: Exception) {
            // Cache jest udogodnieniem. W przypadku niepowodzenia odblokowanie następuje hasłem.
        }
    }

    suspend fun loadCachedUserKeys(email: String): UserKeyPair? {
        try {
            val cachedEmail = settings.getStringOrNull(KEY_CACHE_EMAIL) ?: return null
            if (cachedEmail != email) return null

            val publicKeySpkiBase64 = settings.getStringOrNull(KEY_CACHE_PUBLIC_KEY) ?: return null
            val privateKeyPkcs8Base64 = settings.getStringOrNull(KEY_CACHE_PRIVATE_KEY) ?: return null

            val publicKeySpki = publicKeySpkiBase64.fromBase64()
            val privateKeyPkcs8 = privateKeyPkcs8Base64.fromBase64()

            val provider = getCryptographyProvider()
            val ecdh = provider.get(ECDH)

            val publicKey = ecdh.publicKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PublicKey.Format.DER, publicKeySpki)
            val privateKey = ecdh.privateKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PrivateKey.Format.DER, privateKeyPkcs8)

            return UserKeyPair(
                publicKey = publicKey,
                privateKey = privateKey,
                publicKeySpki = publicKeySpki,
                privateKeyPkcs8 = privateKeyPkcs8,
            )
        } catch (_: Exception) {
            return null
        }
    }

    fun clearCachedUserKeys() {
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
