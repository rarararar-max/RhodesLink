package com.example.rhodesterminal.ui.impressions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rhodesterminal.data.db.entity.MemoryEntity
import com.example.rhodesterminal.data.db.entity.OperatorEntity
import com.example.rhodesterminal.ui.theme.*
import com.example.rhodesterminal.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImpressionsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOperatorClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val operators by viewModel.operators.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var impressions by remember { mutableStateOf<List<MemoryEntity>>(emptyList()) }
    var showClearDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        impressions = viewModel.getAllImpressions()
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空所有印象") },
            text = { Text("确定删除所有干员对你的印象数据？此操作不可撤销。") },
            confirmButton = { TextButton(onClick = { scope.launch { viewModel.deleteAllImpressions(); impressions = emptyList() }; showClearDialog = false }) { Text("确认清空", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("取消", color = TextSecondary) } }
        )
    }

    Column(modifier = modifier.fillMaxSize().background(BG)) {
        Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Spacer(modifier = Modifier.width(4.dp))
            Text("大家的印象", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showClearDialog = true }) { Text("清空", color = ErrorRed, fontSize = 14.sp) }
        }
        HorizontalDivider(color = Divider)

        Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (tab == 0) PrimaryContainer else Color.Transparent).clickable { tab = 0 }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text("标签云", fontSize = 13.sp, fontWeight = if (tab == 0) FontWeight.SemiBold else FontWeight.Normal, color = if (tab == 0) Primary else TextSecondary)
            }
            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (tab == 1) PrimaryContainer else Color.Transparent).clickable { tab = 1 }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text("详细印象", fontSize = 13.sp, fontWeight = if (tab == 1) FontWeight.SemiBold else FontWeight.Normal, color = if (tab == 1) Primary else TextSecondary)
            }
        }
        HorizontalDivider(color = Divider)

        if (tab == 0) {
            TagCloudPage(impressions)
        } else {
            DetailImpressionsPage(impressions, operators, onOperatorClick)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagCloudPage(impressions: List<MemoryEntity>) {
    val rawTags = impressions.flatMap { mem ->
        mem.keywords.split(",").filter { it.isNotBlank() }.map { it.trim() }
    }
    val tagFreq = rawTags.groupingBy { it }.eachCount()
    val maxFreq = tagFreq.values.maxOrNull() ?: 1
    val tags = tagFreq.entries.sortedByDescending { it.value }

    Column(modifier = Modifier.fillMaxSize().background(BG).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("AI眼中的你", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text("来自所有干员的长期印象关键词", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 16.dp))
        if (tags.isEmpty()) {
            Text("暂无印象数据", color = TextTertiary)
        } else {
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { (tag, count) ->
                    val size = (14 + (count.toFloat() / maxFreq) * 10).coerceIn(14f, 24f)
                    val color = Primary
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text("$tag ($count)", fontSize = size.sp, fontWeight = FontWeight.Medium, color = color)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailImpressionsPage(impressions: List<MemoryEntity>, operators: List<OperatorEntity>, onOperatorClick: (String) -> Unit = {}) {
    if (impressions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无印象数据", fontSize = 16.sp, color = TextTertiary)
        }
        return
    }
    LazyColumn {
        items(impressions) { entry ->
            val op = operators.find { it.id == entry.operatorId }
            val displayName = op?.name ?: entry.operatorId
            Column(modifier = Modifier.fillMaxWidth().background(Surface).padding(16.dp)) {
                Row(modifier = Modifier.clickable { onOperatorClick(entry.operatorId) }, verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
                        Text(displayName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("更新于 ${SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai") }.format(Date(entry.createdAt))}", fontSize = 11.sp, color = TextTertiary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(entry.content, fontSize = 14.sp, color = TextPrimary, lineHeight = 22.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.keywords.split(",").filter { it.isNotBlank() }.forEach { kw ->
                        Text(kw.trim(), fontSize = 11.sp, color = Primary, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Primary.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            HorizontalDivider(color = Divider)
        }
    }
}
