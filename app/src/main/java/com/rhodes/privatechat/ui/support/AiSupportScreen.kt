package com.rhodes.privatechat.ui.support

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rhodes.privatechat.MainActivity
import com.rhodes.privatechat.shared.settings.AgentProfile
import com.rhodes.privatechat.shared.settings.AgentProfiles
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.viewmodel.AiSupportMessage
import com.rhodes.privatechat.viewmodel.AiSupportViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AiSupportScreen(onBack: () -> Unit, viewModel: AiSupportViewModel = koinViewModel()) {
    val messages by viewModel.messages.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val manualReady by viewModel.manualReady.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val remote by viewModel.remoteConfirmation.collectAsState()
    val currentAgent by viewModel.currentAgent.collectAsState()
    val settings: SettingsRepository = koinInject()
    var input by remember { mutableStateOf("") }
    var showAgentPicker by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var pendingImageUri by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Row(verticalAlignment = Alignment.CenterVertically) { AgentAvatar(agent = currentAgent, size = 30.dp); Spacer(Modifier.width(8.dp)); Text(currentAgent.name, fontWeight = FontWeight.Bold) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) } },
                actions = {
                    IconButton(onClick = { showAgentPicker = true }, enabled = !busy) { Icon(Icons.Default.SwapHoriz, "切换客服", tint = if (busy) TextTertiary else TextPrimary) }
                    IconButton(onClick = { showClearConfirmation = true }) { Icon(Icons.Default.DeleteSweep, "清空聊天记录", tint = TextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }, containerColor = BG
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (messages.isEmpty()) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp), state = listState) {
                    item(key = "greeting") {
                        SupportBubble(
                            message = AiSupportMessage(
                                id = -1L,
                                role = "assistant",
                                text = viewModel.greeting()
                            ),
                            agent = currentAgent,
                            userAvatarUri = settings.userAvatarUri
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp), state = listState, reverseLayout = false) {
                    items(items = messages, key = { it.id }) { message ->
                        SupportBubble(
                            message = message,
                            agent = currentAgent,
                            userAvatarUri = settings.userAvatarUri
                        )
                    }
                }
            }
            if (notice.isNotBlank()) Text(notice, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            if (pendingImageUri.isNotBlank()) Row(Modifier.fillMaxWidth().background(ElevatedSurface).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = pendingImageUri, contentDescription = "待发送图片", modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                Text("图片待发送", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                TextButton(onClick = { pendingImageUri = "" }) { Text("移除", color = ErrorRed) }
            }
            if (busy) Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = viewModel::cancelRequest) { Text("停止", color = Primary) }
            }
            if (busy) Text("正在检索产品说明并请求模型…", fontSize = 11.sp, color = Primary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
            if (!manualReady) Text("正在准备客服说明，请稍候…", fontSize = 11.sp, color = Primary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
            AiSupportInput(input = input, hasPendingImage = pendingImageUri.isNotBlank(), busy = busy || !manualReady, onInputChange = { input = it }, onSubmit = { question ->
                val imageUri = pendingImageUri
                pendingImageUri = ""
                viewModel.ask(question, imageUri, imageUri.takeIf { it.isNotBlank() }?.let(MainActivity::imageForModel))
                input = ""
            }, onPickImage = {
                MainActivity.pickImage { pendingImageUri = it }
            }, onTakePhoto = {
                MainActivity.takePhoto { pendingImageUri = it }
            })
        }
    }
    if (showAgentPicker) {
        AgentPickerDialog(
            currentId = currentAgent.id,
            onConfirm = { agent ->
                viewModel.setAgent(agent)
                showAgentPicker = false
            },
            onDismiss = { showAgentPicker = false }
        )
    }
    if (showClearConfirmation) AlertDialog(
        onDismissRequest = { showClearConfirmation = false },
        title = { Text("清空客服记录？") },
        text = { Text("将永久删除本机保存的客服文字、截图和图片摘要，且无法恢复。") },
        confirmButton = { TextButton(onClick = { viewModel.clear(); showClearConfirmation = false }) { Text("清空", color = ErrorRed) } },
        dismissButton = { TextButton(onClick = { showClearConfirmation = false }) { Text("取消") } },
    )
    if (remote) AlertDialog(onDismissRequest = viewModel::dismissRemoteEmbedding, title = { Text("启用第三方向量模型？") }, text = { Text("客服说明书会发送到你配置的向量服务以建立索引，可能产生费用。客服问题会用于向量检索。未确认时继续使用本地章节检索，不影响客服使用。") }, confirmButton = { TextButton(onClick = viewModel::confirmRemoteEmbedding) { Text("确认并建立索引") } }, dismissButton = { TextButton(onClick = viewModel::dismissRemoteEmbedding) { Text("继续本地检索") } })
}

