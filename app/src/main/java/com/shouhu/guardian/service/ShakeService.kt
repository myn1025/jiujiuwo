package com.shouhu.guardian.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.shouhu.guardian.R
import kotlinx.coroutines.*

/**
 * 摇一摇求救服务
 *
 * 算法：3次剧烈摇晃（加速度>2.5g）在2秒内 → 触发报警
 * 防误触：正常走路/跑步加速度<1.5g，丢手机<2.0g
 * 冷却：触发后5秒冷却期
 */
class ShakeService : Service() {
    companion object {
        const val TAG = "ShakeService"
        const val NOTIFICATION_ID = 2003
        const val CHANNEL_ID = "shake_service"

        // 检测参数
        private const val SHAKE_THRESHOLD = 2.5f  // g 力阈值（正常活动<1.5g）
        private const val SHAKE_WINDOW_MS = 2000L  // 时间窗口
        private const val SHAKE_COUNT = 3           // 需要多少次摇晃
        private const val COOLDOWN_MS = 5000L       // 冷却期
    }

    private var sensorManager: SensorManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // 摇晃检测状态
    private val shakeTimes = mutableListOf<Long>()
    private var lastTriggerTime = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        initSensor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // ===== 通知 =====
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "摇一摇求救",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "摇一摇触发报警"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, Class.forName("com.shouhu.guardian.ui.MainActivity")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("摇一摇求救 运行中")
            .setContentText("剧烈摇晃手机触发报警")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    // ===== 传感器 =====
    private fun initSensor() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel != null) {
            sensorManager?.registerListener(
                object : android.hardware.SensorEventListener {
                    override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                        event ?: return
                        handleAccelerometer(event)
                    }
                    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
                },
                accel,
                SensorManager.SENSOR_DELAY_GAME  // 20ms 采样，够快不费电
            )
            Log.i(TAG, "加速度传感器已注册")
        } else {
            Log.e(TAG, "设备无加速度传感器")
            stopSelf()
        }
    }

    private fun handleAccelerometer(event: android.hardware.SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // 计算 g 力：加速度矢量大小 / 重力加速度
        val gForce = Math.sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
        val now = System.currentTimeMillis()

        // 低于阈值：忽略
        if (gForce < SHAKE_THRESHOLD) return

        // 冷却期检查
        if (now - lastTriggerTime < COOLDOWN_MS) return

        // 记录摇晃时间
        shakeTimes.add(now)

        // 清理过期记录（窗口外）
        shakeTimes.removeAll { now - it > SHAKE_WINDOW_MS }

        // 判断：窗口内摇晃次数达标 → 触发
        if (shakeTimes.size >= SHAKE_COUNT) {
            shakeTimes.clear()
            lastTriggerTime = now
            Log.w(TAG, "⚡ 检测到摇一摇求救！触发报警")
            triggerEmergency()
        }
    }

    private fun triggerEmergency() {
        try {
            val intent = Intent(this, EmergencyService::class.java).apply {
                action = EmergencyService.ACTION_TRIGGER
                putExtra(EmergencyService.EXTRA_TRIGGER_SOURCE, "shake")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "触发 EmergencyService 失败: ${e.message}")
        }
    }
}
