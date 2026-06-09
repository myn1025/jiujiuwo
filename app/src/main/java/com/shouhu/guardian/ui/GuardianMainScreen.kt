package com.shouhu.guardian.ui

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
import com.shouhu.guardian.service.VolumeKeyService
import com.shouhu.guardian.util.AccessibilityUtils
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
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val resp = RetrofitClient.apiService.getAlertHistory(30)
            if (resp.isSuccessful) alerts = resp.body() ?: emptyList()
        } catch (_: Exception) {}
        loading = false
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
                    Row(modifier = Modifier.padding(16.dp)) {
                        // 状态指示灯
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = if (alert.status == "active") c.errorColor else c.successColor
                        ) {}
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
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
                    }
                }
            }
        }
    }
}

private fun formatTime(iso: String): String {
    return try {
        iso.replace("T", " ").substringBefore(".")
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
    var triggerVolume by remember { mutableStateOf(false) }
    var triggerVoice by remember { mutableStateOf(false) }
    var autoRecord by remember { mutableStateOf(true) }
    var autoGps by remember { mutableStateOf(true) }
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // 检测无障碍服务状态
        accessibilityEnabled = AccessibilityUtils.isAccessibilityEnabled(context, VolumeKeyService::class.java)
        try {
            val resp = RetrofitClient.apiService.getSettings()
            if (resp.isSuccessful) {
                resp.body()?.let { s ->
                    safePassword = s.safePassword
                    triggerVolume = s.triggerVolumeKey
                    triggerVoice = s.triggerVoice
                    autoRecord = s.autoRecord
                    autoGps = s.autoGps
                }
            }
        } catch (_: Exception) {}
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
                    // 无障碍状态提示
                    if (!accessibilityEnabled) {
                        Text(
                            "⚠️ 按键唤醒需要开启「无障碍服务」，请先开启后再使用",
                            color = Color(0xFFF59E0B),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    SwitchRow(c, "长按音量键", "锁屏时长按音量键 2 秒触发报警", triggerVolume) { wantOn ->
                        if (wantOn && !accessibilityEnabled) {
                            // 未开启无障碍 → 弹出引导对话框
                            showAccessibilityDialog = true
                        } else {
                            triggerVolume = wantOn
                            scope.launch {
                                try { RetrofitClient.apiService.updateSettings(SettingsUpdateRequest(triggerVolumeKey = wantOn)) } catch (_: Exception) {}
                            }
                        }
                    }
                    SwitchRow(c, "语音唤醒", "喊'紫守护救命'触发", triggerVoice) {
                        triggerVoice = it
                        scope.launch {
                            try { RetrofitClient.apiService.updateSettings(SettingsUpdateRequest(triggerVoice = it)) } catch (_: Exception) {}
                        }
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

    // ====== 无障碍引导对话框 ======
    if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            title = { Text("开启按键唤醒") },
            text = {
                Text(
                    "长按音量键触发报警需要启用「无障碍服务」。\n\n" +
                    "我们只会检测按键事件，\n" +
                    "不会读取屏幕内容，不会控制屏幕。\n\n" +
                    "请点击下方按钮前往设置页面，\n" +
                    "在「已安装的服务」中找到「紫守护」并开启。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAccessibilityDialog = false
                    AccessibilityUtils.openAccessibilitySettings(context)
                }) { Text("前往设置") }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDialog = false }) { Text("取消") }
            }
        )
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
