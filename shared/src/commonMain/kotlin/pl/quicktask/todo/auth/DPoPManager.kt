package pl.quicktask.todo.auth

import com.russhwolf.settings.Settings
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

    fun clearKeyPair()
}

class DefaultDPoPManager(
    private val settings: Settings = createSettings(),
) : DPoPManager {

    private val provider by lazy { getCryptographyProvider() }
    private var privateKey: ECDSA.PrivateKey? = null
    private var publicKeyRaw: ByteArray? = null

    private suspend fun getOrGenerateKeyPair(): Pair<ECDSA.PrivateKey, ByteArray> {
        val savedPrivateKeyBase64 = settings.getStringOrNull(KEY_DPOP_PRIVATE_KEY)
        val savedPublicKeyBase64 = settings.getStringOrNull(KEY_DPOP_PUBLIC_KEY)

        if (savedPrivateKeyBase64.isNullOrBlank() || savedPublicKeyBase64.isNullOrBlank()) {
            privateKey = null
            publicKeyRaw = null
        } else {
            privateKey?.let { priv ->
                publicKeyRaw?.let { raw -> return priv to raw }
            }

            val ecdsa = provider.get(ECDSA)
            try {
                val privateKeyBytes = savedPrivateKeyBase64.fromBase64()
                val publicKeyBytes = savedPublicKeyBase64.fromBase64()

                val restoredPrivateKey = ecdsa.privateKeyDecoder(EC.Curve.P256)
                    .decodeFromByteArray(EC.PrivateKey.Format.DER, privateKeyBytes)
                val restoredPublicKey = ecdsa.publicKeyDecoder(EC.Curve.P256)
                    .decodeFromByteArray(EC.PublicKey.Format.DER, publicKeyBytes)

                val raw = restoredPublicKey.encodeToByteArray(EC.PublicKey.Format.RAW)

                privateKey = restoredPrivateKey
                publicKeyRaw = raw

                return restoredPrivateKey to raw
            } catch (_: Exception) {
                clearKeyPair()
            }
        }

        val ecdsa = provider.get(ECDSA)
        val generatedPair = ecdsa.keyPairGenerator(EC.Curve.P256).generateKey()
        val privateKeyBytes = generatedPair.privateKey.encodeToByteArray(EC.PrivateKey.Format.DER)
        val publicKeyBytes = generatedPair.publicKey.encodeToByteArray(EC.PublicKey.Format.DER)
        val raw = generatedPair.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW)

        try {
            settings.putString(KEY_DPOP_PRIVATE_KEY, privateKeyBytes.toBase64())
            settings.putString(KEY_DPOP_PUBLIC_KEY, publicKeyBytes.toBase64())
        } catch (_: Exception) {
            // Memory key pair will still function for current process lifetime if settings write fails
        }

        privateKey = generatedPair.privateKey
        publicKeyRaw = raw

        return generatedPair.privateKey to raw
    }

    override fun clearKeyPair() {
        privateKey = null
        publicKeyRaw = null
        settings.remove(KEY_DPOP_PRIVATE_KEY)
        settings.remove(KEY_DPOP_PUBLIC_KEY)
    }

    override suspend fun generateDPoPProof(
        method: String,
        url: String,
        accessToken: String?,
    ): String {
        val (privKey, rawPublic) = getOrGenerateKeyPair()

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
        val signatureGenerator = privKey.signatureGenerator(SHA256, ECDSA.SignatureFormat.RAW)
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

    companion object {
        private const val KEY_DPOP_PRIVATE_KEY = "clearmind.dpop.privateKey"
        private const val KEY_DPOP_PUBLIC_KEY = "clearmind.dpop.publicKey"
    }
}
