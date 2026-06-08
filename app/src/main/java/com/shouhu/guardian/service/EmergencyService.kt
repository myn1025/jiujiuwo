package com.shouhu.guardian.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.shouhu.guardian.ui.MainActivity

/**
 * 紧急后台服务
 *
 * 触发报警后运行，执行：
 * 1. 获取 GPS 定位
 * 2. 录制音频
 * 3. 发送短信给紧急联系人
 * 4. 上传服务器
 */
class EmergencyService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = buildNotification()
        startForeground(1001, notification)

        // TODO: 执行紧急流程
        // 1. 获取位置
        // 2. 录音
        // 3. 联系紧急联系人
        // 4. 上报服务器

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "紧急求救",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "救救我报警服务"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("救救我")
            .setContentText("正在发送求救信号…")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "emergency_channel"
    }
}
