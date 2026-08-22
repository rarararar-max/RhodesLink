package com.rhodes.privatechat.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.ui.theme.AccentBlue
import com.rhodes.privatechat.ui.theme.AccentGreen
import com.rhodes.privatechat.ui.theme.AccentOrange
import com.rhodes.privatechat.ui.theme.BG
import com.rhodes.privatechat.ui.theme.ErrorRed
import com.rhodes.privatechat.ui.theme.Primary
import com.rhodes.privatechat.ui.theme.Surface as AppSurface
import com.rhodes.privatechat.ui.theme.TextPrimary
import com.rhodes.privatechat.ui.theme.TextSecondary
import com.rhodes.privatechat.ui.theme.TextTertiary
import com.rhodes.privatechat.util.DebugLogger
import com.rhodes.privatechat.util.DebugLogger.DebugOperation
import com.rhodes.privatechat.util.ProblemChecker
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

private enum class OperationFilter(val label: String) {
    ALL("全部"), CHAT("聊天"), CONTENT("动态与日记"), MEMORY("记忆"), SUPPORT("客服"), SPECIAL("异常")
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(onBack: () -> Unit) {
    val settings: SettingsRepository = koinInject()
    val repository: ChatRepository = koinInject()
    val sharedUtils: SharedUtils = koinInject()
    val appState: AppStateHolder = koinInject()
    var operations by remember { mutableStateOf(DebugLogger.getOperations()) }
    var selected by remember { mutableStateOf<DebugOperation?>(null) }
    var loggingEnabled by remember { mutableStateOf(settings.debugLogEnabled) }
    var payloadsEnabled by remember { mutableStateOf(settings.debugLogPayloadsEnabled) }
    var filter by remember { mutableStateOf(OperationFilter.ALL) }
    var resultFilter by remember { mutableStateOf("全部") }
    var problemProgress by remember { mutableStateOf(ProblemChecker.progress()) }
    var showProblemReport by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            operations = DebugLogger.getOperations().asReversed()
            problemProgress = ProblemChecker.progress()
            delay(800)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("调试记录", fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("每次互动只保留一张最终结果卡", fontSize = 11.sp, color = TextTertiary) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) } },
                actions = {
                    Switch(checked = loggingEnabled, onCheckedChange = {
                        loggingEnabled = it
                        settings.debugLogEnabled = it
                        DebugLogger.enabled = it
                        DebugLogger.allowSensitiveTrace = it && payloadsEnabled
                    })
                    IconButton(onClick = { clipboard.setText(AnnotatedString(DebugLogger.getLogText())); Toast.makeText(context, "已复制诊断日志", Toast.LENGTH_SHORT).show() }) { Icon(Icons.Default.ContentCopy, "复制诊断日志", tint = TextPrimary) }
                    IconButton(onClick = { DebugLogger.clear(); operations = emptyList() }) { Icon(Icons.Default.DeleteSweep, "清空", tint = TextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppSurface)
            )
        }, containerColor = BG
    ) { padding ->
        val filtered = operations.filter { operation ->
            (filter == OperationFilter.ALL || operationFilter(operation) == filter) &&
                (resultFilter == "全部" || operation.result == resultFilter)
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxWidth().background(AppSurface).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("状态：${if (loggingEnabled) "正在记录" else "仅保留关键异常"} · ${operations.size} 条最终结果", fontSize = 12.sp, color = if (loggingEnabled) AccentGreen else AccentOrange)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OperationFilter.entries.forEach { item -> FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item.label, fontSize = 11.sp) }) }
                }
                Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("全部", "成功", "部分完成", "失败").forEach { item -> FilterChip(selected = resultFilter == item, onClick = { resultFilter = item }, label = { Text(item, fontSize = 11.sp) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("记录完整模型请求和返回", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                    Switch(checked = payloadsEnabled, enabled = loggingEnabled, onCheckedChange = { payloadsEnabled = it; settings.debugLogPayloadsEnabled = it; DebugLogger.allowSensitiveTrace = loggingEnabled && it })
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    TextButton(onClick = {
                        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        ProblemChecker.start(context, repository, sharedUtils, appState, packageInfo.versionName ?: "未知", packageInfo.versionCode)
                        showProblemReport = true
                    }, enabled = problemProgress.checkId.isBlank() || problemProgress.finishedAt > 0L) { Text("一键自检", color = Primary) }
                    if (problemProgress.checkId.isNotBlank()) {
                        TextButton(onClick = { showProblemReport = true }) { Text(if (problemProgress.finishedAt > 0L) "查看报告" else "检查中…", color = TextSecondary) }
                    }
                    if (problemProgress.checkId.isNotBlank() && problemProgress.finishedAt == 0L) {
                        Text("当前：${problemProgress.currentStage}", fontSize = 10.sp, color = AccentOrange)
                    }
                }
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无最终结果记录\n完成一次聊天、客服、动态或记忆操作后会显示在这里", fontSize = 14.sp, color = TextTertiary) }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered, key = { it.id }) { operation -> OperationCard(operation) { selected = operation } }
                }
            }
        }
    }
    selected?.let { OperationDetails(it, onDismiss = { selected = null }, onCopy = { clipboard.setText(AnnotatedString(formatOperation(it))); Toast.makeText(context, "已复制本次诊断", Toast.LENGTH_SHORT).show() }) }
    if (showProblemReport) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val report = ProblemChecker.report(packageInfo.versionName ?: "未知", packageInfo.versionCode)
        ProblemReportDialog(report.summary, report.report, problemProgress, onDismiss = { showProblemReport = false }, onCopySummary = { clipboard.setText(AnnotatedString(report.summary)); Toast.makeText(context, "已复制自检摘要", Toast.LENGTH_SHORT).show() }, onCopyTechnical = { clipboard.setText(AnnotatedString(report.report)); Toast.makeText(context, "已复制技术报告", Toast.LENGTH_SHORT).show() })
    }
}

