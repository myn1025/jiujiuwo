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
import com.shouhu.guardian.data.model.LoginRequest
import com.shouhu.guardian.data.model.RegisterRequest
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
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
            // Logo 区域
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

            // 输入框
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
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("昵称") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = darkFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("手机号（选填）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                    colors = darkFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
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
                    if (email.isBlank() || password.length < 6) {
                        errorMsg = "邮箱不能为空，密码至少6位"
                        return@Button
                    }
                    loading = true
                    errorMsg = null
                    scope.launch {
                        try {
                            if (isRegister) {
                                val resp = RetrofitClient.apiService.register(
                                    RegisterRequest(email, password, username.ifEmpty { null }, phone.ifEmpty { null })
                                )
                                if (resp.isSuccessful && resp.body() != null) {
                                    val data = resp.body()!!
                                    RetrofitClient.setToken(data.accessToken)
                                    onLoginSuccess(data.accessToken, data.email)
                                } else {
                                    errorMsg = "注册失败: ${resp.message()}"
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

            // 切换登录/注册
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = {
                isRegister = !isRegister
                errorMsg = null
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

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color(0xFFE0D8F0),
    cursorColor = Color(0xFF7C3AED),
    focusedBorderColor = Color(0xFF7C3AED),
    unfocusedBorderColor = Color(0xFF2A1A3A),
    focusedLabelColor = Color(0xFF7C3AED),
    unfocusedLabelColor = Color(0xFF8878A0)
)
