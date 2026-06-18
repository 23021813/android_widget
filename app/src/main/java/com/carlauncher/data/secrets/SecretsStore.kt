package com.carlauncher.data.secrets

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for sensitive credentials (SMTP password, Telegram bot token).
 *
 * Uses EncryptedSharedPreferences backed by AndroidKeyStore so values are encrypted
 * at rest and not exposed via adb backup or file extraction.
 *
 * Keys are intentionally distinct from SettingsDataStore (which holds non-secret config).
 */
class SecretsStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to init EncryptedSharedPreferences, falling back to in-memory", e)
        InMemoryPrefs.create()
    }

    fun setTelegramBotToken(token: String) {
        prefs.edit().putString(KEY_TG_TOKEN, token).apply()
    }

    fun getTelegramBotToken(): String? = prefs.getString(KEY_TG_TOKEN, null)

    fun clearTelegramBotToken() {
        prefs.edit().remove(KEY_TG_TOKEN).apply()
    }

    fun hasTelegramBotToken(): Boolean = !getTelegramBotToken().isNullOrBlank()

    fun setSmtpPassword(password: String) {
        prefs.edit().putString(KEY_SMTP_PASS, password).apply()
    }

    fun getSmtpPassword(): String? = prefs.getString(KEY_SMTP_PASS, null)

    fun clearSmtpPassword() {
        prefs.edit().remove(KEY_SMTP_PASS).apply()
    }

    fun hasSmtpPassword(): Boolean = !getSmtpPassword().isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "SecretsStore"
        private const val FILE_NAME = "car_launcher_secrets"
        private const val KEY_TG_TOKEN = "telegram_bot_token"
        private const val KEY_SMTP_PASS = "smtp_password"
    }
}

/**
 * Tiny in-memory fallback used only when the AndroidKeyStore is unavailable
 * (corrupted keystore, broken emulator image). Keeps the app functional instead
 * of crashing; secrets live only for the process lifetime.
 */
private object InMemoryPrefs {
    fun create(): SharedPreferences {
        val map = HashMap<String, String>()
        return object : SharedPreferences {
            override fun getAll(): MutableMap<String, *> = map
            override fun getString(key: String?, defValue: String?): String? = map[key] ?: defValue
            override fun getStringSet(key: String?, defValues: MutableSet<String>?) =
                throw UnsupportedOperationException()
            override fun getInt(key: String?, defValue: Int) = defValue
            override fun getLong(key: String?, defValue: Long) = defValue
            override fun getFloat(key: String?, defValue: Float) = defValue
            override fun getBoolean(key: String?, defValue: Boolean) = defValue
            override fun contains(key: String?) = map.containsKey(key)
            override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
                private val pending = HashMap(map)
                override fun putString(k: String, v: String?): SharedPreferences.Editor =
                    apply { if (v == null) pending.remove(k) else pending[k] = v }
                override fun putStringSet(k: String, v: MutableSet<String>?): SharedPreferences.Editor =
                    throw UnsupportedOperationException()
                override fun putInt(k: String, v: Int) = this
                override fun putLong(k: String, v: Long) = this
                override fun putFloat(k: String, v: Float) = this
                override fun putBoolean(k: String, v: Boolean) = this
                override fun remove(k: String): SharedPreferences.Editor =
                    apply { pending.remove(k) }
                override fun clear(): SharedPreferences.Editor =
                    apply { pending.clear() }
                override fun commit(): Boolean { map.clear(); map.putAll(pending); return true }
                override fun apply() { map.clear(); map.putAll(pending) }
            }
            override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
            override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        }
    }
}
