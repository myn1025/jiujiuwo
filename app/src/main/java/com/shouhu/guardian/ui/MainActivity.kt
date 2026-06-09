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

/**
 * 主 Activity — App 入口
 *
 * 启动流程：
 * 1. 尝试从本地读取已保存的 Token
 * 2. 有 Token → 直接进入主界面
 * 3. 无 Token → 显示伪装计算器（可输入密码解锁或登录）
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JiuJiuWoTheme {
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
