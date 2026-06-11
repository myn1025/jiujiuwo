package com.shouhu.guardian.service

import ai.picovoice.porcupine.*
import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.shouhu.guardian.R

/**
 * 语音唤醒服务 — 基于 Porcupine 离线唤醒词检测
 *
 * 特性：
 * - 纯本地运行，无需联网（安全场景必需）
 * - 前台服务+低优先级通知，关闭APP仍运行
 * - 内置关键词"Porcupine"（英文），后续可替换为中文"紫守护"自定义关键词
 * - AccessKey 从 SharedPreferences 读取，需在 Picovoice Console 免费获取
 *
 * 获取 AccessKey: https://console.picovoice.ai/（免费，3个用户+1个自定义关键词）
 */
class WakeWordService : Service() {
    companion object {
        const val TAG = "WakeWordService"
        const val NOTIFICATION_ID = 2002
        const val CHANNEL_ID = "wake_word_service"
        private const val SAMPLE_RATE = 16000
    }

    private var porcupine: Porcupine? = null
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private var recordThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        initPorcupine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopListening()
        porcupine?.delete()
        porcupine = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // ===== 通知 =====
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "语音唤醒",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "语音唤醒持续监听"
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
            .setContentTitle("语音唤醒 监听中")
            .setContentText("说唤醒词触发报警（纯本地，不上传音频）")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun buildErrorNotification(msg: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, Class.forName("com.shouhu.guardian.ui.MainActivity")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("语音唤醒 启动失败")
            .setContentText(msg)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pi)
            .build()
    }

    // ===== Porcupine 初始化 =====
    private fun initPorcupine() {
        try {
            val prefs = getSharedPreferences("wake_word", Context.MODE_PRIVATE)
            val accessKey = prefs.getString("picovoice_access_key", "").orEmpty()

            if (accessKey.isBlank()) {
                val msg = "未配置 Picovoice AccessKey，请前往设置中配置"
                Log.w(TAG, msg)
                updateNotification(buildErrorNotification(msg))
                return
            }

            porcupine = Porcupine.Builder()
                .setAccessKey(accessKey)
                .setKeyword(Porcupine.BuiltInKeyword.PORCUPINE)
                .build(applicationContext)

            Log.i(TAG, "Porcupine 初始化成功 ($SAMPLE_RATE Hz, frame_len=${porcupine?.frameLength})")
            startListening()
        } catch (e: PorcupineInvalidArgumentException) {
            val msg = "AccessKey 无效"
            Log.e(TAG, msg, e)
            updateNotification(buildErrorNotification(msg))
        } catch (e: PorcupineActivationException) {
            val msg = "激活失败，请检查网络或 AccessKey 状态"
            Log.e(TAG, msg, e)
            updateNotification(buildErrorNotification(msg))
        } catch (e: Exception) {
            val msg = "初始化失败: ${e.message}"
            Log.e(TAG, msg, e)
            updateNotification(buildErrorNotification(msg))
        }
    }

    private fun updateNotification(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    // ===== 音频录制 =====
    private fun startListening() {
        if (isListening) return

        val frameLen = porcupine?.frameLength ?: run {
            Log.e(TAG, "Porcupine 未初始化，无法启动监听")
            return
        }

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "AudioRecord 缓冲区计算失败")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf.coerceAtLeast(frameLen * 2)
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "麦克风权限被拒绝", e)
            updateNotification(buildErrorNotification("需要麦克风权限"))
            return
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord 创建失败", e)
            return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord 未初始化")
            audioRecord?.release()
            audioRecord = null
            return
        }

        isListening = true
        audioRecord?.startRecording()

        recordThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            val frame = ShortArray(frameLen)

            while (isListening) {
                try {
                    val read = audioRecord?.read(frame, 0, frameLen) ?: -1
                    if (read <= 0) continue

                    val keywordIndex = porcupine?.process(frame) ?: -1
                    if (keywordIndex >= 0) {
                        Log.w(TAG, "⚡ 检测到唤醒词！触发报警")
                        Handler(Looper.getMainLooper()).post { triggerEmergency() }
                    }
                } catch (e: PorcupineException) {
                    Log.e(TAG, "Porcupine 处理异常", e)
                    Handler(Looper.getMainLooper()).post {
                        updateNotification(buildErrorNotification("语音唤醒异常，请重启服务"))
                    }
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "音频读取异常", e)
                }
            }
        }.apply {
            name = "Porcupine-AudioThread"
            start()
        }

        Log.i(TAG, "音频监听已启动")
    }

    private fun stopListening() {
        isListening = false
        recordThread?.interrupt()
        recordThread = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    // ===== 触发报警 =====
    private fun triggerEmergency() {
        try {
            val intent = Intent(this, EmergencyService::class.java).apply {
                action = EmergencyService.ACTION_TRIGGER
                putExtra(EmergencyService.EXTRA_TRIGGER_SOURCE, "voice")
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
