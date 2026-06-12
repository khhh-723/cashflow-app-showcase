package com.codex.suishouledger.ui.auth

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codex.suishouledger.data.remote.AuthRequest
import com.codex.suishouledger.data.remote.AuthTokenProvider
import com.codex.suishouledger.data.remote.RetrofitClient
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    tokenProvider: AuthTokenProvider,
    onAuthSuccess: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    val api = remember { RetrofitClient.apiService }
    val scope = rememberCoroutineScope()
    val rememberedEmail by tokenProvider.emailFlow.collectAsState(initial = null)

    var isLogin by remember { mutableStateOf(true) }
    var email by remember(rememberedEmail) { mutableStateOf(rememberedEmail.orEmpty()) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    if (onDismiss != null) {
        BackHandler(onBack = onDismiss)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (onDismiss != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = "先用本地版",
                                color = Color(0xFF2F8E4C),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(38.dp))
                BrandBlock()
                Spacer(modifier = Modifier.height(34.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = if (isLogin) "登录" else "创建账户",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    RoundedInputField(
                        value = email,
                        onValueChange = {
                            email = it
                            error = null
                        },
                        label = if (isLogin) "账号邮箱" else "邮箱",
                        placeholder = if (isLogin) "请输入登录邮箱" else "请输入注册邮箱",
                        keyboardType = KeyboardType.Email
                    )

                    if (!isLogin) {
                        RoundedInputField(
                            value = username,
                            onValueChange = {
                                username = it.take(50)
                                error = null
                            },
                            label = "用户名",
                            placeholder = "请输入用户名",
                            keyboardType = KeyboardType.Text
                        )
                    }

                    RoundedInputField(
                        value = password,
                        onValueChange = {
                            password = it
                            error = null
                        },
                        label = "密码",
                        placeholder = "请输入密码",
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )

                    if (error != null) {
                        Text(
                            text = error.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = {
                            val targetEmail = email.trim()
                            if (targetEmail.isBlank() || password.isBlank() || (!isLogin && username.isBlank())) {
                                error = if (isLogin) {
                                    "请先填写邮箱和密码"
                                } else {
                                    "请填写邮箱、用户名和密码"
                                }
                                return@Button
                            }
                            loading = true
                            error = null
                            scope.launch {
                                try {
                                    val response = if (isLogin) {
                                        api.login(AuthRequest(email = targetEmail, password = password))
                                    } else {
                                        api.register(AuthRequest(email = targetEmail, password = password, username = username.trim()))
                                    }
                                    if (response.isSuccessful) {
                                        val body = response.body()!!
                                        tokenProvider.saveAuth(body.token, body.userId, body.username, body.email)
                                        onAuthSuccess()
                                    } else {
                                        error = when (response.code()) {
                                            401 -> "邮箱或密码错误"
                                            409 -> "该邮箱已被注册"
                                            else -> "请求失败 (${response.code()})"
                                        }
                                    }
                                } catch (e: Exception) {
                                    error = "网络连接失败: ${e.localizedMessage ?: "未知错误"}"
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !loading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F8E4C))
                    ) {
                        Text(
                            text = if (loading) "登录中…" else if (isLogin) "登录" else "注册",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            isLogin = !isLogin
                            error = null
                            password = ""
                            username = ""
                            if (rememberedEmail != null) {
                                email = rememberedEmail.orEmpty()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (isLogin) "创建新账户" else "返回登录", fontWeight = FontWeight.SemiBold)
                    }

                    Text(
                        text = "数据仅存储在本地，不上传服务器。",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandBlock() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF2F8E4C), Color(0xFF1F7B3F))
                    ),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("C", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "欢迎使用 CashFlow",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "本地优先 · 隐私安全 · 智能记账",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RoundedInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            singleLine = true,
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            trailingIcon = trailingIcon,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

