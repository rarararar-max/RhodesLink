package com.rhodes.privatechat.ui.support

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.viewmodel.AiSupportMessage
import com.rhodes.privatechat.viewmodel.AiSupportViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AiSupportScreen(onBack: () -> Unit, viewModel: AiSupportViewModel = koinViewModel()) {
    val messages by viewModel.messages.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val remote by viewModel.remoteConfirmation.collectAsState()
    var input by remember { mutableStateOf("") }
    var persistConversation by remember { mutableStateOf(viewModel.persistConversationEnabled) }
    val listState = rememberLazyListState()
    val quickQuestions = listOf("如何配置文本模型？", "历史为什么没传给 AI？", "知识库怎么导入？", "群聊自动续聊怎么开？", "AI 回复失败怎么办？", "如何导出备份？")
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.SmartToy, null, tint = Primary); Spacer(Modifier.padding(3.dp)); Text("AI客服", fontWeight = FontWeight.Bold) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) } },
                actions = {
                    TextButton(onClick = if (viewModel.remoteVectorEnabled) viewModel::disableRemoteEmbedding else viewModel::requestRemoteEmbedding) { Text(if (viewModel.remoteVectorEnabled) "本地检索" else "向量检索", fontSize = 12.sp) }
                    IconButton(onClick = viewModel::clear) { Icon(Icons.Default.DeleteSweep, "清空", tint = TextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }, containerColor = BG
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (messages.isEmpty()) {
                Column(Modifier.weight(1f).padding(16.dp)) {
                    Text("产品使用说明与问题排查", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("我可以回答模型配置、私聊、群聊、知识库、备份和常见错误。", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 5.dp))
                    Spacer(Modifier.height(14.dp))
                    quickQuestions.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            row.forEach { question -> TextButton(onClick = { viewModel.ask(question) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(question, fontSize = 12.sp) } }
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp), state = listState, reverseLayout = false) {
                    items(items = messages, key = { it.id }) { message -> SupportBubble(message) }
                }
            }
            if (notice.isNotBlank()) Text(notice, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = persistConversation, onCheckedChange = { persistConversation = it; viewModel.setPersistConversation(it) })
                Text("保存本次客服记录到本机", fontSize = 11.sp, color = TextSecondary)
                if (busy) TextButton(onClick = viewModel::cancelRequest) { Text("停止", color = Primary) }
                else if (messages.lastOrNull()?.role == "assistant") TextButton(onClick = viewModel::retry) { Text("重试", color = Primary) }
            }
            if (busy) Text("正在检索产品说明并请求模型…", fontSize = 11.sp, color = Primary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
            AiSupportInput(input = input, busy = busy, onInputChange = { input = it }) { question ->
                viewModel.ask(question)
                input = ""
            }
        }
    }
    if (remote) AlertDialog(onDismissRequest = viewModel::dismissRemoteEmbedding, title = { Text("启用第三方向量模型？") }, text = { Text("客服说明书会发送到你配置的向量服务以建立索引，可能产生费用。客服问题会用于向量检索。未确认时继续使用本地章节检索，不影响客服使用。") }, confirmButton = { TextButton(onClick = viewModel::confirmRemoteEmbedding) { Text("确认并建立索引") } }, dismissButton = { TextButton(onClick = viewModel::dismissRemoteEmbedding) { Text("继续本地检索") } })
}

@Composable
fun AiSupportInput(input: String, busy: Boolean, onInputChange: (String) -> Unit, onSubmit: (String) -> Unit) {
    fun submit() {
        if (!busy && input.isNotBlank()) onSubmit(input)
    }
    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Bottom) {
        OutlinedTextField(value = input, onValueChange = onInputChange, modifier = Modifier.weight(1f), placeholder = { Text("输入你的问题…") }, maxLines = 4, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { submit() }))
        IconButton(onClick = ::submit, enabled = !busy && input.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "发送", tint = if (!busy && input.isNotBlank()) Primary else TextTertiary) }
    }
}

@Composable
private fun SupportBubble(message: AiSupportMessage) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalAlignment = if (message.role == "user") Alignment.End else Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (message.role != "user") {
                androidx.compose.foundation.layout.Box(Modifier.size(30.dp).background(Primary, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SmartToy, "AI客服头像", tint = OnPrimary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.padding(3.dp))
            }
            Text(if (message.role == "user") "我" else "AI客服", fontSize = 11.sp, color = TextTertiary)
        }
        Text(message.text, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.padding(top = 3.dp).background(if (message.role == "user") BubbleMine else BubbleOther, RoundedCornerShape(10.dp)).padding(10.dp))
        message.sources.forEach { Text(it, fontSize = 10.sp, color = TextTertiary, modifier = Modifier.padding(top = 2.dp)) }
    }
}
