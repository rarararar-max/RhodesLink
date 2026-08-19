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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import org.koin.compose.koinInject
import kotlinx.coroutines.delay
import java.util.LinkedHashMap

private enum class LogCategory(val label: String) {
    ALL("全部"), PRIVATE("私聊"), GROUP("群聊"), CONTENT("动态与其他 AI"), MEMORY("记忆与知识库"), SPECIAL("特殊")
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(onBack: () -> Unit) {
    val settings: SettingsRepository = koinInject()
    var logs by remember { mutableStateOf(DebugLogger.getLogs()) }
    var selectedGroup by remember { mutableStateOf<LogGroup?>(null) }
    var loggingEnabled by remember { mutableStateOf(settings.debugLogEnabled) }
    var payloadsEnabled by remember { mutableStateOf(settings.debugLogPayloadsEnabled) }
    var category by remember { mutableStateOf(LogCategory.ALL) }
    var statusFilter by remember { mutableStateOf("全部") }
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
                        Text("只显示可定位问题的 AI、聊天、记忆与知识库记录", fontSize = 11.sp, color = TextTertiary)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) } },
                actions = {
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
                isUsefulLog(entry) &&
                (category == LogCategory.ALL || logCategory(entry.tag) == category || (category == LogCategory.SPECIAL && isSpecial(entry))) &&
                (statusFilter == "全部" || (statusFilter == "失败" && isFailure(entry)) || (statusFilter == "成功" && isSuccess(entry)))
        }
        val groupedLogs = buildLogGroups(filteredLogs)
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text("状态：${if (loggingEnabled) "正在记录" else "常规记录已关闭，仅保留关键失败"}  |  当前缓存 ${logs.size}/500 条", fontSize = 12.sp, color = if (loggingEnabled) AccentGreen else AccentOrange)
                Text("卡片按一次功能处理聚合。点击查看请求、原始返回、最终保存结果和上下文取用。", fontSize = 11.sp, color = TextSecondary)
                          Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("记录完整模型输入与输出", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                    Switch(checked = payloadsEnabled, enabled = loggingEnabled, onCheckedChange = {
                        payloadsEnabled = it
                        settings.debugLogPayloadsEnabled = it
                        DebugLogger.allowSensitiveTrace = loggingEnabled && it
                        DebugLogger.log("Debug/Settings", "完整模型输入与输出=${if (it) "开启" else "关闭"}")
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                    LogCategory.values().forEach { item ->
                        FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item.label, fontSize = 11.sp) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    listOf("全部", "成功", "失败").forEach { item ->
                        FilterChip(selected = statusFilter == item, onClick = { statusFilter = item }, label = { Text(item, fontSize = 11.sp) })
                    }
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
                            val tagColor = categoryColor(logCategory(group.primary.tag))
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
                              Text(group.title, fontSize = 13.sp, color = tagColor, fontWeight = FontWeight.Bold)
                             if (group.isModelCall) {
                                 Spacer(Modifier.width(6.dp))
                                 Text(group.status, fontSize = 10.sp, color = group.statusColor, fontWeight = FontWeight.Bold)
                             }
                         }
                          Text(group.description, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                          if (group.status == "失败") {
                              val failure = group.entries.firstOrNull(::isFailure) ?: group.primary
                              Text(readableMessage(failure), fontSize = 12.sp, color = ErrorRed, modifier = Modifier.padding(top = 3.dp))
                          }
                          if (group.isModelCall) {
                             Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                  StageChip("请求内容", group.request != null)
                                  StageChip("原始返回", group.response != null)
                                  StageChip("最终保存", group.saved != null)
                                  if (group.contextEntries.isNotEmpty()) StageChip("上下文 ${group.contextEntries.size}", true)
                             }
                         } else {
                              Text(logPreview(readableMessage(entry)), fontSize = 12.sp, color = if (isFailure(entry)) ErrorRed else TextPrimary)
                         }
                          Text("点击查看详情，长按复制本次完整记录", fontSize = 10.sp, color = TextTertiary)
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
            text = { LogGroupDetails(group) },
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

private fun logCategory(tag: String): LogCategory = when {
    tag.startsWith("Diagnostic/HistoryReply/") || tag.startsWith("Diagnostic/Context/History/") -> LogCategory.SPECIAL
    tag.startsWith("Memory/") || tag.startsWith("MemoryV2") || tag.startsWith("Context/") || tag.contains("Memory", true) || tag.startsWith("Vector/") -> LogCategory.MEMORY
    tag.contains("Group", true) || tag.startsWith("GroupChat/") -> LogCategory.GROUP
    tag.contains("Chat", true) || tag.startsWith("PrivateChat/") || tag.startsWith("ChatEvent/") || tag.startsWith("Round/") -> LogCategory.PRIVATE
    tag.startsWith("AI/") || tag.startsWith("Vision") || tag.startsWith("RHODES_VISION") || tag.startsWith("Dispatch/AI") || tag.startsWith("Galgame") || tag.startsWith("Moment") || tag.startsWith("Diary") -> LogCategory.CONTENT
    else -> LogCategory.ALL
}

@Composable
private fun LogGroupDetails(group: LogGroup) {
    Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
        Text(group.description, fontSize = 12.sp, color = TextSecondary)
        DetailSummary("处理状态", group.status, group.statusColor)
        group.request?.let { DetailSection("实际发送给 AI 的请求", "系统提示词、历史消息、参考资料和本轮输入", it.message) }
        group.response?.let { DetailSection("AI 返回的原始内容", "未经解析、过滤或保存处理的模型原文", it.message) }
        group.saved?.let { DetailSection("最终保存/展示内容", "应用清洗、解析和格式处理后写入聊天记录的内容", it.message) }
        group.contextEntries.forEach { entry -> DetailSection("记忆、向量或知识库取用", logDescription(entry.tag), entry.message) }
        group.related.filterNot { it in group.contextEntries }.forEach { entry ->
            DetailSection(logTitle(entry.tag), logDescription(entry.tag), entry.message)
        }
    }
}

@Composable
private fun DetailSummary(label: String, value: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun DetailSection(title: String, subtitle: String, content: String) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).background(Surface, RoundedCornerShape(8.dp)).padding(10.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Primary)
        Text(subtitle, fontSize = 10.sp, color = TextTertiary, modifier = Modifier.padding(top = 2.dp))
        SelectionContainer {
            Text(content.ifBlank { "本次未记录完整内容。请开启“记录完整模型输入与输出”后复现问题。" }, fontSize = 11.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 7.dp))
        }
    }
}

