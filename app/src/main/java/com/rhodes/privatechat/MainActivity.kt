package com.rhodes.privatechat

import android.graphics.Color
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rhodes.privatechat.navigation.AppNavigation
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.theme.isDarkMode
import com.rhodes.privatechat.ui.theme.罗德岛终端Theme
import org.koin.java.KoinJavaComponent.inject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val shouldDropSavedState = consumeDropSavedStateFlag()
        super.onCreate(if (shouldDropSavedState) null else savedInstanceState)
        val settings: SettingsRepository by inject(SettingsRepository::class.java)
        isDarkMode = settings.darkMode
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        enableEdgeToEdge(
            statusBarStyle = if (isDarkMode) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = if (isDarkMode) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            罗德岛终端Theme {
                AppNavigation()
            }
        }
    }
    private fun consumeDropSavedStateFlag(): Boolean {
        val prefs = getSharedPreferences("rhodes_runtime", Context.MODE_PRIVATE)
        val shouldDrop = prefs.getBoolean("drop_saved_state_once", false)
        if (shouldDrop) prefs.edit().putBoolean("drop_saved_state_once", false).apply()
        return shouldDrop
    }
}
