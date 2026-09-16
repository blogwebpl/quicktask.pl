package pl.quicktask.app.auth

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

object AppContext {
    lateinit var applicationContext: Context
    val isInitialized: Boolean
        get() = ::applicationContext.isInitialized
}

class AppContextProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.applicationContext?.let {
            AppContext.applicationContext = it
        }
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

@Suppress("DEPRECATION")
actual fun createSettings(): Settings {
    if (!AppContext.isInitialized) {
        return Settings()
    }
    val context = AppContext.applicationContext
    return try {
        createEncryptedSettings(context)
    } catch (_: Exception) {
        // W przypadku uszkodzenia MasterKey wyczyszczenie uszkodzonych preferencji i ponowna proba
        context.deleteSharedPreferences("clearmind_encrypted_prefs")
        createEncryptedSettings(context)
    }
}

private fun createEncryptedSettings(context: Context): Settings {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val encryptedSharedPreferences = EncryptedSharedPreferences.create(
        context,
        "clearmind_encrypted_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    return SharedPreferencesSettings(encryptedSharedPreferences)
}
