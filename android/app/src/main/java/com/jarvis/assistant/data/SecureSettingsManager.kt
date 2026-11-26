package com.jarvis.assistant.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jarvis.assistant.BuildConfig

class SecureSettingsManager(context: Context) {

    private val prefs: SharedPreferences = createEncryptedPrefs(context.applicationContext)

    private fun createEncryptedPrefs(appContext: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                appContext,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            // Fallback only if hardware keystore fails on a specific device ROM
            appContext.getSharedPreferences(PREFS_FILE_FALLBACK, Context.MODE_PRIVATE)
        }
    }

    var backendUrl: String
        get() = prefs.getString(KEY_BACKEND_URL, BuildConfig.DEFAULT_BACKEND_URL)
            ?.trim()
            ?.removeSuffix("/")
            ?.ifEmpty { BuildConfig.DEFAULT_BACKEND_URL }
            ?: BuildConfig.DEFAULT_BACKEND_URL
        set(value) {
            prefs.edit().putString(KEY_BACKEND_URL, value.trim().removeSuffix("/")).apply()
        }

    var accessToken: String
        get() = prefs.getString(KEY_ACCESS_TOKEN, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_ACCESS_TOKEN, value.trim()).apply()
        }

    var listeningMode: ListeningMode
        get() {
            val raw = prefs.getString(KEY_LISTENING_MODE, ListeningMode.WAKE_PHRASE.name)
            return runCatching { ListeningMode.valueOf(raw ?: "") }
                .getOrDefault(ListeningMode.WAKE_PHRASE)
        }
        set(value) {
            prefs.edit().putString(KEY_LISTENING_MODE, value.name).apply()
        }

    var requireActionConfirmation: Boolean
        get() = prefs.getBoolean(KEY_REQUIRE_CONFIRMATION, true)
        set(value) {
            prefs.edit().putBoolean(KEY_REQUIRE_CONFIRMATION, value).apply()
        }

    var speechRate: Float
        get() = prefs.getFloat(KEY_SPEECH_RATE, 1.03f)
        set(value) {
            prefs.edit().putFloat(KEY_SPEECH_RATE, value.coerceIn(0.75f, 1.35f)).apply()
        }

    companion object {
        private const val PREFS_FILE = "jarvis_secure_settings"
        private const val PREFS_FILE_FALLBACK = "jarvis_settings_fallback"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_LISTENING_MODE = "listening_mode"
        private const val KEY_REQUIRE_CONFIRMATION = "require_action_confirmation"
        private const val KEY_SPEECH_RATE = "speech_rate"
    }
}