private fun categoryColor(category: LogCategory) = when (category) {
    LogCategory.PRIVATE -> Blue400
    LogCategory.GROUP -> Color(0xFFB388FF)
    LogCategory.CONTENT -> Color(0xFFB388FF)
    LogCategory.MEMORY -> AccentGreen
    LogCategory.SPECIAL -> ErrorRed
    LogCategory.ALL -> TextSecondary
}

private fun isFailure(entry: LogEntry): Boolean =
    entry.tag.endsWith("/错误") || entry.tag.contains("Error", true) || entry.tag.contains("Failed", true) ||
        entry.tag.endsWith("/失败") || entry.tag.endsWith("失败") || entry.tag.startsWith("Vector/Save")

private fun isSpecial(entry: LogEntry): Boolean =
    entry.tag.startsWith("Diagnostic/HistoryReply/") || entry.tag.startsWith("Diagnostic/Context/History/")

private fun isSuccess(entry: LogEntry): Boolean =
    !isFailure(entry) && (entry.tag.contains("成功") || entry.tag.endsWith("响应") || entry.tag.endsWith("/已保存") || entry.tag.startsWith("Context/"))

private fun isUsefulLog(entry: LogEntry): Boolean {
    if (entry.tag.endsWith("/思维链") || entry.tag.endsWith("/思维链状态")) return false
    if (isFailure(entry) || isSpecial(entry)) return true
    return entry.tag.startsWith("Round/") || entry.tag.startsWith("ChatEvent/") ||
        entry.tag.startsWith("AI/") || entry.tag.startsWith("Memory/") ||
        entry.tag.startsWith("MemoryV2") || entry.tag.startsWith("Context/") ||
        entry.tag.startsWith("Vector/") || entry.tag.startsWith("Vision") ||
        entry.tag.startsWith("RHODES_VISION") || entry.tag.startsWith("Dispatch/AI") ||
        entry.tag.startsWith("Moment") || entry.tag.startsWith("Diary")
}

