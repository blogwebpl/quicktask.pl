package pl.quicktask.todo.auth

import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.util.date.getTimeMillis
import io.ktor.util.encodeBase64
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.random.Random

interface DPoPManager {
    suspend fun generateDPoPProof(
        method: String,
        url: String,
        accessToken: String? = null,
    ): String
}

class DefaultDPoPManager : DPoPManager {

    private val provider by lazy { getCryptographyProvider() }
    private var keyPair: ECDSA.KeyPair? = null
    private var publicKeyRaw: ByteArray? = null

    private suspend fun getOrGenerateKeyPair(): Pair<ECDSA.KeyPair, ByteArray> {
        keyPair?.let { pair ->
            publicKeyRaw?.let { raw -> return pair to raw }
        }

        val ecdsa = provider.get(ECDSA)
        val generatedPair = ecdsa.keyPairGenerator(EC.Curve.P256).generateKey()
        val raw = generatedPair.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW)

        keyPair = generatedPair
        publicKeyRaw = raw

        return generatedPair to raw
    }

    override suspend fun generateDPoPProof(
        method: String,
        url: String,
        accessToken: String?,
    ): String {
        val (pair, rawPublic) = getOrGenerateKeyPair()

        // RAW format dla P-256 (65 bajtów): 0x04 || X (32 bajty) || Y (32 bajty)
        val xBytes = if (rawPublic.size >= 65) rawPublic.copyOfRange(1, 33) else rawPublic
        val yBytes = if (rawPublic.size >= 65) rawPublic.copyOfRange(33, 65) else rawPublic

        val xBase64Url = xBytes.toBase64Url()
        val yBase64Url = yBytes.toBase64Url()

        val jwkObj = buildJsonObject {
            put("kty", JsonPrimitive("EC"))
            put("crv", JsonPrimitive("P-256"))
            put("x", JsonPrimitive(xBase64Url))
            put("y", JsonPrimitive(yBase64Url))
        }

        val headerObj = buildJsonObject {
            put("typ", JsonPrimitive("dpop+jwt"))
            put("alg", JsonPrimitive("ES256"))
            put("jwk", jwkObj)
        }

        val nowSeconds = getTimeMillis() / 1000
        val jti = generateRandomJti()

        val payloadObj = buildJsonObject {
            put("jti", JsonPrimitive(jti))
            put("htm", JsonPrimitive(method.uppercase()))
            put("htu", JsonPrimitive(url))
            put("iat", JsonPrimitive(nowSeconds))
            if (!accessToken.isNullOrBlank()) {
                val ath = provider.get(SHA256).hasher().hash(accessToken.encodeToByteArray()).toBase64Url()
                put("ath", JsonPrimitive(ath))
            }
        }

        val headerBase64 = headerObj.toString().encodeToByteArray().toBase64Url()
        val payloadBase64 = payloadObj.toString().encodeToByteArray().toBase64Url()

        val signingInput = "$headerBase64.$payloadBase64"
        val signatureGenerator = pair.privateKey.signatureGenerator(SHA256, ECDSA.SignatureFormat.RAW)
        val signatureBytes = signatureGenerator.generateSignature(signingInput.encodeToByteArray())
        val signatureBase64 = signatureBytes.toBase64Url()

        return "$signingInput.$signatureBase64"
    }

    private fun generateRandomJti(): String {
        val bytes = ByteArray(16)
        Random.nextBytes(bytes)
        return bytes.toBase64Url()
    }

    private fun ByteArray.toBase64Url(): String {
        return encodeBase64()
            .replace('+', '-')
            .replace('/', '_')
            .replace("=", "")
    }
}
