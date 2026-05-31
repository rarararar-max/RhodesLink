package com.example.rhodesterminal.ui.chat
import com.example.rhodesterminal.shared.model.AiMessage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import coil3.compose.AsyncImage
import com.example.rhodesterminal.network.Message
import com.example.rhodesterminal.shared.settings.SettingsRepository
import com.example.rhodesterminal.ui.theme.*
import com.example.rhodesterminal.viewmodel.MainViewModel
import org.koin.compose.koinInject
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    viewModel: MainViewModel, onBack: () -> Unit,
    onEditOperator: () -> Unit = {}, onViewStatus: () -> Unit = {},
    onExportChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val settings: SettingsRepository = koinInject()
    val operator by viewModel.selectedOperator.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val listState = rememberLazyListState()
    val op = operator ?: run { onBack(); return }
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()

    val focusManager = LocalFocusManager.current
    val lastMsgKey = messages.lastOrNull()?.let { "${it.id}_${it.content.length}_${it.content.take(50)}" } ?: ""
    var initialScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(messages.size, lastMsgKey) {
        if (messages.isNotEmpty()) {
            try {
                if (!initialScrollDone) {
                    listState.scrollToItem(messages.size - 1)
                    initialScrollDone = true
                } else {
                    listState.animateScrollToItem(messages.size - 1)
                    kotlinx.coroutines.delay(100)
                    listState.animateScrollToItem(messages.size - 1, 100000)
                }
            } catch (_: Exception) { }
        }
    }

    var bgUri by remember { mutableStateOf<String?>(settings.getString("bg_${op.id}", "")) }
    var cropTarget by remember { mutableStateOf<android.net.Uri?>(null) }
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> cropTarget = uri }
    var showBgReset by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding().background(BG).clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
    ) { focusManager.clearFocus() }) {
        bgUri?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }

        Column(modifier = modifier.fillMaxSize()) {
            // 顶部栏（不受键盘影响）
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().background(Surface).padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(op.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    val modeLabel = when (currentMode) { "online" -> "🟢"; "director" -> "🎬"; else -> "🏠" }
                    val modeHint = when (currentMode) { "online" -> "线上"; "director" -> "导演"; else -> "线下" }
                    Text(modeLabel, fontSize = 13.sp)
                    Text(modeHint, fontSize = 11.sp, color = Primary, fontWeight = FontWeight.Medium)
                    if (isLoading) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("输入中...", fontSize = 13.sp, color = Primary, fontStyle = FontStyle.Italic)
                    }
                }
                Row { Text("${op.location} | ${op.activity} | ${op.emotion}", fontSize = 11.sp, color = TextSecondary) }
            }
                Box {
                    IconButton(onClick = { showTopMenu = true }) { Icon(Icons.Default.MoreVert, null, tint = TextPrimary) }
                DropdownMenu(expanded = showTopMenu, onDismissRequest = { showTopMenu = false }) {
                    DropdownMenuItem(text = { Text("更换背景图") }, onClick = { bgPicker.launch("image/*"); showTopMenu = false })
                    if (bgUri != null) DropdownMenuItem(text = { Text("恢复默认背景") }, onClick = { showBgReset = true; showTopMenu = false })
                    DropdownMenuItem(text = { Text("编辑干员") }, onClick = { showTopMenu = false; onEditOperator() })
                    DropdownMenuItem(text = { Text("分享") }, onClick = { showExport = true; showTopMenu = false })
                }
                }
            }
            HorizontalDivider(color = Divider)

            // 消息区域 + 输入框（键盘弹出时自动收缩）
            Column(modifier = Modifier.weight(1f).imePadding()) {
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                items(messages.size) { i ->
                    val msg = messages[i]
                    val prevTime = if (i > 0) messages[i - 1].timestamp else 0L
                    ChatBubble(message = msg, aiAvatarUri = op.avatarUri, userAvatarUri = userProfile.avatarUri,
                        onRecall = { viewModel.recallMessage(it) },
                        onRegenerate = { viewModel.regenerateAiMessage(it) },
                        onContinue = { viewModel.continueAiMessage(it) },
                        showTime = shouldShowTimeSeparator(msg.timestamp, prevTime))
                }
                if (isLoading) {
                    // Typing indicator moved to top bar
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

                ChatInputBar(
                    text = inputText, onTextChange = { viewModel.updateInputText(it) },
                    onSend = { viewModel.sendMessage() }, enabled = true,
                    currentMode = currentMode, onModeChange = { viewModel.setMode(it) },
                    onViewStatus = onViewStatus, viewModel = viewModel
                )
            }
        }
    }

    if (showBgReset) {
        AlertDialog(onDismissRequest = { showBgReset = false }, title = { Text("恢复默认背景", color = TextPrimary) },
            text = { Text("将移除当前背景图", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { bgUri = null; settings.remove("bg_${op.id}"); showBgReset = false }) { Text("确认", color = Primary) } },
            dismissButton = { TextButton(onClick = { showBgReset = false }) { Text("取消", color = TextSecondary) } })
    }
    if (showExport) {
        AlertDialog(onDismissRequest = { showExport = false }, title = {}, text = {
            ChatExportDialog(operatorName = op.name, messages = messages, userProfile = viewModel.userProfile.value, operatorAvatarUri = op.avatarUri, onDismiss = { showExport = false })
        }, confirmButton = {})
    }
    cropTarget?.let { uri ->
        com.example.rhodesterminal.ui.common.ImageCropperDialog(
            imageUri = uri, aspectX = 9f, aspectY = 16f,
            onConfirm = { cropped -> val s = com.example.rhodesterminal.util.copyToInternalStorage(context, cropped); bgUri = s; settings.putString("bg_${op.id}", s); cropTarget = null },
            onCancel = { cropTarget = null }
        )
    }
}

