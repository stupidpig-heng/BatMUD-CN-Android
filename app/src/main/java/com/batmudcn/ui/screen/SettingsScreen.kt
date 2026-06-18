package com.batmudcn.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batmudcn.data.AppSettings
import com.batmudcn.ui.theme.*
import com.batmudcn.viewmodel.GameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: GameViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()

    var host by remember(settings) { mutableStateOf(settings.serverHost) }
    var port by remember(settings) { mutableStateOf(settings.serverPort.toString()) }
    var appId by remember(settings) { mutableStateOf(settings.appId) }
    var secretKey by remember(settings) { mutableStateOf(settings.secretKey) }
    var modelType by remember(settings) { mutableStateOf(settings.modelType) }
    var translationEnabled by remember(settings) { mutableStateOf(settings.translationEnabled) }
    var cacheSize by remember(settings) { mutableStateOf(settings.cacheSize.toString()) }
    var minChars by remember(settings) { mutableStateOf(settings.minChars.toString()) }

    var showSaved by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", color = TerminalTextBright) },
                navigationIcon = {
                    Text(
                        text = "← 返回",
                        color = TerminalAccent,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clickable { onBack() }
                            .padding(12.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TerminalBgLighter,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(TerminalBg)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Server Settings ----
            SettingsSection("服务器") {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("服务器地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text("端口") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(),
                )
                Button(
                    onClick = {
                        viewModel.updateServer(host, port.toIntOrNull() ?: 23)
                        showSaved = true
                    },
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = TerminalAccent),
                ) {
                    Text("保存服务器设置")
                }
            }

            // ---- Baidu API ----
            SettingsSection("百度翻译 API") {
                Text(
                    text = "从 https://fanyi-api.baidu.com/ 获取凭据，需开通「大模型文本翻译API」",
                    color = TerminalDimText,
                    fontSize = 12.sp,
                )
                OutlinedTextField(
                    value = appId,
                    onValueChange = { appId = it },
                    label = { Text("APP ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(),
                )
                OutlinedTextField(
                    value = secretKey,
                    onValueChange = { secretKey = it },
                    label = { Text("Secret Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(),
                )
                Button(
                    onClick = {
                        viewModel.updateBaiduCredentials(appId, secretKey)
                        showSaved = true
                    },
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = TerminalAccent),
                ) {
                    Text("保存 API 凭据")
                }
            }

            // ---- Translation Settings ----
            SettingsSection("翻译设置") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("启用翻译", color = TerminalText)
                    Switch(
                        checked = translationEnabled,
                        onCheckedChange = { translationEnabled = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = TerminalAccent),
                    )
                }

                // Model type selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("翻译模型", color = TerminalText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = modelType == "llm",
                            onClick = { modelType = "llm" },
                            label = { Text("LLM", fontSize = 12.sp) },
                        )
                        FilterChip(
                            selected = modelType == "nmt",
                            onClick = { modelType = "nmt" },
                            label = { Text("NMT", fontSize = 12.sp) },
                        )
                    }
                }

                OutlinedTextField(
                    value = cacheSize,
                    onValueChange = { cacheSize = it.filter { c -> c.isDigit() } },
                    label = { Text("缓存条数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(),
                )

                OutlinedTextField(
                    value = minChars,
                    onValueChange = { minChars = it.filter { c -> c.isDigit() } },
                    label = { Text("最少翻译字符数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors(),
                )

                Button(
                    onClick = {
                        viewModel.updateTranslationSettings(
                            enabled = translationEnabled,
                            modelType = modelType,
                            cacheSize = cacheSize.toIntOrNull() ?: 2000,
                            minChars = minChars.toIntOrNull() ?: 4,
                        )
                        showSaved = true
                    },
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = TerminalAccent),
                ) {
                    Text("保存翻译设置")
                }
            }

            // ---- Cache Stats ----
            SettingsSection("缓存统计") {
                Text(
                    text = viewModel.getCacheStats(),
                    color = TerminalDimText,
                    fontSize = 12.sp,
                )
            }

            // Show save confirmation
            if (showSaved) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2000)
                    showSaved = false
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = TerminalAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        HorizontalDivider(color = TerminalBorder, thickness = 1.dp)
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TerminalTextBright,
    unfocusedTextColor = TerminalText,
    focusedBorderColor = TerminalAccent,
    unfocusedBorderColor = TerminalBorder,
    focusedLabelColor = TerminalAccent,
    unfocusedLabelColor = TerminalDimText,
    cursorColor = TerminalAccent,
)