@Composable
private fun ProblemReportDialog(summary: String, report: String, progress: com.rhodes.privatechat.util.ProblemCheckProgress, onDismiss: () -> Unit, onCopySummary: () -> Unit, onCopyTechnical: () -> Unit) {
    var showTechnicalDetails by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (progress.finishedAt > 0L) "一键自检报告" else "正在一键自检") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(summary, fontSize = 12.sp, color = TextPrimary) }
                TextButton(onClick = { showTechnicalDetails = !showTechnicalDetails }) { Text(if (showTechnicalDetails) "隐藏技术详情" else "查看技术详情", fontSize = 12.sp, color = Primary) }
                if (showTechnicalDetails) {
                    progress.stages.forEach { (name, stage) ->
                        Text("${stageLabel(name)}：${stage.status.name.lowercase()}${stage.detail.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()}", fontSize = 11.sp, color = if (stage.status.name in setOf("FAILED", "TIMEOUT", "ABANDONED")) ErrorRed else TextPrimary, modifier = Modifier.padding(top = 7.dp))
                    }
                    SelectionContainer { Text(report, fontSize = 10.sp, color = TextTertiary, modifier = Modifier.padding(top = 12.dp)) }
                }
            }
        },
        confirmButton = { Row { TextButton(onClick = onCopySummary) { Text("复制摘要", color = Primary) }; TextButton(onClick = onCopyTechnical) { Text("复制技术报告", color = Primary) } } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

private fun stageLabel(name: String): String = when (name) {
    "vector_diagnostics_probe" -> "向量 SQL、解码与评分"
    "memory_items_stats_probe" -> "Memory V2 数据量与大字段统计"
    "backup_snapshot_timing_probe" -> "备份快照读取与序列化估算"
    "db_native_read_probe" -> "原生 SQLite 基础读取"
    "db_repository_read_probe" -> "应用数据库读取"
    "embedding_compute_probe" -> "本地向量计算"
    "private_message_probe" -> "私聊消息写入与读取"
    "group_message_probe" -> "群聊消息写入与读取"
    "private_ai_probe" -> "私聊结构化模型回复"
    "group_ai_probe" -> "群聊模型回复"
    "private_pipeline_history" -> "私聊会话与历史读取"
    "private_pipeline_context" -> "私聊角色、记忆、知识库与提示词前置条件"
    "private_pipeline_reply_parse" -> "私聊已保存 AI 回复解析与读回"
    "private_pipeline_last_state" -> "最近真实私聊管线状态"
    "group_pipeline_roster_history" -> "群聊成员与历史读取"
    "group_pipeline_context" -> "群聊成员、记忆、知识库与提示词前置条件"
    "group_pipeline_reply_parse" -> "群聊已保存 AI 回复解析与读回"
    "vector_embedding_probe" -> "固定诊断文本向量化与本地检索"
    "support_manual_probe" -> "客服说明书与本地检索"
    "support_transcript_probe" -> "客服会话持久化与配置"
    "database_open" -> "数据库打开"
    "database_schema" -> "数据库结构"
    "database_copy_write_test" -> "数据库复制写入"
    "persistent_state_probe" -> "持久化设置、状态与旧提示词"
    "backup_snapshot_probe" -> "完整备份快照构建"
    "backup_file_probe" -> "备份文件写入与回读校验"
    "backup_memories" -> "备份传统记忆读取"
    "backup_anchors" -> "备份记忆锚点读取"
    "session_integrity" -> "会话完整性"
    "contacts_recovery" -> "角色与会话恢复"
    else -> name
}

@Composable
private fun OperationCard(operation: DebugOperation, onClick: () -> Unit) {
    val color = resultColor(operation.result)
    Surface(shape = RoundedCornerShape(10.dp), color = AppSurface, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(operation.formattedTime, fontSize = 11.sp, color = TextTertiary)
                Spacer(Modifier.width(8.dp))
                Text(operationTitle(operation), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                Text(operation.result, fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
            }
            Text(operation.summary.ifBlank { "正在处理" }, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
            Text("耗时 ${formatDuration(operation.durationMs)} · ${operation.steps.size} 个处理步骤 · 点击查看详情", fontSize = 10.sp, color = TextTertiary, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun OperationDetails(operation: DebugOperation, onDismiss: () -> Unit, onCopy: () -> Unit) {
    var tab by remember(operation.id) { mutableIntStateOf(0) }
    val tabs = buildList {
        add("概览"); add("过程")
        addAll(operation.modules.keys)
        if (operation.result == "失败" || operation.result == "部分完成") add("错误")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(operationTitle(operation), fontSize = 18.sp) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
                TabRow(selectedTabIndex = tab) { tabs.forEachIndexed { index, title -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title, fontSize = 12.sp) }) } }
                when (tabs[tab]) {
                    "概览" -> OverviewTab(operation)
                    "过程" -> StepsTab(operation)
                    "错误" -> ErrorTab(operation)
                    else -> ModuleTab(tabs[tab], operation.modules[tabs[tab]].orEmpty(), splitRequest = tabs[tab] == "完整请求")
                }
            }
        },
        confirmButton = { TextButton(onClick = onCopy) { Text("复制本次诊断", color = Primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun ModuleTab(title: String, content: String, splitRequest: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Primary)
        if (content.isBlank()) {
            Text("本次未记录完整内容。请开启“记录完整模型请求和返回”后重新复现。", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
        } else if (splitRequest) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                requestSections(content).forEach { (title, body) -> ModuleSection(title, body) }
            }
        } else {
            SelectionContainer { Text(content, fontSize = 11.sp, color = TextPrimary, modifier = Modifier.padding(top = 8.dp).verticalScroll(rememberScrollState())) }
        }
    }
}

@Composable
private fun ModuleSection(title: String, body: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = AppSurface) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Primary)
            SelectionContainer { Text(body, fontSize = 11.sp, color = TextPrimary, modifier = Modifier.padding(top = 5.dp)) }
        }
    }
}

