package pl.quicktask.app.auth.crypto

import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64

private const val PRIVATE_KEY_WRAP_INFO = "clearmind-user-private-key-v1"
private const val FILE_KEY_WRAP_INFO = "clearmind-file-key-v1"
private const val EPHEMERAL_PUBLIC_BYTES = 65
private const val NONCE_BYTES = 12

data class WrappedPrivateKey(
    val ciphertext: String,
    val nonce: String,
)

fun ByteArray.toBase64(): String = encodeBase64()
fun String.fromBase64(): ByteArray = decodeBase64Bytes()

fun String.fromBase64Url(): ByteArray {
    var base64 = replace('-', '+').replace('_', '/')
    while ((base64.length % 4) != 0) {
        base64 += "="
    }
    return base64.decodeBase64Bytes()
}

fun ByteArray.toBase64Url(): String {
    return encodeBase64()
        .replace('+', '-')
        .replace('/', '_')
        .replace("=", "")
}

suspend fun generateUserKeyPair(): UserKeyPair {
    val provider = getCryptographyProvider()
    val ecdh = provider.get(ECDH)
    val keyPair = ecdh.keyPairGenerator(EC.Curve.P256).generateKey()
    val spki = keyPair.publicKey.encodeToByteArray(EC.PublicKey.Format.DER)
    val pkcs8 = keyPair.privateKey.encodeToByteArray(EC.PrivateKey.Format.DER)
    return UserKeyPair(
        publicKey = keyPair.publicKey,
        privateKey = keyPair.privateKey,
        publicKeySpki = spki,
        privateKeyPkcs8 = pkcs8,
    )
}

@OptIn(DelicateCryptographyApi::class)
suspend fun wrapPrivateKey(
    exportKey: String,
    privateKeyPkcs8: ByteArray,
): WrappedPrivateKey {
    val exportKeyBytes = exportKey.fromBase64Url()
    
    val wrapKey = deriveWrapKey(exportKeyBytes, PRIVATE_KEY_WRAP_INFO)
    
    val nonce = secureRandomBytes(NONCE_BYTES)
    val ciphertext = wrapKey.cipher().encryptWithIv(
        iv = nonce,
        plaintext = privateKeyPkcs8,
    )

    return WrappedPrivateKey(
        ciphertext = ciphertext.toBase64(),
        nonce = nonce.toBase64(),
    )
}

@OptIn(DelicateCryptographyApi::class)
suspend fun restoreUserKeyPair(
    exportKey: String,
    material: UserKeyMaterialDto,
): UserKeyPair {
    val exportKeyBytes = exportKey.fromBase64Url()

    val wrapKey = deriveWrapKey(exportKeyBytes, PRIVATE_KEY_WRAP_INFO)

    val nonce = material.privateKeyNonce.fromBase64()
    val ciphertext = material.encryptedPrivateKey.fromBase64()

    val privateKeyPkcs8 = wrapKey.cipher().decryptWithIv(
        iv = nonce,
        ciphertext = ciphertext,
    )

    val publicKeySpki = material.publicKey.fromBase64()
    return decodeUserKeyPair(publicKeySpki, privateKeyPkcs8)
}

private suspend fun deriveWrapKey(secret: ByteArray, info: String): AES.GCM.Key {
    val provider = getCryptographyProvider()
    val keyBytes = provider.get(HKDF).secretDerivation(
        digest = SHA256,
        outputSize = 32.bytes,
        salt = ByteArray(0),
        info = info.encodeToByteArray(),
    ).deriveSecretToByteArray(secret)
    return provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, keyBytes)
}

suspend fun createItemKey(): AES.GCM.Key {
    val provider = getCryptographyProvider()
    return provider.get(AES.GCM).keyGenerator(AES.Key.Size.B256).generateKey()
}

suspend fun wrapItemKey(
    publicKey: ECDH.PublicKey,
    itemKey: AES.GCM.Key,
): String {
    val provider = getCryptographyProvider()
    val ecdh = provider.get(ECDH)
    val ephemeralKeyPair = ecdh.keyPairGenerator(EC.Curve.P256).generateKey()

    val sharedSecret = ephemeralKeyPair.privateKey.sharedSecretGenerator()
        .generateSharedSecretToByteArray(publicKey)

    val wrapKey = deriveWrapKey(sharedSecret, FILE_KEY_WRAP_INFO)

    val encryptedPayload = encryptBuffer(wrapKey, itemKey.encodeToByteArray(AES.Key.Format.RAW))
    val ephemeralPublicRaw = ephemeralKeyPair.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW)
    val fullPayload = ephemeralPublicRaw + encryptedPayload

    return fullPayload.toBase64()
}

suspend fun unwrapItemKey(
    privateKey: ECDH.PrivateKey,
    value: String,
): AES.GCM.Key {
    val provider = getCryptographyProvider()
    val payload = value.fromBase64()

    require(payload.size > (EPHEMERAL_PUBLIC_BYTES + NONCE_BYTES)) {
        "Nieprawidłowy rozmiar pakietu klucza zadania"
    }

    val ephemeralPublicRaw = payload.copyOfRange(0, EPHEMERAL_PUBLIC_BYTES)
    val encryptedPayload = payload.copyOfRange(EPHEMERAL_PUBLIC_BYTES, payload.size)

    val ecdh = provider.get(ECDH)
    val ephemeralPublic = ecdh.publicKeyDecoder(EC.Curve.P256)
        .decodeFromByteArray(EC.PublicKey.Format.RAW, ephemeralPublicRaw)

    val sharedSecret = privateKey.sharedSecretGenerator()
        .generateSharedSecretToByteArray(ephemeralPublic)

    val wrapKey = deriveWrapKey(sharedSecret, FILE_KEY_WRAP_INFO)

    val rawItemKey = decryptBuffer(wrapKey, encryptedPayload)
    return provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, rawItemKey)
}

@OptIn(DelicateCryptographyApi::class)
suspend fun encryptBuffer(key: AES.GCM.Key, plaintext: ByteArray): ByteArray {
    val nonce = secureRandomBytes(NONCE_BYTES)
    val ciphertext = key.cipher().encryptWithIv(
        iv = nonce,
        plaintext = plaintext,
    )
    return nonce + ciphertext
}

@OptIn(DelicateCryptographyApi::class)
suspend fun decryptBuffer(key: AES.GCM.Key, payload: ByteArray): ByteArray {
    require(payload.size > NONCE_BYTES) { "Nieprawidłowy rozmiar bufora" }
    val nonce = payload.copyOfRange(0, NONCE_BYTES)
    val ciphertext = payload.copyOfRange(NONCE_BYTES, payload.size)
    return key.cipher().decryptWithIv(
        iv = nonce,
        ciphertext = ciphertext,
    )
}

suspend fun encryptText(key: AES.GCM.Key, text: String): String {
    val payload = encryptBuffer(key, text.encodeToByteArray())
    return payload.toBase64()
}

suspend fun decryptText(key: AES.GCM.Key, value: String): String {
    val payload = value.fromBase64()
    val decryptedBytes = decryptBuffer(key, payload)
    return decryptedBytes.decodeToString()
}

suspend fun sha256Base64(bytes: ByteArray): String {
    val provider = getCryptographyProvider()
    val hash = provider.get(SHA256).hasher().hash(bytes)
    return hash.toBase64()
}
