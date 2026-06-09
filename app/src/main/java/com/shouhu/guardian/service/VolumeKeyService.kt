package com.shouhu.guardian.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * 音量键触发服务 - AccessibilityService
 *
 * 拦截音量键事件，检测长按（≥3秒）触发报警。
 * 锁屏下也能接收按键事件。
 */
class VolumeKeyService : AccessibilityService() {

    private var volumeDownPressed = false
    private var volumeUpPressed = false
    private var downPressTime = 0L
    private var upPressTime = 0L
    private var emergencyTriggered = false
    private val handler = Handler(Looper.getMainLooper())
    private val LONG_PRESS_MS = 3000L // 长按阈值
    private var longPressRunnable: Runnable? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要处理屏幕事件，只处理按键
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode

        // 只处理音量键
        if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
            return super.onKeyEvent(event)
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        if (!volumeDownPressed) {
                            volumeDownPressed = true
                            downPressTime = event.eventTime
                            Log.d(TAG, "音量- 按下")
                        }
                    }
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        if (!volumeUpPressed) {
                            volumeUpPressed = true
                            upPressTime = event.eventTime
                            Log.d(TAG, "音量+ 按下")
                        }
                    }
                }

                // 同侧连按检测（单键连续触发，最常见的使用方式）
                if (volumeDownPressed || volumeUpPressed) {
                    // 开始长按计时
                    val pressTime = if (volumeDownPressed) downPressTime else upPressTime
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    longPressRunnable = Runnable {
                        if ((volumeDownPressed || volumeUpPressed) && !emergencyTriggered) {
                            Log.w(TAG, "⚡ 音量键长按 ${LONG_PRESS_MS / 1000}秒 → 触发报警！")
                            triggerEmergency()
                        }
                    }
                    handler.postDelayed(longPressRunnable!!, LONG_PRESS_MS)
                }

                // 双键同时按（音量+和音量-同时按住）→ 立即触发（最高优先级）
                if (volumeDownPressed && volumeUpPressed) {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    if (!emergencyTriggered) {
                        Log.w(TAG, "⚡ 音量键同时按下 → 立即触发报警！")
                        triggerEmergency()
                    }
                }
            }

            KeyEvent.ACTION_UP -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        volumeDownPressed = false
                    }
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        volumeUpPressed = false
                    }
                }
                // 取消长按计时
                longPressRunnable?.let { handler.removeCallbacks(it) }
                longPressRunnable = null
            }
        }

        // 只在追踪长按期间消费事件（阻止音量调节），其他时候放行
        return (volumeDownPressed || volumeUpPressed)
    }

    private fun triggerEmergency() {
        emergencyTriggered = true
        val intent = Intent(this, EmergencyService::class.java).apply {
            action = EmergencyService.ACTION_TRIGGER
            putExtra(EmergencyService.EXTRA_TRIGGER_SOURCE, "volume_key")
        }
        startService(intent)

        // 2秒后重置状态，允许再次触发
        handler.postDelayed({
            emergencyTriggered = false
        }, 2000)
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService 被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        longPressRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        // 必须在代码中动态设置 serviceInfo，XML 中的 canRequestFilterKeyEvents
        // 在部分国产 ROM（小米/OPPO/vivo 等）上不可靠
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 100
        }
        serviceInfo = info

        Log.i(TAG, "音量键监听服务已启动 ✅ — FLAG_REQUEST_FILTER_KEY_EVENTS 已设置")
        Log.i(TAG, "  单键长按 ≥${LONG_PRESS_MS / 1000}秒 → 触发报警")
        Log.i(TAG, "  双键同时按 → 立即触发")
        Log.i(TAG, "  锁屏下也生效")
    }

    companion object {
        const val TAG = "VolumeKeyService"
    }
}