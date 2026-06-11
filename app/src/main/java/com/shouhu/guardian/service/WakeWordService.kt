package com.shouhu.guardian.service

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
import org.vosk.*
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

/**
 * 语音唤醒服务 — 基于 Vosk 离线语音识别
 *
 * 特性：
 * - 完全免费，无试用期限制
 * - 纯本地运行，无需联网（仅首次下载模型）
 * - 支持中文触发关键词（用户可自定义）
 * - 可识别多个关键词（逗号分隔）
 * - 前台服务+低优先级通知，关闭APP仍运行
 *
 * 模型：vosk-model-small-cn-0.22（~42MB，首次使用自动下载）
 * 下载链接：https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip
 */
class WakeWordService : Service() {
    companion object {
        const val TAG = "WakeWordService"
        const val NOTIFICATION_ID = 2002
        const val CHANNEL_ID = "wake_word_service"
        private const val SAMPLE_RATE = 16000
        private const val MODEL_DIR = "vosk-model-small-cn-0.22"
        private const val MODEL_ZIP = "$MODEL_DIR.zip"
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_ZIP"

        // 每次 acceptWaveform 的帧大小（400 samples = 25ms at 16kHz）
        private const val FRAME_LEN = 400
    }

    // 服务状态
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private var pendingRestart = false

    // 线程
    private var recordThread: Thread? = null
    private var downloadThread: Thread? = null

    // 唤醒词列表（从 SharedPreferences 读取）
    private var wakeWords: List<String> = listOf("救救我")

