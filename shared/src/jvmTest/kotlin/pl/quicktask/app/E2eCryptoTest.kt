package pl.quicktask.app

import com.opaquekmp.base64UrlDecode
import com.opaquekmp.base64UrlEncode
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.AES
import pl.quicktask.app.auth.crypto.getCryptographyProvider
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import pl.quicktask.app.auth.crypto.UserKeyMaterialDto
import pl.quicktask.app.auth.crypto.createItemKey
import pl.quicktask.app.auth.crypto.decryptBuffer
import pl.quicktask.app.auth.crypto.decryptText
import pl.quicktask.app.auth.crypto.encryptBuffer
import pl.quicktask.app.auth.crypto.encryptText
import pl.quicktask.app.auth.crypto.fromBase64Url
import pl.quicktask.app.auth.crypto.generateUserKeyPair
import pl.quicktask.app.auth.crypto.restoreUserKeyPair
import pl.quicktask.app.auth.crypto.sha256Base64
import pl.quicktask.app.auth.crypto.toBase64
import pl.quicktask.app.auth.crypto.unwrapItemKey
import pl.quicktask.app.auth.crypto.wrapItemKey
import pl.quicktask.app.auth.crypto.wrapPrivateKey
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class E2eCryptoTest {

    @Test
    fun testUserKeyPairGenerationAndWrapRestore(): Unit = runBlocking {
        val userPair = generateUserKeyPair()
        assertNotNull(userPair.publicKey)
        assertNotNull(userPair.privateKey)
        assertNotNull(userPair.privateKeyPkcs8)

        val fakeExportKey = "dGVzdC1leHBvcnQta2V5LXZhbHVlLTEyMzQ1Njc4OTA="
        val wrapped = wrapPrivateKey(fakeExportKey, userPair.privateKeyPkcs8)

        val material = UserKeyMaterialDto(
            publicKey = userPair.publicKeySpki.toBase64(),
            encryptedPrivateKey = wrapped.ciphertext,
            privateKeyNonce = wrapped.nonce,
            encryptionVersion = 1,
        )

        val restoredPair = restoreUserKeyPair(fakeExportKey, material)
        assertNotNull(restoredPair.publicKey)
        assertNotNull(restoredPair.privateKey)

        val originalSpki = userPair.publicKey.encodeToByteArray(EC.PublicKey.Format.DER)
        val restoredSpki = restoredPair.publicKey.encodeToByteArray(EC.PublicKey.Format.DER)
        assertContentEquals(originalSpki, restoredSpki)
        assertContentEquals(userPair.privateKeyPkcs8, restoredPair.privateKeyPkcs8)
    }

    @Test
    fun testNodeJsWebCryptoCompatibility(): Unit = runBlocking {
        val exportKey = "test_export_key_base64url_string_1234567890"
        val material = UserKeyMaterialDto(
            publicKey = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEX5SKlp51IQ9l56iiutV9IgXAQqU/yLIdFr9gIyEcJfGivqTuS+khMIN7lOUNwO1702q0o0VZAuqtTSwsofZXjQ==",
            encryptedPrivateKey = "jcLiidheaMDeaoGQLHOjjGjNxMAGS6OQxqHZ9Y4cJJVKZLpc4LfWKkA2JmvHM/UXxp7wUAklzvXakX6E0i4LhfZrbwblrHef9tTRqP0s9m6b+qDm8t8xzXgu9dMFTF3toC8cwM4kmxUVJqM5ng9BawvL3wlZto+uQdUZEwNQxDBJxsbBV3uAlo8l+mFFRLBmDicyPiNh987dqA==",
            privateKeyNonce = "hT0MvEssQu4qhUep",
            encryptionVersion = 1,
        )

        val restoredPair = restoreUserKeyPair(exportKey, material)
        assertNotNull(restoredPair.publicKey)
        assertNotNull(restoredPair.privateKey)
        assertNotNull(restoredPair.privateKeyPkcs8)
    }

    @Test
    fun testBase64UrlRoundtrip() {
        val bytes = Random.nextBytes(32)
        val encoded = base64UrlEncode(bytes)
        val decoded1 = base64UrlDecode(encoded)
        val decoded2 = encoded.fromBase64Url()
        assertContentEquals(bytes, decoded1)
        assertContentEquals(bytes, decoded2)
    }

    @Test
    fun testItemKeyWrapAndUnwrap(): Unit = runBlocking {
        val userPair = generateUserKeyPair()
        val itemKey = createItemKey()

        val wrappedItemKey = wrapItemKey(userPair.publicKey, itemKey)
        assertNotNull(wrappedItemKey)

        val unwrappedKey = unwrapItemKey(userPair.privateKey, wrappedItemKey)

        val testMessage = "Tajne zadanie w Inboxie"
        val encryptedText = encryptText(itemKey, testMessage)
        val decryptedWithUnwrapped = decryptText(unwrappedKey, encryptedText)

        assertEquals(testMessage, decryptedWithUnwrapped)
    }



    @Test
    fun testBufferEncryptionAndSha256(): Unit = runBlocking {
        val key = createItemKey()
        val data = "Plik testowy 123456789".encodeToByteArray()

        val encryptedBuffer = encryptBuffer(key, data)
        val hash = sha256Base64(encryptedBuffer)
        assertNotNull(hash)

        val decryptedData = decryptBuffer(key, encryptedBuffer)
        assertContentEquals(data, decryptedData)
    }

    @Test
    fun testBufferFormatCompatibilityWithJce(): Unit = runBlocking {
        val rawKey = ByteArray(32) { it.toByte() }
        val key = getCryptographyProvider().get(AES.GCM).keyDecoder()
            .decodeFromByteArray(AES.Key.Format.RAW, rawKey)
        val nonce = ByteArray(12) { (it + 1).toByte() }
        val plaintext = "Zapisany tekst: zażółć gęślą".encodeToByteArray()
        val jce = Cipher.getInstance("AES/GCM/NoPadding")
        val jceKey = SecretKeySpec(rawKey, "AES")

        // Existing payload format: 12-byte nonce followed by ciphertext and tag.
        jce.init(Cipher.ENCRYPT_MODE, jceKey, GCMParameterSpec(128, nonce))
        val existingPayload = nonce + jce.doFinal(plaintext)
        assertContentEquals(plaintext, decryptBuffer(key, existingPayload))

        val newPayload = encryptBuffer(key, plaintext)
        jce.init(Cipher.DECRYPT_MODE, jceKey, GCMParameterSpec(128, newPayload.copyOfRange(0, 12)))
        assertContentEquals(plaintext, jce.doFinal(newPayload.copyOfRange(12, newPayload.size)))
    }
}
