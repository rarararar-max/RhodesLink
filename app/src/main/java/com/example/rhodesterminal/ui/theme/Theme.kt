package com.example.rhodesterminal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.rhodesterminal.shared.settings.SettingsRepository
import org.koin.compose.koinInject

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
    val settings: SettingsRepository = koinInject()
    val isDark = settings.darkMode
    if (isDark) applyDarkTheme() else applyLightTheme()
    MaterialTheme(
        colorScheme = if (isDark) comfortDark() else comfortLight(),
        typography = Typography, content = content
    )
}
