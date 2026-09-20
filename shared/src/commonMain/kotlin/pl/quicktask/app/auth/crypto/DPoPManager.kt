package pl.quicktask.app.auth.crypto

import pl.quicktask.app.auth.session.createSecretSettings

import com.russhwolf.settings.Settings
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.util.date.getTimeMillis
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface DPoPManager {
    suspend fun generateDPoPProof(
        method: String,
        url: String,
        accessToken: String? = null,
    ): String

    suspend fun clearKeyPair()
}


class DefaultDPoPManager(
    private val settings: Settings = createSecretSettings(),
) : DPoPManager {

    private val provider by lazy { getCryptographyProvider() }
    private val mutex = Mutex()
    private var memoryOnly = false
    private var privateKey: ECDSA.PrivateKey? = null
    private var publicKeyRaw: ByteArray? = null
    private var cachedPrivateKeyBase64: String? = null

    private suspend fun getOrGenerateKeyPair(): Pair<ECDSA.PrivateKey, ByteArray> {
        if (memoryOnly) privateKey?.let { key -> publicKeyRaw?.let { return key to it } }
        val savedPrivateKeyBase64 = settings.getStringOrNull(KEY_DPOP_PRIVATE_KEY)
        val savedPublicKeyBase64 = settings.getStringOrNull(KEY_DPOP_PUBLIC_KEY)

        if (savedPrivateKeyBase64.isNullOrBlank() || savedPublicKeyBase64.isNullOrBlank()) {
            privateKey = null
            publicKeyRaw = null
            cachedPrivateKeyBase64 = null
        } else {
            if (savedPrivateKeyBase64 == cachedPrivateKeyBase64) {
                privateKey?.let { priv ->
                    publicKeyRaw?.let { raw -> return priv to raw }
                }
            } else {
                privateKey = null
                publicKeyRaw = null
                cachedPrivateKeyBase64 = null
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
                cachedPrivateKeyBase64 = savedPrivateKeyBase64

                return restoredPrivateKey to raw
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                clearUnlocked()
            }
        }

        val ecdsa = provider.get(ECDSA)
        val generatedPair = ecdsa.keyPairGenerator(EC.Curve.P256).generateKey()
        val privateKeyBytes = generatedPair.privateKey.encodeToByteArray(EC.PrivateKey.Format.DER)
        val publicKeyBytes = generatedPair.publicKey.encodeToByteArray(EC.PublicKey.Format.DER)
        val raw = generatedPair.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW)

        val privateKeyBase64 = privateKeyBytes.toBase64()
        val publicKeyBase64 = publicKeyBytes.toBase64()

        try {
            settings.putString(KEY_DPOP_PRIVATE_KEY, privateKeyBase64)
            settings.putString(KEY_DPOP_PUBLIC_KEY, publicKeyBase64)
        } catch (_: Exception) {
            memoryOnly = true
        }

        privateKey = generatedPair.privateKey
        publicKeyRaw = raw
        cachedPrivateKeyBase64 = privateKeyBase64

        return generatedPair.privateKey to raw
    }

    override suspend fun clearKeyPair() = mutex.withLock { clearUnlocked() }

    private fun clearUnlocked() {
        memoryOnly = false
        privateKey = null
        publicKeyRaw = null
        cachedPrivateKeyBase64 = null
        settings.remove(KEY_DPOP_PRIVATE_KEY)
        settings.remove(KEY_DPOP_PUBLIC_KEY)
    }

    override suspend fun generateDPoPProof(
        method: String,
        url: String,
        accessToken: String?,
    ): String = mutex.withLock {
        val (privKey, rawPublic) = getOrGenerateKeyPair()

        // RAW format dla P-256 (65 bajtów): 0x04 || X (32 bajty) || Y (32 bajty)
        val xBytes: ByteArray
        val yBytes: ByteArray
        if (rawPublic.size >= 65) {
            xBytes = rawPublic.copyOfRange(1, 33)
            yBytes = rawPublic.copyOfRange(33, 65)
        } else if (rawPublic.size == 64) {
            xBytes = rawPublic.copyOfRange(0, 32)
            yBytes = rawPublic.copyOfRange(32, 64)
        } else {
            xBytes = rawPublic
            yBytes = rawPublic
        }

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
        val targetUrl = url.substringBefore('?').substringBefore('#')

        val payloadObj = buildJsonObject {
            put("jti", JsonPrimitive(jti))
            put("htm", JsonPrimitive(method.uppercase()))
            put("htu", JsonPrimitive(targetUrl))
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

        "$signingInput.$signatureBase64"
    }

    private fun generateRandomJti(): String {
        val bytes = secureRandomBytes(16)
        return bytes.toBase64Url()
    }

    companion object {
        private const val KEY_DPOP_PRIVATE_KEY = "clearmind.dpop.privateKey"
        private const val KEY_DPOP_PUBLIC_KEY = "clearmind.dpop.publicKey"
    }
}
