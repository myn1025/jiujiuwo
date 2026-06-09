package com.shouhu.guardian.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.shouhu.guardian.ui.theme.JiuJiuWoTheme
import com.shouhu.guardian.ui.theme.isDarkMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen 必须在 setContent 前调用，防闪屏标题栏残留
        installSplashScreen()
        super.onCreate(savedInstanceState)
        title = "" // 消除系统级标题
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
