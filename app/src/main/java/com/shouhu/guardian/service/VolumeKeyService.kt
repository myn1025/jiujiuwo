package com.shouhu.guardian.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class VolumeKeyService : AccessibilityService() {

    private var volDown = false
    private var volUp = false
    private var triggered = false
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        try {
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
            } else {
                if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) volDown = false else volUp = false
                runnable?.let { handler.removeCallbacks(it) }
            }
            return (volDown || volUp)
        } catch (e: Exception) {
            Log.e(TAG, "onKeyEvent: ${e.message}")
            return false
        }
    }

    private fun trigger() {
        triggered = true
        try {
            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(200, 200))
        } catch (_: Exception) {}
        try {
            startService(Intent(this, EmergencyService::class.java).apply {
                action = EmergencyService.ACTION_TRIGGER
                putExtra(EmergencyService.EXTRA_TRIGGER_SOURCE, "volume_key")
            })
        } catch (_: Exception) {}
        handler.postDelayed({ triggered = false }, 5000L)
    }

    override fun onServiceConnected() {
        // 恢复认证 token（进程重启后）
        try {
            val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
            com.shouhu.guardian.data.api.RetrofitClient.setToken(token)
        } catch (_: Exception) {}

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
        Log.i(TAG, "onServiceConnected OK")
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        runnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    companion object { const val TAG = "VolumeKeyService" }
}