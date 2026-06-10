package com.shouhu.guardian

import android.app.Application
import com.shouhu.guardian.data.api.RetrofitClient

/**
 * 救救我 - 应用入口
 * 女性安全报警 App
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        // 进程重启后自动恢复认证 token（防止 RetrofitClient 丢 token 导致所有 API 静默失败）
        try {
            val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
            RetrofitClient.setToken(token)
        } catch (_: Exception) {}
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