private fun requestSections(content: String): List<Pair<String, String>> {
    val pattern = Regex("""(?m)^\[(\d+)]\s+(system|user|assistant)\s+\|\s+\d+字\s*$""")
    val matches = pattern.findAll(content).toList()
    if (matches.isEmpty()) return listOf("完整请求" to content)
    return matches.mapIndexed { index, match ->
        val role = match.groupValues[2]
        val body = content.substring(match.range.last + 1, matches.getOrNull(index + 1)?.range?.first ?: content.length).trim()
        requestSectionTitle(role, body) to body
    }
}

private fun requestSectionTitle(role: String, body: String): String = when {
    role == "system" -> "系统规则、角色设定与输出协议"
    body.contains("【用户本轮消息】") -> "当前用户消息"
    body.contains("【本轮输出检查清单】") -> "输出检查清单"
    body.contains("【本轮参考资料】") || body.contains("【本轮背景资料】") -> "运行时资料、连续性状态与上下文"
    role == "assistant" -> "历史角色回复"
    else -> "历史用户消息"
}

@Composable
private fun OverviewTab(operation: DebugOperation) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).verticalScroll(rememberScrollState())) {
        DetailLine("操作", operation.surface)
        DetailLine("对象", operation.target.ifBlank { "未指定" })
        DetailLine("模式", operation.mode.ifBlank { "默认" })
        DetailLine("结果", operation.result, resultColor(operation.result))
        DetailLine("耗时", formatDuration(operation.durationMs))
        DetailLine("概述", operation.summary.ifBlank { "无" })
    }
}

