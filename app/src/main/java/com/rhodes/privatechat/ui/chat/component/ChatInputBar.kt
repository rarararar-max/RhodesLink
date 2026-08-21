package com.rhodes.privatechat.ui.chat.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.ui.common.softTextFieldColors
import com.rhodes.privatechat.ui.common.TerminalPanelShape

/**
 * 私聊和群聊共用的输入栏。
 *
 * @param text 输入框文本（外部控制）
 * @param onTextChange 文本变化回调
 * @param onSend 发送回调（传入当前文本）
 * @param enabled 是否启用发送
 * @param currentMode 当前模式
 * @param onModeChange 模式切换回调
 * @param placeholder 输入框占位文本
 * @param indicatorBanner 顶部指示器（如催眠状态），null 则不显示
 * @param suggestions 灵感建议列表（为空时点击灵感按钮调用 onGenerateSuggestions）
 * @param onGenerateSuggestions 生成灵感建议回调
 * @param menuItems 菜单面板内容（切换模式、重启聊天、道具等）
 * @param showModePicker 外部控制的模式选择器状态（父组件可通过此触发模式选择对话框）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    enabled: Boolean = true,
    currentMode: String = "online",
    onModeChange: (String) -> Unit = {},
    placeholder: String = "消息...",
    indicatorBanner: @Composable (() -> Unit)? = null,
    suggestions: List<String> = emptyList(),
    onGenerateSuggestions: ((callback: (List<String>) -> Unit) -> Unit)? = null,
    menuItems: @Composable (() -> Unit)? = null,
    showModePicker: androidx.compose.runtime.MutableState<Boolean> = remember { mutableStateOf(false) },
    forceSendEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var showInspire by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var localSuggestions by remember(suggestions) { mutableStateOf(suggestions) }
    var tfValue by remember { mutableStateOf(TextFieldValue(text)) }
    LaunchedEffect(text) {
        if (text != tfValue.text) {
            tfValue = TextFieldValue(text, TextRange(text.length))
        }
    }

    val terminal = isRhodesTerminal
    Column(modifier = modifier.fillMaxWidth().background(ElevatedSurface.copy(alpha = 0.96f)).border(1.dp, Stroke)) {
        if (terminal) {
            Row(Modifier.fillMaxWidth().height(2.dp)) {
                Box(Modifier.weight(0.18f).fillMaxWidth().background(Primary))
                Box(Modifier.weight(0.82f).fillMaxWidth().background(Stroke))
            }
        }
        // 顶部指示器（如催眠状态）
        indicatorBanner?.invoke()

        // 灵感建议面板
        AnimatedVisibility(visible = showInspire, enter = slideInVertically(initialOffsetY = { it }), exit = slideOutVertically(targetOffsetY = { it })) {
            Column(modifier = Modifier.fillMaxWidth().background(ElevatedSurface).padding(12.dp)) {
                Text("灵感建议", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                if (localSuggestions.isEmpty() && onGenerateSuggestions != null) {
                    Text("加载中...", fontSize = 13.sp, color = TextTertiary)
                }
                localSuggestions.forEach { s ->
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SurfaceVariant)
                        .clickable { onTextChange(s); showInspire = false }.padding(12.dp)) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Primary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(s, fontSize = 13.sp, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        // 菜单面板
        if (menuItems != null) {
            AnimatedVisibility(visible = showMenu, enter = slideInVertically(initialOffsetY = { it }), exit = slideOutVertically(targetOffsetY = { it })) {
                Column(modifier = Modifier.fillMaxWidth().background(ElevatedSurface).padding(12.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MenuChip("( )", Primary) {
                            val pos = tfValue.selection.start
                            val newText = tfValue.text.substring(0, pos) + "()" + tfValue.text.substring(pos)
                            tfValue = TextFieldValue(newText, TextRange(pos + 1))
                            onTextChange(newText)
                        }
                        menuItems()
                    }
                }
            }
        }

        // 输入行
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onGenerateSuggestions != null) {
                IconButton(onClick = {
                    showInspire = !showInspire
                    if (showInspire) {
                        showMenu = false
                        if (localSuggestions.isEmpty()) {
                            onGenerateSuggestions { localSuggestions = it }
                        }
                    }
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.AutoAwesome, "灵感", tint = if (showInspire) Primary else TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
            OutlinedTextField(
                value = tfValue, onValueChange = { newValue ->
                    tfValue = newValue
                    onTextChange(newValue.text)
                }, modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder, fontSize = 14.sp, color = TextTertiary) },
                shape = if (terminal) TerminalPanelShape else RoundedCornerShape(20.dp), singleLine = false, enabled = true,
                minLines = 1, maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                keyboardActions = KeyboardActions(onAny = { /* 不做任何事，防止键盘收起 */ }),
                colors = softTextFieldColors()
            )
            Spacer(modifier = Modifier.width(4.dp))
            if (menuItems != null) {
                IconButton(onClick = {
                    showMenu = !showMenu
                    if (showMenu) showInspire = false
                }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Widgets, "菜单", tint = if (showMenu) Primary else TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
            IconButton(
                onClick = { onSend(tfValue.text) },
                enabled = enabled && (tfValue.text.isNotBlank() || forceSendEnabled),
                modifier = Modifier.size(terminal.let { if (it) 42.dp else 36.dp }).clip(if (terminal) TerminalPanelShape else CircleShape)
                    .background(if ((tfValue.text.isNotBlank() || forceSendEnabled) && enabled) Brush.linearGradient(listOf(Primary, Blue400)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, if (terminal) "发送通讯" else "发送",
                    tint = if ((tfValue.text.isNotBlank() || forceSendEnabled) && enabled) OnPrimary else TextSecondary,
                    modifier = Modifier.size(18.dp))
            }
        }
    }

    // 模式选择对话框
    if (showModePicker.value) {
        val modeNames = mapOf("online" to "线上", "offline" to "线下", "director" to "导演")
        AlertDialog(
            onDismissRequest = { showModePicker.value = false },
            title = { Text("切换模式", color = TextPrimary) },
            text = {
                Column {
                    modeNames.forEach { (k, v) ->
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (k == currentMode) PrimaryContainer else Color.Transparent)
                            .clickable { onModeChange(k); showModePicker.value = false }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(v, fontWeight = if (k == currentMode) FontWeight.Bold else FontWeight.Normal,
                                color = if (k == currentMode) Primary else TextPrimary)
                            if (k == currentMode) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text("← 当前", fontSize = 11.sp, color = Primary)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModePicker.value = false }) { Text("取消", color = TextSecondary) } }
        )
    }
}

/**
 * 菜单项 Chip 组件，供 ChatInputBar 的 menuItems 使用。
 */
@Composable
fun MenuChip(label: String, color: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f))
        .clickable { onClick() }.padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
    }
}
