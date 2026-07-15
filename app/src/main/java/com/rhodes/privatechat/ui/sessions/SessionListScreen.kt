package com.rhodes.privatechat.ui.sessions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rhodes.privatechat.data.db.entity.ChatSessionEntity
import com.rhodes.privatechat.ui.common.OperatorAvatarImage
import com.rhodes.privatechat.ui.common.AppBackground
import com.rhodes.privatechat.ui.common.ThemedAlertDialog
import com.rhodes.privatechat.ui.common.ThemedDropdownMenu
import com.rhodes.privatechat.ui.common.WechatListGroup
import com.rhodes.privatechat.ui.common.WechatTopBar
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionListScreen(
    viewModel: MainViewModel,
    onSessionClick: (ChatSessionEntity) -> Unit,
    onPin: (String) -> Unit = {},
    onMarkRead: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sessions by viewModel.sessions.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var showSessionActions by remember { mutableStateOf<ChatSessionEntity?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(BG)) {
        WechatTopBar("聊天", actions = {
            Box {
                IconButton(onClick = { showOverflowMenu = true }) { Icon(Icons.Default.MoreVert, "菜单", tint = TextPrimary) }
                ThemedDropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                    DropdownMenuItem(text = { Text("移除主页所有会话") }, onClick = { showOverflowMenu = false; showClearAllConfirm = true })
                    DropdownMenuItem(text = { Text("全部标记已读") }, onClick = { showOverflowMenu = false; viewModel.markAllRead() })
                }
            }
        })

        if (sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无聊天记录", fontSize = 16.sp, color = TextTertiary)
            }
        } else {
            LazyColumn {
                item { WechatListGroup { } }
                items(sessions, key = { it.id }) { session ->
                    SessionItem(session = session, onClick = { onSessionClick(session) },
                        onLongClick = { showSessionActions = session })
                }
            }
        }
    }

    showDeleteConfirm?.let { sessionId ->
        ThemedAlertDialog("从主页移除", "会话将暂时不在聊天主页显示。收到新的消息后，会自动重新出现在主页。", { showDeleteConfirm = null }, "确认移除", { onDelete(sessionId); showDeleteConfirm = null }, danger = true)
    }

    if (showClearAllConfirm) {
        ThemedAlertDialog("移除主页所有会话", "所有会话将暂时不在聊天主页显示。任一会话收到新的消息后，会自动重新出现在主页。", { showClearAllConfirm = false }, "确认移除", { viewModel.clearAllMessages(); showClearAllConfirm = false }, danger = true)
    }

    showSessionActions?.let { s ->
        AlertDialog(
            onDismissRequest = { showSessionActions = null },
            title = { Text("操作", color = TextPrimary) },
            text = {
                Column {
                    TextButton(onClick = { onPin(s.id); showSessionActions = null }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (s.isPinned) "取消置顶" else "置顶", color = TextPrimary)
                    }
                    TextButton(onClick = { onMarkRead(s.id); showSessionActions = null }, modifier = Modifier.fillMaxWidth()) { Text("标记已读", color = TextPrimary) }
                    TextButton(onClick = { showSessionActions = null; showDeleteConfirm = s.id }, modifier = Modifier.fillMaxWidth()) { Text("从主页移除", color = ErrorRed) }
                }
            },
            confirmButton = { TextButton(onClick = { showSessionActions = null }) { Text("取消", color = TextSecondary) } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionItem(session: ChatSessionEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier
            .fillMaxWidth()
            .background(if (session.isPinned) Primary.copy(alpha = 0.06f) else Surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                OperatorAvatarImage(avatarUri = session.avatarUri, name = session.operatorName, modifier = Modifier.size(48.dp))
                if (session.unreadCount > 0) {
                    Box(modifier = Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).background(ErrorRed).align(Alignment.TopEnd), contentAlignment = Alignment.Center) {
                        Text("${session.unreadCount}", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (session.isPinned) {
                        Icon(Icons.Default.PushPin, null, tint = Primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(session.operatorName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(formatSessionTime(session.lastTime), fontSize = 12.sp, color = TextSecondary)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(session.lastMessage.ifBlank { "暂无消息" }, fontSize = 14.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider(color = Divider.copy(alpha = 0.45f), modifier = Modifier.padding(start = 76.dp))
    }
}

private fun formatSessionTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000}分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"
        else -> SimpleDateFormat("MM/dd", Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai") }.format(Date(timestamp))
    }
}
