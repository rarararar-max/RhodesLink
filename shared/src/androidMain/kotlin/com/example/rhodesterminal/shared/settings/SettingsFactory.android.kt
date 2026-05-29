package com.example.rhodesterminal.shared.settings

import android.content.Context
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings

object AndroidSettingsFactory {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun createSettings(): ObservableSettings {
        return SharedPreferencesSettings(appContext.getSharedPreferences("rhodes_settings", Context.MODE_PRIVATE))
    }
}

actual fun createPlatformSettings(): ObservableSettings = AndroidSettingsFactory.createSettings()
