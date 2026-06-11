package com.shouhu.guardian.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.shouhu.guardian.R
import org.vosk.*
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

/**
 * 语音唤醒服务 — 基于 Vosk 离线语音识别
 *
 * 使用 Vosk Android 官方的 SpeechService 包装类，自动处理
 * AudioRecord + Recognizer + 多帧流式识别全链路。
 *
 * 特性：
 * - 完全免费（Apache 2.0），无试用期限制
 * - 纯本地运行，无需联网（仅首次下载模型）
 * - 支持中文触发关键词（用户可自定义，逗号分隔多关键词）
 * - 前台服务 + 低优先级通知，APP 关闭仍运行
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

        // 外部重启接口
        const val ACTION_RESTART = "com.shouhu.guardian.action.RESTART_WAKE_WORD"
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var isListening = false
    private var pendingRestart = false

    private var downloadThread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())

    /** 唤醒词列表（从 SharedPreferences 读取） */
    private var wakeWords: List<String> = listOf("救救我")

    // ===== 生命周期 =====

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
        speechService?.shutdown()
        speechService = null
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
            val ch = NotificationChannel(
                CHANNEL_ID, "语音唤醒", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
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
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    // ===== 模型管理 =====

    private fun getModelDir() = "${filesDir.absolutePath}/$MODEL_DIR"

    private fun modelExists(): Boolean {
        val dir = File(getModelDir())
        return dir.exists() && File(dir, "am").exists()
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
                handler.post { updateNotification("语音唤醒 下载失败", e.message ?: "请检查网络后重启服务") }
            }
            downloadThread = null
        }.apply { name = "Vosk-ModelDownload"; start() }
    }

    @Throws(IOException::class)
    private fun downloadAndExtractModel() {
        val cacheZip = File(cacheDir, MODEL_ZIP)
        val targetDir = File(getModelDir())

        val conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 30000
        }
        conn.connect()
        val total = conn.contentLengthLong
        val input = conn.inputStream
        val output = FileOutputStream(cacheZip)
        val buf = ByteArray(8192)
        var done = 0L; var lastPct = 0
        while (true) {
            val n = input.read(buf); if (n < 0) break
            output.write(buf, 0, n); done += n
            if (total > 0) {
                val pct = (done * 100 / total).toInt()
                if (pct > lastPct) {
                    lastPct = pct
                    val p = pct
                    handler.post { updateNotification("语音唤醒 下载中...", "$p% ($MODEL_DIR)") }
                }
            }
        }
        output.close(); input.close(); conn.disconnect()
        Log.i(TAG, "下载完成: ${cacheZip.length()} bytes")

        if (targetDir.exists()) targetDir.deleteRecursively()
        targetDir.mkdirs()
        val zis = java.util.zip.ZipInputStream(FileInputStream(cacheZip))
        var entry = zis.nextEntry
        while (entry != null) {
            val rel = entry.name.substringAfter("/")
            if (rel.isNotEmpty()) {
                val f = File(targetDir, rel)
                if (entry.isDirectory) f.mkdirs()
                else {
                    f.parentFile?.mkdirs()
                    FileOutputStream(f).use { zis.copyTo(it) }
                }
            }
            zis.closeEntry(); entry = zis.nextEntry
        }
        zis.close()
        cacheZip.delete()
        Log.i(TAG, "模型解压完成: ${targetDir.absolutePath}")
    }

    // ===== 识别器初始化 =====

    private fun initRecognizer() {
        try {
            model = Model(getModelDir())
            recognizer = Recognizer(model!!, SAMPLE_RATE.toFloat())
            Log.i(TAG, "Vosk 识别器初始化成功")
            startListening()
        } catch (e: Exception) {
            Log.e(TAG, "识别器初始化失败: ${e.message}")
            updateNotification("语音唤醒 初始化失败", e.message ?: "请重启服务")
        }
    }

    // ===== 监听启动（使用官方 SpeechService） =====

    private fun startListening() {
        if (isListening || recognizer == null) return

        isListening = true
        speechService = SpeechService(object : RecognitionListener {
            override fun onPartialResult(hypothesis: String) {
                checkForWakeWord(hypothesis)
            }

            override fun onResult(hypothesis: String) {
                Log.d(TAG, "onResult: $hypothesis")
            }

            override fun onFinalResult(hypothesis: String) {
                Log.d(TAG, "onFinalResult: $hypothesis")
                // 最终结果达到后自动重置识别器
                speechService?.shutdown()
                speechService = null
                // 创建新的 recognizer 重新开始
                recognizer?.close()
                recognizer = Recognizer(model!!, SAMPLE_RATE.toFloat())
                restartSpeechService()
            }

            override fun onError(e: Exception) {
                Log.e(TAG, "SpeechService 错误: ${e.message}")
                if (isListening && !pendingRestart) {
                    // 尝试自动恢复
                    handler.postDelayed({
                        if (isListening && !pendingRestart) {
                            Log.w(TAG, "SpeechService 自动恢复")
                            speechService?.shutdown()
                            speechService = null
                            recognizer?.close()
                            recognizer = Recognizer(model!!, SAMPLE_RATE.toFloat())
                            restartSpeechService()
                        }
                    }, 1000)
                }
            }

            override fun onTimeout() {
                Log.d(TAG, "onTimeout — 重置识别器")
                speechService?.shutdown()
                speechService = null
                recognizer?.close()
                recognizer = Recognizer(model!!, SAMPLE_RATE.toFloat())
                restartSpeechService()
            }
        }, SAMPLE_RATE.toFloat())

        speechService?.startListening(recognizer!!)
        updateNotification("语音唤醒 监听中", "关键词: ${wakeWords.joinToString(", ")}")
    }

    private fun restartSpeechService() {
        if (!isListening || pendingRestart) return
        speechService = SpeechService(object : RecognitionListener {
            override fun onPartialResult(hypothesis: String) {
                checkForWakeWord(hypothesis)
            }

            override fun onResult(hypothesis: String) {}

            override fun onFinalResult(hypothesis: String) {
                speechService?.shutdown()
                speechService = null
                recognizer?.close()
                recognizer = Recognizer(model!!, SAMPLE_RATE.toFloat())
                restartSpeechService()
            }

            override fun onError(e: Exception) {
                Log.e(TAG, "SpeechService 错误", e)
            }

            override fun onTimeout() {
                speechService?.shutdown()
                speechService = null
                recognizer?.close()
                recognizer = Recognizer(model!!, SAMPLE_RATE.toFloat())
                restartSpeechService()
            }
        }, SAMPLE_RATE.toFloat())
        speechService?.startListening(recognizer!!)
    }

    private fun stopListening() {
        isListening = false
        speechService?.shutdown()
        speechService = null
    }

    // ===== 关键词匹配 =====

    private fun checkForWakeWord(jsonStr: String) {
        try {
            val text = JSONObject(jsonStr).optString("partial", "")
                .ifEmpty { JSONObject(jsonStr).optString("text", "") }
            if (text.isBlank()) return

            for (word in wakeWords) {
                if (text.contains(word)) {
                    Log.w(TAG, "⚡ 检测到唤醒词 '$word' 在 \"$text\"")
                    handler.post { triggerEmergency() }
                    return
                }
            }
        } catch (_: Exception) {}
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
