package com.rhodes.privatechat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.shared.model.ChatMessage
import com.rhodes.privatechat.ui.theme.BG
import com.rhodes.privatechat.ui.theme.Card
import com.rhodes.privatechat.ui.theme.Primary
import com.rhodes.privatechat.ui.theme.Surface
import com.rhodes.privatechat.ui.theme.TextPrimary
import com.rhodes.privatechat.ui.theme.TextSecondary
import com.rhodes.privatechat.ui.theme.TextTertiary
import com.rhodes.privatechat.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistoryScreen(
    viewModel: MainViewModel,
    operatorName: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var keyword by remember { mutableStateOf("") }
    var dates by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedDate by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var pendingJump by remember { mutableStateOf<ChatMessage?>(null) }

    fun loadDate(date: String) {
        selectedDate = date
        keyword = ""
        scope.launch { results = viewModel.getCurrentSessionMessagesByDate(date) }
    }

    LaunchedEffect(Unit) {
        dates = viewModel.getCurrentSessionMessageDates()
        dates.firstOrNull()?.let { first ->
            selectedDate = first
            results = viewModel.getCurrentSessionMessagesByDate(first)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding(),
        containerColor = BG,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("聊天记录", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(operatorName, color = TextSecondary, fontSize = 12.sp)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            OutlinedTextField(
                value = keyword,
                onValueChange = {
                    keyword = it
                    selectedDate = ""
                    scope.launch { results = if (it.isBlank()) emptyList() else viewModel.searchCurrentSessionMessages(it) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("搜索聊天关键字", color = TextTertiary) }
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                if (keyword.isBlank()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            dates.take(4).forEach { date ->
                                AssistChip(onClick = { loadDate(date) }, label = { Text(if (date == selectedDate) "$date" else date) })
                            }
                        }
                    }
                }
                if (results.isEmpty()) {
                    item { Text(if (keyword.isBlank()) "暂无聊天记录" else "没有找到相关记录", color = TextTertiary, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
                } else {
                    items(results, key = { it.id }) { msg ->
                        HistoryMessageItem(msg, onClick = { pendingJump = msg })
                    }
                }
            }
        }
    }

    pendingJump?.let { msg ->
        AlertDialog(
            onDismissRequest = { pendingJump = null },
            title = { Text("跳转到这条聊天？", color = TextPrimary) },
            text = { Text("将回到聊天页，并定位到这条记录附近。", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.jumpToCurrentSessionMessage(msg.id)
                    pendingJump = null
                    onBack()
                }) { Text("确认跳转", color = Primary) }
            },
            dismissButton = { TextButton(onClick = { pendingJump = null }) { Text("取消", color = TextSecondary) } }
        )
    }
}

@Composable
private fun HistoryMessageItem(msg: ChatMessage, onClick: () -> Unit) {
    val sender = when {
        msg.isMe -> "我"
        msg.senderName.isBlank() -> "系统"
        else -> msg.senderName
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).clickable { onClick() }.padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(sender, color = if (msg.isMe) Primary else TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(historyTime(msg.timestamp), color = TextTertiary, fontSize = 11.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(historyPreview(msg), color = TextSecondary, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

private fun historyTime(timestamp: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun historyPreview(msg: ChatMessage): String {
    if (msg.type != "ai_json") return msg.content
    return Regex("\\\"(?:content|message|dialogue)\\\"\\s*:\\s*\\\"([^\\\"]{1,120})\\\"")
        .findAll(msg.content)
        .lastOrNull()
        ?.groupValues
        ?.getOrNull(1)
        ?.replace("\\n", " ")
        ?: "[AI回复]"
}
