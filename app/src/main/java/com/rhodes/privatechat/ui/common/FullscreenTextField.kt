package com.rhodes.privatechat.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rhodes.privatechat.ui.theme.Blue400
import com.rhodes.privatechat.ui.theme.Card
import com.rhodes.privatechat.ui.theme.Divider
import com.rhodes.privatechat.ui.theme.Primary
import com.rhodes.privatechat.ui.theme.TextPrimary
import com.rhodes.privatechat.ui.theme.TextSecondary
import com.rhodes.privatechat.ui.theme.TextTertiary

@Composable
fun FullscreenTextField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    minHeight: Dp = 120.dp
) {
    val context = LocalContext.current
    var showEditor by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().height(minHeight),
            placeholder = { Text(placeholder, fontSize = 13.sp, color = TextTertiary) },
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Blue400, unfocusedBorderColor = Divider)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallActionButton("粘贴", pasteIcon = true) {
                readClipboard(context)?.let { onValueChange(it) }
            }
            SmallActionButton("复制", pasteIcon = false) {
                writeClipboard(context, title, value)
            }
            SmallActionButton("全屏填写", pasteIcon = null) { showEditor = true }
        }
        Text("${value.length} 字", fontSize = 11.sp, color = TextTertiary, modifier = Modifier.padding(top = 4.dp))
    }

    if (showEditor) {
        var draft by remember(value) { mutableStateOf(value) }
        Dialog(onDismissRequest = { onValueChange(draft); showEditor = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).clip(RoundedCornerShape(16.dp))
                    .background(Card).imePadding().padding(16.dp)
            ) {
                Text("编辑$title", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("${draft.length} 字", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    placeholder = { Text(placeholder, color = TextTertiary) },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Blue400, unfocusedBorderColor = Divider)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SmallActionButton("复制", pasteIcon = false) { writeClipboard(context, title, draft) }
                    SmallActionButton("粘贴", pasteIcon = true) {
                        readClipboard(context)?.let { clip ->
                            draft = if (draft.isBlank()) clip else "$draft\n$clip"
                        }
                    }
                    SmallActionButton("清空", pasteIcon = null) { draft = "" }
                    Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onValueChange(draft); showEditor = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) { Text("保存", fontSize = 15.sp) }
                }
            }
        }
    }
}

@Composable
private fun SmallActionButton(text: String, pasteIcon: Boolean?, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.height(36.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue400)) {
        if (pasteIcon != null) {
            Icon(if (pasteIcon) Icons.Default.ContentPaste else Icons.Default.ContentCopy, null, modifier = Modifier.padding(end = 4.dp))
        }
        Text(text, fontSize = 13.sp)
    }
}

private fun readClipboard(context: Context): String? {
    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    return clip?.primaryClip?.getItemAt(0)?.text?.toString()
}

private fun writeClipboard(context: Context, label: String, text: String) {
    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clip?.setPrimaryClip(ClipData.newPlainText(label, text))
}
