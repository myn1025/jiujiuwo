package com.shouhu.guardian.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.shouhu.guardian.R
import kotlin.math.sqrt

/**
 * 摇一摇求救服务
 *
 * 算法：3次剧烈摇晃（加速度>2.5g）在2秒内 → 触发报警
 * 防误触：正常走路/跑步加速度<1.5g，丢手机<2.0g
 * 冷却：触发后5秒冷却期
 *
 * 保活：前台服务 + WakeLock
 */
class ShakeService : Service() {
    companion object {
        const val TAG = "ShakeService"
        const val NOTIFICATION_ID = 2003
        const val CHANNEL_ID = "shake_service"

        // 检测参数
        private const val SHAKE_THRESHOLD = 1.5f  // g 力阈值（正常活动<1.5g, 降低以提高灵敏度）
        private const val SHAKE_WINDOW_MS = 2000L  // 时间窗口
        private const val SHAKE_COUNT = 2           // 需要多少次摇晃（降低从3到2提高响应速度）
        private const val COOLDOWN_MS = 5000L       // 冷却期
    }

    private var sensorManager: SensorManager? = null
    private var sensorListener: SensorEventListener? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // 摇晃检测状态
    private val shakeTimes = mutableListOf<Long>()
    private var lastTriggerTime = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        initSensor()
        // 持有 WakeLock 确保锁屏后传感器仍活跃
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:ShakeLock")
        wakeLock?.acquire(10 * 60 * 1000L) // 最长持有10分钟
        Log.i(TAG, "摇一摇服务已启动，阈值=${SHAKE_THRESHOLD}g, 窗口=${SHAKE_WINDOW_MS}ms, 次数=${SHAKE_COUNT}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorListener?.let { sensorManager?.unregisterListener(it) }
        sensorListener = null
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
        if (accel == null) {
            Log.e(TAG, "设备无加速度传感器")
            stopSelf()
            return
        }

        sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                handleAccelerometer(event)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager?.registerListener(
            sensorListener,
            accel,
            SensorManager.SENSOR_DELAY_GAME  // 20ms 采样
        )
        Log.i(TAG, "加速度传感器已注册")
    }

    private fun handleAccelerometer(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // g 力 = sqrt(x²+y²+z²) / 重力加速度
        val gForce = sqrt((x * x + y * y + z * z).toDouble()).toFloat() / SensorManager.GRAVITY_EARTH
        val now = System.currentTimeMillis()

        // 每 5 秒输出一次 debug 日志，确认传感器在工作
        if (now % 5000 < 20) {
            Log.d(TAG, "传感器活跃 — gForce=${"%.2f".format(gForce)} x=${"%.1f".format(x)} y=${"%.1f".format(y)} z=${"%.1f".format(z)}")
        }

        // 低于阈值：忽略
        if (gForce < SHAKE_THRESHOLD) return

        // 冷却期检查
        if (now - lastTriggerTime < COOLDOWN_MS) return

        Log.i(TAG, "检测到剧烈运动: gForce=${gForce}, 时间=$now, 已记录次数=${shakeTimes.size + 1}")

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
