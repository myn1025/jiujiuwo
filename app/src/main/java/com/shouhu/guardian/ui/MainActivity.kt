package com.shouhu.guardian.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.shouhu.guardian.ui.theme.JiuJiuWoTheme
import com.shouhu.guardian.ui.theme.isDarkMode

class MainActivity : AppCompatActivity() {

    private val allPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        title = ""

        // 一次性请求所有运行时权限
        requestAllPermissions()

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

    private fun requestAllPermissions() {
        val missing = mutableListOf<String>()
        // 通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                missing.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // 短信权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(Manifest.permission.SEND_SMS)
        }
        // 定位权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        // 录音权限（语音唤醒）
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(Manifest.permission.RECORD_AUDIO)
        }
        if (missing.isNotEmpty()) {
            allPermissionsLauncher.launch(missing.toTypedArray())
        }
    }
}
