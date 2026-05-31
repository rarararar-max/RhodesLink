package com.example.rhodesterminal.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rhodesterminal.ui.theme.*
import com.example.rhodesterminal.shared.settings.SettingsRepository
import com.example.rhodesterminal.viewmodel.MainViewModel
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class TokenCategory(val key: String, val name: String, val tokens: Int, val color: Color)

@Composable
fun TokenStatsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings: SettingsRepository = koinInject()
    val bjSdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai") } }
    val cal = remember { Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai")) }

    val tabs = listOf("今日", "本周", "全部")
    var tabIndex by remember { mutableIntStateOf(0) }

    // 所有分类配置（key 与 trackTokens 中的 category 一致）
    val allCategories = remember {
        listOf(
            TokenCategory("private", "私聊", 0, Primary),
            TokenCategory("group", "群聊", 0, AccentOrange),
            TokenCategory("moment", "动态生成", 0, AccentGreen),
            TokenCategory("diary", "日记生成", 0, AccentPurple),
            TokenCategory("dispatch", "派遣日志", 0, ErrorRed),
            TokenCategory("memory", "记忆系统", 0, AccentBlue)
        )
    }

    // 读取今日数据
    val todayStr = remember { bjSdf.format(Date()) }
    val todayData = remember {
        allCategories.map { cat ->
            cat.copy(tokens = settings.getDailyTokenCount(cat.key, todayStr))
        }
    }

    // 读取本周数据（周一~今天）
    cal.time = Date()
    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    val weekStart = bjSdf.format(cal.time)
    val weekData = remember {
        allCategories.map { cat ->
            var sum = 0
            val c = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
            c.time = bjSdf.parse(weekStart)!!
            val today = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
            while (c <= today) {
                val d = bjSdf.format(c.time)
                sum += settings.getDailyTokenCount(cat.key, d)
                c.add(Calendar.DAY_OF_MONTH, 1)
            }
            cat.copy(tokens = sum)
        }
    }

    // 读取全部数据
    val allData = remember {
        allCategories.map { cat ->
            cat.copy(tokens = settings.getTokenCount(cat.key))
        }
    }

    // 周趋势数据（过去8周）
    val weeklyLabels = remember { mutableListOf<String>() }
    val weeklyTotals = remember { mutableListOf<Int>() }
    remember {
        val c = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        // 定位到本周一
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        for (w in 0 until 8) {
            val weekEnd = c.time.clone() as Date
            c.add(Calendar.DAY_OF_MONTH, -6)
            val weekStart2 = c.time.clone() as Date
            var sum = 0
            val scan = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
            scan.time = weekStart2
            while (scan.time <= weekEnd) {
                val d = bjSdf.format(scan.time)
                for (cat in allCategories) {
                    sum += settings.getDailyTokenCount(cat.key, d)
                }
                scan.add(Calendar.DAY_OF_MONTH, 1)
            }
            weeklyLabels.add(0, "${w + 1}周前")
            weeklyTotals.add(0, sum)
            c.add(Calendar.DAY_OF_MONTH, -1)
            c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        }
        // 最后一条改成"本周"
        if (weeklyLabels.isNotEmpty()) weeklyLabels[weeklyLabels.size - 1] = "本周"
    }

    val currentData = when (tabIndex) {
        0 -> todayData; 1 -> weekData; else -> allData
    }
    val total = currentData.sumOf { it.tokens }

    Column(modifier = modifier.fillMaxSize().background(BG)) {
        Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Icon(Icons.Default.BarChart, null, tint = Primary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("消费统计", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        HorizontalDivider(color = Divider)

        TabRow(selectedTabIndex = tabIndex, containerColor = Surface, contentColor = Primary) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tabIndex == i, onClick = { tabIndex = i }, text = { Text(title, fontWeight = if (tabIndex == i) FontWeight.SemiBold else FontWeight.Normal) })
            }
        }

        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            // 总消耗
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
                Column {
                    Text(if (tabIndex == 0) "今日Token消耗" else if (tabIndex == 1) "本周Token消耗" else "历史总Token消耗", fontSize = 13.sp, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(formatTokens(total), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Primary)
                }
            }
            Spacer(Modifier.height(12.dp))

            // 折线图（全部和周视图显示，今日不显示）
            if (tabIndex != 0 && weeklyTotals.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
                    Column {
                        Text("每周消费趋势", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Spacer(Modifier.height(12.dp))
                        val lineColor = Primary
                        val gridColor = Divider
                        val maxVal = weeklyTotals.max().coerceAtLeast(1).toFloat()
                        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                            val w = size.width / weeklyTotals.size
                            val chartH = size.height - 40f
                            // 网格线
                            for (i in 0..4) {
                                val y = size.height - 20 - chartH * i / 4f
                                drawLine(gridColor.copy(alpha = 0.3f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                            }
                            // 折线
                            if (weeklyTotals.size > 1) {
                                val path = Path()
                                weeklyTotals.forEachIndexed { i, v ->
                                    val x = i * w + w / 2
                                    val y = size.height - 20 - (v / maxVal) * chartH
                                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(path, lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                            // 数据点 + 数值
                            weeklyTotals.forEachIndexed { i, v ->
                                val x = i * w + w / 2
                                val y = size.height - 20 - (v / maxVal) * chartH
                                drawCircle(lineColor, 5f, Offset(x, y))
                                drawContext.canvas.nativeCanvas.drawText(formatTokens(v), x - 10f, y - 10f,
                                    android.graphics.Paint().apply { color = TextPrimary.hashCode(); textSize = 22f; textAlign = android.graphics.Paint.Align.CENTER })
                            }
                        }
                        // 周标签
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            weeklyLabels.forEach { label ->
                                Text(label, fontSize = 10.sp, color = TextSecondary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // 分类柱状图
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
                Column {
                    Text("分类消耗", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    val maxVal = currentData.maxOf { it.tokens }.toFloat().coerceAtLeast(1f)
                    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                        val w = size.width / currentData.size
                        currentData.forEachIndexed { i, cat ->
                            val h = (cat.tokens / maxVal) * (size.height - 40)
                            val x = i * w + 8
                            drawRect(cat.color, Offset(x, size.height - h - 20), Size(w - 16, h.coerceAtLeast(0f)))
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        currentData.forEach { cat ->
                            Text(cat.name, fontSize = 9.sp, color = TextSecondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 明细
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
                Column {
                    Text("明细", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    currentData.forEach { cat ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(cat.color))
                            Spacer(Modifier.width(8.dp))
                            Text(cat.name, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                            Text(formatTokens(cat.tokens), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(" (${if (total > 0) (cat.tokens * 100f / total).toInt() else 0}%)", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatTokens(t: Int): String = when {
    t >= 1_000_000 -> "${"%.1f".format(t / 1_000_000f)}M"
    t >= 1_000 -> "${"%.1f".format(t / 1000f)}K"
    else -> "$t"
}
