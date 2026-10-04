package com.agentos.app.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.agentos.app.core.logging.Logger

/**
 * Stores API keys and other secrets in EncryptedSharedPreferences backed by an
 * Android Keystore master key (AES-256 GCM). Secrets are never written to
 * DataStore, source code, or build config.
 *
 * Android 9 (API 28) fully supports androidx.security.crypto.
 */
class SecureStore(context: Context) : SecretStore {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "agentos_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Keystore corruption (rare, e.g. factory reset mid-write): recreate cleanly.
            Logger.e("SecureStore", "Encrypted prefs unavailable, recreating", e)
            context.deleteSharedPreferences("agentos_secure_prefs")
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "agentos_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    override fun saveSecret(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
        Logger.i("SecureStore", "Secret stored for key='$key'")
    }

    override fun getSecret(key: String): String? = prefs.getString(key, null)

    override fun deleteSecret(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun wipeAll() {
        prefs.edit().clear().apply()
    }
}
