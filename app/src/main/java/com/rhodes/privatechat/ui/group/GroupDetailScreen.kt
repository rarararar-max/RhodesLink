package com.rhodes.privatechat.ui.group

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rhodes.privatechat.ui.chat.ChatShareDialog
import com.rhodes.privatechat.ui.chat.ShareMessage
import com.rhodes.privatechat.ui.chat.component.ChatDropdownMenuItem
import com.rhodes.privatechat.ui.chat.component.ChatHeader
import com.rhodes.privatechat.ui.chat.component.ChatInputBar
import com.rhodes.privatechat.ui.chat.component.MenuChip
import com.rhodes.privatechat.ui.chat.component.MessageList
import com.rhodes.privatechat.ui.chat.model.ChatUiMessage
import com.rhodes.privatechat.ui.chat.util.MessageParser
import com.rhodes.privatechat.viewmodel.MainViewModel
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.util.ChatTrace
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.MainActivity
import com.rhodes.privatechat.audio.LocalAudioController
import com.rhodes.privatechat.shared.voice.AsrGateway
import com.rhodes.privatechat.shared.voice.AsrRequest
import org.koin.compose.koinInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GroupDetailScreen(viewModel: MainViewModel, groupName: String, onBack: () -> Unit, onEditGroup: (String) -> Unit, groupId: String = "", modifier: Modifier = Modifier, onOperatorClick: (String) -> Unit = {}, onViewHistory: (String) -> Unit = {}) {
    val settings: SettingsRepository = koinInject()
    val listState = rememberLazyListState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var bgUri by remember { mutableStateOf<String?>(settings.getString("gbg_$groupId", "")) }
    var cropTarget by remember { mutableStateOf<Uri?>(null) }
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            cropTarget = com.rhodes.privatechat.util.copyToCache(ctx, uri)
            if (cropTarget == null) android.widget.Toast.makeText(ctx, "无法读取此图片，请尝试选择JPG/PNG图片", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    var showBgReset by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var currentMode by remember { mutableStateOf(settings.getGroupMode(groupId)) }
    val showModePicker = remember { mutableStateOf(false) }
    var inputText by rememberSaveable { mutableStateOf("") }
    var pendingImageUri by rememberSaveable { mutableStateOf("") }
    var recordingVoice by rememberSaveable { mutableStateOf(false) }
    val audioController = remember { LocalAudioController(ctx) }

    val groupMessages by viewModel.groupMessages.collectAsState()
    val groupRestartAt by viewModel.groupRestartAt.collectAsState()
    val isLoadingOlder by viewModel.isLoadingOlderGroupMessages.collectAsState()
    val hasMoreMessages by viewModel.hasMoreGroupMessages.collectAsState()
    val groupLoading by viewModel.groupLoading.collectAsState()
    val sessions by viewModel.allSessions.collectAsState()
    val groupSession = remember(groupId, sessions) { sessions.find { it.id == groupId } }
    val sendError by viewModel.groupChatViewModel.lastSendError.collectAsState()
    LaunchedEffect(sendError) {
        if (sendError.isNotBlank()) {
            android.widget.Toast.makeText(ctx, sendError, android.widget.Toast.LENGTH_LONG).show()
            viewModel.groupChatViewModel.clearSendError()
        }
    }

    DisposableEffect(groupId) {
        if (groupId.isNotBlank()) viewModel.setCurrentGroup(groupId)
        onDispose {
            audioController.release()
            viewModel.clearCurrentGroup()
        }
    }

    // Sender 颜色映射
    val senderColor: (String) -> Color = { name ->
        when (name) {
            "阿米娅" -> Color(0xFF5B8DEF); "能天使" -> Color(0xFFFF7043)
            "德克萨斯" -> Color(0xFF607D8B); "夜莺" -> Color(0xFF81D4FA)
            "银灰" -> Color(0xFFFFD54F); "凯尔希" -> Color(0xFF4DB6AC)
            else -> Primary
        }
    }
    val allOperators by viewModel.operators.collectAsState()
    val profile by viewModel.userProfile.collectAsState()
    fun senderAvatar(name: String): String = allOperators.find { it.id == name || it.name == name }?.avatarUri ?: ""

    // 使用 MessageParser 将原始消息转换为统一 UI 模型
    val uiMessages = remember(groupMessages, allOperators, profile, groupRestartAt) {
        try {
            val parsed = MessageParser.parse(
                messages = groupMessages,
                isGroup = true,
                senderColor = senderColor,
                senderAvatar = ::senderAvatar,
                userAvatarUri = profile.avatarUri,
                restartAt = groupRestartAt
            )
            ChatTrace.d("GroupScreen", "parsed group=$groupId raw=${groupMessages.size} parsed=${parsed.size}")
            parsed
        } catch (e: Exception) {
            ChatTrace.e("GroupScreen", "parse.ERROR group=$groupId raw=${groupMessages.size} err=${e.message}", e)
            emptyList()
        }
    }

    LaunchedEffect(groupMessages.size, uiMessages.size, groupLoading) {
        ChatTrace.d("GroupScreen", "group=$groupId raw=${groupMessages.size} parsed=${uiMessages.size} loading=$groupLoading")
    }

    Box(modifier = modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding().clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
    ) { focusManager.clearFocus() }) {
        bgUri?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        Column(modifier = Modifier.fillMaxSize()) {
            ChatHeader(
                title = groupName,
                avatarUri = groupSession?.avatarUri ?: "",
                mode = currentMode,
                subtitleText = "${groupMessages.size}条消息",
                showGroupIcon = true,
                onBack = onBack,
                onModeClick = { showModePicker.value = true },
                menuContent = {
                    ChatDropdownMenuItem(
                        text = { Text("聊天记录") },
                        leadingIcon = { Icon(Icons.Default.DateRange, null, tint = TextPrimary) },
                        onClick = { onViewHistory(groupId) }
                    )
                    HorizontalDivider(color = Stroke)
                    ChatDropdownMenuItem(
                        text = { Text("编辑群聊") },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = TextPrimary) },
                        onClick = { onEditGroup(groupId) }
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
                        text = { Text("重新开始群聊") },
                        leadingIcon = { Icon(Icons.Default.Refresh, null, tint = ErrorRed) },
                        onClick = { showClearConfirm = true }
                    )
                }
            )

            Column(modifier = Modifier.weight(1f).imePadding().clipToBounds()) {
                MessageList(
                    messages = uiMessages,
                    listState = listState,
                    onRecall = { msgId, segIdx -> viewModel.recallMessageSegment(msgId, segIdx) },
                    onSenderClick = onOperatorClick,
                    progressiveDisplay = true,
                    onLoadOlder = { viewModel.loadOlderGroupMessages() },
                    isLoadingOlder = isLoadingOlder,
                    hasMore = hasMoreMessages,
                    forceScrollToLatest = false,
                    modifier = Modifier.weight(1f)
                )

                val groupLoading by viewModel.groupLoading.collectAsState()
                if (pendingImageUri.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth().background(ElevatedSurface.copy(alpha = 0.96f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = pendingImageUri, contentDescription = "待发送图片", modifier = Modifier.size(72.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(10.dp))
                        Text("图片待发送", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { pendingImageUri = "" }) { Text("移除", color = ErrorRed) }
                    }
                }
                ChatInputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    onSend = { text ->
                        if (pendingImageUri.isNotBlank() && groupId.isNotBlank()) {
                            val imageUri = pendingImageUri
                            pendingImageUri = ""
                            viewModel.groupChatViewModel.sendGroupImageMessage(groupId, groupName, imageUri, MainActivity.imageForModel(imageUri), inputText, currentMode) {
                                inputText = ""
                            }
                        } else if (text.isNotBlank() && groupId.isNotBlank()) {
                            viewModel.sendGroupMessage(groupId, groupName, text, currentMode) {
                                inputText = ""
                            }
                        }
                    },
                    enabled = !groupLoading,
                    forceSendEnabled = pendingImageUri.isNotBlank(),
                    currentMode = currentMode,
                    onModeChange = { currentMode = it; settings.putGroupMode(groupId, it) },
                    showModePicker = showModePicker,
                    menuItems = {
                        MenuChip("切换模式", Primary) { showModePicker.value = true }
                        MenuChip("相册", Primary) { MainActivity.pickImage { pendingImageUri = it } }
                        MenuChip("拍照", Primary) { MainActivity.takePhoto { pendingImageUri = it } }
                        MenuChip(if (recordingVoice) "停止录音" else "录音", if (recordingVoice) ErrorRed else Primary) {
                            if (!recordingVoice) {
                                recordingVoice = audioController.startRecording()
                                if (!recordingVoice) android.widget.Toast.makeText(ctx, "无法开始录音，请检查麦克风权限", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                recordingVoice = false
                                val recorded = audioController.stopRecording()
                                if (recorded == null) {
                                    android.widget.Toast.makeText(ctx, "录音为空", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    scope.launch {
                                        try {
                                            val asr: AsrGateway = org.koin.core.context.GlobalContext.get().get()
                                            val text = asr.transcribe(AsrRequest(audioController.readPcmFromWav(recorded.path))).text
                                            if (text.isBlank()) android.widget.Toast.makeText(ctx, "没有识别到文字", android.widget.Toast.LENGTH_SHORT).show() else inputText = text
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(ctx, "语音识别失败：${e.message?.take(40) ?: "未知错误"}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }
    }

    // 对话框
    if (showClearConfirm) AlertDialog(
        onDismissRequest = { showClearConfirm = false },
        title = { Text("重新开始群聊", color = TextPrimary) },
        text = { Text("旧群聊不会删除，会在页面中变成浅灰色；后续群聊默认只参考新的会话内容。", color = TextSecondary) },
        confirmButton = { TextButton(onClick = { viewModel.restartGroupSession(groupId); showClearConfirm = false }) { Text("确认开始", color = Primary) } },
        dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消", color = TextSecondary) } }
    )

    if (showBgReset) AlertDialog(onDismissRequest = { showBgReset = false }, title = { Text("恢复默认背景", color = TextPrimary) }, text = { Text("将移除当前背景图", color = TextSecondary) }, confirmButton = { TextButton(onClick = { bgUri = null; settings.remove("gbg_$groupId"); showBgReset = false }) { Text("确认", color = Primary) } }, dismissButton = { TextButton(onClick = { showBgReset = false }) { Text("取消", color = TextSecondary) } })

    if (showShare) {
        val shareMsgs = uiMessages.map { msg ->
            ShareMessage(
                senderName = msg.senderName,
                content = msg.content,
                isMe = msg.isMe,
                isSystem = msg.isSystem,
                isNarration = msg.isNarration
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
        com.rhodes.privatechat.ui.common.ImageCropperDialog(
            imageUri = uri, aspectX = 9f, aspectY = 16f,
            onConfirm = { cropped -> scope.launch { val s = com.rhodes.privatechat.util.copyToInternalStorageAsync(ctx, cropped); bgUri = s; settings.putString("gbg_$groupId", s); cropTarget = null } },
            onCancel = { cropTarget = null }
        )
    }
}