@Composable
private fun ChatInputBar(
    text: String, onTextChange: (String) -> Unit, onSend: () -> Unit,
    enabled: Boolean, currentMode: String, onModeChange: (String) -> Unit,
    onViewStatus: () -> Unit, viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val modeNames = mapOf("online" to "线上", "offline" to "线下", "director" to "导演")
    var showInspire by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showModePicker by remember { mutableStateOf(false) }
    var showRestartConfirm by remember { mutableStateOf(false) }
    var showPropShop by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hypnosisRounds by viewModel.hypnosisRounds.collectAsState()

    Column(modifier = modifier.fillMaxWidth().background(Surface)) {
        // 催眠状态指示器（模式已移到顶部角色名旁）
        if (hypnosisRounds > 0) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                Text("🧠 催眠剩余${hypnosisRounds}轮", fontSize = 11.sp, color = Color(0xFFE65100).copy(alpha = 0.8f))
            }
        }
        AnimatedVisibility(visible = showInspire, enter = slideInVertically(initialOffsetY = { it }), exit = slideOutVertically(targetOffsetY = { it })) {
            val suggestions = remember { mutableStateListOf("加载中...") }
            LaunchedEffect(showInspire) { if (showInspire) viewModel.generateInspirations { suggestions.clear(); suggestions.addAll(it) } }
            Column(modifier = Modifier.fillMaxWidth().background(Card).padding(12.dp)) {
                Text("灵感建议", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                suggestions.forEach { s ->
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SurfaceVariant).clickable { onTextChange(s); showInspire = false; onSend() }.padding(12.dp)) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Primary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(8.dp)); Text(s, fontSize = 13.sp, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
        AnimatedVisibility(visible = showMenu, enter = slideInVertically(initialOffsetY = { it }), exit = slideOutVertically(targetOffsetY = { it })) {
            Column(modifier = Modifier.fillMaxWidth().background(Card).padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuChip("切换模式", Primary) { showModePicker = true; showMenu = false }
                    MenuChip("重启聊天", ErrorRed) { showRestartConfirm = true; showMenu = false }
                    MenuChip("查看状态", Primary) { onViewStatus(); showMenu = false }
                    MenuChip("使用道具", AccentOrange) { showPropShop = true; showMenu = false }
                }
            }
        }
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { showInspire = !showInspire; if (showInspire) showMenu = false }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.AutoAwesome, "灵感", tint = if (showInspire) Primary else TextSecondary, modifier = Modifier.size(20.dp))
            }
            OutlinedTextField(value = text, onValueChange = onTextChange, modifier = Modifier.weight(1f),
                placeholder = { Text(if (hypnosisRounds > 0) "催眠中 · 剩余${hypnosisRounds}轮" else "消息...", fontSize = 14.sp, color = if (hypnosisRounds > 0) ErrorRed else TextTertiary) },
                shape = RoundedCornerShape(20.dp), singleLine = false, enabled = true,
                minLines = 1, maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                keyboardActions = KeyboardActions(onAny = { /* 不做任何事，防止键盘收起 */ }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Divider, unfocusedBorderColor = Divider))
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = { showMenu = !showMenu; if (showMenu) showInspire = false }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Widgets, "菜单", tint = if (showMenu) Primary else TextSecondary, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onSend, enabled = enabled && text.isNotBlank(),
                modifier = Modifier.size(36.dp).clip(CircleShape).background(if (text.isNotBlank() && enabled) Primary else Color.Transparent)) {
                Icon(Icons.AutoMirrored.Filled.Send, "发送", tint = if (text.isNotBlank() && enabled) OnPrimary else TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }

    if (showModePicker) {
        AlertDialog(onDismissRequest = { showModePicker = false }, title = { Text("切换模式", color = TextPrimary) }, text = {
            Column {
                modeNames.forEach { (k, v) ->
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (k == currentMode) PrimaryContainer else Color.Transparent).clickable { onModeChange(k); showModePicker = false }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(v, fontWeight = if (k == currentMode) FontWeight.Bold else FontWeight.Normal, color = if (k == currentMode) Primary else TextPrimary)
                        if (k == currentMode) { Spacer(modifier = Modifier.weight(1f)); Text("← 当前", fontSize = 11.sp, color = Primary) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showModePicker = false }) { Text("取消", color = TextSecondary) } })
    }
    if (showRestartConfirm) {
        AlertDialog(onDismissRequest = { showRestartConfirm = false }, title = { Text("重启聊天", color = TextPrimary) },
            text = { Text("将清空本页聊天内容，确认重启吗？", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { viewModel.clearMessages(); showRestartConfirm = false }) { Text("确认", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { showRestartConfirm = false }) { Text("取消", color = TextSecondary) } })
    }

    if (showPropShop) {
        PropShopDialog(viewModel = viewModel, context = context, scope = scope, onDismiss = { showPropShop = false })
    }
}

@Composable
private fun MenuChip(label: String, color: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f)).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun PropShopDialog(
    viewModel: MainViewModel, context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope, onDismiss: () -> Unit
) {
    var hypnotizeInput by remember { mutableStateOf("") }
    var showHypnotizeInput by remember { mutableStateOf(false) }
    var innerThoughts by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val settings: SettingsRepository = koinInject()
    val balance = remember { mutableStateOf(settings.lmb) }

    AlertDialog(onDismissRequest = onDismiss, title = { Row { Icon(Icons.Default.ShoppingCart, null, tint = AccentOrange, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(6.dp)); Text("道具商店", color = TextPrimary) } },
        text = {
            Column {
                Text("余额：${balance.value} 龙门币", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AccentOrange)
                Spacer(modifier = Modifier.height(12.dp))
                // 催眠怀表
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Card).padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("催眠怀表", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("对干员施加催眠指令，持续10轮", fontSize = 12.sp, color = TextSecondary)
                        }
                        Text("100", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (showHypnotizeInput) {
                        OutlinedTextField(value = hypnotizeInput, onValueChange = { hypnotizeInput = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text("输入催眠指令...", color = TextTertiary) }, shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Divider))
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { showHypnotizeInput = false }) { Text("取消") }
                            TextButton(onClick = {
                                val err = viewModel.buyProp("催眠怀表", context)
                                if (err != null) { Toast.makeText(context, err, Toast.LENGTH_SHORT).show(); return@TextButton }
                                viewModel.setHypnosis(hypnotizeInput)
                                balance.value = settings.lmb
                                showHypnotizeInput = false; hypnotizeInput = ""
                                onDismiss()
                            }) { Text("确认", color = Primary) }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(PrimaryContainer).clickable {
                            if (balance.value < 100) { Toast.makeText(context, "余额不足", Toast.LENGTH_SHORT).show(); return@clickable }
                            showHypnotizeInput = true
                        }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                            Text("购买", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Primary)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // 看穿眼镜
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Card).padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("看穿眼镜", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("读取干员的内心独白", fontSize = 12.sp, color = TextSecondary)
                        }
                        Text("100", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    }
                    if (loading) {
                        Text("正在读取内心想法…", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
                    } else if (innerThoughts != null) {
                        Column(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                            Text(innerThoughts!!, fontSize = 14.sp, color = TextPrimary, lineHeight = 22.sp, fontStyle = FontStyle.Italic)
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(PrimaryContainer).clickable {
                            if (balance.value < 100) { Toast.makeText(context, "余额不足", Toast.LENGTH_SHORT).show(); return@clickable }
                            viewModel.buyProp("看穿眼镜", context)
                            balance.value = settings.lmb
                            loading = true
                            scope.launch {
                                val result = StringBuilder()
                                try {
                                    val temp = settings.aiTemperature
                                    val op = viewModel.selectedOperator.value
                                    val mood = op?.emotion ?: "平静"
                                    val loc = op?.location ?: "罗德岛"
                                    val stateDesc = op?.activity ?: "休息"
                                    val profile = viewModel.getUserProfile()
                                    val persona = op?.privatePrompt?.ifBlank { op?.description } ?: ""
                                    val recentChats = viewModel.messages.value.takeLast(6).joinToString("\n") { msg ->
                                        "${if (msg.isMe) profile.nickname else msg.senderName}：${msg.content.take(80)}"
                                    }
                                    val innerPrompt = """
【角色】
你是${op?.name ?: "干员"}。现在请表达你此刻最真实的内心独白——那些不会说出口、但正在脑海里翻涌的想法。这是你对自己说的话，不会有任何人看到。

【你扮演的角色信息】
名字：${op?.name ?: "干员"}
身份：${op?.title ?: ""}
人设：${persona}

【当前场景】
现在的时间是：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}
你所在的位置是：${loc}
你正在做的事情是：${stateDesc}
你此刻的情绪是：${mood}

【用户信息】
用户扮演的角色是：
姓名：${profile.nickname}
性别：${profile.gender.ifBlank { "未知" }}
设定：${profile.bio.ifBlank { "无" }}

【你们最近的聊天记录】
${recentChats.ifBlank { "暂无" }}

【独白要求】
- 100~200字，纯心理活动，不输出任何格式标记、JSON、括号动作
- 像日记一样自然，是你在心里对自己说话
- 结合你的人设、记忆、以及刚才的聊天记录
- 夸大情绪，有戏剧张力，但不能完全违背你的核心性格
- 冷静理智的角色内心可有波澜，但不会突然歇斯底里
- 写出矛盾和纠结：嘴上说没事，心里却在咆哮
- 写出不会说出口的真实感受：嫉妒、不安、偷偷开心、暗自期待
- 如果记忆信息为空，可以自由发挥，但必须贴合你的人设

直接输出纯文本，不加任何前缀或说明。
""".trimIndent()
                                    android.util.Log.d("AI调试输出", "╔══════════════════════════════════════════════")
                                    android.util.Log.d("AI调试输出", "║ [InnerThoughts]")
                                    android.util.Log.d("AI调试输出", "╠══ PROMPT ══════════════════════════════════")
                                    innerPrompt.lines().forEach { android.util.Log.d("AI调试输出", "║ $it") }
                                    viewModel.sharedUtils.streamChat(listOf(
                                        AiMessage("system", innerPrompt)
                                    ), "InnerThoughts").collect { result.append(it) }
                                    innerThoughts = result.toString()
                                    val thoughtText = innerThoughts
                                    // 设置读心效果
                                    if (!thoughtText.isNullOrBlank()) {
                                        viewModel.setMindRead(thoughtText)
                                    }
                                    android.util.Log.d("AI调试输出", "╠══ RESPONSE ════════════════════════════════")
                                    thoughtText?.lines()?.forEach { android.util.Log.d("AI调试输出", "║ $it") }
                                    android.util.Log.d("AI调试输出", "╚══════════════════════════════════════════════")
                                } catch (_: Exception) { innerThoughts = "读取失败，请重试" }
                                loading = false
                            }
                        }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                            Text("购买", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Primary)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭", color = TextSecondary) } })
}
