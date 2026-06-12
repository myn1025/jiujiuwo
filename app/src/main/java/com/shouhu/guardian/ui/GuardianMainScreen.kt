package com.shouhu.guardian.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.shouhu.guardian.data.api.RetrofitClient
import com.shouhu.guardian.data.model.*
import com.shouhu.guardian.service.EmergencyService
import com.shouhu.guardian.service.ShakeService
import com.shouhu.guardian.service.WakeWordService
import kotlinx.coroutines.launch

// 主题感知色板
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val primaryContainer: Color,
    val topBarBg: Color,
    val topBarTitle: Color,
    val navBarBg: Color,
    val selectedColor: Color,
    val unselectedColor: Color,
    val cardBg: Color,
    val white: Color,
    val divider: Color,
    val errorColor: Color,
    val successColor: Color,
)

private val DarkColors = AppColors(
    background = Color(0xFF0F0C12),
    surface = Color(0xFF1A1525),
    surfaceVariant = Color(0xFF2A1A3A),
    onBackground = Color(0xFF8878A0),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF8878A0),
    primary = Color(0xFF7C3AED),
    primaryContainer = Color(0xFF2A1A3A),
    topBarBg = Color(0xFF1A1525),
    topBarTitle = Color(0xFF7C3AED),
    navBarBg = Color(0xFF1A1525),
    selectedColor = Color(0xFF7C3AED),
    unselectedColor = Color(0xFF8878A0),
    cardBg = Color(0xFF1A1525),
    white = Color.White,
    divider = Color(0xFF2A1A3A),
    errorColor = Color(0xFFE53935),
    successColor = Color(0xFF4CAF50),
)

private val LightColors = AppColors(
    background = Color(0xFFF5F3FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFF3E8FF),
    onBackground = Color(0xFF666666),
    onSurface = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF666666),
    primary = Color(0xFF7C3AED),
    primaryContainer = Color(0xFFF3E8FF),
    topBarBg = Color.White,
    topBarTitle = Color(0xFF7C3AED),
    navBarBg = Color.White,
    selectedColor = Color(0xFF7C3AED),
    unselectedColor = Color(0xFF999999),
    cardBg = Color.White,
    white = Color.White,
    divider = Color(0xFFE8E0F0),
    errorColor = Color(0xFFE53935),
    successColor = Color(0xFF4CAF50),
)

