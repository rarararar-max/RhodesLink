package com.rhodes.privatechat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    when (currentAppearanceSkin) {
        AppearanceSkin.ClassicLight -> applyLightTheme()
        AppearanceSkin.ClassicDark -> applyDarkTheme()
        AppearanceSkin.RhodesDay -> applyRhodesDayTheme()
        AppearanceSkin.RhodesNight -> applyRhodesNightTheme()
    }
    isDarkMode = currentAppearanceSkin.isDark
    val view = LocalView.current
    val context = LocalContext.current
    SideEffect {
        val activity = context as android.app.Activity
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = !isDarkMode
        controller.isAppearanceLightNavigationBars = !isDarkMode
    }
    val textSelectionColors = TextSelectionColors(
        handleColor = Primary,
        backgroundColor = Primary.copy(alpha = 0.4f)
    )
    MaterialTheme(
        colorScheme = if (isDarkMode) comfortDark() else comfortLight(),
        typography = Typography
    ) {
        CompositionLocalProvider(LocalTextSelectionColors provides textSelectionColors) {
            content()
        }
    }
}
