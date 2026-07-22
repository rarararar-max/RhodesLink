package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.util.DebugLogger
import com.rhodes.privatechat.util.DebugLogger.LogEntry
import com.rhodes.privatechat.shared.settings.SettingsRepository
import org.koin.compose.koinInject
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(onBack: () -> Unit) {
    val settings: SettingsRepository = koinInject()
    var logs by remember { mutableStateOf(DebugLogger.getLogs()) }
    var selectedEntry by remember { mutableStateOf<LogEntry?>(null) }
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            logs = DebugLogger.getLogs()
            delay(1_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("调试日志", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("按时间顺序显示与复制", fontSize = 11.sp, color = TextTertiary)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) } },
                actions = {
                    Switch(checked = settings.debugLogEnabled, onCheckedChange = {
                        settings.debugLogEnabled = it
                        DebugLogger.enabled = it
                    })
                    IconButton(onClick = { clipboardManager.setText(AnnotatedString(DebugLogger.getLogText())) }) {
                        Icon(Icons.Default.ContentCopy, "复制全部日志", tint = TextPrimary)
                    }
                    IconButton(onClick = { DebugLogger.clear(); logs = emptyList() }) {
                        Icon(Icons.Default.DeleteSweep, "清空", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        containerColor = BG
    ) { padding ->
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无日志", fontSize = 14.sp, color = TextTertiary)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logs, key = { it.id }) { entry ->
                    val tagColor = when {
                        entry.tag.contains("ERROR") -> ErrorRed
                        entry.tag.contains("AI") -> AccentOrange
                        entry.tag.contains("DB") -> AccentGreen
                        entry.tag.contains("State") -> AccentBlue
                        entry.tag.startsWith("Chat/") -> Primary
                        entry.tag.startsWith("Group") -> AccentPurple
                        entry.tag.startsWith("Moment") -> AccentGreen
                        entry.tag.startsWith("Diary") -> AccentOrange
                        else -> TextSecondary
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Surface, RoundedCornerShape(4.dp))
                            .combinedClickable(
                                onClick = { selectedEntry = entry },
                                onLongClick = {
                                    clipboardManager.setText(AnnotatedString(formatEntry(entry)))
                                    Toast.makeText(context, "已复制这条完整日志", Toast.LENGTH_SHORT).show()
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.formattedTime, fontSize = 10.sp, color = TextTertiary, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.width(6.dp))
                            Text("[${entry.tag}]", fontSize = 10.sp, color = tagColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Text(logDescription(entry.tag), fontSize = 11.sp, color = TextSecondary)
                        Text(logPreview(entry.message), fontSize = 12.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                        Text("点击查看完整内容，长按复制本条", fontSize = 10.sp, color = TextTertiary)
                    }
                }
            }
        }
    }

    selectedEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { selectedEntry = null },
            title = { Text("${logDescription(entry.tag)}") },
            text = {
                SelectionContainer {
                    Text(
                        formatEntry(entry),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                        fontSize = 12.sp,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(formatEntry(entry)))
                    Toast.makeText(context, "已复制这条完整日志", Toast.LENGTH_SHORT).show()
                }) { Text("复制本条") }
            },
            dismissButton = { TextButton(onClick = { selectedEntry = null }) { Text("关闭") } }
        )
    }
}

private fun formatEntry(entry: LogEntry): String =
    "[${entry.formattedTime}][${entry.tag}]\n${logDescription(entry.tag)}\n\n${entry.message}"

private fun logPreview(message: String): String =
    if (message.length <= 800) message else "${message.take(800)}\n… 已省略列表预览，点击查看完整内容"

private fun logDescription(tag: String): String = when {
    tag == "AI/→GroupChat" -> "群聊模型请求：发送给模型的完整提示词、历史消息和本轮用户输入。"
    tag == "AI/←GroupChat" -> "群聊模型响应：本次请求的完整上下文，以及模型返回的原始内容。"
    tag == "AI/GroupContentRetry" -> "群聊内容重试：首次模型输出没有可用群聊内容时记录的原始输出。"
    tag == "AI/GroupFormatRepair" -> "群聊格式修复：模型输出格式不规范时的原文与修复结果。"
    tag == "GroupChat/InvalidResponse" -> "群聊回复无效：模型返回内容无法解析为当前群成员可展示的消息。"
    tag == "GroupChat/Error" -> "群聊请求失败：网络、API 配置、额度或服务端异常的具体原因。"
    tag == "GroupChat/Decision" -> "群聊处理决策：本轮解析、格式修复、重试和最终展示的结果。"
    tag == "GroupChat/Token" -> "群聊上下文长度：本轮发送给模型的 token 估算及截断情况。"
    tag.startsWith("Memory/") -> "记忆处理：本轮聊天使用、写入或清理记忆的情况。"
    tag.startsWith("GroupChat/DB") -> "群聊本地存储：消息已写入本机数据库。"
    tag.startsWith("GroupChat/Auto") -> "群聊自动发言：定时器、等待时间和自动回复任务状态。"
    tag.startsWith("GroupChat") -> "群聊处理：群成员、会话或回复流程状态。"
    tag.startsWith("AI/") -> "模型调用：发送给 AI 的请求或 AI 返回的原始结果。"
    else -> "应用调试信息：${tag}。"
}
