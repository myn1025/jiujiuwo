package com.shouhu.guardian.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
 * 🔑 关键架构决策：Vosk 的 model.close() / recognizer.close()
 * 后立即重新创建会触发原生层 JNI 崩溃（"加固技术未适配"）。
 * 因此服务生命周期内绝不关闭 Vosk 实例，只通过 startListening/stopListening
 * 切换监听状态。
 */
class WakeWordService : Service() {
    companion object {
        const val TAG = "WakeWordService"
        const val NOTIFICATION_ID = 2002
        const val CHANNEL_ID = "wake_word_service"
        private const val SAMPLE_RATE = 16000
        private const val MODEL_DIR = "vosk-model-small-cn-0.22"
        private const val MODEL_ZIP = "$MODEL_DIR.zip"
        private const val MODEL_URL = "https://tc-api.cn/app/zishouhu/vosk-model-small-cn-0.22.zip"

        // 外部控制接口
        const val ACTION_RESTART = "com.shouhu.guardian.action.RESTART_WAKE_WORD"     // 重启监听（换关键词后）
        const val ACTION_STOP_LISTENING = "com.shouhu.guardian.action.STOP_LISTENING" // 暂停监听（关开关）
        const val ACTION_START_LISTENING = "com.shouhu.guardian.action.START_LISTENING" // 恢复监听（开开关）

        // 通知 UI
        const val ACTION_READY = "com.shouhu.guardian.action.WAKEWORD_READY"
        const val ACTION_FAILED = "com.shouhu.guardian.action.WAKEWORD_FAILED"
        const val EXTRA_ERROR = "error"

        // SharedPreferences 状态键
        const val PREF_STATE = "service_state"
        const val STATE_DOWNLOADING = "downloading"
        const val STATE_EXTRACTING = "extracting"
        const val STATE_INITIALIZING = "initializing"
        const val STATE_LISTENING = "listening"
        const val STATE_ERROR = "error"
        const val STATE_WAITING_WIFI = "waiting_wifi"

        // 非 WiFi 下载确认
        const val ACTION_DOWNLOAD_CONFIRM = "com.shouhu.guardian.action.DOWNLOAD_CONFIRM"
        const val ACTION_DOWNLOAD_CANCEL = "com.shouhu.guardian.action.DOWNLOAD_CANCEL"
        private const val NOTIFICATION_ID_PROMPT = 2003
        private const val MODEL_SIZE_MB = 42
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var isListening = false
    private var isForeground = false
    private var isRestarting = false  // 🔑 防止重复 ACTION_RESTART 导致 SpeechService 崩溃

    private var downloadThread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())

    /** 唤醒词列表（从 SharedPreferences 读取） */
    private var wakeWords: List<String> = listOf("救救我")

