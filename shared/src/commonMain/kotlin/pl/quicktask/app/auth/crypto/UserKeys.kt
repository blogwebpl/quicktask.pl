package pl.quicktask.app.auth.crypto

import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDH
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

class UserKeyPair(
    val publicKey: ECDH.PublicKey,
    val privateKey: ECDH.PrivateKey,
    val publicKeySpki: ByteArray,
    val privateKeyPkcs8: ByteArray? = null,
)

internal suspend fun decodeUserKeyPair(
    publicKeySpki: ByteArray,
    privateKeyPkcs8: ByteArray,
): UserKeyPair {
    val ecdh = getCryptographyProvider().get(ECDH)
    val publicKey = ecdh.publicKeyDecoder(EC.Curve.P256)
        .decodeFromByteArray(EC.PublicKey.Format.DER, publicKeySpki)
    val privateKey = ecdh.privateKeyDecoder(EC.Curve.P256)
        .decodeFromByteArray(EC.PrivateKey.Format.DER, privateKeyPkcs8)
    return UserKeyPair(publicKey, privateKey, publicKeySpki, privateKeyPkcs8)
}

@Serializable
data class UserKeyMaterialDto(
    val publicKey: String,
    val encryptedPrivateKey: String,
    val privateKeyNonce: String,
    val encryptionVersion: Int = 1,
)

class KeyStore {
    private val _userKeys = MutableStateFlow<UserKeyPair?>(null)
    val userKeys: StateFlow<UserKeyPair?> = _userKeys.asStateFlow()

    fun getSnapshot(): UserKeyPair? = _userKeys.value

    fun set(keys: UserKeyPair?) {
        _userKeys.value = keys
    }

    fun requireUserKeys(): UserKeyPair {
        return getSnapshot() ?: error("Klucze użytkownika są zablokowane. Wymagane odblokowanie.")
    }
}
