package com.rhodes.privatechat.ui.chat.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rhodes.privatechat.ui.common.OperatorAvatarImage
import com.rhodes.privatechat.ui.common.ThemedDropdownMenu
import com.rhodes.privatechat.ui.chat.formatChatTime
import com.rhodes.privatechat.ui.chat.model.ChatUiMessage
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.shared.data.ChatDisplayEvent
import kotlinx.coroutines.delay


/**
 * 私聊和群聊共用的消息列表。
 *
 * @param messages 已解析的 ChatUiMessage 列表
 * @param listState LazyListState
 * @param onRecall 撤回回调（传入 originalMessageId, segmentIndex）
 * @param onRegenerate 重说回调（私聊专用，null 则不显示）
 * @param onContinue 继续说回调（私聊专用，null 则不显示）
 * @param onSenderClick 点击发送者头像/名称回调（群聊专用，null 则不响应点击）
 * @param progressiveDisplay 是否启用 ai_json 分段渐进展示
 */
@Composable
fun MessageList(
    displaySessionKey: String,
    messages: List<ChatUiMessage>,
    listState: LazyListState,
    onRecall: (Long, Int) -> Unit,
    onRegenerate: ((Long) -> Unit)? = null,
    onContinue: ((Long) -> Unit)? = null,
    onSenderClick: ((String) -> Unit)? = null,
    progressiveDisplay: Boolean = false,
    displayEvents: List<ChatDisplayEvent> = emptyList(),
    displayEventsLoaded: Boolean = false,
    legacyMessageCutoff: Long = Long.MAX_VALUE,
    onReveal: (suspend (ChatUiMessage) -> Long)? = null,
    onLoadOlder: (() -> Unit)? = null,
    isLoadingOlder: Boolean = false,
    hasMore: Boolean = false,
    forceScrollToLatest: Boolean = false,
    onPlay: ((ChatUiMessage) -> Unit)? = null,
    speakingMessageKey: String = "",
    modifier: Modifier = Modifier,
) {
    fun eventKey(message: ChatUiMessage) = "${message.originalMessageId}:${message.segmentIndex}"
    var localEvents by remember(displaySessionKey) { mutableStateOf(emptyList<ChatDisplayEvent>()) }
    var legacyMaterialized by remember(displaySessionKey) { mutableStateOf(false) }
    LaunchedEffect(displayEvents) { localEvents = displayEvents }

    // Histories created before display events existed retain their chronological appearance.
    LaunchedEffect(displayEventsLoaded, messages, legacyMaterialized, legacyMessageCutoff) {
        if (!progressiveDisplay || onReveal == null || legacyMaterialized || !displayEventsLoaded || localEvents.isNotEmpty() || messages.isEmpty()) return@LaunchedEffect
        messages.filter { it.timestamp <= legacyMessageCutoff }.forEach { message ->
            val order = onReveal(message)
            localEvents = localEvents + ChatDisplayEvent(message.originalMessageId, message.segmentIndex, order)
        }
        legacyMaterialized = true
    }
    // User/system bubbles are immediate. Their persisted event puts them ahead of delayed AI segments.
    LaunchedEffect(messages, localEvents, displayEventsLoaded) {
        if (!progressiveDisplay || onReveal == null || !displayEventsLoaded || (!legacyMaterialized && localEvents.isEmpty())) return@LaunchedEffect
        messages.filter { !it.isAiSegment && localEvents.none { event -> event.messageId == it.originalMessageId && event.segmentIndex == it.segmentIndex } }
            .forEach { message ->
                val order = onReveal(message)
                localEvents = localEvents + ChatDisplayEvent(message.originalMessageId, message.segmentIndex, order)
            }
    }
    val nextAi = messages.firstOrNull { message ->
        message.isAiSegment && localEvents.none { event -> event.messageId == message.originalMessageId && event.segmentIndex == message.segmentIndex }
    }
    LaunchedEffect(nextAi?.let(::eventKey), displayEventsLoaded, legacyMaterialized) {
        val message = nextAi ?: return@LaunchedEffect
        if (!progressiveDisplay || onReveal == null || !displayEventsLoaded || (!legacyMaterialized && localEvents.isEmpty())) return@LaunchedEffect
        val priorAiSegmentIsVisible = messages.indexOf(message).takeIf { it > 0 }?.let { index ->
            val previous = messages[index - 1]
            previous.isAiSegment && previous.originalMessageId == message.originalMessageId &&
                localEvents.any { it.messageId == previous.originalMessageId && it.segmentIndex == previous.segmentIndex }
        } == true
        if (priorAiSegmentIsVisible) delay((1_000L + (Math.random() * 500)).toLong())
        if (localEvents.none { it.messageId == message.originalMessageId && it.segmentIndex == message.segmentIndex }) {
            val order = onReveal(message)
            localEvents = localEvents + ChatDisplayEvent(message.originalMessageId, message.segmentIndex, order)
        }
    }
    val eventOrder = localEvents.associate { "${it.messageId}:${it.segmentIndex}" to it.revealOrder }
    val displayMessages = if (!progressiveDisplay) messages else {
        val historicalWithoutEvent = messages.filter { it.timestamp <= legacyMessageCutoff && eventKey(it) !in eventOrder }
            .sortedWith(compareBy<ChatUiMessage> { it.timestamp }.thenBy { it.originalMessageId }.thenBy { it.segmentIndex })
        val revealed = messages.filter { eventKey(it) in eventOrder }
            .sortedWith(compareBy<ChatUiMessage> { eventOrder.getValue(eventKey(it)) }.thenBy { it.originalMessageId }.thenBy { it.segmentIndex })
        historicalWithoutEvent + revealed
    }

    var lastBottomMessageId by remember(displaySessionKey) { mutableStateOf<Long?>(null) }
    var lastDisplayMessageCount by remember(displaySessionKey) { mutableStateOf(0) }
    var initialPositioned by remember(displaySessionKey) { mutableStateOf(false) }
    var prependAnchorKey by remember(displaySessionKey) { mutableStateOf<String?>(null) }
    var prependAnchorOffset by remember(displaySessionKey) { mutableStateOf(0) }
    var awaitingPrepend by remember(displaySessionKey) { mutableStateOf(false) }
    var prependLoadingStarted by remember(displaySessionKey) { mutableStateOf(false) }
    var initialContentSettled by remember(displaySessionKey) { mutableStateOf(false) }

    // Display-event hydration can update the initial list more than once. Wait for one quiet
    // frame before positioning so those updates cannot trigger a visible second scroll.
    LaunchedEffect(displaySessionKey, displayEventsLoaded, displayMessages.lastOrNull()?.id, displayMessages.size) {
        if (!initialPositioned && displayEventsLoaded && displayMessages.isNotEmpty()) {
            initialContentSettled = false
            delay(80)
            try {
                // Index zero is the permanent top spacer, so the final message is size.
                listState.scrollToItem(displayMessages.size)
            } catch (_: Exception) {
            }
            // scrollToItem updates list state synchronously, but LazyColumn applies that state on
            // the next display frame. Do not reveal the list until that frame has elapsed.
            delay(16)
            initialPositioned = true
            initialContentSettled = true
        }
    }
    LaunchedEffect(displayMessages.lastOrNull()?.id, displayMessages.size) {
        val lastId = displayMessages.lastOrNull()?.id ?: return@LaunchedEffect
        val isInitial = lastBottomMessageId == null
        val isRemoval = displayMessages.size < lastDisplayMessageCount
        val nearBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= displayMessages.lastIndex - 2 } ?: true
        if (initialContentSettled && !isRemoval && !isInitial && !isLoadingOlder && (forceScrollToLatest || nearBottom)) {
            try {
                listState.scrollToItem(displayMessages.size)
            } catch (_: Exception) {}
        }
        lastBottomMessageId = lastId
        lastDisplayMessageCount = displayMessages.size
    }

    // Keep a conversation already at the bottom visible when the IME reduces the viewport.
    val imeBottom = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && displayMessages.isNotEmpty()) {
            val nearBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= displayMessages.lastIndex - 2 } ?: true
            if (nearBottom) listState.animateScrollToItem(displayMessages.size)
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex, displayMessages.size, isLoadingOlder, hasMore) {
        if (initialContentSettled && !awaitingPrepend && onLoadOlder != null && hasMore && !isLoadingOlder && displayMessages.isNotEmpty() && listState.firstVisibleItemIndex <= 1) {
            val anchorMessage = displayMessages.getOrNull((listState.firstVisibleItemIndex - 1).coerceAtLeast(0))
            prependAnchorKey = anchorMessage?.let(::eventKey)
            prependAnchorOffset = listState.firstVisibleItemScrollOffset
            awaitingPrepend = true
            prependLoadingStarted = false
            onLoadOlder()
        }
    }

    // Prepending an older page shifts every list index. Restore the previously visible message
    // and its pixel offset so scrolling upward never makes the reader lose their place.
    LaunchedEffect(isLoadingOlder, displayMessages.size, prependAnchorKey, awaitingPrepend, prependLoadingStarted) {
        if (awaitingPrepend && isLoadingOlder) {
            prependLoadingStarted = true
        }
        if (!isLoadingOlder && awaitingPrepend && prependLoadingStarted) {
            val key = prependAnchorKey ?: return@LaunchedEffect
            val index = displayMessages.indexOfFirst { eventKey(it) == key }
            if (index >= 0) {
                try {
                    listState.scrollToItem(index + 1, prependAnchorOffset)
                } catch (_: Exception) {
                }
            }
            prependAnchorKey = null
            awaitingPrepend = false
            prependLoadingStarted = false
        }
    }

    LazyColumn(state = listState, modifier = modifier.fillMaxWidth().alpha(if (initialContentSettled || displayMessages.isEmpty()) 1f else 0f)) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        if (isLoadingOlder) {
            item {
                Text("加载历史消息...", fontSize = 12.sp, color = TextTertiary, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)
            }
        }
        items(displayMessages.size, key = { i -> "${displayMessages[i].originalMessageId}_${displayMessages[i].segmentIndex}" }) { i ->
            val msg = displayMessages[i]
            val prevTime = if (i > 0) displayMessages[i - 1].timestamp else 0L
            val showTime = prevTime == 0L || (msg.timestamp - prevTime) > 3 * 60 * 1000
            MessageBubble(
                message = msg,
                showTime = showTime,
                onRecall = { onRecall(msg.originalMessageId, msg.segmentIndex) },
                onRegenerate = if (onRegenerate != null && !msg.isMe) { { onRegenerate(msg.originalMessageId) } } else null,
                onContinue = if (onContinue != null && !msg.isMe) { { onContinue(msg.originalMessageId) } } else null,
                onSenderClick = onSenderClick,
                onPlay = onPlay,
                isSpeaking = speakingMessageKey == "${msg.originalMessageId}:${msg.segmentIndex}:${msg.id}",
            )
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

/**
 * 单条消息气泡，根据 ChatUiMessage 的属性自动选择渲染方式。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatUiMessage,
    showTime: Boolean,
    onRecall: () -> Unit,
    onRegenerate: (() -> Unit)?,
    onContinue: (() -> Unit)?,
    onSenderClick: ((String) -> Unit)?,
    onPlay: ((ChatUiMessage) -> Unit)?,
    isSpeaking: Boolean,
) {
    val archivedAlpha = if (message.isArchived) 0.45f else 1f
    if (message.isSystem) {
        if (message.isNarration) {
            // 旁白：屏幕居中，圆角矩形半透明气泡，文字左对齐，支持长按撤回
            val context = LocalContext.current
            var showNarrationMenu by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth().alpha(archivedAlpha).padding(horizontal = 12.dp, vertical = 6.dp).combinedClickable(onLongClick = { showNarrationMenu = true }, onClick = {}), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.fillMaxWidth(0.9f).clip(RoundedCornerShape(16.dp)).background(Card.copy(alpha = 0.88f)).border(1.dp, Stroke, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(message.content, fontSize = 14.sp, color = TextPrimary, fontStyle = FontStyle.Italic, textAlign = TextAlign.Start, lineHeight = 20.sp)
                }
                ThemedDropdownMenu(expanded = showNarrationMenu, onDismissRequest = { showNarrationMenu = false }) {
                    DropdownMenuItem(text = { Row { Icon(Icons.Default.ContentCopy, null, tint = TextPrimary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("复制", color = TextPrimary) } },
                        onClick = {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                                ?.setPrimaryClip(ClipData.newPlainText("msg", message.content))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            showNarrationMenu = false
                        })
                    DropdownMenuItem(text = { Row { Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("撤回", color = ErrorRed) } },
                        onClick = { onRecall(); showNarrationMenu = false })
                }
            }
        } else {
            // 系统消息：居中
            Text(message.content, fontSize = 12.sp, color = TextTertiary,
                modifier = Modifier.fillMaxWidth().alpha(archivedAlpha).padding(horizontal = 32.dp, vertical = 6.dp),
                textAlign = TextAlign.Center)
        }
        return
    }

    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val isMe = message.isMe
    val bubbleColor = if (isMe) BubbleMine else BubbleOther
    val bubbleShape = if (isMe) RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp) else RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)

    Column(modifier = Modifier.fillMaxWidth().alpha(archivedAlpha).padding(horizontal = 12.dp, vertical = 2.dp)) {
        if (showTime) {
            Text(formatChatTime(message.timestamp), fontSize = 12.sp, color = TextTertiary,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)
        }

        Box {
            Row(
                modifier = Modifier.fillMaxWidth().combinedClickable(onLongClick = { showMenu = true }, onClick = {}),
                horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                if (!isMe) {
                    val avatarModifier = if (onSenderClick != null) {
                        Modifier.size(36.dp).clickable { onSenderClick(message.senderName) }
                    } else {
                        Modifier.size(36.dp)
                    }
                    OperatorAvatarImage(avatarUri = message.avatarUri, name = message.senderName, modifier = avatarModifier)
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                    // 群聊：显示发送者名称 + 时间
                    if (!isMe && onSenderClick != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(message.senderName, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                color = message.senderColor,
                                modifier = Modifier.clickable { onSenderClick(message.senderName) })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(formatChatTime(message.timestamp), fontSize = 10.sp, color = TextTertiary)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    // 气泡
                    Box(modifier = Modifier.widthIn(max = if (onSenderClick != null) 240.dp else 260.dp)
                        .clip(bubbleShape)
                        .background(if (isMe) Brush.linearGradient(listOf(BubbleMine, BubbleMineEnd)) else Brush.linearGradient(listOf(bubbleColor, bubbleColor)))
                        .border(1.dp, if (isMe) Primary.copy(alpha = 0.20f) else Stroke, bubbleShape)
                        .padding(horizontal = if (onSenderClick != null) 12.dp else 14.dp, vertical = if (onSenderClick != null) 8.dp else 10.dp)) {
                        if (message.imageUri.isNotBlank()) {
                            Column {
                                AsyncImage(model = message.imageUri, contentDescription = "图片消息", modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                                 if (message.content.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                     Text(message.content, fontSize = if (onSenderClick != null) 15.sp else 16.sp, color = TextPrimary, fontWeight = FontWeight.Normal)
                                 }
                                 if (message.imageStatus == "failed") {
                                     Spacer(Modifier.height(6.dp))
                                     Text("图片识别失败，请重新发送", fontSize = 12.sp, color = ErrorRed)
                                 }
                            }
                        } else {
                            Text(message.content.ifEmpty { if (isMe) "" else "..." },
                                fontSize = if (onSenderClick != null) 15.sp else 16.sp,
                                color = if (isMe) TextPrimary else TextPrimary,
                                fontWeight = FontWeight.Normal)
                        }
                    }
                    if (isSpeaking) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp)) {
                            Icon(Icons.Default.GraphicEq, contentDescription = "正在朗读", tint = Primary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("朗读中", fontSize = 10.sp, color = Primary)
                        }
                    }
                }

                if (isMe) {
                    Spacer(modifier = Modifier.width(8.dp))
                    if (message.avatarUri.isNotBlank()) {
                        AsyncImage(model = message.avatarUri, contentDescription = null,
                            modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF6B7280)),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, "我的头像", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // 上下文菜单
            ThemedDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Row { Icon(Icons.Default.ContentCopy, null, tint = TextPrimary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("复制", color = TextPrimary) } },
                    onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                            ?.setPrimaryClip(ClipData.newPlainText("msg", message.content))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        showMenu = false
                    })
                DropdownMenuItem(text = { Row { Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("撤回", color = ErrorRed) } },
                    onClick = { onRecall(); showMenu = false })
                if (onRegenerate != null) {
                    DropdownMenuItem(text = { Row { Icon(Icons.Default.Refresh, null, tint = Primary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("重说", color = Primary) } },
                        onClick = { onRegenerate(); showMenu = false })
                }
                if (onContinue != null) {
                    DropdownMenuItem(text = { Row { Icon(Icons.Default.SkipNext, null, tint = Primary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("继续说", color = Primary) } },
                        onClick = { onContinue(); showMenu = false })
                }
                if (onPlay != null && !message.isMe && !message.isNarration && message.imageUri.isBlank()) {
                    DropdownMenuItem(text = { Row { Icon(Icons.Default.VolumeUp, null, tint = Primary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("播放", color = Primary) } },
                        onClick = { onPlay(message); showMenu = false })
                }
            }
        }
    }
}

