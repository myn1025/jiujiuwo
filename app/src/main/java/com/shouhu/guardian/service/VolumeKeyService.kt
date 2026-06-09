package com.shouhu.guardian.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.content.Intent

class VolumeKeyService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val LONG_PRESS_MS = 2000L
    private var longPressRunnable: Runnable? = null
    private var volumeDownPressed = false
    private var volumeUpPressed = false
    private var triggered = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 必须实现，部分 ROM 靠此判断服务活跃
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val action = event.action
        val keyCode = event.keyCode

        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    volumeDownPressed = true
                    startLongPressTimer()
                } else {
                    volumeDownPressed = false
                    cancelLongPressTimer()
                }
                return true // 总是消费音量事件，阻止系统调音量
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    volumeUpPressed = true
                    startLongPressTimer()
                } else {
                    volumeUpPressed = false
                    cancelLongPressTimer()
                }
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    private fun startLongPressTimer() {
        if (triggered) return
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = Runnable {
            if (volumeDownPressed || volumeUpPressed) {
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
        Log.i(TAG, "🚨 触发报警")

        // 振动反馈
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(300)
            }
        } catch (_: Exception) {}

        // 启动报警
        try {
            startService(Intent(this, EmergencyService::class.java).apply {
                action = EmergencyService.ACTION_TRIGGER
            })
        } catch (_: Exception) {}

        handler.postDelayed({ triggered = false }, 5000)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPES_ALL_MASK
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                    AccessibilityServiceInfo.DEFAULT
                notificationTimeout = 100
            }
            serviceInfo = info
            Log.i(TAG, "✅ 服务已启动")
        } catch (e: Exception) {
            Log.e(TAG, "onServiceConnected 异常: ${e.message}")
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        cancelLongPressTimer()
        super.onDestroy()
    }

    companion object {
        const val TAG = "VolumeKeyService"
    }
}