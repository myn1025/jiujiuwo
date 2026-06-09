package com.shouhu.guardian.service

import android.accessibilityservice.AccessibilityService
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

class VolumeKeyService : AccessibilityService() {

    private var volumeDownPressed = false
    private var volumeUpPressed = false
    private var downPressTime = 0L
    private var upPressTime = 0L
    private var triggered = false
    private val handler = Handler(Looper.getMainLooper())
    private val LONG_PRESS_MS = 2000L
    private var longPressRunnable: Runnable? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
            return super.onKeyEvent(event)
        }

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        if (!volumeDownPressed) { volumeDownPressed = true; downPressTime = event.eventTime }
                    }
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        if (!volumeUpPressed) { volumeUpPressed = true; upPressTime = event.eventTime }
                    }
                }
                // 长按计时
                longPressRunnable?.let { handler.removeCallbacks(it) }
                longPressRunnable = Runnable {
                    if ((volumeDownPressed || volumeUpPressed) && !triggered) {
                        Log.i(TAG, "⚡ 长按${LONG_PRESS_MS / 1000}秒 → 触发")
                        triggerAlert()
                    }
                }
                handler.postDelayed(longPressRunnable!!, LONG_PRESS_MS)

                // 双键同时 → 立即触发
                if (volumeDownPressed && volumeUpPressed && !triggered) {
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    Log.i(TAG, "⚡ 双键同时 → 触发")
                    triggerAlert()
                }
            }
            KeyEvent.ACTION_UP -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_DOWN -> volumeDownPressed = false
                    KeyEvent.KEYCODE_VOLUME_UP -> volumeUpPressed = false
                }
                longPressRunnable?.let { handler.removeCallbacks(it) }
                longPressRunnable = null
            }
        }

        return (volumeDownPressed || volumeUpPressed)
    }

    private fun triggerAlert() {
        triggered = true
        try {
            // 振动
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(300)
            }
        } catch (_: Exception) {}

        try {
            startService(Intent(this, EmergencyService::class.java).apply {
                action = EmergencyService.ACTION_TRIGGER
                putExtra(EmergencyService.EXTRA_TRIGGER_SOURCE, "volume_key")
            })
        } catch (_: Exception) {}

        handler.postDelayed({ triggered = false }, 5000)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "✅ 音键监听已启动")
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    companion object {
        const val TAG = "VolumeKeyService"
    }
}
