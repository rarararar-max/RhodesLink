package com.rhodes.privatechat.ui.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rhodes.privatechat.ui.common.OperatorAvatarImage
import com.rhodes.privatechat.ui.common.ThemedDropdownMenu
import com.rhodes.privatechat.ui.common.TerminalPanelShape
import com.rhodes.privatechat.ui.theme.*

data class ChatStatusDetails(
    val emotion: String = "",
    val location: String = "",
    val activity: String = "",
)

/** 供 ChatDropdownMenuItem 使用的菜单关闭回调 */
val LocalDismissMenu = compositionLocalOf<(() -> Unit)?> { null }

/**
 * 私聊和群聊共用的顶部栏。
 *
 * @param title 标题（私聊为 operator.name，群聊为 groupName）
 * @param avatarUri 头像 URI（私聊为 operator.avatarUri，群聊为 groupSession.avatarUri）
 * @param mode 当前模式 "online"/"offline"/"director"
 * @param isLoading 是否正在加载（私聊显示"输入中..."）
 * @param subtitleText 副标题文本（私聊为状态摘要，群聊为 "模式 · N条消息"）
 * @param statusDetails 私聊状态详情；为空时副标题不可点击
 * @param showGroupIcon 群聊头像为空时显示默认 Groups 图标
 * @param onBack 返回回调
 * @param menuContent 菜单内容（DropdownMenuItem 列表）
 */
@Composable
fun ChatHeader(
    title: String,
    avatarUri: String = "",
    mode: String = "online",
    isLoading: Boolean = false,
    subtitleText: String = "",
    statusDetails: ChatStatusDetails? = null,
    showGroupIcon: Boolean = false,
    onBack: () -> Unit,
    onModeClick: (() -> Unit)? = null,
    voiceEnabled: Boolean? = null,
    onVoiceToggle: (() -> Unit)? = null,
    menuContent: @Composable () -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }
    if (isRhodesTerminal) {
        val modeCode = when (mode) { "online" -> "COMMS"; "director" -> "DIRECT"; else -> "FIELD" }
        val modeHint = when (mode) { "online" -> "线上通讯"; "director" -> "导演模式"; else -> "线下互动" }
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    .background(Brush.horizontalGradient(listOf(HeaderStart, HeaderEnd)))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp).clip(TerminalPanelShape).background(Card).border(1.dp, Stroke, TerminalPanelShape)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(9.dp))
                if (showGroupIcon && avatarUri.isBlank()) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Primary),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Groups, "群聊", tint = Color.White, modifier = Modifier.size(19.dp)) }
                } else if (showGroupIcon) {
                    AsyncImage(
                        model = avatarUri,
                        contentDescription = title,
                        modifier = Modifier.size(36.dp).clip(CircleShape).border(1.dp, Primary.copy(alpha = 0.55f), CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    OperatorAvatarImage(avatarUri = avatarUri, name = title, modifier = Modifier.size(36.dp).border(1.dp, Primary.copy(alpha = 0.55f), CircleShape))
                }
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    HeaderSubtitle(
                        text = subtitleText.ifBlank { if (showGroupIcon) "群聊频道已连接" else "与${title}的专属通讯" },
                        statusDetails = statusDetails,
                        fontSize = 10.sp,
                    )
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.then(if (onModeClick != null) Modifier.clickable { onModeClick() } else Modifier)) {
                    Text(modeCode, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Primary, letterSpacing = 0.7.sp)
                    Text(modeHint, fontSize = 9.sp, color = TextTertiary)
                }
                Box {
                    if (voiceEnabled != null && onVoiceToggle != null) {
                        IconButton(onClick = onVoiceToggle, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (voiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                if (voiceEnabled) "关闭自动语音" else "开启自动语音",
                                tint = if (voiceEnabled) Primary else TextTertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp).clip(TerminalPanelShape).background(Card).border(1.dp, Stroke, TerminalPanelShape)) {
                        Icon(Icons.Default.MoreVert, "菜单", tint = TextPrimary, modifier = Modifier.size(19.dp))
                    }
                    ThemedDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        CompositionLocalProvider(LocalDismissMenu provides { showMenu = false }) { menuContent() }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(2.dp)) {
                Box(Modifier.weight(0.3f).fillMaxWidth().background(Primary))
                Box(Modifier.weight(0.7f).fillMaxWidth().background(Stroke))
            }
        }
        return
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
                .background(Brush.horizontalGradient(listOf(HeaderStart.copy(alpha = 0.96f), HeaderEnd.copy(alpha = 0.96f))))
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.clip(CircleShape).background(Card.copy(alpha = 0.48f))) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary)
            }

            // 头像
            if (showGroupIcon) {
                if (avatarUri.isBlank()) {
                    Box(
                        modifier = Modifier.size(34.dp).clip(CircleShape).background(Primary),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Groups, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                } else {
                    AsyncImage(model = avatarUri, contentDescription = null,
                        modifier = Modifier.size(34.dp).clip(CircleShape).border(1.dp, StrokeStrong, CircleShape), contentScale = ContentScale.Crop)
                }
            } else {
                OperatorAvatarImage(avatarUri = avatarUri, name = title, modifier = Modifier.size(34.dp).border(1.dp, StrokeStrong, CircleShape))
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, modifier = Modifier.weight(1f, fill = false), fontSize = if (showGroupIcon) 16.sp else 17.sp,
                        fontWeight = if (showGroupIcon) FontWeight.SemiBold else FontWeight.Bold,
                        color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.width(6.dp))
                    val modeLabel = when (mode) { "online" -> "🟢"; "director" -> "🎬"; else -> "🏠" }
                    val modeHint = when (mode) { "online" -> "线上"; "director" -> "导演"; else -> "线下" }
                    val modeModifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                        .background(Primary.copy(alpha = 0.12f))
                        .then(if (onModeClick != null) Modifier.clickable { onModeClick() } else Modifier)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                    Text("$modeLabel $modeHint", fontSize = 11.sp, color = Primary, fontWeight = FontWeight.Medium, modifier = modeModifier)
                    if (voiceEnabled != null && onVoiceToggle != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = onVoiceToggle, modifier = Modifier.size(28.dp)) {
                            Icon(if (voiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                if (voiceEnabled) "关闭自动语音" else "开启自动语音",
                                tint = if (voiceEnabled) Primary else TextTertiary,
                                modifier = Modifier.size(18.dp))
                        }
                    }
                    if (isLoading && !showGroupIcon) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("输入中...", fontSize = 13.sp, color = Primary, fontStyle = FontStyle.Italic)
                    }
                }
                if (subtitleText.isNotBlank()) {
                    HeaderSubtitle(text = subtitleText, statusDetails = statusDetails, fontSize = 11.sp)
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.clip(CircleShape).background(Card.copy(alpha = 0.38f))) {
                    Icon(Icons.Default.MoreVert, null, tint = TextPrimary)
                }
                ThemedDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    CompositionLocalProvider(LocalDismissMenu provides { showMenu = false }) {
                        menuContent()
                    }
                }
            }
        }
        HorizontalDivider(color = Stroke)
    }
}

