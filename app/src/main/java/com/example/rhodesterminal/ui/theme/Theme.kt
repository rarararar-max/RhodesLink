package com.example.rhodesterminal.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private fun comfortDark() = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = TextPrimary,
    secondary = AccentGreen,
    onSecondary = OnPrimary,
    secondaryContainer = Card,
    tertiary = AccentBlue,
    background = BG,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Card,
    onSurfaceVariant = TextSecondary,
    outline = Divider,
    outlineVariant = Divider,
    error = ErrorRed,
    onError = OnPrimary
)

private fun comfortLight() = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = TextPrimary,
    secondary = AccentGreen,
    onSecondary = OnPrimary,
    secondaryContainer = Card,
    tertiary = AccentBlue,
    background = BG,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = Divider,
    outlineVariant = Divider,
    error = ErrorRed,
    onError = OnPrimary
)

@Composable
fun 罗德岛终端Theme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("chat_prefs", 0)
    var isDark by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "dark_mode") isDark = prefs.getBoolean("dark_mode", false)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    if (isDark) applyDarkTheme() else applyLightTheme()
    MaterialTheme(
        colorScheme = if (isDark) comfortDark() else comfortLight(),
        typography = Typography, content = content
    )
}
