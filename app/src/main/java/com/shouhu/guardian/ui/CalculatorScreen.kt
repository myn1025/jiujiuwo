package com.shouhu.guardian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * 伪装计算器界面
 *
 * 显示为普通计算器。输入安全密码 2580 解锁。
 * 连续输错 3 次 → 静默触发报警（调用 API）。
 */
@Composable
fun CalculatorScreen(darkTheme: Boolean = false, onUnlocked: () -> Unit) {
    var displayText by remember { mutableStateOf("") }
    var inputBuffer by remember { mutableStateOf("") }
    var errorCount by remember { mutableIntStateOf(0) }
    var emergencyTriggered by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val SAFE_PIN = "2580"
    val bgColor = if (darkTheme) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val displayColor = if (darkTheme) Color.White else Color(0xFF1A1A1A)
    val numKeyColor = if (darkTheme) Color(0xFF505050) else Color(0xFFD1D1D6)
    val numTextColor = if (darkTheme) Color.White else Color(0xFF1A1A1A)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // 显示屏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(bottom = 24.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = if (emergencyTriggered) "!!!" else displayText.ifEmpty { "0" },
                fontSize = 48.sp,
                color = if (emergencyTriggered) Color(0xFFE53935) else displayColor,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 键盘
        val keys = listOf(
            listOf("C", "÷", "×", "⌫"),
            listOf("7", "8", "9", "−"),
            listOf("4", "5", "6", "+"),
            listOf("1", "2", "3", "="),
            listOf("", "0", ".", "")
        )

        for (row in keys) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (label in row) {
                    if (label.isEmpty()) {
                        Spacer(modifier = Modifier.weight(1f))
                        continue
                    }
                    CalcButton(
                        label = label,
                        modifier = Modifier.weight(1f),
                        darkTheme = darkTheme,
                        onClick = {
                            when {
                                label == "=" -> {
                                    if (inputBuffer == SAFE_PIN) {
                                        onUnlocked()
                                    } else {
                                        errorCount++
                                        inputBuffer = ""
                                        displayText = "Error"
                                        if (errorCount >= 3 && !emergencyTriggered) {
                                            emergencyTriggered = true
                                            displayText = "!!!"
                                            scope.launch {
                                                triggerSilentEmergency()
                                            }
                                        }
                                    }
                                }
                                label == "C" -> {
                                    inputBuffer = ""
                                    displayText = ""
                                }
                                label == "⌫" -> {
                                    inputBuffer = inputBuffer.dropLast(1)
                                    displayText = inputBuffer
                                }
                                label in listOf("÷", "×", "−", "+", ".") -> {
                                    displayText = if (displayText.isNotEmpty()) displayText + label else displayText
                                }
                                label.toIntOrNull() != null -> {
                                    inputBuffer += label
                                    displayText = if (displayText == "0" || displayText == "Error") label
                                        else displayText + label
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.CalcButton(
    label: String,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = false,
    onClick: () -> Unit
) {
    val orangeColor = Color(0xFFFF9500)
    val redColor = Color(0xFFD32F2F)
    val numBg = if (darkTheme) Color(0xFF505050) else Color(0xFFD1D1D6)
    val numText = if (darkTheme) Color.White else Color(0xFF1A1A1A)
    val bgColor = when (label) {
        "C" -> redColor
        "=" -> orangeColor
        "÷", "×", "−", "+" -> orangeColor
        else -> numBg
    }
    val textColor = when (label) {
        "C", "=", "÷", "×", "−", "+" -> Color.White
        else -> numText
    }
    Box(
        modifier = modifier
            .padding(5.dp)
            .aspectRatio(if (label == "0") 2f else 1f)
            .clip(if (label == "0") RoundedCornerShape(40.dp) else CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

/**
 * 静默触发报警 — 无延迟，不打断用户操作
 */
private suspend fun triggerSilentEmergency() {
    try {
        val api = com.shouhu.guardian.data.api.RetrofitClient.apiService
        val body = com.shouhu.guardian.data.model.EmergencyRequest(
            latitude = 0.0,
            longitude = 0.0,
            address = "伪装密码错误-静默报警",
            deviceInfo = mapOf("type" to "silent_alarm")
        )
        api.triggerEmergency(body)
    } catch (_: Exception) {
        // 静默失败 — 不暴露报警行为
    }
}
