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
import androidx.compose.material.icons.filled.Groups
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
import com.rhodes.privatechat.shared.settings.SettingsRepository
import org.koin.compose.koinInject
import kotlinx.coroutines.delay

@Composable
fun GroupDetailScreen(viewModel: MainViewModel, groupName: String, onBack: () -> Unit, onEditGroup: (String) -> Unit, groupId: String = "", modifier: Modifier = Modifier, onOperatorClick: (String) -> Unit = {}) {
    val settings: SettingsRepository = koinInject()
    val listState = rememberLazyListState()
    val ctx = LocalContext.current
    var bgUri by remember { mutableStateOf<String?>(settings.getString("gbg_$groupId", "")) }
    var cropTarget by remember { mutableStateOf<Uri?>(null) }
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> cropTarget = uri }
    var showBgReset by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var currentMode by remember { mutableStateOf(settings.getGroupMode(groupId)) }
    val showModePicker = remember { mutableStateOf(false) }
    var inputText by rememberSaveable { mutableStateOf("") }

    val groupMessages by viewModel.groupMessages.collectAsState()
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
        onDispose { viewModel.clearCurrentGroup() }
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
    val uiMessages = remember(groupMessages, allOperators, profile) {
        MessageParser.parse(
            messages = groupMessages,
            isGroup = true,
            senderColor = senderColor,
            senderAvatar = ::senderAvatar,
            userAvatarUri = profile.avatarUri
        )
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
                subtitleText = "${if (currentMode == "online") "线上" else if (currentMode == "offline") "线下" else "导演"} · ${groupMessages.size}条消息",
                showGroupIcon = true,
                onBack = onBack,
                menuContent = {
                    ChatDropdownMenuItem(text = { Text("编辑群聊") }, onClick = { onEditGroup(groupId) })
                    ChatDropdownMenuItem(text = { Text("更换背景图") }, onClick = { bgPicker.launch("image/*") })
                    if (bgUri != null) ChatDropdownMenuItem(text = { Text("恢复默认背景") }, onClick = { showBgReset = true })

                    ChatDropdownMenuItem(text = { Text("清除聊天记录") }, onClick = { showClearConfirm = true })
                }
            )

            Column(modifier = Modifier.weight(1f).imePadding().clipToBounds()) {
                MessageList(
                    messages = uiMessages,
                    listState = listState,
                    onRecall = { msgId, segIdx -> viewModel.recallMessageSegment(msgId, segIdx) },
                    onSenderClick = onOperatorClick,
                    progressiveDisplay = true,
                    modifier = Modifier.weight(1f)
                )

                val groupLoading by viewModel.groupLoading.collectAsState()
                ChatInputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    onSend = { text ->
                        if (text.isNotBlank() && groupId.isNotBlank()) {
                            viewModel.sendGroupMessage(groupId, groupName, text, currentMode) {
                                inputText = ""
                            }
                        }
                    },
                    enabled = !groupLoading,
                    currentMode = currentMode,
                    onModeChange = { currentMode = it; settings.putGroupMode(groupId, it) },
                    showModePicker = showModePicker,
                    menuItems = {
                        MenuChip("切换模式", Primary) { showModePicker.value = true }
                    }
                )
            }
        }
    }
    }

    // 对话框
    if (showClearConfirm) AlertDialog(
        onDismissRequest = { showClearConfirm = false },
        title = { Text("清除聊天记录", color = TextPrimary) },
        text = { Text("将清除本群聊全部聊天记录，此操作不可撤销。", color = TextSecondary) },
        confirmButton = { TextButton(onClick = { viewModel.clearGroupMessages(groupId); showClearConfirm = false }) { Text("确认清除", color = ErrorRed) } },
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
            onConfirm = { cropped -> val s = com.rhodes.privatechat.util.copyToInternalStorage(ctx, cropped); bgUri = s; settings.putString("gbg_$groupId", s); cropTarget = null },
            onCancel = { cropTarget = null }
        )
    }
}
