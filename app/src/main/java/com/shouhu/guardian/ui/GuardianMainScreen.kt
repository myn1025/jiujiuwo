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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shouhu.guardian.data.api.RetrofitClient
import com.shouhu.guardian.data.model.*
import kotlinx.coroutines.launch

/**
 * 救救我 - 主界面
 *
 * 三栏：联系人 | 报警记录 | 设置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianMainScreen(token: String, email: String, onLogout: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("联系人" to Icons.Default.People, "报警记录" to Icons.Default.Warning, "设置" to Icons.Default.Settings)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("救救我", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1525),
                    titleContentColor = Color(0xFF7C3AED)
                ),
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, "退出", tint = Color(0xFF8878A0))
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1A1525)) {
                tabs.forEachIndexed { index, (title, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(icon, title) },
                        label = { Text(title, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF7C3AED),
                            selectedTextColor = Color(0xFF7C3AED),
                            indicatorColor = Color(0xFF2A1A3A),
                            unselectedIconColor = Color(0xFF8878A0),
                            unselectedTextColor = Color(0xFF8878A0)
                        )
                    )
                }
            }
        },
        containerColor = Color(0xFF0F0C12)
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> ContactsPanel()
                1 -> AlertsPanel()
                2 -> SettingsPanel(onLogout)
            }
        }
    }
}

// ====== 联系人面板 ======
@Composable
fun ContactsPanel() {
    var contacts by remember { mutableStateOf<List<ContactResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
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

    Column(modifier = Modifier.fillMaxSize()) {
        // 添加按钮
        Button(
            onClick = { /* TODO: 弹窗添加联系人 */ },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("添加紧急联系人", fontWeight = FontWeight.Bold)
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF7C3AED))
            }
        } else if (contacts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有紧急联系人", color = Color(0xFF8878A0), fontSize = 15.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
                items(contacts) { contact ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1525)),
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
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(contact.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(contact.phone, color = Color(0xFF8878A0), fontSize = 13.sp)
                            }
                            Surface(
                                color = Color(0xFFFFD54F).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "#${contact.priority}",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    color = Color(0xFFFFD54F),
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
fun AlertsPanel() {
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
            CircularProgressIndicator(color = Color(0xFF7C3AED))
        }
    } else if (alerts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无报警记录", color = Color(0xFF8878A0), fontSize = 15.sp)
        }
    } else {
        LazyColumn(modifier = Modifier.padding(16.dp)) {
            items(alerts) { alert ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1525)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        // 状态指示灯
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = if (alert.status == "active") Color(0xFFE53935) else Color(0xFF4CAF50)
                        ) {}
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                alert.address ?: "未知位置",
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Text(
                                formatTime(alert.triggeredAt),
                                color = Color(0xFF8878A0),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                if (alert.status == "active") "⚠ 报警中" else "✅ 已取消",
                                color = if (alert.status == "active") Color(0xFFE53935) else Color(0xFF4CAF50),
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
fun SettingsPanel(onLogout: () -> Unit) {
    var safePassword by remember { mutableStateOf("2580") }
    var triggerVolume by remember { mutableStateOf(true) }
    var triggerVoice by remember { mutableStateOf(true) }
    var autoRecord by remember { mutableStateOf(true) }
    var autoGps by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
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
        item {
            // 安全密码
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1525)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🔑 安全密码", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("取消报警 / 停止伪装界面时使用", color = Color(0xFF8878A0), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF7C3AED),
                            unfocusedBorderColor = Color(0xFF2A1A3A)
                        )
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                    ) { Text("保存") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 触发方式
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1525)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⚙️ 触发方式", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    SwitchRow("长按音量键", "锁屏时长按音量-触发", triggerVolume) { triggerVolume = it }
                    SwitchRow("语音唤醒", "喊'紫守护救命'触发", triggerVoice) { triggerVoice = it }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 报警行为
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1525)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🎙️ 报警行为", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    SwitchRow("自动录音", "报警时录制环境声音", autoRecord) { autoRecord = it }
                    SwitchRow("发送位置", "报警时附带GPS位置", autoGps) { autoGps = it }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 退出登录
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE53935)),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.verticalGradient(listOf(Color(0xFFE53935), Color(0xFFE53935)))
                )
            ) { Text("退出登录") }
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp)
            Text(desc, color = Color(0xFF8878A0), fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF7C3AED))
        )
    }
}
