package com.shouhu.guardian.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.shouhu.guardian.data.api.RetrofitClient
import com.shouhu.guardian.data.model.EmergencyRequest
import com.shouhu.guardian.ui.MainActivity
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

/**
 * 紧急后台服务
 *
 * 收到 ACTION_TRIGGER 时执行完整报警流程：
 * 1. 获取 GPS 定位（用 LocationManager，不依赖 Google Play Services）
 * 2. 录制音频
 * 3. 发送短信给紧急联系人
 * 4. 上传服务器
 */
class EmergencyService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var locationManager: LocationManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentLevel = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        Log.i(TAG, "EmergencyService 已创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 恢复认证 token（防止进程重启后 RetrofitClient 丢 token）
        try {
            val token = getSharedPreferences("auth", MODE_PRIVATE).getString("token", null)
            RetrofitClient.setToken(token)
        } catch (_: Exception) {}

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
        try {
            val notification = buildNotification("⚠ 求救中", "正在获取位置…", true)
            startForeground(NOTIFICATION_ID, notification)
            Log.i(TAG, "startForeground 成功")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground 失败: ${e.message}", e)
            try {
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(9999, buildNotification("⚠ 求救中", "正在获取位置…", true))
            } catch (_: Exception) {}
        }

        // 保底定时器：流程跑完后至少保持30秒前台
        handler.postDelayed({
            if (mediaRecorder == null) {
                Log.i(TAG, "⏰ 30秒超时，自动停止")
                stopEmergency()
            }
        }, 30_000L)

        scope.launch {
            try {
                // 1. 获取 GPS 位置 + 反向地理编码
                val location = getLocation()
                val address = if (location != null) {
                    reverseGeocode(location)
                } else {
                    "位置获取失败"
                }
                Log.i(TAG, "📍 位置: $address")

                updateNotification("📍 位置已获取", "位置: $address\n正在发送求救…", true)

                // 2. 开始录音
                startRecording()

                // 3. 发送短信
                val smsResult = sendSOSMessages(address, location)

                // 4. 上报服务器
                uploadToServer(source, location, address)

                // 5. 更新通知
                updateNotification("📡 已发送求救信号", "位置: $address\n$smsResult", true)
            } catch (e: Exception) {
                Log.e(TAG, "startEmergencyFlow 内部异常: ${e.message}", e)
                updateNotification("⚠ 求救中（部分失败）", "位置或短信可能未成功", true)
            }
        }
    }

    /**
     * 获取 GPS 位置 — 使用 Android 原生 LocationManager（不依赖 Google Play Services）
     * 高德地图定位需另外接入 SDK，现阶段用原生 GPS+网络双定位
     */

    /**
     * 反向地理编码：坐标 → 地址
     * 用 Android Geocoder 转换，失败降级为坐标字符串
     */
    private fun reverseGeocode(location: Location): String {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val sb = StringBuilder()
                if (!addr.thoroughfare.isNullOrBlank()) sb.append(addr.thoroughfare)
                if (!addr.featureName.isNullOrBlank()) {
                    if (sb.isNotEmpty()) sb.append(" ")
                    sb.append(addr.featureName)
                }
                if (!addr.locality.isNullOrBlank()) {
                    if (sb.isNotEmpty()) sb.append("，")
                    sb.append(addr.locality)
                }
                if (sb.isEmpty()) {
                    "${location.latitude},${location.longitude}"
                } else {
                    "${sb}，(${location.latitude},${location.longitude})"
                }
            } else {
                "${location.latitude},${location.longitude}"
            }
        } catch (e: Exception) {
            Log.w(TAG, "反向地理编码失败: ${e.message}")
            "${location.latitude},${location.longitude}"
        }
    }
    private suspend fun getLocation(): Location? = suspendCancellableCoroutine { cont ->
        var resumed = false
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                if (!resumed) { resumed = true; cont.resume(loc) {} }
            }
            override fun onProviderDisabled(p: String) {}
            override fun onProviderEnabled(p: String) {}
            override fun onStatusChanged(p: String, s: Int, e: Bundle) {}
        }

        try {
            // 先尝试 getLastKnownLocation（最快）
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (provider in providers) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    val last = locationManager?.getLastKnownLocation(provider)
                    if (last != null && (System.currentTimeMillis() - last.time) < 120_000) {
                        // 2分钟内的缓存用
                        cont.resume(last) {}
                        return@suspendCancellableCoroutine
                    }
                }
            }

            // 请求单次定位 — 先 GPS 后网络
            val hasFine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasFine || hasCoarse) {
                // 先请求 GPS（有精细权限时）
                if (hasFine) {
                    locationManager?.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper())
                }
                // 同时请求网络定位作为兜底
                if (hasCoarse) {
                    locationManager?.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper())
                }
                // 8秒超时 → 15秒（室内GPS冷启动需更长时间）
                handler.postDelayed({
                    if (!resumed) { resumed = true; cont.resume(null) {} }
                }, 15000L)
            } else {
                Log.e(TAG, "缺少定位权限")
                cont.resume(null) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "定位异常: ${e.message}")
            cont.resume(null) {}
        }

        cont.invokeOnCancellation {
            try { locationManager?.removeUpdates(listener) } catch (_: Exception) {}
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

    private suspend fun sendSOSMessages(address: String, location: Location?): String {
        var successCount = 0
        var failCount = 0
        try {
            // 🔑 先检查 SMS 权限
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "❌ SEND_SMS 权限未授予，无法发送短信")
                return "❌ 短信权限未开启"
            }
            val resp = RetrofitClient.apiService.getContacts()
            if (!resp.isSuccessful) {
                Log.e(TAG, "获取联系人失败 HTTP ${resp.code()}")
                return "❌ 获取联系人失败(${resp.code()})"
            }
            val contacts = resp.body()
            if (contacts.isNullOrEmpty()) {
                Log.w(TAG, "联系人列表为空，请在APP中添加紧急联系人")
                return "⚠️ 未设置紧急联系人"
            }

            Log.i(TAG, "📋 获取到 ${contacts.size} 个联系人")

            contacts.sortedBy { it.priority }.take(5).forEach { contact ->
                // 🔑 构建包含原始坐标和地图链接的完整位置信息
                val coordInfo = if (location != null) {
                    "\n🌐 GPS坐标: ${location.latitude}, ${location.longitude}\n🗺️ 地图: https://maps.google.com/?q=${location.latitude},${location.longitude}"
                } else ""
                val message = "【SOS】${contact.name}，这是来自紫守护的紧急求救信号！\n📍 位置：$address$coordInfo\n请立即确认对方安全！"
                try {
                    val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        getSystemService(SmsManager::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getDefault()
                    }
                    smsManager.sendTextMessage(contact.phone, null, message, null, null)
                    successCount++
                    Log.i(TAG, "📩 短信已发送至 ${contact.name}(${contact.phone})")
                } catch (e: Exception) {
                    failCount++
                    Log.e(TAG, "短信发送失败: ${contact.phone}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取联系人异常", e)
            return "❌ 网络异常，无法获取联系人"
        }
        return "✅ 已通知 $successCount 人" + if (failCount > 0) "（${failCount}人失败）" else ""
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
            .setSmallIcon(com.shouhu.guardian.R.drawable.ic_notification)
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
