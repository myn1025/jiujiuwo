package com.shouhu.guardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.shouhu.guardian.data.api.RetrofitClient
import com.shouhu.guardian.service.ShakeService
import com.shouhu.guardian.service.WakeWordService
import kotlinx.coroutines.*

/**
 * 开机自启接收器 — 恢复语音唤醒和摇一摇服务
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i(TAG, "开机检测，恢复触发服务")

        // 异步查询设置
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resp = RetrofitClient.apiService.getSettings()
                if (resp.isSuccessful) {
                    resp.body()?.let { s ->
                        if (s.triggerVoice) {
                            startService(context, WakeWordService::class.java)
                        }
                        if (s.triggerShake) {
                            startService(context, ShakeService::class.java)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "查询设置失败: ${e.message}")
                // Token 可能失效，试试从本地预读取
                val prefs = context.getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean("trigger_shake_enabled", false)) {
                    startService(context, ShakeService::class.java)
                }
            }
        }
    }

    private fun startService(context: Context, cls: Class<*>) {
        try {
            val intent = Intent(context, cls)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.i(TAG, "已启动: ${cls.simpleName}")
        } catch (e: Exception) {
            Log.e(TAG, "启动 ${cls.simpleName} 失败: ${e.message}")
        }
    }
}
