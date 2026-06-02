package com.rhodes.privatechat.ui.dispatch

import android.util.Log
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

private const val TAG = "Dispatch"

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
        Log.i(TAG, "[ProgressScreen] 开始加载 dispatchId=$dispatchId")
        // 轮询等待记录出现（AI 生成中 status="generating"）
        var rec = viewModel.repository.getDispatch(dispatchId)
        while (rec == null) { delay(500); rec = viewModel.repository.getDispatch(dispatchId) }
        val r = rec!!
        Log.d(TAG, "[ProgressScreen] 记录已加载 status=${r.status} task=${r.taskType} segs=${r.totalSegments} logLen=${r.logChain.length}")
        taskType = r.taskType
        budget = r.budget
        interval = r.segmentInterval
        totalSeg = r.totalSegments
        loaded = true
        // 等待 AI 生成完成（status 变为 active 或 cancelled）
        if (r.status == "generating") {
            Log.d(TAG, "[ProgressScreen] 等待AI生成完成...")
            while (true) {
                delay(1000)
                val updated = viewModel.repository.getDispatch(dispatchId) ?: break
                if (updated.status != "generating") { rec = updated; break }
            }
            Log.i(TAG, "[ProgressScreen] AI生成完成 status=${rec?.status}")
        }
        val finalRec = rec!!
        if (finalRec.status == "cancelled" || finalRec.status == "finished") {
            Log.d(TAG, "[ProgressScreen] 已结束 status=${finalRec.status}，直接显示")
            done = true; visibleCount = totalSeg
            netProfit = finalRec.netProfit; items = finalRec.items
            return@LaunchedEffect
        }
        startTime = finalRec.startTime
        interval = finalRec.segmentInterval
        totalSeg = finalRec.totalSegments
        // 解析 segments
        try {
            val arr = Json.parseToJsonElement(finalRec.logChain) as JsonArray
            if (arr.isEmpty()) { errorMsg = "故事数据异常"; Log.e(TAG, "[ProgressScreen] logChain为空数组"); return@LaunchedEffect }
            val list = mutableListOf<Map<String, Any?>>()
            for (el in arr) {
                val obj = el.jsonObject
                val segType = obj["type"]?.jsonPrimitive?.content ?: ""
                val content = obj["content"]?.jsonPrimitive?.content ?: ""
                list.add(mapOf("type" to segType, "content" to content))
            }
            segments = list
            totalSeg = list.size
            Log.i(TAG, "[ProgressScreen] JSON解析成功 segments=${list.size}")
        } catch (e: Exception) {
            Log.w(TAG, "[ProgressScreen] JSON解析失败，尝试文本分割: ${e.message}")
            val textSegments = finalRec.logChain.split("\n\n").filter { it.isNotBlank() }.map { it.trim() }
            if (textSegments.isEmpty()) { errorMsg = "故事数据异常"; Log.e(TAG, "[ProgressScreen] 文本分割也为空"); return@LaunchedEffect }
            segments = textSegments.map { mapOf("type" to "progress", "content" to it) }.toMutableList()
            totalSeg = segments.size
            interval = if (interval > 0) interval else 30_000L
            Log.i(TAG, "[ProgressScreen] 文本分割成功 segments=${segments.size}")
        }
        // 定时检查已解锁段数
        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val count = (elapsed / (interval.coerceAtLeast(1L))).toInt().coerceIn(1, totalSeg)
            if (count > visibleCount) {
                visibleCount = count
                Log.d(TAG, "[ProgressScreen] 解锁新段落 visible=$count/$totalSeg")
            }
            if (count >= totalSeg) {
                Log.i(TAG, "[ProgressScreen] 所有段落已解锁，执行finishDispatch")
                viewModel.finishDispatch(dispatchId)
                done = true
                val ended = viewModel.repository.getDispatch(dispatchId)
                netProfit = ended?.netProfit ?: 0
                items = ended?.items ?: ""
                break
            }
            delay(1000)
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(errorMsg, color = TextTertiary) }
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
            Button(onClick = { showCancel = true }, modifier = Modifier.fillMaxWidth().padding(16.dp).height(44.dp), shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)) {
                Icon(Icons.Default.Cancel, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("中断派遣", fontWeight = FontWeight.SemiBold)
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
