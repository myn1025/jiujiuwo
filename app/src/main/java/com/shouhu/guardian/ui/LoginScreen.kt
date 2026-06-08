package com.shouhu.guardian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shouhu.guardian.data.api.RetrofitClient
import com.shouhu.guardian.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var codeCountdown by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0F0C12), Color(0xFF1A1525), Color(0xFF0F0C12))
                )
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo
            Text(
                text = "救 救 我",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF7C3AED),
                textAlign = TextAlign.Center
            )
            Text(
                text = "守护每一次呼喊",
                fontSize = 13.sp,
                color = Color(0xFF8878A0),
                modifier = Modifier.padding(bottom = 40.dp)
            )

            // 邮箱
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim(); errorMsg = null },
                label = { Text("邮箱") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                colors = darkFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 密码
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMsg = null },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (isRegister) ImeAction.Next else ImeAction.Done
                ),
                colors = darkFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            if (isRegister) {
                // 昵称
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("昵称（用于登录）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = darkFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                // 手机号
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("手机号（选填）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    colors = darkFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                // 验证码
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = verificationCode,
                        onValueChange = { verificationCode = it; errorMsg = null },
                        label = { Text("验证码") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                        colors = darkFieldColors(),
                        modifier = Modifier.weight(1f),
                        enabled = codeSent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (email.isBlank()) {
                                errorMsg = "请先输入邮箱"
                                return@Button
                            }
                            scope.launch {
                                try {
                                    loading = true
                                    errorMsg = null
                                    val resp = RetrofitClient.apiService.sendVerificationCode(SendCodeRequest(email))
                                    if (resp.isSuccessful && resp.body()?.success == true) {
                                        codeSent = true
                                        codeCountdown = 60
                                        launch {
                                            while (codeCountdown > 0) {
                                                delay(1000)
                                                codeCountdown--
                                            }
                                            codeCountdown = 0
                                        }
                                    } else {
                                        errorMsg = resp.body()?.message ?: "发送验证码失败"
                                    }
                                } catch (e: Exception) {
                                    errorMsg = "网络错误: ${e.message}"
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (codeCountdown > 0) Color(0xFF3A2A5A) else Color(0xFF7C3AED)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        enabled = codeCountdown == 0 && !loading
                    ) {
                        Text(
                            if (codeCountdown > 0) "${codeCountdown}s" else "获取验证码",
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // 错误提示
            errorMsg?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = Color(0xFFE53935), fontSize = 13.sp)
            }

            // 按钮
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    if (email.isBlank()) {
                        errorMsg = "请输入邮箱"; return@Button
                    }
                    if (isRegister) {
                        if (password.length < 8) {
                            errorMsg = "密码至少8位"; return@Button
                        }
                        if (username.isBlank()) {
                            errorMsg = "请输入昵称"; return@Button
                        }
                        if (verificationCode.isBlank()) {
                            errorMsg = "请先获取并输入验证码"; return@Button
                        }
                    } else {
                        if (password.length < 1) {
                            errorMsg = "请输入密码"; return@Button
                        }
                    }
                    loading = true; errorMsg = null
                    scope.launch {
                        try {
                            if (isRegister) {
                                val resp = RetrofitClient.apiService.register(
                                    RegisterRequest(email, password, username, verificationCode, phone.ifEmpty { null })
                                )
                                if (resp.isSuccessful && resp.body() != null) {
                                    val data = resp.body()!!
                                    RetrofitClient.setToken(data.accessToken)
                                    onLoginSuccess(data.accessToken, data.email)
                                } else {
                                    val err = resp.errorBody()?.string() ?: resp.message()
                                    errorMsg = "注册失败: $err"
                                }
                            } else {
                                val resp = RetrofitClient.apiService.login(LoginRequest(email, password))
                                if (resp.isSuccessful && resp.body() != null) {
                                    val data = resp.body()!!
                                    RetrofitClient.setToken(data.accessToken)
                                    onLoginSuccess(data.accessToken, data.email)
                                } else {
                                    val err = resp.errorBody()?.string() ?: resp.message()
                                    errorMsg = if (resp.code() == 401) "邮箱或密码错误" else "登录失败: $err"
                                }
                            }
                        } catch (e: Exception) {
                            errorMsg = "网络错误: ${e.message}"
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                shape = RoundedCornerShape(12.dp),
                enabled = !loading
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Text(if (isRegister) "注 册" else "登 录", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 切换
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = {
                isRegister = !isRegister; errorMsg = null
            }) {
                Text(
                    if (isRegister) "已有账号？去登录" else "没有账号？立即注册",
                    color = Color(0xFF8E2DE2),
                    fontSize = 14.sp
                )
            }
        }
    }
}
