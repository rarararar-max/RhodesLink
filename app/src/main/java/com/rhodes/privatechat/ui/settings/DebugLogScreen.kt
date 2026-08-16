package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.util.DebugLogger
import com.rhodes.privatechat.util.DebugLogger.LogEntry
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.rhodes.privatechat.util.ProblemChecker
import org.koin.compose.koinInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.os.SystemClock
import java.util.LinkedHashMap

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(onBack: () -> Unit) {
    val settings: SettingsRepository = koinInject()
    val repository: ChatRepository = koinInject()
    val appState: AppStateHolder = koinInject()
    val sharedUtils: SharedUtils = koinInject()
    var logs by remember { mutableStateOf(DebugLogger.getLogs()) }
    var selectedGroup by remember { mutableStateOf<LogGroup?>(null) }
    var loggingEnabled by remember { mutableStateOf(settings.debugLogEnabled) }
    var payloadsEnabled by remember { mutableStateOf(settings.debugLogPayloadsEnabled) }
    var onlyAi by remember { mutableStateOf(false) }
    var onlyErrors by remember { mutableStateOf(false) }
    var onlySpecial by remember { mutableStateOf(false) }
    var onlyRounds by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var problemReport by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            logs = DebugLogger.getLogs()
            delay(1_000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("调试日志", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("可按对话轮次查看私聊/群聊的完整处理结果", fontSize = 11.sp, color = TextTertiary)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) } },
                actions = {
                    TextButton(enabled = !checking, onClick = {
                        checking = true
                        problemReport = "RHODES_PROBLEM_CHECK\nreportVersion=7\nstatus=running\nmessage=检查已启动，界面看门狗不会等待后台探针"
                        scope.launch {
                            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                            val checkId = ProblemChecker.start(
                                context = context,
                                repository = repository,
                                sharedUtils = sharedUtils,
                                appState = appState,
                                versionName = packageInfo.versionName ?: "unknown",
                                versionCode = packageInfo.longVersionCode.toInt()
                            )
                            val deadline = SystemClock.elapsedRealtime() + 120_000L
                            while (checking && SystemClock.elapsedRealtime() < deadline) {
                                problemReport = ProblemChecker.report(
                                    packageInfo.versionName ?: "unknown",
                                    packageInfo.longVersionCode.toInt()
                                ).report
                                val snapshot = ProblemChecker.progress()
                                if (snapshot.checkId == checkId && snapshot.finishedAt != 0L) break
                                delay(250L)
                            }
                            ProblemChecker.abandon(checkId)
                            problemReport = ProblemChecker.report(
                                packageInfo.versionName ?: "unknown",
                                packageInfo.longVersionCode.toInt()
                            ).report
                            checking = false
                            logs = DebugLogger.getLogs()
                        }
                    }) { Text(if (checking) "检测中" else "问题检查", color = Primary, fontSize = 12.sp) }
                    if (problemReport.isNotBlank()) {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(problemReport))
                            Toast.makeText(context, "检查报告已复制", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "复制检查报告", tint = Primary) }
                    }
                    Switch(checked = loggingEnabled, onCheckedChange = {
                        loggingEnabled = it
                        settings.debugLogEnabled = it
                        DebugLogger.enabled = it
                        DebugLogger.allowSensitiveTrace = it && payloadsEnabled
                        if (it) DebugLogger.log("Debug/Settings", "调试日志已手动开启 | 完整模型内容=${if (payloadsEnabled) "开启" else "关闭"}")
                    })
                    IconButton(onClick = { clipboardManager.setText(AnnotatedString(DebugLogger.getLogText())) }) {
                        Icon(Icons.Default.ContentCopy, "复制全部日志", tint = TextPrimary)
                    }
                    IconButton(onClick = { DebugLogger.clear(); logs = emptyList() }) {
                        Icon(Icons.Default.DeleteSweep, "清空", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        },
        containerColor = BG
    ) { padding ->
        val filteredLogs = logs.asReversed().filter { entry ->
                (!onlyAi || entry.tag.startsWith("AI/")) &&
                (!onlyErrors || entry.tag.contains("错误") || entry.tag.contains("ERROR", true) || entry.tag.endsWith("/失败") || entry.message.contains("失败")) &&
                (!onlyRounds || entry.tag.startsWith("Round/")) &&
                (!onlySpecial || entry.tag.startsWith("Diagnostic/Special/"))
        }
        val groupedLogs = buildLogGroups(filteredLogs)
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("状态：${if (loggingEnabled) "正在记录" else "常规日志关闭，关键诊断仍保留"}  |  当前缓存 ${logs.size}/500 条", fontSize = 12.sp, color = if (loggingEnabled) AccentGreen else AccentOrange)
                Text("按模型调用分组显示：请求、原始返回、解析结果和最终保存内容。点击卡片查看完整内容。", fontSize = 11.sp, color = TextSecondary)
                if (problemReport.isNotBlank()) {
                    Text("最近一次问题检查报告", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Primary)
                    SelectionContainer {
                        Text(problemReport, fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace, modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("记录完整模型输入与输出", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                    Switch(checked = payloadsEnabled, enabled = loggingEnabled, onCheckedChange = {
                        payloadsEnabled = it
                        settings.debugLogPayloadsEnabled = it
                        DebugLogger.allowSensitiveTrace = loggingEnabled && it
                        DebugLogger.log("Debug/Settings", "完整模型输入与输出=${if (it) "开启" else "关闭"}")
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = onlyAi, onClick = { onlyAi = !onlyAi }, label = { Text("仅模型调用") })
                    FilterChip(selected = onlyRounds, onClick = { onlyRounds = !onlyRounds }, label = { Text("仅对话轮次") })
                    FilterChip(selected = onlyErrors, onClick = { onlyErrors = !onlyErrors }, label = { Text("仅失败/错误") })
                    FilterChip(selected = onlySpecial, onClick = { onlySpecial = !onlySpecial }, label = { Text("特殊问题") })
                }
            }
        if (groupedLogs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(if (loggingEnabled) "暂无符合筛选条件的日志\n发送一条消息后可查看完整处理链路" else "暂无关键诊断记录\n复现问题后返回此页复制日志", fontSize = 14.sp, color = TextTertiary)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(groupedLogs, key = { it.key }) { group ->
                    val entry = group.primary
                    val tagColor = if (group.isModelCall) AccentOrange else TextSecondary
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Surface, RoundedCornerShape(4.dp))
                             .combinedClickable(
                                 onClick = { selectedGroup = group },
                                 onLongClick = {
                                     clipboardManager.setText(AnnotatedString(formatGroup(group)))
                                     Toast.makeText(context, "已复制这组完整日志", Toast.LENGTH_SHORT).show()
                                 }
                             )
                             .padding(horizontal = 8.dp, vertical = 4.dp)
                     ) {
                         Row(verticalAlignment = Alignment.CenterVertically) {
                             Text(group.time, fontSize = 10.sp, color = TextTertiary, fontFamily = FontFamily.Monospace)
                             Spacer(Modifier.width(6.dp))
                             Text(group.title, fontSize = 11.sp, color = tagColor, fontWeight = FontWeight.Bold)
                             if (group.isModelCall) {
                                 Spacer(Modifier.width(6.dp))
                                 Text(group.status, fontSize = 10.sp, color = group.statusColor, fontWeight = FontWeight.Bold)
                             }
                         }
                         Text(group.description, fontSize = 11.sp, color = TextSecondary)
                         if (group.isModelCall) {
                             Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                 StageChip("请求", group.request != null)
                                 StageChip("返回", group.response != null)
                                 StageChip("保存", group.saved != null)
                                 if (group.related.isNotEmpty()) StageChip("流程 ${group.related.size}", true)
                             }
                         } else {
                             Text(logPreview(entry.message), fontSize = 12.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                         }
                         Text("点击查看完整内容，长按复制整组", fontSize = 10.sp, color = TextTertiary)
                     }
                 }
            }
        }
        }
    }

    selectedGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { selectedGroup = null },
            title = { Text(group.title) },
            text = {
                SelectionContainer {
                    Text(
                        formatGroup(group),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                        fontSize = 12.sp,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                 TextButton(onClick = {
                     clipboardManager.setText(AnnotatedString(formatGroup(group)))
                     Toast.makeText(context, "已复制这组完整日志", Toast.LENGTH_SHORT).show()
                 }) { Text("复制本条") }
             },
             dismissButton = { TextButton(onClick = { selectedGroup = null }) { Text("关闭") } }
         )
    }
}