@Composable
private fun AgentPickerDialog(currentId: String, onConfirm: (AgentProfile) -> Unit, onDismiss: () -> Unit) {
    var selectedId by remember(currentId) { mutableStateOf(currentId) }
    val selected = AgentProfiles.byId(selectedId)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("切换客服", color = TextPrimary) },
        text = {
            Column {
                Text("切换后，新客服将从下一条消息开始服务。当前记录仍可查看，但不会作为新客服的对话上下文发送；如需衔接，请在切换后简要说明。", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                items(AgentProfiles.all, key = { it.id }) { agent ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selectedId = agent.id }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AgentAvatar(agent = agent, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(agent.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                        }
                        RadioButton(selected = agent.id == selectedId, onClick = { selectedId = agent.id })
                    }
                }
            }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("确认", color = Primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) } }
    )
}

/** 客服头像:有图用图,无图用按名字首字的彩色圆形占位。 */
@Composable
private fun AgentAvatar(agent: AgentProfile, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(agentColor(agent.id), CircleShape), contentAlignment = Alignment.Center) {
        val context = LocalContext.current
        val avatarResourceId = context.resources.getIdentifier(agent.avatarUri, "drawable", context.packageName)
        if (avatarResourceId != 0) {
            AsyncImage(model = avatarResourceId, contentDescription = agent.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(agent.name.take(1), fontSize = (size.value * 0.45f).sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

private fun agentColor(id: String): Color = when (id) {
    "nuan" -> Color(0xFFFF8A65)
    "yu" -> Color(0xFF64B5F6)
    "fei" -> Color(0xFFBA68C8)
    "chuan" -> Color(0xFF4DB6AC)
    "lin" -> Color(0xFFE57373)
    else -> Color(0xFF81C784)
}

@Composable
fun AiSupportInput(input: String, hasPendingImage: Boolean = false, busy: Boolean, onInputChange: (String) -> Unit, onSubmit: (String) -> Unit, onPickImage: () -> Unit = {}, onTakePhoto: () -> Unit = {}) {
    fun submit() {
        if (!busy && (input.isNotBlank() || hasPendingImage)) onSubmit(input)
    }
    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
        IconButton(onClick = onPickImage, enabled = !busy) { Icon(Icons.Default.Image, "从相册选择图片", tint = if (busy) TextTertiary else Primary) }
        IconButton(onClick = onTakePhoto, enabled = !busy) { Icon(Icons.Default.PhotoCamera, "拍照", tint = if (busy) TextTertiary else Primary) }
        OutlinedTextField(value = input, onValueChange = onInputChange, modifier = Modifier.weight(1f), placeholder = { Text("输入问题或发送截图…") }, maxLines = 4, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { submit() }))
        IconButton(onClick = ::submit, enabled = !busy && (input.isNotBlank() || hasPendingImage)) { Icon(Icons.AutoMirrored.Filled.Send, "发送", tint = if (!busy && (input.isNotBlank() || hasPendingImage)) Primary else TextTertiary) }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SupportBubble(message: AiSupportMessage, agent: AgentProfile, userAvatarUri: String) {
    val isMe = message.role == "user"
    val messageAgent = AgentProfiles.byId(message.agentId.ifBlank { agent.id })
    val context = LocalContext.current
    var showCopyMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
        Row(verticalAlignment = Alignment.Top) {
            if (!isMe) {
                AgentAvatar(agent = messageAgent, size = 36.dp)
                Spacer(Modifier.width(8.dp))
            }
            Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                Text(if (isMe) "我" else messageAgent.name, fontSize = 11.sp, color = TextTertiary, modifier = Modifier.padding(horizontal = 2.dp))
                Spacer(Modifier.height(3.dp))
                Column {
                    if (message.imageUri.isNotBlank()) {
                        AsyncImage(model = message.imageUri, contentDescription = "客服求助图片", modifier = Modifier.size(180.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                        if (message.text.isNotBlank()) Spacer(Modifier.height(6.dp))
                    }
                    if (message.text.isNotBlank()) Text(
                        message.text,
                        fontSize = 14.sp,
                        color = TextPrimary,
                        modifier = Modifier
                            .combinedClickable(onClick = {}, onLongClick = { if (!isMe) showCopyMenu = true })
                            .background(if (isMe) BubbleMine else BubbleOther, RoundedCornerShape(if (isMe) 16.dp else 16.dp, if (isMe) 4.dp else 16.dp, if (isMe) 16.dp else 16.dp, if (isMe) 16.dp else 4.dp))
                            .padding(10.dp)
                    )
                    DropdownMenu(expanded = showCopyMenu, onDismissRequest = { showCopyMenu = false }) {
                        DropdownMenuItem(
                            text = { Row { Icon(Icons.Default.ContentCopy, null, tint = TextPrimary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("复制", color = TextPrimary) } },
                            onClick = {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                                    ?.setPrimaryClip(ClipData.newPlainText("客服回复", message.text))
                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                                showCopyMenu = false
                            }
                        )
                    }
                }
                message.sources.forEach { Text(it, fontSize = 10.sp, color = TextTertiary, modifier = Modifier.padding(top = 2.dp)) }
            }
            if (isMe) {
                Spacer(Modifier.width(8.dp))
                UserAvatar(avatarUri = userAvatarUri)
            }
        }
    }
}

@Composable
private fun UserAvatar(avatarUri: String) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF6B7280)), contentAlignment = Alignment.Center) {
        if (avatarUri.isNotBlank()) {
            AsyncImage(model = avatarUri, contentDescription = "我的头像", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text("我", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
