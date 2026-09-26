package pl.quicktask.app.auth.crypto

import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.quicktask.app.testing.TestSettings
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Exercise the provider selected by the Apple app, including the crypto used after OPAQUE login. */
class AppleCryptographyTest {
    @Test
    fun loginProofHasValidEs256Signature() = runTest {
        val proof = DefaultDPoPManager(TestSettings()).generateDPoPProof(
            method = "POST",
            url = "https://compatibility.example.test/auth/login?ignored=true",
            accessToken = "synthetic-access-token",
        )
        val parts = proof.split('.')
        assertEquals(3, parts.size)
        val header = Json.parseToJsonElement(parts[0].fromBase64Url().decodeToString()).jsonObject
        val payload = Json.parseToJsonElement(parts[1].fromBase64Url().decodeToString()).jsonObject
        assertEquals("ES256", header.getValue("alg").jsonPrimitive.content)
        assertEquals("https://compatibility.example.test/auth/login", payload.getValue("htu").jsonPrimitive.content)
        val expectedTokenHash = getCryptographyProvider().get(SHA256).hasher()
            .hash("synthetic-access-token".encodeToByteArray()).toBase64Url()
        assertEquals(expectedTokenHash, payload.getValue("ath").jsonPrimitive.content)

        val jwk = header.getValue("jwk").jsonObject
        val publicBytes = byteArrayOf(4) +
            jwk.getValue("x").jsonPrimitive.content.fromBase64Url() +
            jwk.getValue("y").jsonPrimitive.content.fromBase64Url()
        val publicKey = getCryptographyProvider().get(ECDSA).publicKeyDecoder(EC.Curve.P256)
            .decodeFromByteArray(EC.PublicKey.Format.RAW, publicBytes)
        val verifier = publicKey.signatureVerifier(SHA256, ECDSA.SignatureFormat.RAW)
        val signedBytes = "${parts[0]}.${parts[1]}".encodeToByteArray()
        val signature = parts[2].fromBase64Url()
        assertEquals(64, signature.size)
        assertTrue(verifier.tryVerifySignature(signedBytes, signature))
        assertFalse(verifier.tryVerifySignature(signedBytes + byteArrayOf(0), signature))
    }

    @Test
    fun restoredLoginKeysDecryptAnEncryptedItem() = runTest {
        val original = generateUserKeyPair()
        val privateKey = assertNotNull(original.privateKeyPkcs8)
        val syntheticExportKey = ByteArray(64) { it.toByte() }.toBase64Url()
        val wrapped = wrapPrivateKey(syntheticExportKey, privateKey)
        val restored = restoreUserKeyPair(
            syntheticExportKey,
            UserKeyMaterialDto(
                publicKey = original.publicKeySpki.toBase64(),
                encryptedPrivateKey = wrapped.ciphertext,
                privateKeyNonce = wrapped.nonce,
            ),
        )
        assertContentEquals(original.publicKeySpki, restored.publicKeySpki)
        assertContentEquals(privateKey, restored.privateKeyPkcs8)

        val itemKey = createItemKey()
        val wrappedItemKey = wrapItemKey(original.publicKey, itemKey)
        val recoveredItemKey = unwrapItemKey(restored.privateKey, wrappedItemKey)
        val plaintext = "Apple login: zażółć gęślą 🗝"
        assertEquals(plaintext, decryptText(recoveredItemKey, encryptText(itemKey, plaintext)))
    }
}
