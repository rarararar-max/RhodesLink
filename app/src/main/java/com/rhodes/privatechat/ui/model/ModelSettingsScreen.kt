package com.rhodes.privatechat.ui.model

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.shared.network.providers
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.theme.*
import org.koin.compose.koinInject

@Composable
fun ModelSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings: SettingsRepository = koinInject()
    val ctx = LocalContext.current

    val providerIds = providers.keys.toList()
    val providerNames = providerIds.map { providers[it]!!.name }
    val savedProvider = settings.provider.takeIf { it in providers } ?: "deepseek"

    var selectedProvider by remember { mutableIntStateOf(providerIds.indexOf(savedProvider).coerceAtLeast(0)) }
    val currentProviderId = providerIds[selectedProvider]
    val currentConfig = providers[currentProviderId]!!

    val savedModel = settings.modelName
    var selectedModelIdx by remember {
        val savedIndex = currentConfig.models.indexOf(savedModel)
        mutableIntStateOf(if (savedIndex >= 0) savedIndex else currentConfig.models.size)
    }
    var customModelName by remember { mutableStateOf(savedModel) }
    var customUrl by remember { mutableStateOf(settings.customUrl) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val isCustom = currentProviderId == "custom"
    val modelOptions = if (isCustom) listOf("自填") else currentConfig.models + "自填"
    val currentModelName = if (isCustom || selectedModelIdx >= currentConfig.models.size) customModelName.trim() else currentConfig.models[selectedModelIdx].trim()
    val hasChanges = currentProviderId != settings.provider || currentModelName != settings.modelName || customUrl.trim() != settings.customUrl || apiKey.trim() != settings.apiKey

    fun validateSettings(): String? {
        val modelName = if (isCustom || selectedModelIdx >= currentConfig.models.size) {
            customModelName.trim()
        } else {
            currentConfig.models[selectedModelIdx].trim()
        }
        if (apiKey.trim().isBlank()) return "请填写 API 密钥"
        if (modelName.isBlank() || modelName == "自填") return "请填写模型名"
        if (isCustom) {
            val url = customUrl.trim()
            if (url.isBlank()) return "请填写 API 地址"
            if (!url.startsWith("http://") && !url.startsWith("https://")) return "API 地址需以 http:// 或 https:// 开头"
        }
        return null
    }

    val saveSettings: () -> Boolean = {
        val validationError = validateSettings()
        if (validationError != null) {
            errorText = validationError
            false
        } else {
            val modelName = currentModelName
            settings.provider = currentProviderId
            settings.modelName = modelName
            settings.customUrl = customUrl.trim()
            settings.apiKey = apiKey.trim()
            errorText = ""
            true
        }
    }

    fun requestBack() {
        if (hasChanges) showUnsavedDialog = true else onBack()
    }

    BackHandler(onBack = { requestBack() })

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { requestBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Spacer(Modifier.weight(1f))
            Text("模型设置", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { if (saveSettings()) onBack() }) {
                Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(20.dp))
                Text("保存", color = Primary, fontWeight = FontWeight.SemiBold)
            }
        }
        HorizontalDivider(color = Divider)

        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            // Vendor
            DropDown("厂商", providerNames, selectedProvider) { i -> selectedProvider = i; selectedModelIdx = 0; errorText = "" }
            Spacer(Modifier.height(12.dp))

            // Model
            if (!isCustom) DropDown("模型", modelOptions, selectedModelIdx.coerceIn(modelOptions.indices)) { selectedModelIdx = it; errorText = "" }
            Spacer(Modifier.height(12.dp))

            // Custom model name
            if (isCustom || selectedModelIdx >= currentConfig.models.size) {
                LabeledField("自定义模型名") { OutlinedTextField(value = customModelName, onValueChange = { customModelName = it; errorText = "" }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp), colors = fieldColors()) }
                Spacer(Modifier.height(12.dp))
            }

            // Custom URL
            if (isCustom) {
                LabeledField("API 地址") {
                    Row { OutlinedTextField(value = customUrl, onValueChange = { customUrl = it; errorText = "" }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp), colors = fieldColors()); Spacer(Modifier.width(4.dp)); PasteBtn(ctx) { customUrl = it; errorText = "" } }
                }
                Spacer(Modifier.height(12.dp))
            }

            // API Key
            LabeledField("API 密钥") {
                Row {
                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it; errorText = "" }, modifier = Modifier.weight(1f), singleLine = true, visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                    Spacer(Modifier.width(4.dp)); PasteBtn(ctx) { apiKey = it; errorText = "" }
                }
                TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "隐藏" else "显示", fontSize = 11.sp, color = Primary) }
            }
            if (errorText.isNotBlank()) {
                Text(errorText, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    }
    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("有未保存的修改", color = TextPrimary) },
            text = { Text("你已经修改了模型设置。要保存后离开，还是放弃这些修改？", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { if (saveSettings()) { showUnsavedDialog = false; onBack() } }) { Text("保存修改", color = Primary) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { showUnsavedDialog = false; onBack() }) { Text("放弃修改", color = ErrorRed) }
                    TextButton(onClick = { showUnsavedDialog = false }) { Text("继续编辑", color = TextSecondary) }
                }
            }
        )
    }
}

@Composable private fun DropDown(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Box {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Card).clickable { expanded = true }.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(options[selected], fontSize = 14.sp, color = TextPrimary); Text("▼", fontSize = 10.sp, color = TextTertiary) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Surface) {
                options.forEachIndexed { i, opt -> DropdownMenuItem(text = { Text(opt, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal, color = if (i == selected) Primary else TextPrimary) }, onClick = { onSelect(i); expanded = false }) }
            }
        }
    }
}

@Composable private fun LabeledField(label: String, content: @Composable () -> Unit) {
    Column { Text(label, fontSize = 13.sp, color = TextSecondary); Spacer(Modifier.height(4.dp)); content() }
}

@Composable private fun PasteBtn(ctx: Context, onPaste: (String) -> Unit) {
    OutlinedButton(onClick = { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip?.getItemAt(0)?.text?.toString()?.let { onPaste(it) } }, modifier = Modifier.height(48.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)) { Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(16.dp)) }
}

@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Divider)
