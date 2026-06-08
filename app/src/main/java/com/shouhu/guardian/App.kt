package com.shouhu.guardian

import android.app.Application

/**
 * 救救我 - 应用入口
 * 女性安全报警 App
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
