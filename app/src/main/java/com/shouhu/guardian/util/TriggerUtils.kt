package com.shouhu.guardian.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings

/**
 * 触发功能工具类
 */
object TriggerUtils {

    /**
     * 检查无障碍服务是否已开启
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val service = "${context.packageName}/.service.VolumeKeyService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(service) || enabledServices.contains("com.shouhu.guardian")
    }

    /**
     * 打开无障碍服务设置页面
     */
    fun openAccessibilitySettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        activity.startActivity(intent)
    }

    /**
     * 获取 MediaSession 音量控制状态
     */
    fun initMediaSessionForBluetooth(context: Context): AudioManager {
        return context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
}