private fun formatEntry(entry: LogEntry): String =
    "[${entry.formattedTime}] ${logTitle(entry.tag)}\n${logDescription(entry.tag)}\n\n${entry.message}"

private fun logPreview(message: String): String =
    if (message.length <= 800) message else "${message.take(800)}\n… 已省略列表预览，点击查看完整内容"

private fun logDescription(tag: String): String = when {
    tag.startsWith("Round/") -> "对话轮次：按顺序记录本轮用户消息、模型请求、返回解析、内容重试、格式补全、保存与本轮总览。完整内容仅在“记录完整模型输入与输出”开启时可用。"
    tag.endsWith("/请求") -> "模型请求摘要：厂商、模型、参数和输入规模。完整输入请查看同名“传给大模型”记录。"
    tag.endsWith("/响应") -> "模型响应摘要：耗时、Token 使用量和输出规模。完整输出请查看同名“大模型返回”记录。"
    tag.endsWith("/错误") -> "模型调用失败：异常类型、耗时和服务端/网络原因。"
    tag.startsWith("ChatEvent/") -> "聊天流程事件：请求、模型返回、解析、重试、格式修复和最终写入结果。"
    tag.startsWith("AI/→GroupChat") -> "群聊最终请求载荷：已完成上下文裁剪后，实际发给模型的全部消息。"
    tag.startsWith("AI/←GroupChat") -> "群聊原始模型返回：未经解析、补全或保存处理的模型输出。"
    tag.startsWith("AI/✓群聊") -> "群聊最终保存内容：解析、筛选和补全后实际写入本地数据库的内容。"
    tag.endsWith("/思维链状态") -> "DeepSeek 思考模式诊断：请求是否明确关闭，以及响应是否仍返回 reasoning_content。"
    tag.endsWith("/思维链") -> "DeepSeek 返回的完整 reasoning_content。该内容敏感，仅在“完整模型内容”已开启时记录。"
    tag.startsWith("AI/→Chat") || tag.startsWith("AI/→VisionChat") -> "私聊最终请求载荷：已完成上下文裁剪后，实际发给模型的全部消息。"
    tag.startsWith("AI/←Chat") || tag.startsWith("AI/←VisionChat") -> "私聊原始模型返回：未经解析、补全或保存处理的模型输出。"
    tag.startsWith("AI/✓私聊") -> "私聊最终保存内容：解析、清理和补全后实际写入本地数据库的内容。"
    tag.startsWith("AI/GroupContentRetry") -> "群聊内容重试：首次模型输出没有可用群聊内容时记录的原始输出。"
    tag == "AI/GroupFormatRepair" -> "群聊格式修复：模型输出格式不规范时的原文与修复结果。"
    tag == "GroupChat/InvalidResponse" -> "群聊回复无效：模型返回内容无法解析为当前群成员可展示的消息。"
    tag == "GroupChat/Error" -> "群聊请求失败：网络、API 配置、额度或服务端异常的具体原因。"
    tag == "GroupChat/Decision" -> "群聊处理决策：本轮解析、格式修复、重试和最终展示的结果。"
    tag == "GroupChat/Token" -> "群聊上下文长度：本轮发送给模型的 token 估算及截断情况。"
    tag.startsWith("Memory/") -> "记忆处理：本轮聊天使用、写入或清理记忆的情况。"
    tag.startsWith("GroupChat/DB") -> "群聊本地存储：消息已写入本机数据库。"
    tag.startsWith("GroupChat/Auto") -> "群聊自动发言：定时器、等待时间和自动回复任务状态。"
    tag.startsWith("GroupChat") -> "群聊处理：群成员、会话或回复流程状态。"
    tag.startsWith("AI/→") -> "最终请求载荷：实际发给大模型的完整消息列表。"
    tag.startsWith("AI/←") -> "原始模型返回：未经应用解析或保存处理的模型输出。"
    tag.startsWith("AI/") -> "大模型处理过程：格式修复、内容重试或解析诊断信息。"
    else -> "应用调试信息：${tag}。"
}

