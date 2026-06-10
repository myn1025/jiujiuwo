package com.shouhu.guardian.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.shouhu.guardian.data.api.RetrofitClient
import com.shouhu.guardian.data.model.*
import com.shouhu.guardian.util.BiometricAuthUtils
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    darkTheme: Boolean = true,
    savedToken: String? = null,
    onLoginSuccess: (String, String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var confirmPassword by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var codeCountdown by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    val bgColors = if (darkTheme)
        listOf(Color(0xFF0F0C12), Color(0xFF1A1525), Color(0xFF0F0C12))
    else
        listOf(Color(0xFFF5F0FF), Color(0xFFFFFFFF), Color(0xFFF5F0FF))

    val titleColor = if (darkTheme) Color(0xFF7C3AED) else Color(0xFF6200EE)
    val subtitleColor = if (darkTheme) Color(0xFF8878A0) else Color(0xFF666666)
    val fieldColors = if (darkTheme) darkFieldColors() else lightFieldColors()

    // Biometric available?
    val hasBiometric = savedToken != null && remember { BiometricAuthUtils.isBiometricAvailable(context) }
    var showBiometric by remember { mutableStateOf(hasBiometric) }

    // Auto-trigger biometric on first render
    LaunchedEffect(showBiometric) {
        if (showBiometric && context is FragmentActivity) {
            BiometricAuthUtils.authenticate(
                activity = context as FragmentActivity,
                onSuccess = {
                    showBiometric = false
                    RetrofitClient.setToken(savedToken!!)
                    onLoginSuccess(savedToken!!, "")
                },
                onError = { error ->
                    showBiometric = false
                    errorMsg = "验证失败: $error"
                },
                onFallback = { showBiometric = false }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(colors = bgColors))
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("救 救 我", fontSize = 32.sp, fontWeight = FontWeight.Black, color = titleColor, textAlign = TextAlign.Center)
            Text("守护每一次呼喊", fontSize = 13.sp, color = subtitleColor, modifier = Modifier.padding(bottom = 32.dp))

            // Biometric login button
            if (hasBiometric) {
                OutlinedButton(
                    onClick = {
                        showBiometric = true
                        if (context is FragmentActivity) {
                            BiometricAuthUtils.authenticate(
                                activity = context as FragmentActivity,
                                onSuccess = {
                                    RetrofitClient.setToken(savedToken!!)
                                    onLoginSuccess(savedToken!!, "")
                                },
                                onError = { error -> errorMsg = "验证失败: $error" },
                                onFallback = {}
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF7C3AED)),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = "指纹", modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("指纹/面容登录", fontSize = 15.sp)
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF2A1A3A).copy(alpha = 0.3f))
                Spacer(Modifier.height(16.dp))
            }

            // Error
            if (errorMsg != null) {
                Text(errorMsg!!, color = Color(0xFFEF4444), fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            }

            // Username
            OutlinedTextField(
                value = username, onValueChange = { username = it; errorMsg = null },
                label = { Text("用户名") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = fieldColors, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // Password
            OutlinedTextField(
                value = password, onValueChange = { password = it; errorMsg = null },
                label = { Text("密码") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (isRegister) ImeAction.Next else ImeAction.Done),
                colors = fieldColors, modifier = Modifier.fillMaxWidth()
            )

            if (isRegister) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPassword, onValueChange = { confirmPassword = it; errorMsg = null },
                    label = { Text("确认密码") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    colors = fieldColors, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email, onValueChange = { email = it.trim(); errorMsg = null },
                    label = { Text("邮箱") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    colors = fieldColors, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = verificationCode, onValueChange = { verificationCode = it.trim(); errorMsg = null },
                        label = { Text("验证码") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        colors = fieldColors, modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val resp = RetrofitClient.apiService.sendVerificationCode(email)
                                    if (resp.isSuccessful) {
                                        codeSent = true; codeCountdown = 60
                                        while (codeCountdown > 0) { delay(1000); codeCountdown-- }
                                    } else errorMsg = "获取验证码失败"
                                } catch (e: Exception) { errorMsg = "网络错误: ${e.message}" }
                            }
                        },
                        enabled = email.contains("@") && codeCountdown == 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (codeCountdown > 0) "${codeCountdown}s" else "获取验证码", fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Submit button
            Button(
                onClick = {
                    keyboard?.hide()
                    if (username.isBlank()) { errorMsg = "请输入用户名"; return@Button }
                    if (password.length < 8) { errorMsg = "密码至少8位"; return@Button }
                    if (isRegister) {
                        if (password != confirmPassword) { errorMsg = "两次密码不一致"; return@Button }
                        if (!email.contains("@")) { errorMsg = "请输入有效邮箱"; return@Button }
                        if (verificationCode.isBlank()) { errorMsg = "请输入验证码"; return@Button }
                    }
                    loading = true; errorMsg = null
                    scope.launch {
                        try {
                            if (isRegister) {
                                val resp = RetrofitClient.apiService.register(RegisterRequest(email, password, username, verificationCode))
                                if (resp.isSuccessful && resp.body() != null) {
                                    val data = resp.body()!!
                                    RetrofitClient.setToken(data.accessToken)
                                    val p = context.getSharedPreferences("auth", Context.MODE_PRIVATE).edit()
                                    p.putString("token", data.accessToken).apply()
                                    onLoginSuccess(data.accessToken, data.email)
                                } else errorMsg = "注册失败: ${resp.errorBody()?.string() ?: resp.message()}"
                            } else {
                                val resp = RetrofitClient.apiService.login(LoginRequest(username, password))
                                if (resp.isSuccessful && resp.body() != null) {
                                    val data = resp.body()!!
                                    RetrofitClient.setToken(data.accessToken)
                                    val p = context.getSharedPreferences("auth", Context.MODE_PRIVATE).edit()
                                    p.putString("token", data.accessToken).apply()
                                    onLoginSuccess(data.accessToken, data.email)
                                } else errorMsg = if (resp.code() == 401) "用户名或密码错误" else "登录失败: ${resp.errorBody()?.string() ?: resp.message()}"
                            }
                        } catch (e: Exception) { errorMsg = "网络错误: ${e.message}" }
                        finally { loading = false }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                shape = RoundedCornerShape(12.dp),
                enabled = !loading
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                else Text(if (isRegister) "注 册" else "登 录", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { isRegister = !isRegister; errorMsg = null }) {
                Text(if (isRegister) "已有账号？去登录" else "没有账号？立即注册", color = Color(0xFF8E2DE2), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color(0xFFE0D8F0),
    cursorColor = Color(0xFF7C3AED), focusedBorderColor = Color(0xFF7C3AED),
    unfocusedBorderColor = Color(0xFF2A1A3A), focusedLabelColor = Color(0xFF7C3AED),
    unfocusedLabelColor = Color(0xFF8878A0)
)

@Composable
private fun lightFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFF1A1A1A), unfocusedTextColor = Color(0xFF333333),
    cursorColor = Color(0xFF6200EE), focusedBorderColor = Color(0xFF6200EE),
    unfocusedBorderColor = Color(0xFFCCCCCC), focusedLabelColor = Color(0xFF6200EE),
    unfocusedLabelColor = Color(0xFF888888)
)