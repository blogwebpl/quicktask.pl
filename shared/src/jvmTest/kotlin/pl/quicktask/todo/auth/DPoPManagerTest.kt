package pl.quicktask.todo.auth

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.util.date.getTimeMillis
import io.ktor.util.decodeBase64String
import io.ktor.util.encodeBase64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DPoPManagerTest {

    private fun decodeBase64Url(base64Url: String): String {
        var base64 = base64Url.replace('-', '+').replace('_', '/')
        val pad = (4 - base64.length % 4) % 4
        base64 += "=".repeat(pad)
        return base64.decodeBase64String()
    }

    @Test
    fun testGenerateDPoPProofStructureAndPayload() = runBlocking {
        val manager = DefaultDPoPManager()
        val method = "POST"
        val url = "https://api.example.com/auth/login"

        val proof = manager.generateDPoPProof(method = method, url = url)

        val parts = proof.split(".")
        assertEquals(3, parts.size, "DPoP proof JWT must have 3 parts")

        // 1. Verify Header
        val headerJson = decodeBase64Url(parts[0])
        val header = Json.parseToJsonElement(headerJson).jsonObject

        assertEquals("dpop+jwt", header["typ"]?.jsonPrimitive?.content)
        assertEquals("ES256", header["alg"]?.jsonPrimitive?.content)

        val jwk = header["jwk"]?.jsonObject
        assertNotNull(jwk)
        assertEquals("EC", jwk["kty"]?.jsonPrimitive?.content)
        assertEquals("P-256", jwk["crv"]?.jsonPrimitive?.content)
        assertTrue(jwk["x"]?.jsonPrimitive?.content?.isNotEmpty() == true, "JWK x must not be empty")
        assertTrue(jwk["y"]?.jsonPrimitive?.content?.isNotEmpty() == true, "JWK y must not be empty")

        // 2. Verify Payload
        val payloadJson = decodeBase64Url(parts[1])
        val payload = Json.parseToJsonElement(payloadJson).jsonObject

        assertTrue(payload["jti"]?.jsonPrimitive?.content?.isNotEmpty() == true, "jti must not be empty")
        assertEquals("POST", payload["htm"]?.jsonPrimitive?.content)
        assertEquals(url, payload["htu"]?.jsonPrimitive?.content)

        val iat = payload["iat"]?.jsonPrimitive?.content?.toLongOrNull()
        assertNotNull(iat)
        val nowSeconds = getTimeMillis() / 1000
        assertTrue(abs(nowSeconds - iat) < 10, "iat timestamp should be recent")

        assertNull(payload["ath"], "ath should be null when accessToken is not provided")

        // 3. Verify Signature part exists
        assertTrue(parts[2].isNotEmpty(), "Signature part must not be empty")
    }

    @Test
    fun testGenerateDPoPProofWithAccessToken() = runBlocking {
        val manager = DefaultDPoPManager()
        val accessToken = "test_access_token_xyz_123"

        val proof = manager.generateDPoPProof(
            method = "GET",
            url = "https://api.example.com/users/me",
            accessToken = accessToken,
        )

        val parts = proof.split(".")
        val payloadJson = decodeBase64Url(parts[1])
        val payload = Json.parseToJsonElement(payloadJson).jsonObject

        val athInPayload = payload["ath"]?.jsonPrimitive?.content
        assertNotNull(athInPayload, "ath must be present when accessToken is provided")

        // Verify SHA-256 of accessToken matches ath
        val sha256Hasher = CryptographyProvider.Default.get(SHA256).hasher()
        val expectedHash = sha256Hasher.hash(accessToken.encodeToByteArray())
        val expectedAth = expectedHash.encodeBase64()
            .replace('+', '-')
            .replace('/', '_')
            .replace("=", "")

        assertEquals(expectedAth, athInPayload)
    }

    @Test
    fun testKeyPairReusedAcrossCalls() = runBlocking {
        val manager = DefaultDPoPManager()

        val proof1 = manager.generateDPoPProof("GET", "https://api.example.com/1")
        val proof2 = manager.generateDPoPProof("POST", "https://api.example.com/2")

        val header1 = Json.parseToJsonElement(decodeBase64Url(proof1.split(".")[0])).jsonObject
        val header2 = Json.parseToJsonElement(decodeBase64Url(proof2.split(".")[0])).jsonObject

        val jwk1 = header1["jwk"]?.jsonObject
        val jwk2 = header2["jwk"]?.jsonObject

        assertEquals(
            jwk1?.get("x")?.jsonPrimitive?.content,
            jwk2?.get("x")?.jsonPrimitive?.content,
            "X coordinate should match across requests",
        )
        assertEquals(
            jwk1?.get("y")?.jsonPrimitive?.content,
            jwk2?.get("y")?.jsonPrimitive?.content,
            "Y coordinate should match across requests",
        )
    }

    @Test
    fun testKeyPairPersistedAcrossInstances() = runBlocking {
        val manager1 = DefaultDPoPManager()
        val manager2 = DefaultDPoPManager()

        val proof1 = manager1.generateDPoPProof("GET", "https://api.example.com/1")
        val proof2 = manager2.generateDPoPProof("POST", "https://api.example.com/2")

        val header1 = Json.parseToJsonElement(decodeBase64Url(proof1.split(".")[0])).jsonObject
        val header2 = Json.parseToJsonElement(decodeBase64Url(proof2.split(".")[0])).jsonObject

        val jwk1 = header1["jwk"]?.jsonObject
        val jwk2 = header2["jwk"]?.jsonObject

        assertEquals(
            jwk1?.get("x")?.jsonPrimitive?.content,
            jwk2?.get("x")?.jsonPrimitive?.content,
            "X coordinate should match across separate instances using same settings",
        )

        // Clear key pair
        manager1.clearKeyPair()

        val proof3 = manager2.generateDPoPProof("GET", "https://api.example.com/3")
        val header3 = Json.parseToJsonElement(decodeBase64Url(proof3.split(".")[0])).jsonObject
        val jwk3 = header3["jwk"]?.jsonObject

        assertTrue(
            jwk1?.get("x")?.jsonPrimitive?.content != jwk3?.get("x")?.jsonPrimitive?.content,
            "After clearing key pair, a new key pair should be generated",
        )
    }
}
