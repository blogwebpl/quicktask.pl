package pl.quicktask.todo

import com.opaquekmp.base64UrlDecode
import com.opaquekmp.base64UrlEncode
import dev.whyoleg.cryptography.algorithms.EC
import kotlinx.coroutines.runBlocking
import pl.quicktask.todo.auth.UserKeyMaterialDto
import pl.quicktask.todo.auth.createItemKey
import pl.quicktask.todo.auth.decryptBuffer
import pl.quicktask.todo.auth.decryptText
import pl.quicktask.todo.auth.encryptBuffer
import pl.quicktask.todo.auth.encryptText
import pl.quicktask.todo.auth.fromBase64Url
import pl.quicktask.todo.auth.generateUserKeyPair
import pl.quicktask.todo.auth.restoreUserKeyPair
import pl.quicktask.todo.auth.sha256Base64
import pl.quicktask.todo.auth.toBase64
import pl.quicktask.todo.auth.unwrapItemKey
import pl.quicktask.todo.auth.wrapItemKey
import pl.quicktask.todo.auth.wrapPrivateKey
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
}
