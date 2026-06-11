package com.shouhu.guardian.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.shouhu.guardian.ui.theme.isDarkMode
import com.shouhu.guardian.ui.theme.setDarkMode
import com.shouhu.guardian.util.CredentialStore
import com.shouhu.guardian.data.api.RetrofitClient

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    var authToken by remember { mutableStateOf(prefs.getString("token", null)) }
    var userEmail by remember { mutableStateOf(prefs.getString("email", "") ?: "") }
    var darkMode by remember { mutableStateOf(isDarkMode(context)) }

    val startScreen = if (authToken != null) Screen.Main else Screen.Login
    var screen by remember { mutableStateOf(startScreen) }

    when (screen) {
        Screen.Login -> {
            LoginScreen(
                darkTheme = darkMode,
                savedToken = authToken,
                onLoginSuccess = { token, email ->
                    authToken = token
                    userEmail = email
                    // 存加密凭据 — 退出登录后也能指纹恢复
                    CredentialStore.save(context, token, email)
                    prefs.edit()
                        .putString("token", token)
                        .putString("email", email)
                        .apply()
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
                    prefs.edit().remove("token").remove("email").apply()
                    RetrofitClient.setToken(null)
                    // 加密凭据保留不动 — 下次登录可用指纹恢复
                    screen = Screen.Login
                }
            )
        }
    }
}

enum class Screen { Login, Main }
