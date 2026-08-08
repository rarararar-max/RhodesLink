package com.rhodes.privatechat.shared.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings

object AndroidSettingsFactory {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun createSettings(): ObservableSettings {
        val legacy = appContext.getSharedPreferences("rhodes_settings", Context.MODE_PRIVATE)
        return try {
            val masterKey = MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val encrypted = EncryptedSharedPreferences.create(
                appContext,
                "rhodes_settings_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            if (legacy.all.isNotEmpty()) {
                // Keep the legacy store intact until secure storage has a verified copy.
                val encryptedValues = encrypted.all
                val editor = encrypted.edit()
                legacy.all.forEach { (key, value) ->
                    val secureValue = encryptedValues[key]
                    val restoreValue = key !in encryptedValues ||
                        (secureValue is String && secureValue.isBlank() && value is String && value.isNotBlank())
                    if (restoreValue) {
                        when (value) {
                            is String -> editor.putString(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                            is Float -> editor.putFloat(key, value)
                            is Boolean -> editor.putBoolean(key, value)
                            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                        }
                    }
                }
                if (!editor.commit()) throw IllegalStateException("无法提交加密设置迁移")
            }
            SharedPreferencesSettings(encrypted)
        } catch (_: Exception) {
            // Keystore failures must not make an upgraded user lose API configuration or UI state.
            SharedPreferencesSettings(legacy)
        }
    }
}

actual fun createPlatformSettings(): ObservableSettings = AndroidSettingsFactory.createSettings()
