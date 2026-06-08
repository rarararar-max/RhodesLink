package com.rhodes.privatechat.ui.chat.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rhodes.privatechat.ui.common.OperatorAvatarImage
import com.rhodes.privatechat.ui.theme.*

/** 供 ChatDropdownMenuItem 使用的菜单关闭回调 */
val LocalDismissMenu = compositionLocalOf<(() -> Unit)?> { null }

/**
 * 私聊和群聊共用的顶部栏。
 *
 * @param title 标题（私聊为 operator.name，群聊为 groupName）
 * @param avatarUri 头像 URI（私聊为 operator.avatarUri，群聊为 groupSession.avatarUri）
 * @param mode 当前模式 "online"/"offline"/"director"
 * @param isLoading 是否正在加载（私聊显示"输入中..."）
 * @param subtitleText 副标题文本（私聊为 "地点 | 活动 | 心情"，群聊为 "模式 · N条消息"）
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
    showGroupIcon: Boolean = false,
    onBack: () -> Unit,
    menuContent: @Composable () -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().background(Surface)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
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
                        modifier = Modifier.size(34.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                }
            } else {
                OperatorAvatarImage(avatarUri = avatarUri, name = title, modifier = Modifier.size(34.dp))
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = if (showGroupIcon) 16.sp else 17.sp,
                        fontWeight = if (showGroupIcon) FontWeight.SemiBold else FontWeight.Bold,
                        color = TextPrimary)
                    if (!showGroupIcon) {
                        Spacer(modifier = Modifier.width(6.dp))
                        val modeLabel = when (mode) { "online" -> "🟢"; "director" -> "🎬"; else -> "🏠" }
                        val modeHint = when (mode) { "online" -> "线上"; "director" -> "导演"; else -> "线下" }
                        Text(modeLabel, fontSize = 13.sp)
                        Text(modeHint, fontSize = 11.sp, color = Primary, fontWeight = FontWeight.Medium)
                        if (isLoading) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("输入中...", fontSize = 13.sp, color = Primary, fontStyle = FontStyle.Italic)
                        }
                    }
                }
                if (subtitleText.isNotBlank()) {
                    Text(subtitleText, fontSize = 11.sp, color = TextSecondary)
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, null, tint = TextPrimary)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = Surface) {
                    CompositionLocalProvider(LocalDismissMenu provides { showMenu = false }) {
                        menuContent()
                    }
                }
            }
        }
        HorizontalDivider(color = Divider)
    }
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
