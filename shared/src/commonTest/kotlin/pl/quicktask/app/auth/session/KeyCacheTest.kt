package pl.quicktask.app.auth.session

import kotlinx.coroutines.test.runTest
import pl.quicktask.app.auth.crypto.*
import pl.quicktask.app.testing.TestSettings
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KeyCacheTest {
    @Test
    fun cachedKeysCanDecryptAndRetainEncodedMaterial() = runTest {
        val cache = KeyCache(TestSettings())
        val original = generateUserKeyPair()
        cache.cacheUserKeys("user@example.com", original)
        val restored = assertNotNull(cache.loadCachedUserKeys("user@example.com"))
        assertContentEquals(original.publicKeySpki, restored.publicKeySpki)
        assertContentEquals(original.privateKeyPkcs8, restored.privateKeyPkcs8)
        val itemKey = createItemKey()
        val wrapped = wrapItemKey(restored.publicKey, itemKey)
        val decoded = unwrapItemKey(restored.privateKey, wrapped)
        val plaintext = "cached key round trip".encodeToByteArray()
        assertContentEquals(plaintext, decryptBuffer(decoded, encryptBuffer(itemKey, plaintext)))
    }

    @Test
    fun missingOrDifferentUserReturnsNull() = runTest {
        val cache = KeyCache(TestSettings())
        assertNull(cache.loadCachedUserKeys("user@example.com"))
        cache.cacheUserKeys("user@example.com", generateUserKeyPair())
        assertNull(cache.loadCachedUserKeys("other@example.com"))
        cache.clearCachedUserKeys()
        assertNull(cache.loadCachedUserKeys("user@example.com"))
    }

    @Test
    fun corruptPublicOrPrivateKeyReturnsNull() = runTest {
        val original = generateUserKeyPair()
        for (key in listOf("clearmind.keycache.publicKey", "clearmind.keycache.privateKey")) {
            for (invalid in listOf("%%%", byteArrayOf(1, 2, 3).toBase64())) {
                val settings = TestSettings()
                val cache = KeyCache(settings)
                cache.cacheUserKeys("user@example.com", original)
                settings.putString(key, invalid)
                assertNull(cache.loadCachedUserKeys("user@example.com"))
            }
        }
    }
}