private fun readableMessage(entry: LogEntry): String {
    val message = entry.message
    return when {
        entry.tag.startsWith("Context/") -> message.lines().firstOrNull().orEmpty().ifBlank { "记忆或知识库：0 条" }
        message.contains("HTTP 401") -> "API Key 无效或已失效"
        message.contains("HTTP 402") -> "API 账户余额或额度不足"
        message.contains("HTTP 403") -> "当前 API Key 没有权限"
        message.contains("HTTP 404") -> "模型名称或接口地址不存在"
        message.contains("HTTP 429") -> "请求过于频繁，请稍后再试"
        message.contains("HTTP 5") -> "模型服务商暂时异常"
        message.contains("UnknownHost", true) -> "无法连接网络或模型服务地址"
        message.contains("timeout", true) || message.contains("超时") -> "网络超时，暂时没有收到模型回复"
        message.contains("JSONException") || message.contains("SerializationException") -> "模型返回内容格式无法识别"
        message.contains("apiKeyPresent=false") -> "尚未配置 API Key"
        message.contains("modelPresent=false") -> "尚未配置模型名称"
        else -> message.replace(Regex("provider=[^,，\\s]+"), "").replace(Regex("model=[^,，\\s]+"), "").trim().ifBlank { "处理完成" }
    }
}

private fun logPreview(message: String): String =
    if (message.length <= 800) message else "${message.take(800)}\n… 已省略列表预览，点击查看完整内容"

private fun logDescription(tag: String): String = when {
    tag.startsWith("Diagnostic/HistoryReply/") -> "历史 AI 回复无法重新解析为上下文。该条会被占位文本替代，后续模型不会获得其原始回复内容。"
    tag.startsWith("Diagnostic/Context/History/") -> "模型服务拒绝了过长上下文。应用已保留较近回合并裁剪较早历史后自动重试。"
    tag.startsWith("Round/") -> "对话轮次：按顺序记录本轮用户消息、模型请求、返回解析、内容重试、格式补全、保存与本轮总览。完整内容仅在“记录完整模型输入与输出”开启时可用。"
    tag.endsWith("/请求") -> "正在向 AI 发送请求。"
    tag.endsWith("/响应") -> "AI 已返回结果。"
    tag.endsWith("/错误") -> "AI 请求失败，请查看失败原因。"
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
    tag == "Memory/ContextDetail" -> "实际取用内容：仅在开启完整模型输入与输出后记录。"
    tag.startsWith("Memory/") -> "记忆处理：本轮聊天使用、写入或清理记忆的情况。"
    tag.startsWith("Context/") -> "本次请求使用的记忆或知识库数量。"
    tag.startsWith("GroupChat/DB") -> "群聊本地存储：消息已写入本机数据库。"
    tag.startsWith("GroupChat/Auto") -> "群聊自动发言：定时器、等待时间和自动回复任务状态。"
    tag.startsWith("GroupChat") -> "群聊处理：群成员、会话或回复流程状态。"
    tag.startsWith("AI/→") -> "最终请求载荷：实际发给大模型的完整消息列表。"
    tag.startsWith("AI/←") -> "原始模型返回：未经应用解析或保存处理的模型输出。"
    tag.startsWith("AI/") -> "大模型处理过程：格式修复、内容重试或解析诊断信息。"
    else -> "此记录用于定位本次功能处理结果。"
}

