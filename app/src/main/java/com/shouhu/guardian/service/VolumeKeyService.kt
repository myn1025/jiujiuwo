package com.shouhu.guardian.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.shouhu.guardian.ui.MainActivity

class VolumeKeyService : AccessibilityService() {

    private var volDown = false
    private var volUp = false
    private var triggered = false
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onServiceConnected() {
        // 在 super 之前设 serviceInfo，防止系统默认设置覆盖
        try {
            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPES_ALL_MASK
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or AccessibilityServiceInfo.DEFAULT
                notificationTimeout = 100
            }
            serviceInfo = info
        } catch (e: Exception) {
            Log.e(TAG, "setInfo: ${e.message}")
        }
        super.onServiceConnected()

        // 前台通知 — 强制 startForeground() 防止国产ROM杀进程
        startPersistentNotification()
        Log.i(TAG, "onServiceConnected OK (pid=${android.os.Process.myPid()})")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        try {
            Log.d(TAG, "onKeyEvent: code=${event.keyCode} action=${event.action} meta=${event.metaState}")
            if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && event.keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
                return false
            }
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    if (!volDown) { volDown = true }
                } else {
                    if (!volUp) { volUp = true }
                }
                runnable?.let { handler.removeCallbacks(it) }
                runnable = Runnable {
                    if ((volDown || volUp) && !triggered) {
                        trigger()
                    }
                }
                handler.postDelayed(runnable!!, 2000L)
                if (volDown && volUp && !triggered) {
                    handler.removeCallbacks(runnable!!)
                    trigger()
                }
                // 拦截音量键事件（不让系统音量弹窗出来）
                return true
            } else {
                if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) volDown = false else volUp = false
                runnable?.let { handler.removeCallbacks(it) }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "onKeyEvent: ${e.message}")
            return false
        }
    }

    private fun trigger() {
        triggered = true
        Log.w(TAG, "⚡ 音量键触发 — 启动 EmergencyService")
        // 短震反馈
        try {
            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(200, 200))
        } catch (_: Exception) {}
        // 启动紧急服务
        try {
            val intent = Intent(this, EmergencyService::class.java).apply {
                action = EmergencyService.ACTION_TRIGGER
                putExtra(EmergencyService.EXTRA_TRIGGER_SOURCE, "volume_key")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动 EmergencyService 失败: ${e.message}")
        }
        handler.postDelayed({ triggered = false }, 5000L)
    }

    // ===== 前台通知（防杀核心）=====
    private fun startPersistentNotification() {
        try {
            val channelId = "wake_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "按键唤醒",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "保持按键唤醒服务运行"
                    setSound(null, null)
                }
                val nm = getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(channel)
            }
            val pi = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification: Notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("按键唤醒就绪")
                .setContentText("长按音量键2秒即可触发报警")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()

            // 关键：调用 startForeground() 而非单纯 notify()
            startForeground(NOTIFICATION_ID, notification)
            Log.i(TAG, "startForeground 已成功")
        } catch (e: Exception) {
            Log.e(TAG, "前台通知失败: ${e.message}")
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        runnable?.let { handler.removeCallbacks(it) }
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val TAG = "VolumeKeyService"
        const val NOTIFICATION_ID = 9001
    }
}