@Composable
private fun StepsTab(operation: DebugOperation) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        operation.steps.forEach { step ->
            Surface(shape = RoundedCornerShape(8.dp), color = AppSurface) {
                Column(Modifier.padding(10.dp)) {
                    Text("${step.label} · ${step.status}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (step.status == "失败") ErrorRed else TextPrimary)
                    if (step.details.isNotBlank()) Text(step.details, fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun ErrorTab(operation: DebugOperation) {
    val failures = operation.steps.filter { it.status == "失败" || it.status.contains("超时") || it.status.contains("降级") }
    SelectionContainer { Text((failures.ifEmpty { operation.steps }).joinToString("\n\n") { "${it.label}：${it.status}\n${it.details}" }, fontSize = 12.sp, color = ErrorRed, modifier = Modifier.padding(top = 12.dp).verticalScroll(rememberScrollState())) }
}

@Composable
private fun DetailLine(label: String, value: String, color: Color = TextPrimary) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) { Text(label, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(70.dp)); Text(value, fontSize = 12.sp, color = color, modifier = Modifier.weight(1f)) }
}

private fun operationFilter(operation: DebugOperation): OperationFilter = when {
    operation.result == "失败" -> OperationFilter.SPECIAL
    operation.surface.contains("客服") -> OperationFilter.SUPPORT
    operation.surface.contains("记忆") || operation.surface.contains("知识库") -> OperationFilter.MEMORY
    operation.surface.contains("动态") || operation.surface.contains("日记") || operation.surface.contains("派遣") -> OperationFilter.CONTENT
    else -> OperationFilter.CHAT
}

private fun operationTitle(operation: DebugOperation): String = buildString {
    append(operation.surface)
    if (operation.target.isNotBlank()) append(" · ").append(operation.target)
}

private fun resultColor(result: String): Color = when (result) { "成功" -> AccentGreen; "失败" -> ErrorRed; "部分完成" -> AccentOrange; else -> AccentBlue }
private fun formatDuration(milliseconds: Long): String = if (milliseconds < 1_000) "${milliseconds}毫秒" else "%.1f 秒".format(milliseconds / 1_000.0)
private fun formatOperation(operation: DebugOperation): String = buildString {
    append("${operation.formattedTime} ${operationTitle(operation)}\n")
    append("结果：${operation.result}\n耗时：${formatDuration(operation.durationMs)}\n概述：${operation.summary}\n")
    operation.steps.forEach { append("\n${it.label} · ${it.status}\n${it.details}\n") }
    operation.modules.forEach { (name, content) -> append("\n--- $name ---\n$content\n") }
}
