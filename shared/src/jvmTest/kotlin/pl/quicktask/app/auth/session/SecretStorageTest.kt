package pl.quicktask.app.auth.session

import java.util.UUID
import java.util.prefs.Preferences
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.*

class SecretStorageTest {
    @Test fun interruptedRemovalRequiresLoginAfterRestart() {
        val disk = MemorySettings()
        val marker = MemorySettings()
        disk.putString("clearmind.accessToken", "old token")
        val locked = object : com.russhwolf.settings.Settings by disk {
            override fun remove(key: String) { error("locked") }
        }
        val settings = ResilientSecretSettings(locked, marker)
        settings.remove("clearmind.accessToken")
        assertNull(settings.getStringOrNull("clearmind.accessToken"))
        assertTrue(marker.getBoolean("requireLogin", false))
        val restarted = ResilientSecretSettings(disk, marker)
        assertNull(restarted.getStringOrNull("clearmind.accessToken"))
        assertNull(disk.getStringOrNull("clearmind.accessToken"))
        assertFalse(marker.getBoolean("requireLogin", false))
    }

    @Test fun failedMarkerWriteDoesNotPreventLocalLogout() {
        val broken = object : com.russhwolf.settings.Settings by MemorySettings() {
            override fun putString(key: String, value: String) { error("locked") }
            override fun putBoolean(key: String, value: Boolean) { error("locked") }
            override fun remove(key: String) { error("locked") }
        }
        val settings = ResilientSecretSettings(broken, broken)
        settings.putString("clearmind.accessToken", "memory token")
        settings.remove("clearmind.accessToken")
        assertNull(settings.getStringOrNull("clearmind.accessToken"))
    }
    @Test fun windowsProtectsPersistedValuesAndRestoresThem() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        val prefs = Preferences.userRoot().node("quicktask-security-test/" + UUID.randomUUID())
        try {
            WindowsSecretStore(prefs).write("test", "synthetic secret")
            assertNotEquals("synthetic secret", prefs.get("test", null))
            assertEquals("synthetic secret", WindowsSecretStore(prefs).read("test"))
            WindowsSecretStore(prefs).delete("test")
            assertNull(WindowsSecretStore(prefs).read("test"))
        } finally { prefs.removeNode() }
    }

    @Test fun legacyFormatCanBeReadButTamperingCannot() {
        val key = MessageDigest.getInstance("SHA-256").digest("${System.getProperty("user.name")}:${System.getProperty("user.home")}:clearmind_secure_key".toByteArray())
        val aad = ByteArray(12) { it.toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")); cipher.updateAAD(aad)
        val bytes = aad + cipher.iv + cipher.doFinal("old secret".toByteArray())
        assertEquals("old secret", decryptLegacy(Base64.getEncoder().encodeToString(bytes)))
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertNull(decryptLegacy(Base64.getEncoder().encodeToString(bytes)))
        assertNull(decryptLegacy("plaintext must not be imported"))
    }

    @Test fun failedWritesStayOnlyInMemory() {
        val disk = MemorySettings()
        val broken = object : com.russhwolf.settings.Settings by disk {
            override fun putString(key: String, value: String) { error("locked") }
        }
        val settings = ResilientSecretSettings(broken)
        settings.putString("token", "secret")
        assertEquals("secret", settings.getStringOrNull("token"))
        assertNull(disk.getStringOrNull("token"))
        settings.remove("token")
        assertNull(settings.getStringOrNull("token"))
    }
}
