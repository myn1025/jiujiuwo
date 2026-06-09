package com.shouhu.guardian.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.shouhu.guardian.data.api.RetrofitClient
import com.shouhu.guardian.data.model.EmergencyRequest
import com.shouhu.guardian.ui.MainActivity
import kotlinx.coroutines.*
import java.io.File

/**
 * 紧急后台服务
 *
 * 收到 ACTION_TRIGGER 时执行完整报警流程：
 * 1. 获取 GPS 定位
 * 2. 录制音频
 * 3. 发送短信给紧急联系人
 * 4. 上传服务器
 */
class EmergencyService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentLevel = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        Log.i(TAG, "EmergencyService 已创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val source = intent?.getStringExtra(EXTRA_TRIGGER_SOURCE) ?: "unknown"

        when (action) {
            ACTION_TRIGGER -> {
                Log.w(TAG, "⚡ 收到报警触发 — 来源: $source")
                startEmergencyFlow(source)
            }
            ACTION_CANCEL -> {
                Log.i(TAG, "🛑 取消报警")
                stopEmergency()
            }
            else -> {
                // 前台启动
                startForeground(NOTIFICATION_ID, buildNotification("就绪", "等待触发…", false))
            }
        }

        return START_STICKY
    }

    private fun startEmergencyFlow(source: String) {
        currentLevel = 1
        val notification = buildNotification("⚠ 求救中", "正在获取位置…", true)
        startForeground(NOTIFICATION_ID, notification)

        scope.launch {
            // 1. 获取 GPS 位置
            val location = getLastLocation()
            val address = if (location != null) "${location.latitude},${location.longitude}" else "位置获取中…"

            Log.i(TAG, "📍 位置: $address")

            // 2. 开始录音
            startRecording()

            // 3. 发送短信
            sendSOSMessages(address)

            // 4. 上报服务器
            uploadToServer(source, location, address)

            // 5. 更新通知
            updateNotification("📡 已发送求救信号", "位置: $address\n已通知紧急联系人", true)
        }
    }

    private suspend fun getLastLocation(): Location? = suspendCancellableCoroutine { cont ->
        try {
            fusedLocationClient?.lastLocation?.addOnSuccessListener { location ->
                cont.resume(location) {}
            }?.addOnFailureListener {
                cont.resume(null) {}
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "缺少定位权限", e)
            cont.resume(null) {}
        }
    }

    private fun startRecording() {
        try {
            val file = File(externalCacheDir, "emergency_${System.currentTimeMillis()}.aac")
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            Log.i(TAG, "🎙️ 录音已开始: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "录音启动失败", e)
        }
    }

    private suspend fun sendSOSMessages(address: String) {
        try {
            val resp = RetrofitClient.apiService.getContacts()
            if (!resp.isSuccessful) return
            val contacts = resp.body() ?: return

            contacts.sortedBy { it.priority }.take(5).forEach { contact ->
                val message = "【SOS】${contact.name}，这是来自紫守护的紧急求救信号！\n📍 位置：$address\n请立即确认对方安全！"
                try {
                    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        getSystemService(android.telephony.SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        android.telephony.SmsManager.getDefault()
                    }
                    smsManager.sendTextMessage(contact.phone, null, message, null, null)
                    Log.i(TAG, "📩 短信已发送至 ${contact.name}(${contact.phone})")
                } catch (e: Exception) {
                    Log.e(TAG, "短信发送失败: ${contact.phone}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取联系人失败", e)
        }
    }

    private suspend fun uploadToServer(source: String, location: Location?, address: String) {
        try {
            val body = EmergencyRequest(
                latitude = location?.latitude ?: 0.0,
                longitude = location?.longitude ?: 0.0,
                address = address,
                deviceInfo = mapOf("source" to source, "level" to "$currentLevel")
            )
            RetrofitClient.apiService.triggerEmergency(body)
            Log.i(TAG, "✅ 报警已上报服务器")
        } catch (e: Exception) {
            Log.e(TAG, "上报服务器失败", e)
        }
    }

    private fun stopEmergency() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
        } catch (_: Exception) {}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "🛑 报警流程已停止")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        try { mediaRecorder?.release() } catch (_: Exception) {}
        mediaRecorder = null
    }

    private fun buildNotification(title: String, text: String, ongoing: Boolean): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setOngoing(ongoing)
            .setPriority(if (ongoing) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun updateNotification(title: String, text: String, ongoing: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(title, text, ongoing))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "紧急求救",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "紫守护报警服务"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val TAG = "EmergencyService"
        const val CHANNEL_ID = "emergency_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_TRIGGER = "com.shouhu.guardian.TRIGGER"
        const val ACTION_CANCEL = "com.shouhu.guardian.CANCEL"
        const val EXTRA_TRIGGER_SOURCE = "trigger_source"
    }
}
