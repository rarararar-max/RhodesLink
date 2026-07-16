package com.rhodes.privatechat.ui.chat
import com.rhodes.privatechat.shared.model.AiMessage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.chat.component.ChatDropdownMenuItem
import com.rhodes.privatechat.ui.chat.component.ChatHeader
import com.rhodes.privatechat.ui.chat.component.ChatInputBar
import com.rhodes.privatechat.ui.chat.component.MenuChip
import com.rhodes.privatechat.ui.chat.component.MessageList
import com.rhodes.privatechat.ui.chat.model.ChatUiMessage
import com.rhodes.privatechat.ui.chat.util.MessageParser
import com.rhodes.privatechat.ui.common.ThemedAlertDialog
import com.rhodes.privatechat.ui.common.softTextFieldColors
import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.util.ChatTrace
import com.rhodes.privatechat.MainActivity
import com.rhodes.privatechat.audio.LocalAudioController
import com.rhodes.privatechat.shared.voice.AsrRequest
import com.rhodes.privatechat.shared.voice.hasAsrConfiguration
import com.rhodes.privatechat.shared.voice.voiceCallSetupMessage
import com.rhodes.privatechat.viewmodel.MainViewModel
import org.koin.compose.koinInject
import kotlinx.coroutines.launch

private const val PROP_PRICE = 100

private fun String.compactHeaderPart(maxChars: Int): String {
    val clean = trim().replace(Regex("\\s+"), " ")
    return if (clean.length <= maxChars) clean else clean.take(maxChars) + "..."
}

