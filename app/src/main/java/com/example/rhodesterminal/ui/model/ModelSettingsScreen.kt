package com.example.rhodesterminal.ui.model

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.Info
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
import com.example.rhodesterminal.network.providers
import com.example.rhodesterminal.ui.theme.*

@Composable
fun ModelSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("model_prefs", 0) }
    val chatPrefs = remember { ctx.getSharedPreferences("chat_prefs", 0) }

    val providerIds = providers.keys.toList()
    val providerNames = providerIds.map { providers[it]!!.name }

    var selectedProvider by remember { mutableIntStateOf(providerIds.indexOf(prefs.getString("provider", "deepseek"))) }
    val currentProviderId = providerIds[selectedProvider]
    val currentConfig = providers[currentProviderId]!!

    val savedModel = prefs.getString("model_name", "") ?: ""
    var selectedModelIdx by remember { mutableIntStateOf(currentConfig.models.indexOf(savedModel).coerceAtLeast(0)) }
    var customModelName by remember { mutableStateOf(savedModel) }
    var customUrl by remember { mutableStateOf(prefs.getString("custom_url", "") ?: "") }
    var apiKey by remember { mutableStateOf(chatPrefs.getString("api_key", "") ?: "") }
    var showKey by remember { mutableStateOf(false) }

    val isCustom = currentProviderId == "custom"
    val modelOptions = if (isCustom) listOf("自填") else currentConfig.models + "自填"

    Column(modifier = modifier.fillMaxSize().background(BG)) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Spacer(Modifier.weight(1f)); Text("模型设置", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary); Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                prefs.edit().putString("provider", currentProviderId)
                    .putString("model_name", if (isCustom || selectedModelIdx >= currentConfig.models.size) customModelName else currentConfig.models[selectedModelIdx])
                    .putString("custom_url", customUrl).apply()
                chatPrefs.edit().putString("api_key", apiKey).apply()
                onBack()
            }) { Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(20.dp)); Text("保存", color = Primary, fontWeight = FontWeight.SemiBold) }
        }
        HorizontalDivider(color = Divider)

        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            // Vendor
            DropDown("厂商", providerNames, selectedProvider) { i -> selectedProvider = i; selectedModelIdx = 0 }
            Spacer(Modifier.height(12.dp))

            // Model
            if (!isCustom) DropDown("模型", modelOptions, selectedModelIdx) { selectedModelIdx = it }
            Spacer(Modifier.height(12.dp))

            // Custom model name
            if (isCustom || selectedModelIdx >= currentConfig.models.size) {
                LabeledField("自定义模型名") { OutlinedTextField(value = customModelName, onValueChange = { customModelName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp), colors = fieldColors()) }
                Spacer(Modifier.height(12.dp))
            }

            // Custom URL
            if (isCustom) {
                LabeledField("API 地址") {
                    Row { OutlinedTextField(value = customUrl, onValueChange = { customUrl = it }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp), colors = fieldColors()); Spacer(Modifier.width(4.dp)); PasteBtn(ctx) { customUrl = it } }
                }
                Spacer(Modifier.height(12.dp))
            }

            // API Key
            LabeledField("API 密钥") {
                Row {
                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.weight(1f), singleLine = true, visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
                    Spacer(Modifier.width(4.dp)); PasteBtn(ctx) { apiKey = it }
                }
                TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "隐藏" else "显示", fontSize = 11.sp, color = Primary) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable private fun DropDown(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Box {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Card).clickable { expanded = true }.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(options[selected], fontSize = 14.sp, color = TextPrimary); Text("▼", fontSize = 10.sp, color = TextTertiary) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
