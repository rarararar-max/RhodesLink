package com.rhodes.privatechat.ui.group

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class GroupHistoryEntry(
    val speaker: String,
    val content: String,
    val timestamp: Long,
    val isMe: Boolean
)

private val historyJson = Json { ignoreUnknownKeys = true }

private fun parseGroupHistoryJson(content: String): List<Pair<String, String>> {
    return try {
        val root = historyJson.parseToJsonElement(content)
        val arr = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["messages"] as? JsonArray) ?: (root["segments"] as? JsonArray) ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        arr.mapNotNull { el ->
            val obj = el.jsonObject
            val name = obj["speaker"]?.jsonPrimitive?.content ?: obj["sender"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val text = obj["message"]?.jsonPrimitive?.content ?: obj["content"]?.jsonPrimitive?.content ?: obj["text"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (name == "旁白") return@mapNotNull null
            name to text
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun senderColor(name: String): Color = when (name) {
    "阿米娅" -> Color(0xFF5B8DEF)
    "能天使" -> Color(0xFFFF7043)
    "德克萨斯" -> Color(0xFF607D8B)
    "夜莺" -> Color(0xFF81D4FA)
    "银灰" -> Color(0xFFFFD54F)
    "凯尔希" -> Color(0xFF4DB6AC)
    else -> Primary
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatHistoryScreen(
    viewModel: MainViewModel,
    groupName: String,
    groupId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var keyword by remember { mutableStateOf("") }
    var dates by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedDate by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var entries by remember { mutableStateOf<List<GroupHistoryEntry>>(emptyList()) }

    fun loadDate(date: String) {
        selectedDate = date
        keyword = ""
        scope.launch { results = viewModel.getGroupMessagesByDate(groupId, date) }
    }

    LaunchedEffect(Unit) {
        dates = viewModel.getGroupMessageDates(groupId)
        dates.firstOrNull()?.let { first ->
            selectedDate = first
            results = viewModel.getGroupMessagesByDate(groupId, first)
        }
    }

    LaunchedEffect(results) {
        entries = results.flatMap { msg ->
            if (msg.type == "ai_json") {
                parseGroupHistoryJson(msg.content).map { (speaker, text) ->
                    GroupHistoryEntry(speaker, text, msg.timestamp, false)
                }
            } else {
                listOf(GroupHistoryEntry(
                    speaker = if (msg.isMe) "我" else msg.senderName.ifBlank { "系统" },
                    content = msg.content,
                    timestamp = msg.timestamp,
                    isMe = msg.isMe
                ))
            }
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
                        Text(groupName, color = TextSecondary, fontSize = 12.sp)
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
                    scope.launch {
                        results = if (it.isBlank()) emptyList() else viewModel.searchGroupMessages(groupId, it)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("搜索聊天关键字", color = TextTertiary) }
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.fillMaxSize()) {
                if (keyword.isBlank()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            dates.take(4).forEach { date ->
                                AssistChip(onClick = { loadDate(date) }, label = { Text(if (date == selectedDate) "$date" else date) })
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
                if (entries.isEmpty()) {
                    item { Text(if (keyword.isBlank()) "暂无聊天记录" else "没有找到相关记录", color = TextTertiary, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
                } else {
                    items(entries, key = { "${it.speaker}_${it.timestamp}_${it.content.hashCode()}" }) { entry ->
                        GroupHistoryEntryItem(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHistoryEntryItem(entry: GroupHistoryEntry) {
    val color = if (entry.isMe) Primary else senderColor(entry.speaker)
    Column(
        Modifier.fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Card)
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(entry.speaker, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(historyTime(entry.timestamp), color = TextTertiary, fontSize = 11.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(entry.content, color = TextSecondary, fontSize = 14.sp)
    }
}

private fun historyTime(timestamp: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