private fun logTitle(tag: String): String = when {
    tag.startsWith("Round/") -> roundTitle(tag)
    tag.startsWith("ChatEvent/") -> "聊天流程"
    tag.startsWith("AI/→GroupChat") -> "群聊 · 最终请求载荷"
    tag.startsWith("AI/←GroupChat") -> "群聊 · 原始模型返回"
    tag.startsWith("AI/✓群聊") -> "群聊 · 最终保存内容"
    tag.endsWith("/思维链状态") -> "DeepSeek 思考模式状态"
    tag.endsWith("/思维链") -> "DeepSeek 完整思维链"
    tag.startsWith("AI/→Chat") || tag.startsWith("AI/→VisionChat") -> "私聊 · 最终请求载荷"
    tag.startsWith("AI/←Chat") || tag.startsWith("AI/←VisionChat") -> "私聊 · 原始模型返回"
    tag.startsWith("AI/✓私聊") -> "私聊 · 最终保存内容"
    tag.startsWith("AI/GroupContentRetry") -> "群聊内容重试"
    tag == "AI/GroupFormatRepair" -> "群聊格式修复"
    tag.startsWith("AI/") -> "大模型处理"
    tag.startsWith("Memory/") -> "记忆处理"
    tag.startsWith("GroupChat/") -> "群聊处理"
    tag.startsWith("Chat/") -> "私聊处理"
    tag.startsWith("Moment") -> "动态处理"
    tag.startsWith("Diary") -> "日记处理"
    else -> tag
}

