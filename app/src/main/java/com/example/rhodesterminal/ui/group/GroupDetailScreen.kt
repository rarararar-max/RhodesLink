package com.example.rhodesterminal.ui.group

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.rhodesterminal.data.db.entity.ChatMessageEntity
import com.example.rhodesterminal.ui.chat.ChatShareDialog
import com.example.rhodesterminal.ui.chat.ShareMessage
import com.example.rhodesterminal.ui.chat.formatChatTime
import com.example.rhodesterminal.viewmodel.MainViewModel
import com.example.rhodesterminal.ui.theme.*
import kotlinx.coroutines.delay

data class GMsg(val id: Long, val senderName: String, val senderColor: Color, val content: String, val ts: Long = System.currentTimeMillis(), val isSystem: Boolean = false, val isMe: Boolean = false, val avatarUri: String = "")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupDetailScreen(viewModel: MainViewModel, groupName: String, onBack: () -> Unit, onEditGroup: (String) -> Unit, groupId: String = "", modifier: Modifier = Modifier, onOperatorClick: (String) -> Unit = {}) {
    val listState = rememberLazyListState()
    var showMenu by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    var bgUri by remember { mutableStateOf<String?>(ctx.getSharedPreferences("chat_prefs", 0).getString("gbg_$groupId", null)) }
    var cropTarget by remember { mutableStateOf<android.net.Uri?>(null) }
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> cropTarget = uri }
    var showBgReset by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var currentMode by remember { mutableStateOf(ctx.getSharedPreferences("chat_prefs", 0).getString("group_mode_$groupId", "online") ?: "online") }

    val groupMessages by viewModel.groupMessages.collectAsState()
    val groupLoading by viewModel.groupLoading.collectAsState()
    val sessions by viewModel.allSessions.collectAsState()
    val groupSession = remember(groupId, sessions) { sessions.find { it.id == groupId } }
    var lastActivity by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(groupId) {
        if (groupId.isNotBlank()) viewModel.setCurrentGroup(groupId)
    }

    // Auto-speak timer
    LaunchedEffect(groupId, groupLoading) {
        if (groupId.isBlank()) return@LaunchedEffect
        val prefs = ctx.getSharedPreferences("chat_prefs", 0)
        if (!prefs.getBoolean("group_auto_$groupId", true)) return@LaunchedEffect
        val minMs = prefs.getInt("group_auto_min", 20) * 1000L
        val maxMs = prefs.getInt("group_auto_max", 60) * 1000L
        while (true) {
            val interval = minMs + (Math.random() * (maxMs - minMs)).toLong()
            delay(interval)
            if (groupLoading) continue
            val elapsed = System.currentTimeMillis() - lastActivity
            if (elapsed >= interval && groupId.isNotBlank() && !groupLoading) {
                viewModel.sendGroupMessage(groupId, groupName, "", currentMode, autoSpeak = true)
                lastActivity = System.currentTimeMillis()
            }
        }
    }

    val senderColor: (String) -> Color = { name ->
        when (name) {
            "阿米娅" -> Color(0xFF5B8DEF); "能天使" -> Color(0xFFFF7043)
            "德克萨斯" -> Color(0xFF607D8B); "夜莺" -> Color(0xFF81D4FA)
            "银灰" -> Color(0xFFFFD54F); "凯尔希" -> Color(0xFF4DB6AC)
            else -> Primary
        }
    }
    val allOperators by viewModel.operators.collectAsState()
    fun senderAvatar(name: String): String = allOperators.find { it.name == name || it.id == name }?.avatarUri ?: ""
    val rawMessages = remember(groupMessages, allOperators) {
        groupMessages.flatMap { msg ->
            val isOnline = msg.mode == "online"
            when {
                msg.type == "ai_json" -> {
                    try {
                        val arr = com.google.gson.JsonParser.parseString(msg.content).asJsonArray
                        arr.mapIndexedNotNull { idx, el ->
                            val obj = el.asJsonObject
                            val name = obj.get("speaker")?.asString ?: return@mapIndexedNotNull null
                            val content = obj.get("message")?.asString ?: return@mapIndexedNotNull null
                            val msgType = obj.get("type")?.asString ?: "dialogue"
                            if (content.isBlank()) return@mapIndexedNotNull null
                            if (isOnline && (msgType == "narration" || name == "旁白")) return@mapIndexedNotNull null
                            val gid = msg.id * 1000 + idx
                            if (msgType == "narration" || name == "旁白") {
                                GMsg(gid, "旁白", TextTertiary, content, msg.timestamp, isSystem = true)
                            } else {
                                GMsg(gid, name, senderColor(name), content, msg.timestamp, avatarUri = senderAvatar(name))
                            }
                        }
                    } catch (_: Exception) {
                        listOf(GMsg(msg.id, msg.senderName, Gray100, msg.content, msg.timestamp, avatarUri = senderAvatar(msg.senderName)))
                    }
                }
                msg.type == "system" || msg.senderName == "系统" || msg.senderName == "" ->
                    listOf(GMsg(msg.id, msg.senderName, Gray100, msg.content, msg.timestamp, isSystem = true))
                msg.type == "narration" ->
                    if (isOnline) emptyList()
                    else listOf(GMsg(msg.id, "旁白", TextTertiary, msg.content, msg.timestamp, isSystem = true))
                msg.isMe ->
                    listOf(GMsg(msg.id, "我", Primary, msg.content, msg.timestamp, isMe = true))
                else -> {
                    listOf(GMsg(msg.id, msg.senderName, senderColor(msg.senderName), msg.content, msg.timestamp, avatarUri = senderAvatar(msg.senderName)))
                }
            }
        }
    }
    // 渐进展示：首次全显，后续新消息逐条延迟 0.5-2.5s
    var prevRawSize by remember { mutableIntStateOf(0) }
    var displayCount by remember { mutableIntStateOf(Int.MAX_VALUE) }
    LaunchedEffect(rawMessages) {
        if (prevRawSize == 0 || rawMessages.size < prevRawSize) {
            displayCount = rawMessages.size
        } else if (rawMessages.size > prevRawSize) {
            displayCount = prevRawSize + 1
            for (i in (prevRawSize + 1) until rawMessages.size) {
                kotlinx.coroutines.delay((500L + (Math.random() * 2000)).toLong())
                displayCount = i + 1
            }
        }
        prevRawSize = rawMessages.size
    }
    val messages = rawMessages.take(displayCount)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            lastActivity = System.currentTimeMillis()
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(BG).clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
    ) { focusManager.clearFocus() }) {
        bgUri?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().background(Surface).padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
                val groupAvatar = groupSession?.avatarUri
                if (groupAvatar.isNullOrBlank()) {
                    Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) { Icon(Icons.Default.Groups, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                } else {
                    AsyncImage(model = groupAvatar, contentDescription = null, modifier = Modifier.size(34.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) { Text(groupName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary); Text("${if (currentMode == "online") "线上" else if (currentMode == "offline") "线下" else "导演"} · ${groupMessages.size}条消息", fontSize = 11.sp, color = TextSecondary) }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null, tint = TextPrimary) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("编辑群聊") }, onClick = { showMenu = false; onEditGroup(groupId) })
                        DropdownMenuItem(text = { Text("更换背景图") }, onClick = { bgPicker.launch("image/*"); showMenu = false })
                        if (bgUri != null) DropdownMenuItem(text = { Text("恢复默认背景") }, onClick = { showBgReset = true; showMenu = false })
                        DropdownMenuItem(text = { Text("分享") }, onClick = { showShare = true; showMenu = false })
                        DropdownMenuItem(text = { Text("清除聊天记录") }, onClick = { showClearConfirm = true; showMenu = false })
                    }
                }
            }
            HorizontalDivider(color = Divider)

            Column(modifier = Modifier.weight(1f).imePadding()) {
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                items(messages, key = { it.id }) { msg ->
                    if (msg.isSystem) {
                        if (msg.senderName == "旁白") {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
                                Spacer(modifier = Modifier.width(42.dp))
                                Box(modifier = Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)).background(Card).padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(msg.content, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = TextTertiary)
                                }
                            }
                        } else {
                            Text(msg.content, fontSize = 12.sp, color = TextTertiary, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 6.dp), textAlign = TextAlign.Center)
                        }
                    } else {
                        var showBubbleMenu by remember { mutableStateOf(false) }
                        val context = LocalContext.current
                        Box {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).combinedClickable(onLongClick = { showBubbleMenu = true }, onClick = {}), horizontalArrangement = if (msg.isMe) Arrangement.End else Arrangement.Start) {
                                if (!msg.isMe) {
                                    if (msg.avatarUri.isNotBlank()) {
                                        AsyncImage(model = msg.avatarUri, contentDescription = null, modifier = Modifier.size(34.dp).clip(CircleShape).clickable { onOperatorClick(msg.senderName) }, contentScale = ContentScale.Crop)
                                    } else {
                                        Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(msg.senderColor).clickable { onOperatorClick(msg.senderName) }, contentAlignment = Alignment.Center) { Text(msg.senderName.take(1), color = Color.White, fontWeight = FontWeight.Bold) }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Column(horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start) {
                                    if (!msg.isMe) Row(verticalAlignment = Alignment.CenterVertically) { Text(msg.senderName, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = msg.senderColor, modifier = Modifier.clickable { onOperatorClick(msg.senderName) }); Spacer(modifier = Modifier.width(6.dp)); Text(formatChatTime(msg.ts), fontSize = 10.sp, color = TextTertiary) }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(modifier = Modifier.widthIn(max = 240.dp).clip(if (msg.isMe) RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp) else RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)).background(if (msg.isMe) Color(0xFF95EC69) else Card).padding(horizontal = 12.dp, vertical = 8.dp)) { Text(msg.content, fontSize = 15.sp, color = if (msg.isMe) Color(0xFF1C1C1E) else TextPrimary) }
                                }
                                if (msg.isMe) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val userAvatar = viewModel.userProfile.value.avatarUri
                                    if (userAvatar.isNotBlank()) {
                                        AsyncImage(model = userAvatar, contentDescription = null, modifier = Modifier.size(34.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                    } else {
                                        Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(Gray500), contentAlignment = Alignment.Center) { Text("我".take(1), color = Color.White, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                            DropdownMenu(expanded = showBubbleMenu, onDismissRequest = { showBubbleMenu = false }) {
                                DropdownMenuItem(text = { Row { Icon(Icons.Default.ContentCopy, null, tint = TextPrimary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("复制", color = TextPrimary) } }, onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("msg", msg.content)); Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show(); showBubbleMenu = false })
                                DropdownMenuItem(text = { Row { Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("撤回", color = ErrorRed) } }, onClick = { viewModel.recallMessage(msg.id); showBubbleMenu = false })
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                GroupInputBar(
                    currentMode = currentMode,
                    onModeChange = { currentMode = it; ctx.getSharedPreferences("chat_prefs", 0).edit().putString("group_mode_$groupId", it).apply() },
                    onSend = { text ->
                        if (text.isNotBlank() && groupId.isNotBlank()) {
                            lastActivity = System.currentTimeMillis()
                            viewModel.sendGroupMessage(groupId, groupName, text, currentMode)
                        }
                    }
        )
    }
    if (showClearConfirm) AlertDialog(
        onDismissRequest = { showClearConfirm = false },
        title = { Text("清除聊天记录", color = TextPrimary) },
        text = { Text("将清除本群聊全部聊天记录，此操作不可撤销。", color = TextSecondary) },
        confirmButton = { TextButton(onClick = { viewModel.clearMessages(); showClearConfirm = false }) { Text("确认清除", color = ErrorRed) } },
        dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消", color = TextSecondary) } }
    )
        }
    }

    if (showBgReset) AlertDialog(onDismissRequest = { showBgReset = false }, title = { Text("恢复默认背景", color = TextPrimary) }, text = { Text("将移除当前背景图", color = TextSecondary) }, confirmButton = { TextButton(onClick = { bgUri = null; ctx.getSharedPreferences("chat_prefs", 0).edit().remove("gbg_$groupId").apply(); showBgReset = false }) { Text("确认", color = Primary) } }, dismissButton = { TextButton(onClick = { showBgReset = false }) { Text("取消", color = TextSecondary) } })

    if (showShare) {
        val profile by viewModel.userProfile.collectAsState()
        val shareMsgs = messages.map { msg ->
            ShareMessage(
                senderName = msg.senderName,
                content = msg.content,
                isMe = msg.isMe,
                isSystem = msg.isSystem,
                isNarration = msg.senderName == "旁白"
            )
        }
        ChatShareDialog(
            titleContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Groups, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(groupName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Color(0xFF5B8DEF), Color(0xFFFF7043), Color(0xFF607D8B), Color(0xFF81D4FA), Color(0xFFFFD54F)).forEach {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(it))
                    }
                }
            },
            messages = shareMsgs,
            userName = profile.nickname,
            userAvatarUri = profile.avatarUri,
            onDismiss = { showShare = false }
        )
    }
    cropTarget?.let { uri ->
        com.example.rhodesterminal.ui.common.ImageCropperDialog(
            imageUri = uri, aspectX = 9f, aspectY = 16f,
            onConfirm = { cropped -> val s = com.example.rhodesterminal.util.copyToInternalStorage(ctx, cropped); bgUri = s; ctx.getSharedPreferences("chat_prefs", 0).edit().putString("gbg_$groupId", s).apply(); cropTarget = null },
            onCancel = { cropTarget = null }
        )
    }
}