@Composable
fun ChatScreen(
    viewModel: MainViewModel, onBack: () -> Unit,
    operator: Operator,
    onEditOperator: () -> Unit = {}, onViewStatus: () -> Unit = {},
    onViewHistory: () -> Unit = {}, onVoiceCall: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val settings: SettingsRepository = koinInject()
    val rawMessages by viewModel.messages.collectAsState()
    val isLoadingOlder by viewModel.isLoadingOlderMessages.collectAsState()
    val hasMoreMessages by viewModel.hasMoreMessages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val sessionRestartAt by viewModel.sessionRestartAt.collectAsState()
    val scrollToMessageId by viewModel.scrollToMessageId.collectAsState()
    val currentOp by viewModel.selectedOperator.collectAsState()
    val displayOp = currentOp ?: operator
    val listState = rememberLazyListState()
    val op = operator
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    val visionReady = settings.visionBaseUrl.isNotBlank() &&
        settings.visionModelName.isNotBlank() &&
        settings.visionApiKey.ifBlank { settings.apiKey }.isNotBlank()

    // 使用 MessageParser 将原始消息转换为统一 UI 模型
    val messages = remember(rawMessages, op, userProfile, sessionRestartAt) {
        try {
            val parsed = MessageParser.parse(
                messages = rawMessages,
                isGroup = false,
                aiName = op.name,
                aiAvatarUri = op.avatarUri,
                userAvatarUri = userProfile.avatarUri,
                restartAt = sessionRestartAt
            )
            val safeParsed = if (parsed.isEmpty() && rawMessages.isNotEmpty()) {
                rawMessages.map { raw ->
                    com.rhodes.privatechat.ui.chat.model.ChatUiMessage(
                        id = raw.id,
                        senderName = if (raw.isMe) "我" else op.name,
                        senderColor = Primary,
                        content = raw.content.take(1000),
                        timestamp = raw.timestamp,
                        isMe = raw.isMe,
                        avatarUri = if (raw.isMe) userProfile.avatarUri else op.avatarUri,
                        mode = raw.mode,
                        originalMessageId = raw.id
                    )
                }
            } else parsed
            ChatTrace.d("ChatScreen", "parsed op=${op.id} raw=${rawMessages.size} parsed=${safeParsed.size}")
            safeParsed
        } catch (e: Exception) {
            ChatTrace.e("ChatScreen", "parse.ERROR op=${op.id} raw=${rawMessages.size} err=${e.message}", e)
            rawMessages.map { raw ->
                com.rhodes.privatechat.ui.chat.model.ChatUiMessage(
                    id = raw.id,
                    senderName = if (raw.isMe) "我" else op.name,
                    senderColor = Primary,
                    content = raw.content.take(1000),
                    timestamp = raw.timestamp,
                    isMe = raw.isMe,
                    avatarUri = if (raw.isMe) userProfile.avatarUri else op.avatarUri,
                    mode = raw.mode,
                    originalMessageId = raw.id
                )
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(rawMessages.size, messages.size, isLoading) {
        ChatTrace.d("ChatScreen", "op=${op.id} raw=${rawMessages.size} parsed=${messages.size} loading=$isLoading")
    }

    androidx.compose.runtime.LaunchedEffect(scrollToMessageId, messages.size) {
        val target = scrollToMessageId ?: return@LaunchedEffect
        val index = messages.indexOfFirst { it.originalMessageId == target }
        if (index >= 0) {
            listState.scrollToItem(index)
            viewModel.consumeChatScrollTarget()
        }
    }

    var bgUri by remember { mutableStateOf<String?>(settings.getString("bg_${op.id}", "")) }
    var cropTarget by remember { mutableStateOf<Uri?>(null) }
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                cropTarget = com.rhodes.privatechat.util.copyToCache(context, uri)
                if (cropTarget == null) android.widget.Toast.makeText(context, "无法读取此图片，请尝试选择JPG/PNG图片", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                com.rhodes.privatechat.util.DebugLogger.log("ChatScreen/ERROR", "选图失败: ${e.message}")
                android.widget.Toast.makeText(context, "无法加载此图片，请尝试其他图片", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    var showBgReset by rememberSaveable { mutableStateOf(false) }
    var showRestartChoices by rememberSaveable { mutableStateOf(false) }
    var showEraseConfirm by rememberSaveable { mutableStateOf(false) }
    val showModePicker = remember { mutableStateOf(false) }
    var showPropShop by rememberSaveable { mutableStateOf(false) }
    var pendingImageUri by rememberSaveable { mutableStateOf("") }
    var imageSending by rememberSaveable { mutableStateOf(false) }
    var forceScrollThroughMessageCount by remember { mutableStateOf(0) }
    var recordingVoice by rememberSaveable { mutableStateOf(false) }
    val audioController = remember { LocalAudioController(context) }
    val scope = rememberCoroutineScope()

    DisposableEffect(audioController) {
        onDispose { audioController.release() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        bgUri?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }

        Column(modifier = modifier.fillMaxSize()) {
            ChatHeader(
                title = displayOp.name.compactHeaderPart(12),
                avatarUri = displayOp.avatarUri,
                mode = currentMode,
                isLoading = isLoading,
                subtitleText = "",
                onBack = onBack,
                onModeClick = { showModePicker.value = true },
                menuContent = {
                    ChatDropdownMenuItem(
                        text = { Text("聊天记录") },
                        leadingIcon = { Icon(Icons.Default.DateRange, null, tint = TextPrimary) },
                        onClick = { onViewHistory() }
                    )
                    HorizontalDivider(color = Stroke)
                    ChatDropdownMenuItem(
                        text = { Text("编辑干员") },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = TextPrimary) },
                        onClick = { onEditOperator() }
                    )
                    ChatDropdownMenuItem(
                        text = { Text("更换背景图") },
                        leadingIcon = { Icon(Icons.Default.Image, null, tint = TextPrimary) },
                        onClick = { bgPicker.launch("image/*") }
                    )
                    if (bgUri != null) {
                        ChatDropdownMenuItem(
                            text = { Text("恢复默认背景") },
                            leadingIcon = { Icon(Icons.Default.Restore, null, tint = TextTertiary) },
                            onClick = { showBgReset = true }
                        )
                    }
                    HorizontalDivider(color = Stroke)
                    ChatDropdownMenuItem(
                        text = { Text("重新开始会话") },
                        leadingIcon = { Icon(Icons.Default.Refresh, null, tint = ErrorRed) },
                        onClick = { showRestartChoices = true }
                    )
                }
            )

            Column(modifier = Modifier.weight(1f).imePadding().clipToBounds()) {
                MessageList(
                    messages = messages,
                    listState = listState,
                    progressiveDisplay = true,
                    onRecall = { msgId, segIdx -> viewModel.recallMessageSegment(msgId, segIdx) },
                    onRegenerate = { viewModel.regenerateAiMessage(it) },
                    onContinue = { viewModel.continueAiMessage(it) },
                    onLoadOlder = { viewModel.loadOlderMessages() },
                    isLoadingOlder = isLoadingOlder,
                    hasMore = hasMoreMessages,
                    forceScrollToLatest = rawMessages.size <= forceScrollThroughMessageCount,
                    modifier = Modifier.weight(1f)
                )

                val hypnosisRounds by viewModel.hypnosisRounds.collectAsState()
                if (pendingImageUri.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth().background(ElevatedSurface.copy(alpha = 0.96f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = pendingImageUri, contentDescription = "待发送图片", modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(10.dp))
                        Text("图片待发送", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { pendingImageUri = "" }) { Text("移除", color = ErrorRed) }
                    }
                }
                ChatInputBar(
                    text = inputText,
                    onTextChange = { viewModel.updateInputText(it) },
                    onSend = {
                        if (pendingImageUri.isNotBlank()) {
                            val imageUri = pendingImageUri
                            imageSending = true
                            // The persisted image card is now the send-state UI; restore the composer immediately.
                            pendingImageUri = ""
                            forceScrollThroughMessageCount = rawMessages.size + 1
                            viewModel.chatViewModel.sendImageMessage(imageUri, MainActivity.imageForModel(imageUri), inputText) { success ->
                                imageSending = false
                                if (success) forceScrollThroughMessageCount = rawMessages.size
                            }
                        } else {
                            viewModel.sendMessage()
                        }
                    },
                    // Sending again intentionally cancels the unfinished request; do not trap users in image analysis.
                    enabled = !imageSending,
                    forceSendEnabled = pendingImageUri.isNotBlank(),
                    currentMode = currentMode,
                    onModeChange = { viewModel.setMode(it) },
                    placeholder = if (hypnosisRounds > 0) "催眠中 · 剩余${hypnosisRounds}轮" else "消息...",
                    indicatorBanner = if (hypnosisRounds > 0) {
                        { Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
                            Text("🧠 催眠剩余${hypnosisRounds}轮", fontSize = 11.sp, color = Color(0xFFE65100).copy(alpha = 0.8f))
                        } }
                    } else null,
                    onGenerateSuggestions = { callback -> viewModel.generateInspirations(callback) },
                    showModePicker = showModePicker,
                    menuItems = {
                        MenuChip("🔄 模式", Primary) { showModePicker.value = true }
                        MenuChip("🎒 道具", AccentOrange) { showPropShop = true }
                        MenuChip("🖼 相册", Primary) {
                            if (visionReady) MainActivity.pickImage { pendingImageUri = it }
                            else Toast.makeText(context, "图片聊天需要先设置识图模型，请在模型设置中填写识图地址、模型名和密钥。", Toast.LENGTH_LONG).show()
                        }
                        MenuChip("📷 拍照", Primary) {
                            if (visionReady) MainActivity.takePhoto { pendingImageUri = it }
                            else Toast.makeText(context, "图片聊天需要先设置识图模型，请在模型设置中填写识图地址、模型名和密钥。", Toast.LENGTH_LONG).show()
                        }
                        MenuChip(if (recordingVoice) "⏹ 录音" else "🎤 输入", if (recordingVoice) ErrorRed else Primary) {
                            if (!recordingVoice && !settings.hasAsrConfiguration()) {
                                Toast.makeText(context, "请先在模型设置中填写语音识别模型和密钥。", Toast.LENGTH_LONG).show()
                            } else if (!recordingVoice) {
                                MainActivity.requestMicrophonePermission { granted ->
                                    if (!granted) Toast.makeText(context, "需要允许麦克风权限才能使用语音输入。", Toast.LENGTH_LONG).show()
                                    else {
                                        recordingVoice = audioController.startRecording()
                                        if (!recordingVoice) Toast.makeText(context, "无法开始录音，请检查麦克风权限", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                recordingVoice = false
                                val recorded = audioController.stopRecording()
                                if (recorded == null) {
                                    Toast.makeText(context, "录音为空", Toast.LENGTH_SHORT).show()
                                } else {
                                    scope.launch {
                                        try {
                                            val asr = com.rhodes.privatechat.shared.voice.createAsrGateway(settings.asrBaseUrl, settings.asrApiKey.ifBlank { settings.apiKey }, settings.asrModelName)
                                            val text = asr.transcribe(AsrRequest(audioController.readPcmFromWav(recorded.path))).text
                                            if (text.isBlank()) Toast.makeText(context, "没有识别到文字", Toast.LENGTH_SHORT).show() else viewModel.updateInputText(text)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "语音识别失败：${e.message?.take(40) ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            audioController.deleteAudio(recorded.path)
                                        }
                                    }
                                }
                            }
                        }
                        MenuChip("📞 通话", AccentOrange) {
                            settings.voiceCallSetupMessage(op.voiceName)?.let {
                                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                            } ?: onVoiceCall()
                        }
                        MenuChip("📊 状态", Primary) { onViewStatus() }
                    }
                )
            }
        }
    }
    }

    if (showBgReset) {
        ThemedAlertDialog("恢复默认背景", "将移除当前背景图", { showBgReset = false }, "确认", { bgUri = null; settings.remove("bg_${op.id}"); showBgReset = false })
    }
    if (showRestartChoices) {
        AlertDialog(
            onDismissRequest = { showRestartChoices = false },
            title = { Text("重新开始会话", color = TextPrimary) },
            text = { Text("选择如何开始新的会话。", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showRestartChoices = false; showEraseConfirm = true }) {
                    Text("彻底清空", color = ErrorRed)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.restartSession(); showRestartChoices = false }) { Text("仅重新开始", color = Primary) }
                    TextButton(onClick = { showRestartChoices = false }) { Text("取消", color = TextSecondary) }
                }
            }
        )
    }
    if (showEraseConfirm) ThemedAlertDialog(
        "彻底清空并重新开始",
        "这会删除与${op.name}的全部私聊关系记录、摘要、长期印象和私密记忆（包括手动添加的私密记忆），无法恢复。公开动态、公开评论和群聊记录不会受影响。",
        { showEraseConfirm = false },
        "确认清空",
        { viewModel.erasePrivateSessionAndRestart(); showEraseConfirm = false },
        danger = true
    )
    if (showPropShop) {
        PropShopDialog(viewModel = viewModel, context = context, scope = scope, onDismiss = { showPropShop = false })
    }
    cropTarget?.let { uri ->
        com.rhodes.privatechat.ui.common.ImageCropperDialog(
            imageUri = uri, aspectX = 9f, aspectY = 16f,
            onConfirm = { cropped -> scope.launch { val s = com.rhodes.privatechat.util.copyToInternalStorageAsync(context, cropped); bgUri = s; settings.putString("bg_${op.id}", s); cropTarget = null } },
            onCancel = { cropTarget = null }
        )
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
    val balance by settings.lmbFlow.collectAsState(initial = settings.lmb)

    AlertDialog(onDismissRequest = onDismiss, containerColor = ElevatedSurface, shape = RoundedCornerShape(24.dp), title = { Row { Icon(Icons.Default.ShoppingCart, null, tint = AccentOrange, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(6.dp)); Text("道具商店", color = TextPrimary) } },
        text = {
            Column {
                Text("余额：${balance} 龙门币", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AccentOrange)
                Spacer(modifier = Modifier.height(12.dp))
                // 催眠怀表
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Card).padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("催眠怀表", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("对干员施加催眠指令，持续10轮", fontSize = 12.sp, color = TextSecondary)
                        }
                        Text("${PROP_PRICE}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (showHypnotizeInput) {
                        OutlinedTextField(value = hypnotizeInput, onValueChange = { hypnotizeInput = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            placeholder = { Text("输入催眠指令...", color = TextTertiary) }, shape = RoundedCornerShape(8.dp),
                            colors = softTextFieldColors())
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { showHypnotizeInput = false }) { Text("取消") }
                            TextButton(onClick = {
                                val command = hypnotizeInput.trim()
                                if (command.isBlank()) { Toast.makeText(context, "请输入催眠指令", Toast.LENGTH_SHORT).show(); return@TextButton }
                                val err = viewModel.buyProp("催眠怀表", context)
                                if (err != null) { Toast.makeText(context, err, Toast.LENGTH_SHORT).show(); return@TextButton }
                                viewModel.setHypnosis(command)
                                Toast.makeText(context, "催眠怀表已生效，持续10轮", Toast.LENGTH_SHORT).show()
                                showHypnotizeInput = false; hypnotizeInput = ""
                                onDismiss()
                            }) { Text("确认", color = Primary) }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(PrimaryContainer).clickable {
                            if (balance < PROP_PRICE) { Toast.makeText(context, "余额不足", Toast.LENGTH_SHORT).show(); return@clickable }
                            if (viewModel.hypnosisRounds.value > 0) { Toast.makeText(context, "已有催眠指令生效中", Toast.LENGTH_SHORT).show(); return@clickable }
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
                        Text("${PROP_PRICE}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
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
                            if (balance < PROP_PRICE) { Toast.makeText(context, "余额不足", Toast.LENGTH_SHORT).show(); return@clickable }
                            val err = viewModel.buyProp("看穿眼镜", context)
                            if (err != null) { Toast.makeText(context, err, Toast.LENGTH_SHORT).show(); return@clickable }
                            loading = true
                            scope.launch {
                                try {
                                    val temp = settings.aiTemperature
                                    val op = viewModel.selectedOperator.value
                                    val mood = op?.emotion ?: "平静"
                                    val loc = op?.location ?: "罗德岛"
                                    val stateDesc = op?.activity ?: "休息"
                                    val profile = viewModel.getUserProfile()
                                    val persona = op?.privatePrompt?.ifBlank { op?.description } ?: ""
                                    val recentChats = viewModel.messages.value.takeLast(6).joinToString("\n") { msg ->
                                        val content = if (msg.type == "ai_json") {
                                            msg.content.replace(Regex("""[{}\[\]\"]"""), " ").take(120)
                                        } else msg.content.take(120)
                                        "${if (msg.isMe) profile.nickname else msg.senderName}：$content"
                                    }
                                    val innerPrompt = """
【角色】
你是${op?.name ?: "干员"}。现在请表达你此刻最真实的内心独白——那些不会说出口、但正在脑海里翻涌的想法。这是你对自己说的话，不会有任何人看到。

【你扮演的角色信息】
名字：${op?.name ?: "干员"}
身份：${op?.title ?: ""}
人设：${persona}

【当前场景】
现在的时间是：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai") }.format(java.util.Date())}
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
                                    val innerResult = viewModel.sharedUtils.chat(listOf(
                                        AiMessage("system", innerPrompt)
                                    ), "InnerThoughts")
                                    viewModel.sharedUtils.trackTokens("inner_monologue", innerPrompt, innerResult)
                                    innerThoughts = innerResult
                                    // 内心独白只在弹窗显示，不插入聊天记录
                                } catch (_: Exception) {
                                    settings.addLmb(PROP_PRICE)
                                    innerThoughts = "读取失败，本次费用已退回。"
                                }
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
