package com.shouhu.guardian.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.shouhu.guardian.ui.theme.JiuJiuWoTheme
import com.shouhu.guardian.ui.theme.isDarkMode

/**
 * 主 Activity — App 入口
 *
 * 启动流程：
 * 1. 读本地主题偏好（默认浅色）
 * 2. 有 Token → 直接进入主界面
 * 3. 无 Token → 显示伪装计算器（可输入密码解锁或登录）
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val darkMode = isDarkMode(this)
        setContent {
            var dark by remember { mutableStateOf(darkMode) }
            JiuJiuWoTheme(darkTheme = dark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }
}
