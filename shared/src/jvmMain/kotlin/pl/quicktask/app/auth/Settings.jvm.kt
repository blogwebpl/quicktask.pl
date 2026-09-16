package pl.quicktask.app.auth

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import kotlinx.coroutines.runBlocking
import java.util.prefs.Preferences
import kotlin.random.Random

private class EncryptedJvmSettings(
    private val delegate: Settings,
) : Settings by delegate {

    private val cipher = runBlocking {
        val provider = getCryptographyProvider()
        val sha256 = provider.get(SHA256)
        val userFingerprint = "${System.getProperty("user.name")}:${System.getProperty("user.home")}:clearmind_secure_key"
        val keyBytes = sha256.hasher().hash(userFingerprint.encodeToByteArray())
        val key = provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, keyBytes)
        key.cipher()
    }

    override fun putString(key: String, value: String) {
        val encryptedValue = encrypt(value)
        delegate.putString(key, encryptedValue)
    }

    override fun getString(key: String, defaultValue: String): String {
        val encrypted = delegate.getStringOrNull(key) ?: return defaultValue
        return decrypt(encrypted) ?: defaultValue
    }

    override fun getStringOrNull(key: String): String? {
        val encrypted = delegate.getStringOrNull(key) ?: return null
        return decrypt(encrypted)
    }

    private fun encrypt(plainText: String): String {
        return try {
            val iv = ByteArray(12).also { Random.nextBytes(it) }
            val cipherText = runBlocking {
                cipher.encrypt(plainText.encodeToByteArray(), iv)
            }
            val combined = iv + cipherText
            combined.encodeBase64()
        } catch (_: Exception) {
            plainText
        }
    }

    private fun decrypt(encryptedBase64: String): String? {
        return try {
            val combined = encryptedBase64.decodeBase64Bytes()
            if (combined.size <= 12) return null
            val iv = combined.copyOfRange(0, 12)
            val cipherText = combined.copyOfRange(12, combined.size)
            val decryptedBytes = runBlocking {
                cipher.decrypt(cipherText, iv)
            }
            decryptedBytes.decodeToString()
        } catch (_: Exception) {
            null
        }
    }
}

actual fun createSettings(): Settings {
    return try {
        val delegate = PreferencesSettings(Preferences.userNodeForPackage(SessionManager::class.java))
        EncryptedJvmSettings(delegate)
    } catch (_: Exception) {
        try {
            PreferencesSettings(Preferences.userNodeForPackage(SessionManager::class.java))
        } catch (_: Exception) {
            Settings()
        }
    }
}
