package com.shouhu.guardian.util

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * 无障碍服务检测工具
 *
 * 只用于检测是否已启用、引导用户开启，不读取/控制屏幕
 */
object AccessibilityUtils {

    /**
     * 检查无障碍服务是否已启用
     */
    fun isAccessibilityEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val serviceName = "${context.packageName}/${serviceClass.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(serviceName) == true
    }

    /**
     * 打开无障碍设置页面
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
