package com.shouhu.guardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机启动接收器 — 设备重启后提醒用户重新开启无障碍服务
 * 注：AccessibilityService 无法通过代码启动，只能引导用户去设置
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // 之前无障碍被杀时记录的标记
            val wasKilled = context.getSharedPreferences("accessibility", Context.MODE_PRIVATE)
                .getBoolean("was_killed", false)
            if (wasKilled) {
                // 清除标记
                context.getSharedPreferences("accessibility", Context.MODE_PRIVATE)
                    .edit().putBoolean("was_killed", false).apply()
                Log.i("BootReceiver", "设备重启，无障碍服务之前被杀，需提醒用户")
            }
        }
    }
}
