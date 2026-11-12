package com.rhodes.privatechat.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.theme.*

@Composable
fun PrivateRedPacketDialog(recipient: String, settings: SettingsRepository, onDismiss: () -> Unit, onSend: (Int, String) -> Unit) {
    val balance by settings.lmbFlow.collectAsState(initial = settings.lmb)
    var amount by rememberSaveable { mutableStateOf("") }
    var wish by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val value = amount.toIntOrNull()
    val valid = value != null && value in 1..20000 && value <= balance && !sending
    AlertDialog(onDismissRequest = { if (!sending) onDismiss() }, containerColor = ElevatedSurface,
        title = { Text("给${recipient}发红包", color = TextPrimary) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("余额：$balance 龙门币", color = AccentOrange)
            OutlinedTextField(value = amount, onValueChange = { next -> if (next.length <= 5 && next.all { it in '0'..'9' }) amount = next },
                label = { Text("红包金额") }, suffix = { Text("龙门币") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = amount.isNotBlank() && !valid, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(66, 188, 520).forEach { n -> TextButton(onClick = { amount = n.toString() }) { Text("$n", color = AccentOrange) } }
            }
            OutlinedTextField(value = wish, onValueChange = { wish = it.replace("\n", "").replace("\r", "").take(60) },
                label = { Text("附言（选填）") }, placeholder = { Text("一点心意，记得收下。") }, singleLine = true,
                supportingText = { Text("${wish.length}/60") }, modifier = Modifier.fillMaxWidth())
            Text("仅使用应用内龙门币。发送后自动领取；已领取红包不能撤回，回复失败可重试，不再扣红包金额。", color = TextSecondary)
        } },
        confirmButton = { TextButton(enabled = valid, onClick = { sending = true; onSend(value!!, wish); sending = false }) {
            Text("确认发送", color = if (valid) AccentOrange else TextTertiary)
        } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !sending) { Text("取消", color = TextSecondary) } })
}
