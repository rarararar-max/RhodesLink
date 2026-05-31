package com.rhodes.privatechat.ui.chat
import com.rhodes.privatechat.shared.model.AiMessage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.chat.component.ChatHeader
import com.rhodes.privatechat.ui.chat.component.ChatInputBar
import com.rhodes.privatechat.ui.chat.component.MenuChip
import com.rhodes.privatechat.ui.chat.component.MessageList
import com.rhodes.privatechat.ui.chat.model.ChatUiMessage
import com.rhodes.privatechat.ui.chat.util.MessageParser
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.viewmodel.MainViewModel
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
    val rawMessages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val listState = rememberLazyListState()
    val op = operator ?: run { onBack(); return }
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    val focusManager = LocalFocusManager.current

    // 使用 MessageParser 将原始消息转换为统一 UI 模型
    val messages = remember(rawMessages, op, userProfile) {
        MessageParser.parse(
            messages = rawMessages,
            isGroup = false,
            aiName = op.name,
            aiAvatarUri = op.avatarUri,
            userAvatarUri = userProfile.avatarUri
        )
    }

    var bgUri by remember { mutableStateOf<String?>(settings.getString("bg_${op.id}", "")) }
    var cropTarget by remember { mutableStateOf<Uri?>(null) }
    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> cropTarget = uri }
    var showBgReset by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    val showModePicker = remember { mutableStateOf(false) }
    var showPropShop by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding().background(BG).clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
    ) { focusManager.clearFocus() }) {
        bgUri?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }

        Column(modifier = modifier.fillMaxSize()) {
            ChatHeader(
                title = op.name,
                avatarUri = op.avatarUri,
                mode = currentMode,
                isLoading = isLoading,
                subtitleText = "${op.location} | ${op.activity} | ${op.emotion}",
                onBack = onBack,
                menuContent = {
                    DropdownMenuItem(text = { Text("更换背景图") }, onClick = { bgPicker.launch("image/*") })
                    if (bgUri != null) DropdownMenuItem(text = { Text("恢复默认背景") }, onClick = { showBgReset = true })
                    DropdownMenuItem(text = { Text("编辑干员") }, onClick = { onEditOperator() })
                    DropdownMenuItem(text = { Text("分享") }, onClick = { showExport = true })
                }
            )

            Column(modifier = Modifier.weight(1f).imePadding()) {
                MessageList(
                    messages = messages,
                    listState = listState,
                    onRecall = { viewModel.recallMessage(it) },
                    onRegenerate = { viewModel.regenerateAiMessage(it) },
                    onContinue = { viewModel.continueAiMessage(it) },
                    modifier = Modifier.weight(1f)
                )

                val hypnosisRounds by viewModel.hypnosisRounds.collectAsState()
                ChatInputBar(
                    text = inputText,
                    onTextChange = { viewModel.updateInputText(it) },
                    onSend = { viewModel.sendMessage() },
                    enabled = true,
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
                        MenuChip("切换模式", Primary) { showModePicker.value = true }
                        MenuChip("重启聊天", ErrorRed) { viewModel.clearMessages() }
                        MenuChip("查看状态", Primary) { onViewStatus() }
                        MenuChip("使用道具", AccentOrange) { showPropShop = true }
                    }
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
            ChatExportDialog(operatorName = op.name, messages = rawMessages, userProfile = viewModel.userProfile.value, operatorAvatarUri = op.avatarUri, onDismiss = { showExport = false })
        }, confirmButton = {})
    }
    if (showPropShop) {
        PropShopDialog(viewModel = viewModel, context = context, scope = scope, onDismiss = { showPropShop = false })
    }
    cropTarget?.let { uri ->
        com.rhodes.privatechat.ui.common.ImageCropperDialog(
            imageUri = uri, aspectX = 9f, aspectY = 16f,
            onConfirm = { cropped -> val s = com.rhodes.privatechat.util.copyToInternalStorage(context, cropped); bgUri = s; settings.putString("bg_${op.id}", s); cropTarget = null },
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
                                    viewModel.sharedUtils.streamChat(listOf(
                                        AiMessage("system", innerPrompt)
                                    ), "InnerThoughts").collect { result.append(it) }
                                    innerThoughts = result.toString()
                                    val thoughtText = innerThoughts
                                    if (!thoughtText.isNullOrBlank()) {
                                        viewModel.setMindRead(thoughtText)
                                    }
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
