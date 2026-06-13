package com.rhodes.privatechat.ui.stats

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
import androidx.compose.foundation.layout.systemBarsPadding
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
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.MainViewModel
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class TokenCategory(val key: String, val name: String, val tokens: Int, val color: Color, val inputTokens: Int = 0, val outputTokens: Int = 0)

@Composable
fun TokenStatsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings: SettingsRepository = koinInject()
    val bjSdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai") } }

    val tabs = listOf("今日", "本周", "全部")
    var tabIndex by remember { mutableIntStateOf(0) }

    // 所有分类配置（key 与 trackTokens 中的 category 一致）
    val allCategories = remember {
        listOf(
            TokenCategory("private", "私聊", 0, Primary),
            TokenCategory("private_analysis", "私聊分析", 0, Color(0xFF7C4DFF)),
            TokenCategory("group", "群聊", 0, AccentOrange),
            TokenCategory("moment", "动态生成", 0, AccentGreen),
            TokenCategory("diary", "日记生成", 0, AccentPurple),
            TokenCategory("dispatch", "派遣日志", 0, ErrorRed),
            TokenCategory("memory", "记忆系统", 0, AccentBlue)
        )
    }

    // 按 tab 切换刷新数据
    val todayStr = remember { bjSdf.format(Date()) }
    fun sumDaily(cat: TokenCategory, settings: SettingsRepository, start: Calendar, end: Calendar, sdf: SimpleDateFormat): TokenCategory {
        var total = 0; var input = 0; var output = 0
        val scan = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        scan.time = start.time
        while (scan <= end) {
            val d = sdf.format(scan.time)
            total += settings.getDailyTokenCount(cat.key, d)
            input += settings.getDailyInputTokenCount(cat.key, d)
            output += settings.getDailyOutputTokenCount(cat.key, d)
            scan.add(Calendar.DAY_OF_MONTH, 1)
        }
        return cat.copy(tokens = total, inputTokens = input, outputTokens = output)
    }

    val todayData = remember(tabIndex) {
        allCategories.map { cat ->
            cat.copy(
                tokens = settings.getDailyTokenCount(cat.key, todayStr),
                inputTokens = settings.getDailyInputTokenCount(cat.key, todayStr),
                outputTokens = settings.getDailyOutputTokenCount(cat.key, todayStr)
            )
        }
    }

    // 读取本周数据（周一~今天），周日时回退到上周一
    val weekData = remember(tabIndex) {
        val c = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        c.time = Date()
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        if (c.time.after(Date())) c.add(Calendar.WEEK_OF_YEAR, -1)
        val ws = bjSdf.format(c.time)
        val weekStart = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        weekStart.time = bjSdf.parse(ws)!!
        val today = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        allCategories.map { cat -> sumDaily(cat, settings, weekStart, today, bjSdf) }
    }

    // 读取全部数据
    val allData = remember(tabIndex) {
        allCategories.map { cat ->
            cat.copy(
                tokens = settings.getTokenCount(cat.key),
                inputTokens = settings.getInputTokenCount(cat.key),
                outputTokens = settings.getOutputTokenCount(cat.key)
            )
        }
    }

    // 周趋势数据（过去8周）
    val weeklyLabels = remember(tabIndex) { mutableListOf<String>() }
    val weeklyTotals = remember(tabIndex) { mutableListOf<Int>() }
    remember(tabIndex) {
        weeklyLabels.clear()
        weeklyTotals.clear()
        val c = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        if (c.time.after(Date())) c.add(Calendar.WEEK_OF_YEAR, -1)
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
            if (c.time.after(Date())) c.add(Calendar.WEEK_OF_YEAR, -1)
        }
        if (weeklyLabels.isNotEmpty()) weeklyLabels[weeklyLabels.size - 1] = "本周"
    }

    val currentData = when (tabIndex) {
        0 -> todayData; 1 -> weekData; else -> allData
    }
    val total = currentData.sumOf { it.tokens }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
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
                    Spacer(Modifier.height(4.dp))
                    val totalIn = currentData.sumOf { it.inputTokens }
                    val totalOut = currentData.sumOf { it.outputTokens }
                    Text("输入 ${formatTokens(totalIn)}  +  输出 ${formatTokens(totalOut)}", fontSize = 13.sp, color = TextSecondary)
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
                        Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                            val count = weeklyLabels.size
                            val w = size.width / count
                            weeklyLabels.forEachIndexed { i, label ->
                                drawContext.canvas.nativeCanvas.drawText(label, i * w + w / 2, 12f,
                                    android.graphics.Paint().apply { color = TextSecondary.hashCode(); textSize = 28f; textAlign = android.graphics.Paint.Align.CENTER })
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
                    // 分类标签
                    Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                        val w = size.width / currentData.size
                        currentData.forEachIndexed { i, cat ->
                            drawContext.canvas.nativeCanvas.drawText(cat.name, i * w + w / 2, 12f,
                                android.graphics.Paint().apply { color = TextSecondary.hashCode(); textSize = 26f; textAlign = android.graphics.Paint.Align.CENTER })
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 明细
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
                Column {
                    Text("明细", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("分类", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                        Text("输入", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(50.dp))
                        Text("输出", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(50.dp))
                        Text("总计", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(50.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    currentData.forEach { cat ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(cat.color))
                            Spacer(Modifier.width(8.dp))
                            Text(cat.name, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                            Text(formatTokens(cat.inputTokens), fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(50.dp))
                            Text(formatTokens(cat.outputTokens), fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(50.dp))
                            Text(formatTokens(cat.tokens), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.width(50.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    }
}

private fun formatTokens(t: Int): String = when {
    t >= 1_000_000 -> "${"%.1f".format(t / 1_000_000f)}M"
    t >= 1_000 -> "${"%.1f".format(t / 1000f)}K"
    else -> "$t"
}
