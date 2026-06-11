package com.shouhu.guardian.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // 删除确认对话框
    if (deleteAlertId != null) {
        AlertDialog(
            onDismissRequest = { deleteAlertId = null },
            title = { Text("删除记录") },
            text = { Text("确定要删除这条报警记录吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            actionLoading = true
                            try {
                                val resp = RetrofitClient.apiService.deleteAlert(deleteAlertId!!)
                                if (resp.isSuccessful) {
                                    deleteAlertId = null
                                    loadAlerts()
                                }
                            } catch (_: Exception) {}
                            actionLoading = false
                        }
                    },
                    enabled = !actionLoading
                ) { Text("删除", color = Color(0xFFEF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteAlertId = null }) { Text("取消") }
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
    var safePassword by remember { mutableStateOf("2580") }
    var triggerVoice by remember { mutableStateOf(false) }
    var triggerShake by remember { mutableStateOf(false) }
    var autoRecord by remember { mutableStateOf(true) }
    var autoGps by remember { mutableStateOf(true) }
    var useBiometric by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 加载设置
    LaunchedEffect(Unit) {
        try {
            val resp = RetrofitClient.apiService.getSettings()
            if (resp.isSuccessful) {
                resp.body()?.let { s ->
                    safePassword = s.safePassword
                    triggerVoice = s.triggerVoice
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
                    SwitchRow(c, "🎤 语音唤醒", "喊'紫守护救命'触发报警（需麦克风权限）", triggerVoice) { wantOn ->
                        triggerVoice = wantOn
                        context.getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE).edit().putBoolean("trigger_voice_enabled", wantOn).apply()
                        scope.launch {
                            try { RetrofitClient.apiService.updateSettings(SettingsUpdateRequest(triggerVoice = wantOn)) } catch (_: Exception) {}
                        }
                        // 启动或停止语音唤醒服务
                        val intent = Intent(context, WakeWordService::class.java)
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
                    SwitchRow(c, "📳 摇一摇求救", "剧烈摇晃手机3次触发（防误触算法）", triggerShake) { wantOn ->
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
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Picovoice AccessKey
            Card(
                colors = CardDefaults.cardColors(containerColor = c.cardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🔑 Picovoice Key", color = c.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "语音唤醒需 Picovoice 离线引擎。免费注册获取: console.picovoice.ai",
                        color = c.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    var accessKey by remember {
                        mutableStateOf(context.getSharedPreferences("wake_word", Context.MODE_PRIVATE).getString("picovoice_access_key", "") ?: "")
                    }
                    OutlinedTextField(
                        value = accessKey,
                        onValueChange = { accessKey = it },
                        label = { Text("AccessKey") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            context.getSharedPreferences("wake_word", Context.MODE_PRIVATE).edit().putString("picovoice_access_key", accessKey).apply()
                            if (triggerVoice) {
                                context.stopService(Intent(context, WakeWordService::class.java))
                                Handler(Looper.getMainLooper()).postDelayed({
                                    val intent = Intent(context, WakeWordService::class.java)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                }, 500)
                            }
                        },
                        modifier = Modifier.padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) { Text("保存并重启唤醒") }
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