/**
 * 救救我 - 主界面
 *
 * 三栏：联系人 | 报警记录 | 设置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianMainScreen(
    token: String,
    email: String,
    darkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    // 确保 RetrofitClient 持有 token（进程重启后 App.onCreate 已恢复，此处兜底）
    RetrofitClient.setToken(token)

    val c = if (darkTheme) DarkColors else LightColors
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("联系人" to Icons.Default.People, "报警记录" to Icons.Default.Warning, "设置" to Icons.Default.Settings)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("救救我", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = c.topBarBg,
                    titleContentColor = c.topBarTitle
                ),
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, "退出", tint = c.unselectedColor)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = c.navBarBg) {
                tabs.forEachIndexed { index, (title, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, title) },
                        label = { Text(title, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.selectedColor,
                            selectedTextColor = c.selectedColor,
                            indicatorColor = c.primaryContainer,
                            unselectedIconColor = c.unselectedColor,
                            unselectedTextColor = c.unselectedColor
                        )
                    )
                }
            }
        },
        containerColor = c.background
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> ContactsPanel(c)
                1 -> AlertsPanel(c)
                2 -> SettingsPanel(c, onLogout, darkTheme, onToggleTheme)
            }
        }
    }
}

// ====== 联系人面板 ======
@Composable
fun ContactsPanel(c: AppColors) {
    var contacts by remember { mutableStateOf<List<ContactResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    var priority by remember { mutableIntStateOf(1) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            try {
                val resp = RetrofitClient.apiService.getContacts()
                if (resp.isSuccessful) contacts = resp.body() ?: emptyList()
            } catch (_: Exception) {}
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    // 添加联系人弹窗
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("添加紧急联系人", fontWeight = FontWeight.Bold, color = c.onSurface) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("姓名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(c)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it },
                        label = { Text("手机号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(c)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = relation, onValueChange = { relation = it },
                        label = { Text("关系（选填）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(c)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("优先级", color = c.onSurfaceVariant, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 1..5) {
                            FilterChip(
                                selected = priority == i,
                                onClick = { priority = i },
                                label = { Text("$i") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = c.primary,
                                    selectedLabelColor = c.white
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                RetrofitClient.apiService.addContact(
                                    ContactRequest(name, phone, relation.ifBlank { null }, priority)
                                )
                                showDialog = false
                                name = ""; phone = ""; relation = ""; priority = 1
                                load()
                            } catch (_: Exception) {}
                        }
                    },
                    enabled = name.isNotBlank() && phone.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = c.primary)
                ) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消", color = c.onSurfaceVariant)
                }
            },
            containerColor = c.surface
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 添加按钮
        Button(
            onClick = { showDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, null, tint = c.white)
            Spacer(modifier = Modifier.width(8.dp))
            Text("添加紧急联系人", fontWeight = FontWeight.Bold)
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.primary)
            }
        } else if (contacts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有紧急联系人", color = c.onSurfaceVariant, fontSize = 15.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                items(contacts) { contact ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = c.cardBg),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Person, null,
                                tint = c.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(contact.name, color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(contact.phone, color = c.onSurfaceVariant, fontSize = 13.sp)
                            }
                            Surface(
                                color = c.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "#${contact.priority}",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    color = c.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ====== 报警记录面板 ======
@Composable
fun AlertsPanel(c: AppColors) {
    var alerts by remember { mutableStateOf<List<EmergencyResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var cancelAlertId by remember { mutableStateOf<Int?>(null) }
    var cancelPassword by remember { mutableStateOf("") }
    var cancelError by remember { mutableStateOf<String?>(null) }
    var deleteAlertId by remember { mutableStateOf<Int?>(null) }
    var actionLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val prefs = LocalContext.current.getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE)
    val safePassword by remember { mutableStateOf(prefs.getString("safe_password", "2580") ?: "2580") }

    // 加载报警记录
    fun loadAlerts() {
        scope.launch {
            loading = true
            try {
                val resp = RetrofitClient.apiService.getAlertHistory(30)
                if (resp.isSuccessful) alerts = resp.body() ?: emptyList()
            } catch (_: Exception) {}
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadAlerts() }

    // 取消报警确认对话框
    if (cancelAlertId != null) {
        AlertDialog(
            onDismissRequest = { cancelAlertId = null; cancelPassword = ""; cancelError = null },
            title = { Text("取消报警") },
            text = {
                Column {
                    Text("请输入安全密码以取消报警", fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = cancelPassword,
                        onValueChange = { cancelPassword = it; cancelError = null },
                        label = { Text("安全密码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (cancelError != null) {
                        Text(cancelError!!, color = Color(0xFFEF4444), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (cancelPassword.isBlank()) {
                            cancelError = "请输入安全密码"
                            return@TextButton
                        }
                        scope.launch {
                            actionLoading = true
                            try {
                                val resp = RetrofitClient.apiService.cancelAlert(
                                    cancelAlertId!!,
                                    password = cancelPassword
                                )
                                if (resp.isSuccessful) {
                                    cancelAlertId = null
                                    cancelPassword = ""
                                    cancelError = null
                                    loadAlerts()
                                } else {
                                    cancelError = "密码错误或取消失败"
                                }
                            } catch (e: Exception) {
                                cancelError = "网络错误: ${e.message}"
                            }
                            actionLoading = false
                        }
                    },
                    enabled = !actionLoading
                ) { Text("确认取消") }
            },
            dismissButton = {
                TextButton(onClick = { cancelAlertId = null; cancelPassword = ""; cancelError = null }) {
                    Text("返回")
                }
            }
        )
    }

    // 删除确认对话框（需密码）
    if (deleteAlertId != null) {
        var deletePwd by remember { mutableStateOf("") }
        var deletePwdError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { deleteAlertId = null; deletePwd = ""; deletePwdError = null },
            title = { Text("删除记录") },
            text = {
                Column {
                    Text("确定要删除这条报警记录吗？此操作不可撤销。")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = deletePwd,
                        onValueChange = {
                            deletePwd = it
                            deletePwdError = null
                        },
                        label = { Text("请输入安全密码") },
                        singleLine = true,
                        isError = deletePwdError != null,
                        supportingText = deletePwdError?.let { { Text(it) } },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (deletePwd.isEmpty()) {
                            deletePwdError = "请输入安全密码"
                            return@TextButton
                        }
                        if (deletePwd != safePassword) {
                            deletePwdError = "密码错误，请重试"
                            return@TextButton
                        }
                        deletePwdError = null
                        scope.launch {
                            actionLoading = true
                            try {
                                val resp = RetrofitClient.apiService.deleteAlert(deleteAlertId!!)
                                if (resp.isSuccessful) {
                                    deleteAlertId = null
                                    deletePwd = ""
                                    loadAlerts()
                                }
                            } catch (_: Exception) {}
                            actionLoading = false
                        }
                    },
                    enabled = !actionLoading && deletePwd.isNotEmpty()
                ) { Text("删除", color = Color(0xFFEF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteAlertId = null; deletePwd = ""; deletePwdError = false }) { Text("取消") }
            }
        )
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = c.primary)
        }
    } else if (alerts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无报警记录", color = c.onSurfaceVariant, fontSize = 15.sp)
        }
    } else {
        LazyColumn(modifier = Modifier.padding(16.dp)) {
            items(alerts) { alert ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = c.cardBg),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 状态指示灯
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = if (alert.status == "active") c.errorColor else c.successColor
                        ) {}
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                alert.address ?: "未知位置",
                                color = c.onSurface,
                                fontSize = 15.sp
                            )
                            Text(
                                formatTime(alert.triggeredAt),
                                color = c.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                if (alert.status == "active") "⚠ 报警中" else "✅ 已取消",
                                color = if (alert.status == "active") c.errorColor else c.successColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // 活跃报警：取消按钮
                        if (alert.status == "active") {
                            IconButton(onClick = { cancelAlertId = alert.id }) {
                                Icon(
                                    Icons.Default.Cancel,
                                    contentDescription = "取消报警",
                                    tint = c.errorColor
                                )
                            }
                        }
                        // 删除按钮
                        IconButton(onClick = { deleteAlertId = alert.id }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除记录",
                                tint = c.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(iso: String): String {
    return try {
        // 服务器存 UTC，转为本地时间
        val utcFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val date = utcFormat.parse(iso.substringBefore("."))
        val localFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        if (date != null) localFormat.format(date) else iso
    } catch (_: Exception) { iso }
}

// ====== 设置面板 ======
@Composable
fun SettingsPanel(
    c: AppColors,
    onLogout: () -> Unit,
    darkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit
) {
    val wakeWordPrefs = LocalContext.current.getSharedPreferences("wake_word", Context.MODE_PRIVATE)
    val guardianPrefs = LocalContext.current.getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE)
    val context = LocalContext.current
    var safePassword by remember { mutableStateOf("2580") }
    // 🔑 voice switch 以 SP 为准（服务状态不可靠，stopListening crash 后 SP 不会更新）
    var triggerVoice by remember { mutableStateOf(
        guardianPrefs.getBoolean("trigger_voice_enabled", false)
    )}
    var triggerShake by remember { mutableStateOf(guardianPrefs.getBoolean("trigger_shake_enabled", false)) }
    var autoRecord by remember { mutableStateOf(true) }
    var autoGps by remember { mutableStateOf(true) }
    var useBiometric by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    // 🔑 保存并重启按钮防抖
    var restartClickedTime by remember { mutableStateOf(0L) }
    var restartSuccessTime by remember { mutableStateOf(0L) }
    val restartCooling = (System.currentTimeMillis() - restartClickedTime) < 8000L
    val scope = rememberCoroutineScope()

    // 加载设置
    LaunchedEffect(Unit) {
        try {
            val resp = RetrofitClient.apiService.getSettings()
            if (resp.isSuccessful) {
                resp.body()?.let { s ->
                    safePassword = s.safePassword
                    triggerShake = s.triggerShake
                    autoRecord = s.autoRecord
                    autoGps = s.autoGps
                }
            }
        } catch (_: Exception) {}
        useBiometric = context.getSharedPreferences("auth", Context.MODE_PRIVATE).getBoolean("use_biometric", false)
        loaded = true
    }

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        // ========== 外观设置 ==========
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = c.cardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🎨 外观", color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    SwitchRow(c, "深色模式", "切换深色/浅色主题", darkTheme) {
                        onToggleTheme(it)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    SwitchRow(c, "指纹/面容登录", "下次打开APP用指纹或面容验证", useBiometric) {
                        useBiometric = it
                        context.getSharedPreferences("auth", Context.MODE_PRIVATE).edit()
                            .putBoolean("use_biometric", it).apply()
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            // 安全密码
            Card(
                colors = CardDefaults.cardColors(containerColor = c.cardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🔑 安全密码", color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("取消报警 / 停止伪装界面时使用", color = c.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    OutlinedTextField(
                        value = safePassword,
                        onValueChange = { if (it.length <= 4) safePassword = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 24.sp,
                            letterSpacing = 16.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        ),
                        colors = fieldColors(c)
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    RetrofitClient.apiService.updateSettings(
                                        SettingsUpdateRequest(safePassword = safePassword)
                                    )
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.primary)
                    ) { Text("保存") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 触发方式
            Card(
                colors = CardDefaults.cardColors(containerColor = c.cardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⚙️ 触发方式", color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "无需无障碍服务，关闭APP也能触发",
                        color = Color(0xFF10B981),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    SwitchRow(c, "🎤 语音唤醒", "喊关键词触发报警（需麦克风权限）", triggerVoice) { wantOn ->
                        if (wantOn) {
                            // 开启前检查：模型是否就绪
                            val state = wakeWordPrefs.getString(WakeWordService.PREF_STATE, "") ?: ""
                            val ready = state == WakeWordService.STATE_LISTENING
                            if (!ready) {
                                // 🔑 模型未就绪，禁止开启，震动反馈并弹 Toast
                                triggerVoice = false
                                android.widget.Toast.makeText(context, "请先在下方配置并保存唤醒关键词", android.widget.Toast.LENGTH_SHORT).show()
                                return@SwitchRow
                            }
                        }
                        triggerVoice = wantOn
                        guardianPrefs.edit().putBoolean("trigger_voice_enabled", wantOn).apply()
                        scope.launch {
                            try { RetrofitClient.apiService.updateSettings(SettingsUpdateRequest(triggerVoice = wantOn)) } catch (_: Exception) {}
                        }
                        // 🔑 用 ACTION_START/STOP_LISTENING 切换，不关 Vosk 实例
                        val intent = Intent(context, WakeWordService::class.java).apply {
                            action = if (wantOn) WakeWordService.ACTION_START_LISTENING else WakeWordService.ACTION_STOP_LISTENING
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    }
                    // 模型状态指示（模型未就绪时显示进度）
                    val modelState = wakeWordPrefs.getString(WakeWordService.PREF_STATE, "") ?: ""
                    val showModelStatus = modelState.isNotEmpty()
                            && modelState != WakeWordService.STATE_LISTENING
                            && modelState != "stopped"
                    if (showModelStatus) {
                        Text(
                            when (modelState) {
                                WakeWordService.STATE_DOWNLOADING -> "🔽 正在下载语音模型..."
                                WakeWordService.STATE_EXTRACTING -> "📦 正在解压语音模型..."
                                WakeWordService.STATE_INITIALIZING -> "⚙️ 正在初始化语音识别..."
                                WakeWordService.STATE_ERROR -> "❌ 语音唤醒服务异常"
                                WakeWordService.STATE_WAITING_WIFI -> "📶 等待WiFi连接以下载模型"
                                else -> "⏳ 语音模型未就绪"
                            },
                            color = if (modelState == WakeWordService.STATE_ERROR) c.errorColor else Color(0xFFF59E0B),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // 🔍 语音唤醒诊断信息
                    val lastWakeHit = remember { mutableStateOf(wakeWordPrefs.getLong("last_wake_hit", 0L)) }
                    val lastWakeWord = remember { mutableStateOf(wakeWordPrefs.getString("last_wake_word", "") ?: "") }
                    DisposableEffect(Unit) {
                        val diagReceiver = object : BroadcastReceiver() {
                            override fun onReceive(ctx: Context?, intent: Intent?) {
                                if (intent?.action == WakeWordService.ACTION_KEYWORD_HIT) {
                                    lastWakeHit.value = System.currentTimeMillis()
                                    lastWakeWord.value = intent.getStringExtra(WakeWordService.EXTRA_KEYWORD) ?: ""
                                }
                            }
                        }
                        val filter = IntentFilter(WakeWordService.ACTION_KEYWORD_HIT)
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                context.registerReceiver(diagReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                            } else {
                                context.registerReceiver(diagReceiver, filter)
                            }
                        } catch (_: Exception) {}
                        onDispose { try { context.unregisterReceiver(diagReceiver) } catch (_: Exception) {} }
                    }
                    if (modelState == WakeWordService.STATE_LISTENING || modelState == "stopped") {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                if (modelState == WakeWordService.STATE_LISTENING) "🎤 监听中" else "⏸ 已暂停",
                                color = if (modelState == WakeWordService.STATE_LISTENING) Color(0xFF10B981) else Color(0xFF6B7280),
                                fontSize = 11.sp
                            )
                            if (lastWakeHit.value > 0L) {
                                val ago = (System.currentTimeMillis() - lastWakeHit.value) / 1000
                                Text(
                                    "上次命中: ${if (ago < 60) "${ago}秒前" else "${ago/60}分钟前"} \"${lastWakeWord.value}\"",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp
                                )
                            } else {
                                Text("未命中过关键词", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                            }
                        }
                    }

                    SwitchRow(c, "📳 摇一摇求救", "剧烈摇晃手机${ShakeService.SHAKE_COUNT}次触发（防误触算法）", triggerShake) { wantOn ->
                        triggerShake = wantOn
                        context.getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE).edit().putBoolean("trigger_shake_enabled", wantOn).apply()
                        scope.launch {
                            try { RetrofitClient.apiService.updateSettings(SettingsUpdateRequest(triggerShake = wantOn)) } catch (_: Exception) {}
                        }
                        // 启动或停止摇一摇服务
                        val intent = Intent(context, ShakeService::class.java)
                        if (wantOn) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        } else {
                            context.stopService(intent)
                        }
                    }

                    // 🔍 摇一摇诊断 — 实时 gForce 读数
                    val lastShakeG = remember { mutableStateOf(0f) }
                    val lastShakeTime = remember { mutableStateOf(0L) }
                    DisposableEffect(Unit) {
                        val shakeDiagReceiver = object : BroadcastReceiver() {
                            override fun onReceive(ctx: Context?, intent: Intent?) {
                                if (intent?.action == ShakeService.ACTION_DIAG) {
                                    lastShakeG.value = intent.getFloatExtra(ShakeService.EXTRA_GFORCE, 0f)
                                    lastShakeTime.value = System.currentTimeMillis()
                                }
                            }
                        }
                        val filter = IntentFilter(ShakeService.ACTION_DIAG)
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                context.registerReceiver(shakeDiagReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                            } else {
                                context.registerReceiver(shakeDiagReceiver, filter)
                            }
                        } catch (_: Exception) {}
                        onDispose { try { context.unregisterReceiver(shakeDiagReceiver) } catch (_: Exception) {} }
                    }
                    if (triggerShake) {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("📳 传感器活跃", color = Color(0xFF10B981), fontSize = 11.sp)
                            if (lastShakeTime.value > 0L) {
                                val ago = (System.currentTimeMillis() - lastShakeTime.value) / 1000
                                Text(
                                    "上次g力: ${String.format("%.2f", lastShakeG.value)}g (${if (ago < 60) "${ago}秒前" else "${ago/60}分钟前"})",
                                    color = if (lastShakeG.value >= 1.3f) Color(0xFFEF4444) else Color(0xFFF59E0B),
                                    fontSize = 11.sp
                                )
                            } else {
                                Text("摇晃手机查看g力读数", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                            }
                        }
                    }

                    // 🧪 一键测试触发按钮
                    Spacer(Modifier.height(8.dp))
                    var testClicked by remember { mutableStateOf(false) }
                    val hasSmsPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                    // 测试前检查短信权限
                    var showSmsWarning by remember { mutableStateOf(!hasSmsPerm) }
                    if (showSmsWarning) {
                        Text(
                            "⚠️ 短信权限未授予，报警将无法发送短信通知。请到系统设置→权限→短信 中开启",
                            color = Color(0xFFF59E0B),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    }
                    Button(
                        onClick = {
                            testClicked = true
                            val testIntent = Intent(context, EmergencyService::class.java).apply {
                                action = EmergencyService.ACTION_TRIGGER
                                putExtra(EmergencyService.EXTRA_TRIGGER_SOURCE, "manual_test")
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(testIntent)
                            } else {
                                context.startService(testIntent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (testClicked) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            if (testClicked) "✅ 已发送测试报警，请检查通知栏" else "🧪 一键测试触发报警",
                            color = if (testClicked) Color(0xFFEF4444) else Color(0xFF10B981),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 唤醒关键词
            Card(
                colors = CardDefaults.cardColors(containerColor = c.cardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🔑 唤醒关键词", color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "说关键词触发报警，多个用逗号隔开。支持中文。完全免费离线识别。",
                        color = c.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    var keyword by remember {
                        mutableStateOf(context.getSharedPreferences("wake_word", Context.MODE_PRIVATE).getString("trigger_keyword", "救救我") ?: "救救我")
                    }
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text("触发关键词") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // 模型状态
                    // wakeWordPrefs 已在 SettingsPanel 顶部统一定义
                    // 读取服务持久状态，防止切页后状态丢失
                    val currentServiceState = remember { mutableStateOf(wakeWordPrefs.getString(WakeWordService.PREF_STATE, "") ?: "") }
                    // 🔑 isRestarting 设为派生值 — 每次 currentServiceState 变化自动重算
                    // （不再用 remember{mutableStateOf(...)}，那只会算一次）
                    val isRestarting = currentServiceState.value in listOf(
                        WakeWordService.STATE_DOWNLOADING, WakeWordService.STATE_EXTRACTING, WakeWordService.STATE_INITIALIZING
                    )
                    val restartError = remember { mutableStateOf(
                        if (currentServiceState.value == WakeWordService.STATE_ERROR) "语音唤醒服务异常" else null
                    ) }

                    // 监听 WakeWordService 完成广播
                    DisposableEffect(Unit) {
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(ctx: Context?, intent: Intent?) {
                                when (intent?.action) {
                                    WakeWordService.ACTION_READY -> {
                                        restartError.value = null
                                        restartSuccessTime = System.currentTimeMillis()
                                        restartClickedTime = 0L
                                        currentServiceState.value = WakeWordService.STATE_LISTENING
                                        // 不再自动改 triggerVoice，由用户手动打开
                                    }
                                    WakeWordService.ACTION_FAILED -> {
                                        restartError.value = intent.getStringExtra(WakeWordService.EXTRA_ERROR) ?: "初始化失败"
                                        restartClickedTime = 0L  // 🔑 解除防抖
                                        currentServiceState.value = WakeWordService.STATE_ERROR
                                    }
                                }
                            }
                        }
                        val filter = IntentFilter().apply {
                            addAction(WakeWordService.ACTION_READY)
                            addAction(WakeWordService.ACTION_FAILED)
                        }
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                            } else {
                                context.registerReceiver(receiver, filter)
                            }
                        } catch (_: Exception) {}
                        onDispose {
                            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                        }
                    }

                    Button(
                        onClick = {
                            if (isRestarting || restartCooling) return@Button
                            restartClickedTime = System.currentTimeMillis()
                            restartError.value = null
                            // 保存关键词到 SharedPreferences
                            context.getSharedPreferences("wake_word", Context.MODE_PRIVATE).edit()
                                .putString("trigger_keyword", keyword).apply()
                            // 发送 ACTION_RESTART 让服务重载关键词 + 重启 SpeechService
                            // （不关闭 Vosk 实例，避免 JNI 崩溃）
                            val intent = Intent(context, WakeWordService::class.java).apply {
                                action = WakeWordService.ACTION_RESTART
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        enabled = !isRestarting && !restartCooling
                    ) {
                        Text(
                            when {
                                restartCooling -> "⏳ 已请求重启，等待服务响应..."
                                isRestarting -> "⏳ 正在配置语音唤醒，请稍候..."
                                restartError.value != null -> "❌ 失败: ${restartError.value}"
                                restartSuccessTime > 0L && (System.currentTimeMillis() - restartSuccessTime) < 5000L -> "✅ 唤醒已重启成功"
                                else -> "保存并重启唤醒"
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 报警行为
            Card(
                colors = CardDefaults.cardColors(containerColor = c.cardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🎙️ 报警行为", color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    SwitchRow(c, "自动录音", "报警时录制环境声音", autoRecord) {
                        autoRecord = it
                        scope.launch {
                            try { RetrofitClient.apiService.updateSettings(SettingsUpdateRequest(autoRecord = it)) } catch (_: Exception) {}
                        }
                    }
                    SwitchRow(c, "发送位置", "报警时附带GPS位置", autoGps) {
                        autoGps = it
                        scope.launch {
                            try { RetrofitClient.apiService.updateSettings(SettingsUpdateRequest(autoGps = it)) } catch (_: Exception) {}
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 退出登录
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = c.errorColor),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.verticalGradient(listOf(c.errorColor, c.errorColor))
                )
            ) { Text("退出登录") }
        }
    }

}

@Composable
private fun SwitchRow(c: AppColors, title: String, desc: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = c.onSurface, fontSize = 14.sp)
            Text(desc, color = c.onSurfaceVariant, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = c.primary)
        )
    }
}

@Composable
private fun fieldColors(c: AppColors) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = c.onSurface,
    unfocusedTextColor = c.onSurface,
    focusedLabelColor = c.primary,
    unfocusedLabelColor = c.onSurfaceVariant,
    focusedBorderColor = c.primary,
    unfocusedBorderColor = c.divider
)