private fun logTitle(tag: String): String = when {
    tag.startsWith("Diagnostic/HistoryReply/Private") -> "私聊 · 历史回复无法用于上下文"
    tag.startsWith("Diagnostic/HistoryReply/Group") -> "群聊 · 历史回复无法用于上下文"
    tag.startsWith("Diagnostic/Context/History/") -> "聊天 · 上下文超限自动裁剪"
    tag.startsWith("Round/") -> roundTitle(tag)
    tag.startsWith("ChatEvent/") -> "聊天流程"
    tag.startsWith("AI/→GroupChat") -> "群聊 · 发送给 AI 的完整内容"
    tag.startsWith("AI/←GroupChat") -> "群聊 · AI 返回的原始内容"
    tag.startsWith("AI/✓群聊") -> "群聊 · 保存到聊天记录的内容"
    tag.endsWith("/思维链状态") -> "DeepSeek 思考模式状态"
    tag.endsWith("/思维链") -> "DeepSeek 完整思维链"
    tag.startsWith("AI/→Chat") || tag.startsWith("AI/→VisionChat") -> "私聊 · 发送给 AI 的完整内容"
    tag.startsWith("AI/←Chat") || tag.startsWith("AI/←VisionChat") -> "私聊 · AI 返回的原始内容"
    tag.startsWith("AI/✓私聊") -> "私聊 · 保存到聊天记录的内容"
    tag.startsWith("AI/GroupContentRetry") -> "群聊内容重试"
    tag == "AI/GroupFormatRepair" -> "群聊格式修复"
    tag.startsWith("AI/") && tag.endsWith("/请求") -> aiOperationTitle(tag, "给 AI 发送请求")
    tag.startsWith("AI/") && tag.endsWith("/响应") -> aiOperationTitle(tag, "AI 回复")
    tag.startsWith("AI/") && tag.endsWith("/错误") -> aiOperationTitle(tag, "AI 请求失败")
    tag.startsWith("AI/") -> "AI 处理"
    tag == "Memory/ContextDetail" -> "实际取用的记忆与知识库内容"
    tag == "Memory/Parse" -> "记忆解析"
    tag == "Memory/Save" -> "保存记忆"
    tag == "Memory/SaveError" -> "保存记忆失败"
    tag.startsWith("Memory/") -> "记忆处理"
    tag.startsWith("Context/") -> "记忆与知识库"
    tag.startsWith("GroupChat/") -> "群聊处理"
    tag.startsWith("Chat/") -> "私聊处理"
    tag.startsWith("Moment") -> "动态处理"
    tag.startsWith("Diary") -> "日记处理"
    tag.startsWith("Diagnostic/Crash") -> "应用发生异常"
    tag.startsWith("Diagnostic/Database") -> "数据库问题"
    tag.startsWith("Diagnostic/AppState") -> "应用数据加载问题"
    tag.startsWith("Operator/") -> "角色设置问题"
    tag.startsWith("Data/") -> "数据管理"
    else -> "功能处理失败"
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
    val contextEntries: List<LogEntry> get() = entries.filter { it.tag.startsWith("Context/") || it.tag.startsWith("Memory/Context") || it.tag.startsWith("Vector/") }
    val targetName: String get() = entries.asSequence().mapNotNull { entry ->
        Regex("对象=([^，,\\n]+)").find(entry.message)?.groupValues?.getOrNull(1)
            ?: Regex("群=([^，,\\n]+)").find(entry.message)?.groupValues?.getOrNull(1)
            ?: Regex("会话=([^，,\\n]+)").find(entry.message)?.groupValues?.getOrNull(1)
    }.firstOrNull().orEmpty()
    val time: String get() = primary.formattedTime
    val isRound: Boolean get() = primary.tag.startsWith("Round/")
    val title: String get() = when {
        isModelCall -> readableModelTitle(request ?: response ?: saved ?: primary, targetName)
        isRound -> roundTitle(primary.tag)
        else -> logTitle(primary.tag)
    }
    val description: String get() = when {
        isModelCall -> readableModelDescription(request ?: response ?: saved ?: primary, targetName)
        isRound -> "本轮处理链路：输入、请求、解析、重试和保存"
        else -> logDescription(primary.tag)
    }
    val status: String get() = when {
        entries.any(::isFailure) -> "失败"
        saved != null -> "已保存"
        response != null -> "已返回"
        request != null -> "请求中"
        else -> "流程"
    }
    val statusColor get() = when (status) {
        "失败" -> ErrorRed
        "已保存" -> AccentGreen
        "已返回" -> AccentBlue
        else -> AccentOrange
    }
}

