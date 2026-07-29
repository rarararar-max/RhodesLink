package com.rhodes.privatechat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.shared.model.ChatArchive
import com.rhodes.privatechat.shared.model.ChatMessage
import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.ui.theme.BG
import com.rhodes.privatechat.ui.theme.Card
import com.rhodes.privatechat.ui.theme.Primary
import com.rhodes.privatechat.ui.theme.Surface
import com.rhodes.privatechat.ui.theme.TextPrimary
import com.rhodes.privatechat.ui.theme.TextSecondary
import com.rhodes.privatechat.ui.theme.TextTertiary
import com.rhodes.privatechat.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatArchiveScreen(viewModel: MainViewModel, operator: Operator, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var archives by remember { mutableStateOf(emptyList<ChatArchive>()) }
    var showCreate by remember { mutableStateOf(false) }
    var loadTarget by remember { mutableStateOf<ChatArchive?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatArchive?>(null) }
    var detailTarget by remember { mutableStateOf<ChatArchive?>(null) }
    var archiveActionRunning by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    fun refresh() { scope.launch { archives = viewModel.getCurrentChatArchives() } }
    LaunchedEffect(Unit) { refresh(); while (true) { delay(1500); refresh() } }
    val capacity = viewModel.archiveCapacity(operator.intimacy)
    Column(Modifier.fillMaxSize().background(BG)) {
        TopAppBar(
            title = { Text("剧情存档", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) } },
            actions = { IconButton(onClick = { showHelp = true }) { Icon(Icons.Default.HelpOutline, "存档说明", tint = TextSecondary) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
        )
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("已使用 ${archives.size} / $capacity 个", color = TextSecondary, fontSize = 13.sp)
                Text("好感度提升可解锁更多位置", color = TextTertiary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { showCreate = true }, enabled = archives.size < capacity, modifier = Modifier.fillMaxWidth()) { Text("保存当前进度") }
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (archives.isEmpty()) item { Text("完成一轮聊天后，可以在这里保存剧情进度。", color = TextTertiary, modifier = Modifier.padding(top = 28.dp)) }
                items(archives, key = { it.id }) { archive ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(14.dp)) {
                        Text(archive.title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("${archiveTime(archive.createdAt)} · ${modeName(archive.mode)}", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(5.dp))
                        val description = when (archive.status) {
                            ChatArchive.STATUS_PENDING -> "剧情整理中…"
                            ChatArchive.STATUS_FAILED -> "剧情整理失败，暂时无法读取。"
                            else -> archive.summary.take(70).ifBlank { "剧情简介已完成" }
                        }
                        Text(description, color = if (archive.status == ChatArchive.STATUS_FAILED) TextTertiary else TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (archive.status == ChatArchive.STATUS_READY) TextButton(onClick = { loadTarget = archive }, enabled = !archiveActionRunning) { Text("读取", color = Primary) }
                            else if (archive.status == ChatArchive.STATUS_FAILED) TextButton(onClick = { viewModel.retryArchiveSummary(archive.id) }, enabled = !archiveActionRunning) { Text("重新整理", color = Primary) }
                            else Text("整理完成后可读取", color = TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                            TextButton(onClick = { detailTarget = archive }) { Text("详情", color = TextSecondary) }
                            TextButton(onClick = { deleteTarget = archive }) { Text("删除", color = TextSecondary) }
                        }
                    }
                }
            }
        }
    }
    if (showCreate) ArchiveCreateDialog(onDismiss = { showCreate = false }) { title, note ->
        viewModel.createCurrentArchive(title, note) { showCreate = false; refresh() }
    }
    loadTarget?.let { archive ->
        AlertDialog(onDismissRequest = { if (!archiveActionRunning) loadTarget = null }, title = { Text("读取「${archive.title}」") }, text = { Text("将恢复为保存时的${modeName(archive.mode)}和最近5个完整回合。当前聊天后续会保留在聊天记录中，仅供回看，不会再影响当前剧情。") }, confirmButton = { TextButton(onClick = { archiveActionRunning = true; viewModel.loadArchive(archive.id) { success -> archiveActionRunning = false; if (success) onBack() else loadTarget = null } }, enabled = !archiveActionRunning) { Text(if (archiveActionRunning) "读取中…" else "确认读取", color = Primary) } }, dismissButton = { TextButton(onClick = { loadTarget = null }, enabled = !archiveActionRunning) { Text("取消") } })
    }
    deleteTarget?.let { archive ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("删除「${archive.title}」？") }, text = { Text("只会删除这个存档，不会影响当前聊天或其他存档。") }, confirmButton = { TextButton(onClick = { viewModel.deleteArchive(archive.id); deleteTarget = null; refresh() }) { Text("删除", color = TextSecondary) } }, dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } })
    }
    detailTarget?.let { archive ->
        val messages = remember(archive.id) {
            runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(ChatMessage.serializer()), archive.messagesJson) }.getOrDefault(emptyList())
        }
        AlertDialog(
            onDismissRequest = { detailTarget = null },
            title = { Text(archive.title) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("${archiveTime(archive.createdAt)} · ${modeName(archive.mode)}", color = TextSecondary, fontSize = 12.sp) }
                    if (archive.note.isNotBlank()) item { Text("续写备注\n${archive.note}", color = TextSecondary, fontSize = 13.sp) }
                    item { Text(if (archive.status == ChatArchive.STATUS_READY) archive.summary else if (archive.status == ChatArchive.STATUS_FAILED) "剧情整理失败，暂时无法读取。" else "剧情整理中…", color = TextPrimary, fontSize = 14.sp) }
                    item { Text("最近互动", color = TextSecondary, fontWeight = FontWeight.SemiBold) }
                    items(messages, key = { "${it.id}_${it.timestamp}" }) { message -> ArchiveMessagePreview(message) }
                }
            },
            confirmButton = { TextButton(onClick = { detailTarget = null }) { Text("关闭", color = Primary) } }
        )
    }
    if (showHelp) AlertDialog(onDismissRequest = { showHelp = false }, title = { Text("剧情存档位置") }, text = { Text("初始可保存3个存档，随着与角色的好感度提升逐步解锁，最多20个。\n\n0-199：3个\n200-399：5个\n400-599：8个\n600-799：12个\n800-999：16个\n1000：20个") }, confirmButton = { TextButton(onClick = { showHelp = false }) { Text("知道了") } })
}

@Composable
private fun ArchiveCreateDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("保存剧情存档") }, text = {
        Column {
            OutlinedTextField(title, { title = it.take(30) }, label = { Text("存档名称") }, placeholder = { Text("默认使用当前时间") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(note, { note = it.take(120) }, label = { Text("续写备注（可选）") }, placeholder = { Text("例如：保留约好看日出的设定") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
        }
    }, confirmButton = { TextButton(onClick = { onSave(title, note) }) { Text("保存", color = Primary) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

private fun archiveTime(value: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(value))
private fun modeName(mode: String): String = when (mode) { "offline" -> "线下"; "director" -> "导演"; else -> "线上" }

@Composable
private fun ArchiveMessagePreview(message: ChatMessage) {
    val speaker = if (message.isMe) "我" else message.senderName.ifBlank { "系统" }
    val text = if (message.type == "ai_json") Regex("\\\"(?:content|message|dialogue)\\\"\\s*:\\s*\\\"([^\\\"]{1,200})\\\"")
        .findAll(message.content).lastOrNull()?.groupValues?.getOrNull(1)?.replace("\\n", " ") ?: "[角色回复]" else message.content
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Card).padding(10.dp)) {
        Text(speaker, color = if (message.isMe) Primary else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(text, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
    }
}
