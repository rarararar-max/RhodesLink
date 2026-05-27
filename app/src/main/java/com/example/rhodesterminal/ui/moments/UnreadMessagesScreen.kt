package com.example.rhodesterminal.ui.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rhodesterminal.data.db.entity.MomentCommentEntity
import com.example.rhodesterminal.ui.theme.*
import com.example.rhodesterminal.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UnreadMessagesScreen(viewModel: MainViewModel, onBack: () -> Unit, onMomentClick: (Long, Long, String) -> Unit = { _, _, _ -> }, modifier: Modifier = Modifier) {
    var comments by remember { mutableStateOf<List<MomentCommentEntity>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    fun reload() {
        viewModel.loadInboxComments { list ->
            comments = list; loaded = true
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxSize().background(BG)) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("未读消息", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                viewModel.markAllCommentsRead()
                onBack()
            }) { Text("全部已读", fontSize = 13.sp, color = Primary, fontWeight = FontWeight.SemiBold) }
        }
        HorizontalDivider(color = Divider)

        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("加载中...", color = TextTertiary) }
        } else if (comments.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无未读消息", color = TextTertiary) }
        } else {
            LazyColumn {
                items(comments, key = { it.id }) { c ->
                    Row(Modifier.fillMaxWidth().clickable {
                        if (!c.isRead) { viewModel.markCommentRead(c.id); comments = comments.map { if (it.id == c.id) it.copy(isRead = true) else it } }
                        onMomentClick(c.momentId, c.id, c.operatorName)
                    }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        // 红点
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (c.isRead) Color.Transparent else ErrorRed))
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
                            Text(c.operatorName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.operatorName, fontSize = 14.sp, fontWeight = if (c.isRead) FontWeight.Normal else FontWeight.SemiBold, color = TextPrimary)
                            Text(c.content.take(60), fontSize = 13.sp, color = TextSecondary, maxLines = 1)
                        }
                        Text(SimpleDateFormat("MM/dd", Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai") }.format(Date(c.createdAt)), fontSize = 11.sp, color = TextTertiary)
                    }
                    HorizontalDivider(color = Divider, modifier = Modifier.padding(start = 66.dp))
                }
            }
        }
    }
}
