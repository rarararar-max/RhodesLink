package com.rhodes.privatechat.ui.dispatch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.automirrored.filled.SendToMobile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun DispatchProgressScreen(
    viewModel: MainViewModel,
    dispatchId: String,
    onBack: () -> Unit,
    onCancel: () -> Unit = onBack,
    modifier: Modifier = Modifier
) {
    var segments by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var totalSeg by remember { mutableIntStateOf(0) }
    var interval by remember { mutableLongStateOf(0L) }
    var startTime by remember { mutableLongStateOf(0L) }
    var visibleCount by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    var netProfit by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf("") }
    var taskType by remember { mutableStateOf("") }
    var budget by remember { mutableIntStateOf(0) }
    var showCancel by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 加载派遣记录（含等待 AI 生成）
    LaunchedEffect(dispatchId) {
        // 轮询等待记录出现（AI 生成中 status="generating"）
        var rec = viewModel.repository.getDispatch(dispatchId)
        var waitSec = 0
        while (rec == null) { delay(1000); waitSec++; rec = viewModel.repository.getDispatch(dispatchId); if (waitSec > 30) { done = true; errorMsg = "派遣加载超时"; return@LaunchedEffect } }
        val r = rec ?: run {
            done = true
            errorMsg = "派遣记录不存在或已删除"
            return@LaunchedEffect
        }
        taskType = r.taskType
        budget = r.budget
        interval = r.segmentInterval
        totalSeg = r.totalSegments
        loaded = true
        // 等待 AI 生成完成（status 变为 active 或 cancelled）
        if (r.status == "generating") {
            var genSec = 0
            while (true) {
                delay(1000); genSec++
                val updated = viewModel.repository.getDispatch(dispatchId) ?: break
                if (updated.status != "generating") { rec = updated; break }
                if (genSec > 120) { done = true; errorMsg = "AI 生成超时，请重试"; return@LaunchedEffect }
            }
        }
        val finalRec = rec ?: run {
            done = true
            errorMsg = "派遣记录不存在或已删除"
            return@LaunchedEffect
        }
        if (finalRec.status == "cancelled" || finalRec.status == "finished") {
            done = true; visibleCount = totalSeg
            netProfit = finalRec.netProfit; items = finalRec.items
            // 检测AI生成失败：cancelled + 无段落 + 无收益 = AI生成失败
            if (finalRec.status == "cancelled" && finalRec.totalSegments <= 0 && finalRec.netProfit == 0 && finalRec.logChain.isBlank()) {
                errorMsg = "AI生成失败，预算已退还"
            }
            return@LaunchedEffect
        }
        startTime = finalRec.startTime
        interval = finalRec.segmentInterval
        totalSeg = finalRec.totalSegments
        // 解析 segments（支持分段追加）
        startTime = finalRec.startTime
        interval = finalRec.segmentInterval
        totalSeg = finalRec.totalSegments

        fun parseSegmentsFromLog(log: String): List<Map<String, Any?>> {
            if (log.isBlank()) return emptyList()
            val markers = listOf("【开局】", "【第", "【结局】", "【已中断】")
            val rawSegments = mutableListOf<String>()
            var pos = 0
            while (pos < log.length) {
                val next = markers.mapNotNull { m ->
                    val idx = log.indexOf(m, pos)
                    if (idx >= 0) idx to m else null
                }.minByOrNull { it.first }
                if (next == null) break
                val start = next.first
                if (start > pos) rawSegments.add(log.substring(pos, start).trim())
                pos = start
                val end = markers.mapNotNull { m ->
                    val idx = log.indexOf(m, start + next.second.length)
                    if (idx >= 0) idx else null
                }.minOrNull() ?: log.length
                rawSegments.add(log.substring(start, end).trim())
                pos = end
            }
            if (pos < log.length) rawSegments.add(log.substring(pos).trim())
            return rawSegments.filter { it.isNotBlank() }.map { seg ->
                val type = when {
                    seg.startsWith("【结局】") -> "ending"
                    seg.startsWith("【开局】") || seg.startsWith("【第") -> "progress"
                    seg.startsWith("【已中断】") -> "cancelled"
                    else -> "progress"
                }
                val content = seg.replace(Regex("^【[^】]+】"), "").trim()
                mapOf("type" to type, "content" to content)
            }
        }

        val initialSegments = parseSegmentsFromLog(finalRec.logChain)
        segments = initialSegments.toMutableList()
        totalSeg = maxOf(totalSeg, initialSegments.size)

        // 定时检查 DB 中新增的段落 + 按时间解锁
        val waitStartedAt = System.currentTimeMillis()
        while (true) {
            // 总超时（120 分钟）
            if (System.currentTimeMillis() - waitStartedAt > 120L * 60_000L) { done = true; errorMsg = "派遣超时"; break }
            // 从 DB 拉取最新数据
            val currentRec = viewModel.repository.getDispatch(dispatchId)
            if (currentRec == null) { delay(3000); continue }
            if (currentRec.status == "cancelled" || currentRec.status == "finished") {
                done = true; visibleCount = currentRec.totalSegments
                netProfit = currentRec.netProfit; items = currentRec.items
                if (currentRec.status == "cancelled" && currentRec.logChain.isBlank()) {
                    errorMsg = "AI生成失败，预算已退还"
                } else if (currentRec.status == "finished") {
                    // 解析最终段落
                    segments = parseSegmentsFromLog(currentRec.logChain)
                    visibleCount = segments.size
                }
                break
            }

            // 更新段落列表（后台可能已追加新段）
            val newSegments = parseSegmentsFromLog(currentRec.logChain)
            if (newSegments.size > segments.size) {
                segments = newSegments
            }
            totalSeg = maxOf(currentRec.totalSegments, newSegments.size)

            // 按时间解锁段落
            val elapsed = System.currentTimeMillis() - currentRec.startTime
            val unlockedCount = (elapsed / (currentRec.segmentInterval.coerceAtLeast(1L))).toInt().coerceIn(1, totalSeg.coerceAtLeast(1))
            if (unlockedCount > visibleCount) {
                visibleCount = unlockedCount
            }

            if (unlockedCount >= totalSeg) {
                viewModel.finishDispatch(dispatchId)
            }
            delay(3000)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.SendToMobile, null, tint = Primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Column {
                Text(taskType.ifBlank { "野外物资搜集" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(if (done) "状态：已结束" else "进行中", fontSize = 12.sp, color = if (done) Primary else AccentOrange)
            }
        }
        HorizontalDivider(color = Divider)

        if (errorMsg.isNotBlank()) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(errorMsg, color = ErrorRed, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("本次派遣没有开始，你可以返回后重新发起。", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(44.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("返回派遣页") }
            }
            return@Column
        }

        if (!loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("加载中...", color = TextTertiary) }
            return@Column
        }

        // 信息栏
        Column(Modifier.fillMaxWidth().background(Card).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("投入预算", fontSize = 11.sp, color = TextSecondary)
                    Text("$budget 龙门币", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (done) {
                        Text("净收益", fontSize = 11.sp, color = TextSecondary)
                        Text("${netProfit} 龙门币", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (netProfit >= 0) Primary else ErrorRed)
                    } else {
                        Text("${visibleCount}/$totalSeg 段", fontSize = 11.sp, color = TextSecondary)
                        val progress = visibleCount.toFloat() / totalSeg
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.width(120.dp).height(4.dp).clip(RoundedCornerShape(2.dp)), color = Primary, trackColor = Divider)
                    }
                }
            }
        }

        HorizontalDivider(color = Divider, thickness = 8.dp)

        // 故事段落
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().background(Surface).padding(12.dp)) {
            itemsIndexed(segments.take(visibleCount)) { _, seg ->
                val segType = seg["type"] as? String ?: ""
                val content = seg["content"] as? String ?: ""
                val typeLabel = when (segType) { "prep" -> "准备阶段"; "ending" -> "结局"; else -> "第${segments.indexOf(seg) + 1}段" }
                Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Text(typeLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Primary)
                    Spacer(Modifier.height(4.dp))
                    Text(content, fontSize = 13.sp, color = TextPrimary, lineHeight = 22.sp)
                }
            }
        }

        // 结算按钮
        if (done) {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(16.dp).height(44.dp), shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                Text("完成", fontWeight = FontWeight.SemiBold)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                    Text("返回，稍后查看", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text("离开页面不会中断派遣，可在派遣页继续查看进度。", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showCancel = true }, modifier = Modifier.fillMaxWidth().height(40.dp), shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.Cancel, null, tint = ErrorRed, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("中断派遣", color = ErrorRed, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    }

    if (showCancel) {
        AlertDialog(onDismissRequest = { showCancel = false }, title = { Text("确认中断", color = TextPrimary) },
            text = { Text("确定要中断派遣吗？中途返回可能一无所获，已消耗的预算不会退还。", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { viewModel.cancelDispatch(dispatchId); showCancel = false; onBack() }) { Text("确认中断", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { showCancel = false }) { Text("继续派遣", color = Primary) } })
    }
}
