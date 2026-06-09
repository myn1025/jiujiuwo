package com.shouhu.guardian.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.shouhu.guardian.ui.theme.isDarkMode
import com.shouhu.guardian.ui.theme.setDarkMode

@Composable
fun AppRoot() {
    val context = LocalContext.current
    var authToken by remember { mutableStateOf<String?>(
        context.getSharedPreferences("auth", Context.MODE_PRIVATE).getString("token", null)
    ) }
    var userEmail by remember { mutableStateOf("") }
    var darkMode by remember { mutableStateOf(isDarkMode(context)) }

    val startScreen = if (authToken != null) Screen.Main else Screen.Login
    var screen by remember { mutableStateOf(startScreen) }

    when (screen) {
        Screen.Login -> {
            LoginScreen(
                onLoginSuccess = { token, email ->
                    authToken = token
                    userEmail = email
                    screen = Screen.Main
                }
            )
        }
        Screen.Main -> {
            GuardianMainScreen(
                token = authToken!!,
                email = userEmail,
                darkTheme = darkMode,
                onToggleTheme = { dark ->
                    darkMode = dark
                    setDarkMode(context, dark)
                },
                onLogout = {
                    authToken = null
                    userEmail = ""
                    screen = Screen.Login
                }
            )
        }
    }
}

enum class Screen { Login, Main }