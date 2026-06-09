package com.shouhu.guardian.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.shouhu.guardian.ui.theme.isDarkMode
import com.shouhu.guardian.ui.theme.setDarkMode

/**
 * 应用根导航
 *
 * 状态机：CalculatorScreen ↔ LoginScreen → MainScreen
 * - 首次打开：伪装计算器
 * - 输入密码 2580 解锁 → 检查是否登录过
 *   - 有 Token → MainScreen
 *   - 无 Token → LoginScreen
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Calculator) }
    var authToken by remember { mutableStateOf<String?>(null) }
    var userEmail by remember { mutableStateOf("") }
    var darkMode by remember { mutableStateOf(isDarkMode(context)) }

    when (screen) {
        Screen.Calculator -> {
            CalculatorScreen(
                darkTheme = darkMode,
                onUnlocked = { screen = Screen.Login }
            )
        }
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
                    screen = Screen.Calculator
                }
            )
        }
    }
}

enum class Screen {
    Calculator,
    Login,
    Main
}
