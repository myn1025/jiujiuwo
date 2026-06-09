package com.shouhu.guardian.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryPurple,
    onPrimary = Color.White,
    primaryContainer = PrimaryPurpleDark,
    onPrimaryContainer = PrimaryPurpleLight,
    secondary = EmergencyRed,
    onSecondary = Color.White,
    secondaryContainer = EmergencyRedDark,
    onSecondaryContainer = EmergencyRedLight,
    background = DarkBackground,
    onBackground = GrayLight,
    surface = DarkSurface,
    onSurface = GrayLight,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = GoldDark,
    error = EmergencyRed,
    onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryPurple,
    onPrimary = Color.White,
    primaryContainer = PrimaryPurpleLight,
    onPrimaryContainer = PrimaryPurpleDark,
    secondary = EmergencyRed,
    onSecondary = Color.White,
    secondaryContainer = EmergencyRedLight,
    onSecondaryContainer = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF1A1A1A),
    surface = LightSurface,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = GrayText,
    error = EmergencyRed,
    onError = Color.White,
)

private const val PREFS_NAME = "jiujiuwo_prefs"
private const val KEY_DARK_MODE = "dark_mode"

fun isDarkMode(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_DARK_MODE, false) // 默认浅色
}

fun setDarkMode(context: Context, dark: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_DARK_MODE, dark).apply()
}

@Composable
fun JiuJiuWoTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = (if (darkTheme) Color.Black else Color.White).toArgb()
            window.navigationBarColor = (if (darkTheme) DarkBackground else LightBackground).toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
