package com.shouhu.guardian.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = EmergencyRed,
    onPrimary = DarkBackground,
    primaryContainer = EmergencyRedDark,
    onPrimaryContainer = Gold,
    secondary = Gold,
    onSecondary = DarkBackground,
    secondaryContainer = GoldDark,
    onSecondaryContainer = DarkBackground,
    background = DarkBackground,
    onBackground = EmergencyRedLight,
    surface = DarkSurface,
    onSurface = GrayLight,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = GoldDark,
    error = EmergencyRed,
    onError = Color.White,
)

@Composable
fun JiuJiuWoTheme(content: @Composable () -> Unit) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Black.toArgb()
            window.navigationBarColor = DarkBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
