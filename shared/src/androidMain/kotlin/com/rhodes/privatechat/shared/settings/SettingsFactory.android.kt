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
        if (legacy.all.isNotEmpty() && encrypted.all.isEmpty()) {
            encrypted.edit().also { editor ->
                legacy.all.forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
            }.apply()
            legacy.edit().clear().apply()
        }
        return SharedPreferencesSettings(encrypted)
    }
}

actual fun createPlatformSettings(): ObservableSettings = AndroidSettingsFactory.createSettings()