    private val recognitionListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String) {
            if (hypothesis.length > 10) Log.d(TAG, "partial: $hypothesis")
            checkForWakeWord(hypothesis)
        }

        override fun onResult(hypothesis: String) {
            Log.d(TAG, "onResult: $hypothesis")
            checkForWakeWord(hypothesis)
        }

        override fun onFinalResult(hypothesis: String) {
            Log.d(TAG, "onFinalResult: $hypothesis")
            checkForWakeWord(hypothesis)
            // SpeechService 耗尽后重新启动（不关闭 recognizer！）
            try {
                speechService?.shutdown()
                speechService = null
                restartSpeechService()
            } catch (e: Exception) {
                Log.e(TAG, "onFinalResult 恢复失败: ${e.message}")
            }
        }

        override fun onError(e: Exception) {
            Log.e(TAG, "SpeechService 错误: ${e.message}")
            if (isListening) {
                handler.postDelayed({
                    if (isListening) {
                        Log.w(TAG, "SpeechService 自动恢复")
                        try {
                            speechService?.shutdown()
                            speechService = null
                            restartSpeechService()
                        } catch (ex: Exception) {
                            Log.e(TAG, "自动恢复失败: ${ex.message}")
                        }
                    }
                }, 1000)
            }
        }

        override fun onTimeout() {
            Log.d(TAG, "onTimeout — 重置 SpeechService")
            speechService?.shutdown()
            speechService = null
            restartSpeechService()
        }
    }

    // ===== 生命周期 =====

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("语音唤醒 加载中...", "准备中..."))
        isForeground = true
        loadSettings()
        ensureModelAndStart()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            // 重启监听（关键词变更后）— 只重载关键词 + 重启 SpeechService，不碰 Vosk 实例
            ACTION_RESTART -> {
                if (isRestarting) {
                    Log.w(TAG, "ACTION_RESTART 已在处理中，忽略重复请求")
                    return START_STICKY
                }
                isRestarting = true
                handler.post {
                    stopListening()
                    loadSettings()
                    writeState(STATE_INITIALIZING)  // 🔑 通知 UI 按钮锁定
                    if (model != null && recognizer != null) {
                        updateNotification("语音唤醒 监听中", "关键词: ${wakeWords.joinToString(", ")}")
                        restartSpeechService()
                        writeState(STATE_LISTENING)
                        sendBroadcast(Intent(ACTION_READY))  // 🔑 通知 UI 按钮解锁
                    } else {
                        updateNotification("语音唤醒 初始化中", "正在重建识别器...")
                        try { initRecognizer() } catch (e: Exception) {
                            Log.e(TAG, "重建识别器失败: ${e.message}")
                            writeState(STATE_ERROR)
                            sendBroadcast(Intent(ACTION_FAILED).putExtra(EXTRA_ERROR, e.message))
                        }
                    }
                    isRestarting = false
                }
            }

            // 暂停监听（关闭语音唤醒开关）— 停止 SpeechService，保留 model/recognizer
            ACTION_STOP_LISTENING -> {
                handler.post {
                    stopListening()
                    writeState("stopped")
                    updateNotification("语音唤醒 已暂停", "可在设置中重新开启")
                }
            }

            // 恢复监听（开启语音唤醒开关）
            ACTION_START_LISTENING -> {
                handler.post {
                    if (model != null && recognizer != null) {
                        loadSettings()
                        startListening()
                    } else {
                        // 兜底：如果 model 不存在则重新初始化
                        ensureModelAndStart()
                    }
                }
            }

            ACTION_DOWNLOAD_CONFIRM -> {
                cancelPromptNotification()
                startModelDownload()
            }

            ACTION_DOWNLOAD_CANCEL -> {
                cancelPromptNotification()
                updateNotification("语音唤醒 等待WiFi", "请连接WiFi后重新开启语音唤醒，模型约${MODEL_SIZE_MB}MB")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // 🔑 此处 Vosk close 是进程退出时的清理，风险可控但依然加 try-catch
        isListening = false
        downloadThread = null
        try { speechService?.shutdown() } catch (_: Exception) {}
        speechService = null
        try { recognizer?.close() } catch (_: Exception) {}
        recognizer = null
        try { model?.close() } catch (_: Exception) {}
        model = null
        if (isForeground) {
            try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
            isForeground = false
        }
        super.onDestroy()
    }

    // ===== 设置 =====

    private fun writeState(state: String) {
        getSharedPreferences("wake_word", Context.MODE_PRIVATE).edit()
            .putString(PREF_STATE, state).apply()
        Log.i(TAG, "状态变更: $state")
    }

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

    // ===== 模型下载（仅首次） =====

    private fun getModelDir() = "${filesDir.absolutePath}/$MODEL_DIR"

    private fun modelExists(): Boolean {
        val dir = File(getModelDir())
        return dir.exists() && File(dir, "am").exists()
    }

    private fun ensureModelAndStart() {
        if (modelExists()) {
            Log.i(TAG, "模型已存在，直接初始化")
            writeState(STATE_INITIALIZING)
            initRecognizer()
            return
        }
        if (isOnWifi()) {
            Log.i(TAG, "WiFi 环境，直接开始下载 (~${MODEL_SIZE_MB}MB)")
            writeState(STATE_DOWNLOADING)
            startModelDownload()
        } else {
            Log.w(TAG, "非 WiFi 环境，等待用户确认下载")
            writeState(STATE_WAITING_WIFI)
            showDownloadPrompt()
        }
    }

    private fun isOnWifi(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun showDownloadPrompt() {
        val confirmIntent = Intent(this, WakeWordService::class.java).apply { action = ACTION_DOWNLOAD_CONFIRM }
        val cancelIntent = Intent(this, WakeWordService::class.java).apply { action = ACTION_DOWNLOAD_CANCEL }
        val confirmPi = PendingIntent.getService(this, 1, confirmIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancelPi = PendingIntent.getService(this, 2, cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("语音唤醒模型下载")
            .setContentText("当前非WiFi环境，语音模型约${MODEL_SIZE_MB}MB，是否继续下载？")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "当前非WiFi网络，语音唤醒需要下载约${MODEL_SIZE_MB}MB的离线语音模型。建议连接WiFi后重试以节省流量。"))
            .addAction(android.R.drawable.ic_media_play, "继续下载", confirmPi)
            .addAction(android.R.drawable.ic_media_pause, "稍后连WiFi", cancelPi)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID_PROMPT, notification)
    }

    private fun cancelPromptNotification() {
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID_PROMPT)
    }

    private fun startModelDownload() {
        writeState(STATE_DOWNLOADING)
        updateNotification("语音唤醒 下载中...", "首次使用需下载语音模型 (~${MODEL_SIZE_MB}MB)")
        downloadThread = Thread {
            try {
                downloadAndExtractModel()
                handler.post {
                    writeState(STATE_INITIALIZING)
                    updateNotification("语音唤醒 下载完成", "正在初始化...")
                    initRecognizer()
                }
            } catch (e: Exception) {
                Log.e(TAG, "模型下载失败: ${e.message}")
                handler.post {
                    writeState(STATE_ERROR)
                    updateNotification("语音唤醒 下载失败", e.message ?: "请检查网络后重启服务")
                    sendBroadcast(Intent(ACTION_FAILED).putExtra(EXTRA_ERROR, e.message ?: "下载失败"))
                }
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
                    handler.post { updateNotification("语音唤醒 下载中...", "$pct% (${MODEL_DIR})") }
                }
            }
        }
        output.close(); input.close(); conn.disconnect()
        Log.i(TAG, "下载完成: ${cacheZip.length()} bytes")

        if (cacheZip.length() < 1024 * 1024) {
            cacheZip.delete()
            throw IOException("模型文件异常（${cacheZip.length() / 1024}KB），可能下载中断，请重试")
        }

        handler.post { updateNotification("语音唤醒 解压中...", "正在解压语音模型") }
        writeState(STATE_EXTRACTING)

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

        if (!File(targetDir, "am/final.mdl").exists()) {
            throw IOException("模型解压不完整（缺少 am/final.mdl），请重启服务重试")
        }
        Log.i(TAG, "模型解压完成: ${targetDir.absolutePath}")
    }

    // ===== 识别器 ⚠ 只在首次创建，此后永不 close=====

    private fun initRecognizer() {
        try {
            writeState(STATE_INITIALIZING)
            model = Model(getModelDir())
            recognizer = Recognizer(model!!, SAMPLE_RATE.toFloat())
            Log.i(TAG, "Vosk 识别器初始化成功 — 模型: $MODEL_DIR")
            startListening()
            sendBroadcast(Intent(ACTION_READY))
        } catch (e: Exception) {
            Log.e(TAG, "识别器初始化失败: ${e.message}")
            writeState(STATE_ERROR)
            updateNotification("语音唤醒 初始化失败", e.message ?: "请重启服务")
            sendBroadcast(Intent(ACTION_FAILED).putExtra(EXTRA_ERROR, e.message))
        }
    }

    // ===== 监听控制 =====

    private fun startListening() {
        if (isListening || recognizer == null) return
        try {
            speechService = SpeechService(recognizer!!, SAMPLE_RATE.toFloat())
            speechService?.startListening(recognitionListener)
            isListening = true
            writeState(STATE_LISTENING)
            updateNotification("语音唤醒 监听中", "关键词: ${wakeWords.joinToString(", ")}")
            Log.i(TAG, "SpeechService 启动成功，等待唤醒词")
        } catch (e: Exception) {
            Log.e(TAG, "SpeechService 启动失败: ${e.message}")
            writeState(STATE_ERROR)
            updateNotification("语音唤醒 启动失败", "请确认已授权麦克风权限并重启服务")
            isListening = false
            speechService?.shutdown()
            speechService = null
        }
    }

    private fun restartSpeechService() {
        if (!isListening || recognizer == null) return
        speechService = SpeechService(recognizer!!, SAMPLE_RATE.toFloat())
        speechService?.startListening(recognitionListener)
    }

    private fun stopListening() {
        isListening = false
        try { speechService?.shutdown() } catch (_: Exception) {}
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