private fun readableModelDescription(entry: LogEntry, targetName: String = ""): String {
    val surface = modelSurface(entry.tag)
    val target = when {
        targetName.isNotBlank() -> targetName
        entry.message.contains("operatorName=") -> entry.message.substringAfter("operatorName=").substringBefore(",")
        entry.message.contains("operator=") -> entry.message.substringAfter("operator=").substringBefore(",")
        entry.message.contains("groupName=") -> entry.message.substringAfter("groupName=").substringBefore(",")
        else -> "当前会话"
    }
    return when {
        surface == "群聊" -> "在${target}群聊，AI 处理"
        surface == "私聊" -> "与${target}私聊，AI 处理"
        else -> "$surface，AI 处理"
    }
}

private fun readableModelTitle(entry: LogEntry, targetName: String = ""): String {
    val surface = modelSurface(entry.tag)
    val action = when {
        isFailure(entry) || entry.tag.endsWith("/错误") -> "AI 请求失败"
        isRequestTag(entry.tag) -> "给 AI 发送请求"
        isResponseTag(entry.tag) -> "AI 回复"
        isSavedTag(entry.tag) -> "保存 AI 回复"
        else -> "AI 处理"
    }
    val target = targetName.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
    return when (surface) {
        "群聊" -> "群聊$target，$action"
        "私聊" -> "私聊$target，$action"
        else -> "$surface，$action"
    }
}

private fun aiOperationTitle(tag: String, action: String): String {
    val name = tag.removePrefix("AI/").substringBefore('/').lowercase()
    return when {
        name.contains("group") -> "在群聊，$action"
        name.contains("chat") || name.contains("private") -> "私聊，$action"
        name.contains("memory") || name.contains("summary") -> "记忆处理，$action"
        name.contains("moment") -> "生成动态，$action"
        name.contains("comment") -> "生成评论，$action"
        name.contains("diary") -> "生成日记，$action"
        name.contains("vision") -> "识别图片，$action"
        name.contains("dispatch") -> "生成派遣内容，$action"
        else -> "AI 处理，$action"
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
    tag.takeIf { it.startsWith("Round/") }?.split('/')?.getOrNull(1)?.let { "round:$it" }

private fun modelGroupKey(tag: String): String? {
    if (!tag.startsWith("AI/")) return null
    val value = tag.removePrefix("AI/")
    val marker = value.indexOfAny(charArrayOf('→', '←', '✓'))
    if (marker < 0) {
        val round = value.substringBefore('/').substringAfter('#', "")
        return round.takeIf { it.isNotBlank() }?.let { "round:$it" }
    }
    val normalized = value.substring(marker + 1)
        .substringBeforeLast("/思维链")
        .replace("私聊#", "Chat#")
        .replace("群聊#", "GroupChat#")
    return normalized.substringAfter('#', "").takeIf { it.isNotBlank() }?.let { "round:$it" }
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
    append("状态：${group.status}\n")
    if (group.primary.message.contains("记忆") || group.primary.message.contains("知识库")) {
        append("上下文：${contextCountText(group.primary.message)}\n")
    }
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

private fun contextCountText(message: String): String {
    val count = Regex("(?:记忆|知识库|注入|候选)[^0-9]{0,8}(\\d+)").find(message)?.groupValues?.getOrNull(1)
    return if (count == null) "数量未记录" else "记忆或知识库：${count} 条"
}

private fun roundTitle(tag: String): String {
    val parts = tag.split('/')
    val surface = parts.getOrNull(2).orEmpty()
    val stage = parts.getOrNull(3).orEmpty()
    return "$surface · $stage"
}

private fun roundStatus(tag: String): String = tag.substringAfterLast('/')
