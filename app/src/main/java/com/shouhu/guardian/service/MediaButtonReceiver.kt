package com.shouhu.guardian.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent

/**
 * 蓝牙耳机按键触发 - BroadcastReceiver
 *
 * 监听 MediaButton 事件，检测连按3次或长按3秒触发报警。
 * Android 8.0+ 需要通过 MediaSession 注册接收。
 */
class MediaButtonReceiver : BroadcastReceiver() {

    private var clickCount = 0
    private var lastClickTime = 0L
    private var pressStartTime = 0L
    private var longPressTriggered = false
    private val handler = Handler(Looper.getMainLooper())
    private val DOUBLE_CLICK_WINDOW = 800L // 连按时间窗口
    private val TRIPLE_CLICK_COUNT = 3     // 3次触发
    private val LONG_PRESS_MS = 3000L      // 长按阈值

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_MEDIA_BUTTON != intent.action) return

        val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return

        // 只处理耳机按键
        val validCodes = setOf(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS
        )
        if (keyEvent.keyCode !in validCodes) return

        when (keyEvent.action) {
            KeyEvent.ACTION_DOWN -> {
                if (!longPressTriggered) {
                    pressStartTime = keyEvent.eventTime
                    // 启动长按检测
                    handler.postDelayed({
                        if (!longPressTriggered) {
                            Log.w(TAG, "🎧 蓝牙耳机长按 ${LONG_PRESS_MS / 1000}秒 → 触发报警！")
                            longPressTriggered = true
                            triggerEmergency(context)
                        }
                    }, LONG_PRESS_MS)
                }
            }

            KeyEvent.ACTION_UP -> {
                handler.removeCallbacksAndMessages(null)

                if (!longPressTriggered) {
                    val now = keyEvent.eventTime
                    // 连按检测
                    if (now - lastClickTime < DOUBLE_CLICK_WINDOW) {
                        clickCount++
                    } else {
                        clickCount = 1
                    }
                    lastClickTime = now

                    if (clickCount >= TRIPLE_CLICK_COUNT) {
                        Log.w(TAG, "🎧 蓝牙耳机连按${TRIPLE_CLICK_COUNT}次 → 触发报警！")
                        clickCount = 0
                        triggerEmergency(context)
                    }
                }
            }
        }
    }

    private fun triggerEmergency(context: Context) {
        val intent = Intent(context, EmergencyService::class.java).apply {
            action = EmergencyService.ACTION_TRIGGER
            putExtra(EmergencyService.EXTRA_TRIGGER_SOURCE, "bluetooth")
        }
        context.startService(intent)

        // 2秒后重置
        handler.postDelayed({
            longPressTriggered = false
        }, 2000)
    }

    companion object {
        const val TAG = "MediaButtonReceiver"
    }
}