@Composable
private fun HeaderSubtitle(
    text: String,
    statusDetails: ChatStatusDetails?,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    var showStatus by remember { mutableStateOf(false) }
    Box {
        Text(
            text = text,
            fontSize = fontSize,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (statusDetails != null) Modifier.clickable { showStatus = !showStatus } else Modifier,
        )
        if (statusDetails != null) {
            ThemedDropdownMenu(
                expanded = showStatus,
                onDismissRequest = { showStatus = false },
            ) {
                StatusMenuItem("心情", statusDetails.emotion) { showStatus = false }
                StatusMenuItem("位置", statusDetails.location) { showStatus = false }
                StatusMenuItem("状态", statusDetails.activity) { showStatus = false }
            }
        }
    }
}

@Composable
private fun StatusMenuItem(label: String, value: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(label, fontSize = 11.sp, color = TextTertiary)
                Text(value.ifBlank { "未确认" }, fontSize = 13.sp, color = TextPrimary, maxLines = 3)
            }
        },
        onClick = onClick,
    )
}

/**
 * 自动关闭菜单的 DropdownMenuItem，配合 ChatHeader 使用。
 * 点击时先关闭菜单，再执行 onClick。
 */
@Composable
fun ChatDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val dismissMenu = LocalDismissMenu.current
    DropdownMenuItem(
        text = text,
        onClick = { dismissMenu?.invoke(); onClick() },
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
    )
}