@Composable
private fun GroupInputBar(currentMode: String, onModeChange: (String) -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var showInspire by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showModePicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().background(Surface)) {
        AnimatedVisibility(visible = showInspire, enter = slideInVertically(initialOffsetY = { it }), exit = slideOutVertically(targetOffsetY = { it })) {
            Column(modifier = Modifier.fillMaxWidth().background(Card).padding(12.dp)) {
                Text("群聊灵感", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                listOf("大家早上好呀！", "有人看到博士去哪了吗？", "今天训练场有人吗？").forEach { s ->
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SurfaceVariant).clickable { text = s; showInspire = false; onSend(s) }.padding(12.dp)) { Icon(Icons.Default.AutoAwesome, null, tint = Primary, modifier = Modifier.size(14.dp)); Spacer(modifier = Modifier.width(8.dp)); Text(s, fontSize = 13.sp, color = TextPrimary) }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
        AnimatedVisibility(visible = showMenu, enter = slideInVertically(initialOffsetY = { it }), exit = slideOutVertically(targetOffsetY = { it })) {
            Column(modifier = Modifier.fillMaxWidth().background(Card).padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("切换模式", Primary) { showModePicker = true; showMenu = false }
                }
            }
        }
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { showInspire = !showInspire; if (showInspire) showMenu = false }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.AutoAwesome, "灵感", tint = if (showInspire) Primary else TextSecondary, modifier = Modifier.size(20.dp)) }
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f), placeholder = { Text("消息...", fontSize = 14.sp, color = TextTertiary) }, shape = RoundedCornerShape(20.dp), singleLine = false, minLines = 1, maxLines = 4, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default), keyboardActions = KeyboardActions(onAny = { /* 无操作，防止键盘收起 */ }), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Divider, unfocusedBorderColor = Divider))
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = { showMenu = !showMenu; if (showMenu) showInspire = false }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Widgets, "菜单", tint = if (showMenu) Primary else TextSecondary, modifier = Modifier.size(20.dp)) }
            IconButton(onClick = { onSend(text); text = "" }, modifier = Modifier.size(36.dp).clip(CircleShape).background(if (text.isNotBlank()) Primary else Color.Transparent)) { Icon(Icons.AutoMirrored.Filled.Send, "发送", tint = if (text.isNotBlank()) OnPrimary else TextSecondary, modifier = Modifier.size(18.dp)) }
        }
    }

    if (showModePicker) AlertDialog(
        onDismissRequest = { showModePicker = false },
        title = { Text("切换模式", color = TextPrimary) },
        text = {
            Column {
                listOf("线上" to "online", "线下" to "offline", "导演" to "director").forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (currentMode == value) Primary.copy(alpha = 0.15f) else Card)
                            .clickable { onModeChange(value); showModePicker = false }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentMode == value) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Primary))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(label, color = if (currentMode == value) Primary else TextPrimary, fontWeight = if (currentMode == value) FontWeight.Bold else FontWeight.Normal)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { showModePicker = false }) { Text("取消", color = TextSecondary) } }
    )
}

@Composable private fun Chip(label: String, color: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f)).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 8.dp)) { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color) }
}