private data class LogGroup(
    val key: String,
    val primary: LogEntry,
    val entries: List<LogEntry>,
    val request: LogEntry?,
    val response: LogEntry?,
    val saved: LogEntry?,
    val related: List<LogEntry>,
    val isModelCall: Boolean,
) {
    val time: String get() = primary.formattedTime
    val isRound: Boolean get() = primary.tag.startsWith("Round/")
    val title: String get() = when {
        isModelCall -> "${modelSurface(primary.tag)} · ${modelCallName(primary.tag)}"
        isRound -> roundTitle(primary.tag)
        else -> logTitle(primary.tag)
    }
    val description: String get() = when {
        isModelCall -> "一次模型调用的完整链路"
        isRound -> "本轮处理链路：输入、请求、解析、重试和保存"
        else -> logDescription(primary.tag)
    }
    val status: String get() = when {
        saved != null -> "已保存"
        response != null -> "已返回"
        request != null -> "请求中"
        else -> "流程"
    }
    val statusColor get() = when (status) {
        "已保存" -> AccentGreen
        "已返回" -> AccentBlue
        else -> AccentOrange
    }
}

private fun buildLogGroups(logs: List<LogEntry>): List<LogGroup> {
    val groups = LinkedHashMap<String, MutableList<LogEntry>>()
    logs.forEach { entry ->
        val key = modelGroupKey(entry.tag) ?: roundGroupKey(entry.tag) ?: "entry:${entry.id}"
        groups.getOrPut(key) { mutableListOf() }.add(entry)
    }
    return groups.values.map { entries ->
        val primary = entries.first()
        val request = entries.firstOrNull { isRequestTag(it.tag) }
        val response = entries.firstOrNull { isResponseTag(it.tag) }
        val saved = entries.firstOrNull { isSavedTag(it.tag) }
        LogGroup(
            key = modelGroupKey(primary.tag) ?: roundGroupKey(primary.tag) ?: "entry:${primary.id}",
            primary = primary,
            entries = entries,
            request = request,
            response = response,
            saved = saved,
            related = entries.filterNot { it === request || it === response || it === saved },
            isModelCall = request != null || response != null || saved != null,
        )
    }
}

private fun roundGroupKey(tag: String): String? =
    tag.takeIf { it.startsWith("Round/") }?.substringBeforeLast('/').orEmpty().ifBlank { null }

private fun modelGroupKey(tag: String): String? {
    if (!tag.startsWith("AI/")) return null
    val value = tag.removePrefix("AI/")
    val marker = value.indexOfAny(charArrayOf('→', '←', '✓'))
    if (marker < 0) return null
    return value.substring(marker + 1)
        .substringBeforeLast("/思维链")
        .replace("私聊#", "Chat#")
        .replace("群聊#", "GroupChat#")
        .takeIf { it.isNotBlank() && it.contains('#') }
}

private fun isRequestTag(tag: String) = tag.startsWith("AI/→")
private fun isResponseTag(tag: String) = tag.startsWith("AI/←")
private fun isSavedTag(tag: String) = tag.startsWith("AI/✓")

private fun modelCallName(tag: String): String = modelGroupKey(tag).orEmpty().substringBefore('#').ifBlank { "模型调用" }

private fun modelSurface(tag: String): String = when {
    tag.contains("Group", true) -> "群聊"
    tag.contains("MomentComment", true) -> "动态评论"
    tag.contains("Moment", true) -> "动态"
    tag.contains("Diary", true) -> "日记"
    tag.contains("Memory", true) -> "记忆"
    else -> "私聊"
}

@Composable
private fun StageChip(label: String, present: Boolean) {
    Surface(
        color = if (present) AccentGreen.copy(alpha = 0.14f) else Surface.copy(alpha = 0.55f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            if (present) "✓ $label" else "- $label",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp,
            color = if (present) AccentGreen else TextTertiary
        )
    }
}

private fun formatGroup(group: LogGroup): String = buildString {
    append("[${group.time}] ${group.title}\n${group.description}\n")
    group.entries.sortedBy { entry ->
        when {
            isRequestTag(entry.tag) -> 0
            isResponseTag(entry.tag) -> 1
            isSavedTag(entry.tag) -> 2
            else -> 3
        }
    }.forEach { entry ->
        append("\n--- ${logTitle(entry.tag)} ---\n")
        append(entry.message)
        append('\n')
    }
}

private fun roundTitle(tag: String): String {
    val parts = tag.split('/')
    val surface = parts.getOrNull(2).orEmpty()
    val stage = parts.getOrNull(3).orEmpty()
    return "$surface · $stage"
}

private fun roundStatus(tag: String): String = tag.substringAfterLast('/')
