package com.rhodes.privatechat.ui.dispatch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.SendToMobile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.data.db.entity.DispatchRecordEntity
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.viewmodel.MainViewModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun statusText(s: String): String = when (s) {
    "finished" -> "已完成"; "cancelled" -> "已中断"
    "active" -> "进行中"; "generating" -> "生成中"
    else -> s
}

@Composable
fun DispatchHistoryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var historyList by remember { mutableStateOf<List<DispatchRecordEntity>>(emptyList()) }
    var selected by remember { mutableStateOf<DispatchRecordEntity?>(null) }

    LaunchedEffect(Unit) {
        historyList = viewModel.repository.getHistoryDispatches()
    }

    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai") } }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (selected != null) selected = null else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.SendToMobile, null, tint = Primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (selected != null) "派遣详情" else "派遣历史", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        HorizontalDivider(color = Divider)

        if (selected != null) {
            val entry = selected!!
            val markers = listOf("【开局】", "【第", "【结局】", "【已中断】")
            val rawSegments = mutableListOf<String>()
            var pos = 0
            while (pos < entry.logChain.length) {
                val next = markers.mapNotNull { m ->
                    val idx = entry.logChain.indexOf(m, pos)
                    if (idx >= 0) idx to m else null
                }.minByOrNull { it.first }
                if (next == null) break
                val start = next.first
                if (start > pos) rawSegments.add(entry.logChain.substring(pos, start).trim())
                pos = start
                val end = markers.mapNotNull { m ->
                    val idx = entry.logChain.indexOf(m, start + next.second.length)
                    if (idx >= 0) idx else null
                }.minOrNull() ?: entry.logChain.length
                rawSegments.add(entry.logChain.substring(start, end).trim())
                pos = end
            }
            if (pos < entry.logChain.length) rawSegments.add(entry.logChain.substring(pos).trim())
            val segments = rawSegments.filter { it.isNotBlank() }.map { seg ->
                val type = when {
                    seg.startsWith("【结局】") -> "ending"
                    seg.startsWith("【开局】") || seg.startsWith("【第") -> "progress"
                    seg.startsWith("【已中断】") -> "cancelled"
                    else -> "progress"
                }
                val content = seg.replace(Regex("^【[^】]+】"), "").trim()
                Triple(type, content, "")
            }

            LazyColumn(Modifier.padding(12.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.taskType, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("${entry.durationHours}小时", fontSize = 13.sp, color = TextSecondary)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("投入: ${entry.budget}", fontSize = 13.sp, color = TextSecondary)
                            Text("净收益:", fontSize = 13.sp, color = TextSecondary)
                        }
                        Text(if (entry.netProfit >= 0) "+${entry.netProfit}" else "${entry.netProfit}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (entry.netProfit >= 0) AccentGreen else ErrorRed)
                        Spacer(Modifier.height(4.dp))
                        Text("状态: ${statusText(entry.status)}", fontSize = 12.sp, color = TextTertiary)
                        Text(dateFormat.format(Date(entry.endTime)), fontSize = 12.sp, color = TextTertiary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("完整日志", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                itemsIndexed(segments) { i, (type, content) ->
                    val label = when (type) { "prep" -> "准备阶段"; "ending" -> "结局"; else -> "过程"
                    }
                    if (content.isNotBlank()) {
                        Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                            Text("· $label", fontSize = 12.sp, color = if (type == "ending") Primary else TextTertiary, fontWeight = FontWeight.SemiBold)
                            Text(content, fontSize = 13.sp, color = TextPrimary, lineHeight = 22.sp)
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.padding(12.dp)) {
                itemsIndexed(historyList) { i, entry ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).clickable { selected = entry }.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.taskType, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(dateFormat.format(Date(entry.endTime)), fontSize = 12.sp, color = TextTertiary)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("${entry.durationHours}小时 · 投入${entry.budget} · 状态${statusText(entry.status)}", fontSize = 12.sp, color = TextSecondary)
                        Spacer(Modifier.height(2.dp))
                        Text(if (entry.netProfit >= 0) "净收益 +${entry.netProfit}" else "净收益 ${entry.netProfit}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (entry.netProfit >= 0) AccentGreen else ErrorRed)
                    }
                    if (i < historyList.size - 1) Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
    }
}
