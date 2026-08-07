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
        val masterKey = MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        val encrypted = EncryptedSharedPreferences.create(
            appContext,
            "rhodes_settings_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val legacy = appContext.getSharedPreferences("rhodes_settings", Context.MODE_PRIVATE)
        if (legacy.all.isNotEmpty()) {
            // A partially created encrypted store must not make an upgrade lose its old settings.
            // Preserve the legacy store as a recovery source and fill only keys missing from secure.
            val encryptedValues = encrypted.all
            val editor = encrypted.edit()
            legacy.all.forEach { (key, value) ->
                // Some interrupted upgrades create keys with empty values. Those are just as
                // unusable as missing settings for API/model configuration, so restore the
                // non-empty legacy value instead of permanently keeping the empty secure one.
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
            // Commit before returning settings so a process death cannot expose an empty store.
            editor.commit()
        }
        return SharedPreferencesSettings(encrypted)
    }
}

actual fun createPlatformSettings(): ObservableSettings = AndroidSettingsFactory.createSettings()
