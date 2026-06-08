package com.shouhu.guardian.ui

import androidx.compose.runtime.*

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
    // 应用状态
    var screen by remember { mutableStateOf<Screen>(Screen.Calculator) }
    var authToken by remember { mutableStateOf<String?>(null) }
    var userEmail by remember { mutableStateOf("") }

    when (screen) {
        Screen.Calculator -> {
            CalculatorScreen(
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
