package pl.quicktask.app.auth.session

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

actual fun createSettings(): Settings = PreferencesSettings(Preferences.userRoot().node("pl/quicktask/app/preferences"))

internal interface PlatformSecretStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun delete(key: String)
}

private class DesktopSecretSettings(private val store: PlatformSecretStore) : Settings by MemorySettings() {
    override fun getStringOrNull(key: String) = store.read(key)
    override fun getString(key: String, defaultValue: String) = store.read(key) ?: defaultValue
    override fun putString(key: String, value: String) = store.write(key, value)
    override fun remove(key: String) = store.delete(key)
}

private val secretSettings: Settings by lazy {
    val store = runCatching { platformSecretStore().also { migrateLegacy(it) } }.getOrNull()
    ResilientSecretSettings(store?.let(::DesktopSecretSettings), createSettings())
}
actual fun createSecretSettings(): Settings = secretSettings

internal val legacySecretKeys = listOf("clearmind.accessToken", "clearmind.refreshToken", "clearmind.userEmail",
    "clearmind.dpop.privateKey", "clearmind.dpop.publicKey", "clearmind.keycache.email",
    "clearmind.keycache.publicKey", "clearmind.keycache.privateKey")

/** Read-only compatibility: the old key must never encrypt new data. */
internal fun decryptLegacy(value: String): String? = runCatching {
    val bytes = Base64.getDecoder().decode(value)
    require(bytes.size > 40)
    val key = MessageDigest.getInstance("SHA-256").digest(
        "${System.getProperty("user.name")}:${System.getProperty("user.home")}:clearmind_secure_key".toByteArray())
    // Legacy encrypt(plaintext, iv) used iv as AAD and prefixed its own IV.
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, bytes.copyOfRange(12, 24)))
    cipher.updateAAD(bytes.copyOfRange(0, 12))
    cipher.doFinal(bytes.copyOfRange(24, bytes.size)).toString(Charsets.UTF_8)
}.getOrNull()

internal fun migrateLegacy(store: PlatformSecretStore, old: Preferences = Preferences.userNodeForPackage(SessionManager::class.java)) {
    if (old.getBoolean("secureMigrationV2", false)) return
    val values = legacySecretKeys.mapNotNull { key -> old.get(key, null)?.let { key to decryptLegacy(it) } }.toMap()
    val required = legacySecretKeys.take(5)
    if (required.all { !values[it].isNullOrBlank() } && validLegacyPair(values[required[3]], values[required[4]])) {
        val cached = legacySecretKeys.takeLast(3)
        val validCache = values[cached[0]] == values[required[2]] && validLegacyPair(values[cached[2]], values[cached[1]])
        val accepted = values.filterKeys { it !in cached || validCache }
        accepted.forEach { (key, value) -> if (value != null && store.read(key) == null) store.write(key, value) }
        accepted.forEach { (key, value) -> if (value != null) check(store.read(key) == value) }
    }
    legacySecretKeys.forEach { old.remove(it) }
    old.putBoolean("secureMigrationV2", true)
    old.flush()
}

private fun validLegacyPair(privateValue: String?, publicValue: String?): Boolean = runCatching {
    val factory = java.security.KeyFactory.getInstance("EC")
    val privateKey = factory.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateValue))) as java.security.interfaces.ECPrivateKey
    val publicKey = factory.generatePublic(java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(publicValue))) as java.security.interfaces.ECPublicKey
    val p256 = java.security.AlgorithmParameters.getInstance("EC").apply { init(java.security.spec.ECGenParameterSpec("secp256r1")) }
        .getParameterSpec(java.security.spec.ECParameterSpec::class.java)
    require(privateKey.params.order == p256.order && publicKey.params.order == p256.order)
    val signature = java.security.Signature.getInstance("SHA256withECDSA")
    val challenge = "clearmind-local-migration-v2".toByteArray()
    signature.initSign(privateKey); signature.update(challenge)
    val signed = signature.sign()
    signature.initVerify(publicKey); signature.update(challenge)
    signature.verify(signed)
}.getOrDefault(false)
