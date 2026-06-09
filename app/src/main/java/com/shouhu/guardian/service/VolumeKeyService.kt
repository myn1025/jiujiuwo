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
import com.shouhu.guardian.ui.MainActivity

class VolumeKeyService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val LONG_PRESS_MS = 2000L
    private var longPressRunnable: Runnable? = null
    private var volumeDownPressed = false
    private var volumeUpPressed = false
    private var triggered = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 必须实现此方法，否则部分 ROM 不会分派按键事件
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val action = event.action
        val keyCode = event.keyCode

        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    volumeDownPressed = true
                    startLongPressTimer()
                    Log.d(TAG, "🔽 音量- 按下")
                } else if (action == KeyEvent.ACTION_UP) {
                    volumeDownPressed = false
                    cancelLongPressTimer()
                    Log.d(TAG, "🔽 音量- 释放")
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    volumeUpPressed = true
                    startLongPressTimer()
                    Log.d(TAG, "🔼 音量+ 按下")
                } else if (action == KeyEvent.ACTION_UP) {
                    volumeUpPressed = false
                    cancelLongPressTimer()
                    Log.d(TAG, "🔼 音量+ 释放")
                }
            }
        }

        // 双键同时按 → 立即触发
        if (volumeDownPressed && volumeUpPressed) {
            Log.i(TAG, "⚡ 双键同时按下 → 触发！")
            triggerAlert()
            return true
        }

        // 单键长按中 → 消耗事件，阻止系统调整音量
        return volumeDownPressed || volumeUpPressed
    }

    private fun startLongPressTimer() {
        if (triggered) return
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = Runnable {
            if (volumeDownPressed || volumeUpPressed) {
                Log.i(TAG, "⏰ 长按 ${LONG_PRESS_MS}ms → 触发！")
                triggerAlert()
            }
        }
        handler.postDelayed(longPressRunnable!!, LONG_PRESS_MS)
    }

    private fun cancelLongPressTimer() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun triggerAlert() {
        if (triggered) return
        triggered = true

        // 🔔 振动反馈
        vibrate()

        // 启动报警服务
        val intent = Intent(this, EmergencyService::class.java).apply {
            action = EmergencyService.ACTION_TRIGGER
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startService(intent)

        Log.i(TAG, "🚨 报警已触发")
        handler.postDelayed({ triggered = false }, 5000) // 5 秒冷却
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                AccessibilityServiceInfo.DEFAULT
            notificationTimeout = 100
            // 不读屏幕、不模拟点击（权限最小化）
        }
        serviceInfo = info

        // 前台通知，提高存活率（无障碍服务无需 foregroundServiceType）
        showNotification()

        Log.i(TAG, "✅ 音量键监听已启动 (FLAG_REQUEST_FILTER_KEY_EVENTS)")
        Log.i(TAG, "   长按≥${LONG_PRESS_MS / 1000}秒 / 双键同时→触发")
    }

    private fun showNotification() {
        val channelId = "volume_key_service"
        val channelName = "按键监听"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW).apply {
                description = "紫守护按键触发服务"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("紫守护")
                .setContentText("按键唤醒已就绪")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("紫守护")
                .setContentText("按键唤醒已就绪")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1001, notification)
    }

    override fun onInterrupt() {
        Log.w(TAG, "⚠️ 服务被中断")
    }

    override fun onDestroy() {
        cancelLongPressTimer()
        Log.i(TAG, "❌ 音量键监听服务已停止")
        super.onDestroy()
    }

    companion object {
        const val TAG = "VolumeKeyService"
    }
}