    // 回调重试锁
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("语音唤醒 加载中...", "准备中..."))
        loadSettings()
        ensureModelAndStart()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESTART) {
            pendingRestart = true
            handler.post {
                stopListening()
                recognizer?.close()
                recognizer = null
                model = null
                pendingRestart = false
                loadSettings()
                ensureModelAndStart()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pendingRestart = false
        isListening = false
        downloadThread = null
        recordThread?.interrupt()
        recordThread = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        recognizer?.close()
        recognizer = null
        model?.close()
        model = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // ===== 设置 =====
    private fun loadSettings() {
        val prefs = getSharedPreferences("wake_word", Context.MODE_PRIVATE)
        val raw = prefs.getString("trigger_keyword", "救救我").orEmpty()
        wakeWords = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (wakeWords.isEmpty()) wakeWords = listOf("救救我")
        Log.i(TAG, "唤醒关键词: $wakeWords")
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

    private fun buildNotification(title: String, text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, Class.forName("com.shouhu.guardian.ui.MainActivity")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pi)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    // ===== 模型管理 =====
    private fun getModelDir(): String {
        return "${filesDir.absolutePath}/$MODEL_DIR"
    }

    private fun modelExists(): Boolean {
        val dir = File(getModelDir())
        return dir.exists() && dir.isDirectory && File(dir, "am").exists()
    }

    private fun ensureModelAndStart() {
        if (modelExists()) {
            Log.i(TAG, "模型已存在，直接初始化")
            initRecognizer()
            return
        }

        Log.w(TAG, "模型不存在，开始下载 (~42MB)")
        updateNotification("语音唤醒 下载中...", "首次使用需下载语音模型 (~42MB)")
        downloadThread = Thread {
            try {
                downloadAndExtractModel()
                handler.post {
                    updateNotification("语音唤醒 下载完成", "正在初始化...")
                    initRecognizer()
                }
            } catch (e: Exception) {
                Log.e(TAG, "模型下载失败: ${e.message}")
                handler.post {
                    updateNotification("语音唤醒 下载失败", e.message ?: "请检查网络后重启服务")
                }
            }
            downloadThread = null
        }.apply {
            name = "Vosk-ModelDownload"
            start()
        }
    }

    @Throws(IOException::class)
    private fun downloadAndExtractModel() {
        val cacheZip = File(cacheDir, MODEL_ZIP)
        val targetDir = File(getModelDir())

        // 下载 ZIP
        val url = URL(MODEL_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.apply {
            connectTimeout = 15000
            readTimeout = 30000
        }
        conn.connect()

        val totalBytes = conn.contentLengthLong
        val input = conn.inputStream
        val output = FileOutputStream(cacheZip)
        val buf = ByteArray(8192)
        var downloaded = 0L
        var lastProgress = 0

        while (true) {
            val read = input.read(buf)
            if (read < 0) break
            output.write(buf, 0, read)
            downloaded += read
            if (totalBytes > 0) {
                val pct = (downloaded * 100 / totalBytes).toInt()
                if (pct > lastProgress) {
                    lastProgress = pct
                    val progressPct = pct
                    handler.post {
                        updateNotification("语音唤醒 下载中...", "$progressPct% ($MODEL_DIR)")
                    }
                }
            }
        }
        output.close()
        input.close()
        conn.disconnect()

        Log.i(TAG, "下载完成: ${cacheZip.length()} bytes")

        // 解压到目标目录
        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()

        val zis = java.util.zip.ZipInputStream(FileInputStream(cacheZip))
        var entry = zis.nextEntry
        while (entry != null) {
            // 跳过 zip 的根目录前缀（vosk-model-small-cn-0.22/）
            val relativeName = entry.name.substringAfter("/")
            if (relativeName.isNotEmpty()) {
                val outFile = File(targetDir, relativeName)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        zis.close()

        // 删除 ZIP
        cacheZip.delete()

        Log.i(TAG, "模型解压完成: ${targetDir.absolutePath}")
    }

    // ===== 识别器初始化 =====
    private fun initRecognizer() {
        try {
            model = Model(getModelDir())
            recognizer = Recognizer(model, SAMPLE_RATE.toFloat())
            Log.i(TAG, "Vosk 识别器初始化成功")
            startListening()
        } catch (e: Exception) {
            Log.e(TAG, "识别器初始化失败: ${e.message}")
            updateNotification("语音唤醒 初始化失败", e.message ?: "请重启服务")
        }
    }

    // ===== 音频录制 =====
    private fun startListening() {
        if (isListening) return
        if (recognizer == null) {
            Log.e(TAG, "识别器未初始化，无法启动监听")
            return
        }

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "AudioRecord 缓冲区计算失败")
            updateNotification("语音唤醒 启动失败", "AudioRecord 初始化失败")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf.coerceAtLeast(FRAME_LEN * 2)
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "麦克风权限被拒绝", e)
            updateNotification("语音唤醒 无权限", "请允许麦克风权限")
            return
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord 创建失败", e)
            updateNotification("语音唤醒 启动失败", e.message ?: "AudioRecord 异常")
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

        updateNotification("语音唤醒 监听中", "关键词: ${wakeWords.joinToString(", ")}")

        recordThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            val frame = ShortArray(FRAME_LEN)

            while (isListening && !Thread.interrupted()) {
                try {
                    val nread = audioRecord?.read(frame, 0, FRAME_LEN) ?: -1
                    if (nread <= 0) {
                        // 短暂休眠避免 CPU 空转
                        try { Thread.sleep(10) } catch (_: InterruptedException) { break }
                        continue
                    }

                    val isFinal = recognizer?.acceptWaveform(frame, nread) ?: continue

                    val jsonStr = if (isFinal) {
                        recognizer?.result ?: ""
                    } else {
                        recognizer?.partialResult ?: ""
                    }

                    if (jsonStr.isNotEmpty() && checkForWakeWord(jsonStr)) {
                        break // 触发报警后退出线程
                    }
                } catch (e: Exception) {
                    if (isListening) {
                        Log.e(TAG, "识别循环异常", e)
                    }
                    break
                }
            }

            Log.i(TAG, "录音线程结束")
            recognizer?.reset()
        }.apply {
            name = "Vosk-AudioThread"
            start()
        }
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

    // ===== 关键词匹配 =====
    private fun checkForWakeWord(jsonStr: String): Boolean {
        return try {
            val json = JSONObject(jsonStr)
            // 检查 partial（部分识别）和 text（最终结果）
            val text = json.optString("partial", "")
                .ifEmpty { json.optString("text", "") }

            if (text.isBlank()) return false

            for (word in wakeWords) {
                if (text.contains(word)) {
                    Log.w(TAG, "⚡ 检测到唤醒词 '$word' 在 \"$text\"")
                    handler.post { triggerEmergency() }
                    return true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
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

    // ===== 外部重启接口 =====
    companion object {
        const val ACTION_RESTART = "com.shouhu.guardian.action.RESTART_WAKE_WORD"
    }
}
