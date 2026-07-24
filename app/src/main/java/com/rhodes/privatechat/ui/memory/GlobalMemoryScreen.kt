package com.rhodes.privatechat.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.shared.model.MemoryItem
import com.rhodes.privatechat.shared.model.MemoryLevel
import com.rhodes.privatechat.ui.theme.ErrorRed
import com.rhodes.privatechat.ui.theme.TextPrimary
import com.rhodes.privatechat.ui.theme.TextSecondary
import com.rhodes.privatechat.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalMemoryScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var allItems by remember { mutableStateOf<List<MemoryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var ownerFilter by remember { mutableStateOf("全部范围") }
    var levelFilter by remember { mutableStateOf("全部等级") }
    var statusFilter by remember { mutableStateOf("正在使用") }
    var sourceFilter by remember { mutableStateOf("全部来源") }
    var keyword by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<MemoryItem?>(null) }
    var pendingClear by remember { mutableStateOf<List<MemoryItem>?>(null) }
    var pendingRebuild by remember { mutableStateOf(false) }
    var rebuildProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    suspend fun reload() {
        allItems = viewModel.getAllUnifiedMemoryItems()
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    val operators by viewModel.operators.collectAsState()
    val sessions by viewModel.allSessions.collectAsState()
    val operatorNames = remember(operators) { operators.associate { it.id to it.name } }
    val groupNames = remember(sessions) {
        sessions.filter { it.operatorId.startsWith("group") || it.id.startsWith("group") }
            .associate { it.id to it.operatorName }
    }

    val ownerOptions = remember(allItems, operatorNames, groupNames) {
        listOf("全部范围", "角色个人知识", "群共同知识", "公开动态资料") +
            allItems.mapNotNull { item ->
                when (item.ownerType) {
                    "operator" -> "角色：${operatorNames[item.ownerId] ?: item.ownerId}"
                    "group" -> "群：${groupNames[item.ownerId] ?: item.ownerId}"
                    else -> null
                }
            }.distinct().sorted()
    }
    val filtered = allItems.filter { item ->
        val ownerMatches = when (ownerFilter) {
            "全部范围" -> true
            "角色个人知识" -> item.ownerType == "operator"
            "群共同知识" -> item.ownerType == "group"
            "公开动态资料" -> item.ownerType == "global" && item.ownerId == "public"
            else -> ownerFilter == "角色：${operatorNames[item.ownerId] ?: item.ownerId}" ||
                ownerFilter == "群：${groupNames[item.ownerId] ?: item.ownerId}"
        }
        val levelMatches = levelFilter == "全部等级" || item.memoryLevel.name == levelFilter
        val statusMatches = when (statusFilter) {
            "全部状态" -> true
            "正在使用" -> item.status == "active"
            "已归档" -> item.status == "archived"
            else -> true
        }
        val sourceMatches = sourceFilter == "全部来源" || sourceLabel(item) == sourceFilter
        val keywordMatches = keyword.isBlank() || item.content.contains(keyword.trim(), ignoreCase = true) || item.sourceActor.contains(keyword.trim(), ignoreCase = true)
        ownerMatches && levelMatches && statusMatches && sourceMatches && keywordMatches
    }.sortedByDescending { it.createdAt }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记忆管理") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    TextButton(enabled = !working, onClick = { scope.launch { reload() } }) { Text("刷新") }
                    TextButton(enabled = !working, onClick = { pendingRebuild = true }) { Text("重建索引") }
                    TextButton(enabled = !working && filtered.isNotEmpty(), onClick = { pendingClear = filtered }) { Text("清除筛选", color = ErrorRed) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("这里管理近期记录、长期记忆、角色记得的你、群共同知识和公开动态资料。删除会同时停止派生认知和语义召回。", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(keyword, { keyword = it }, Modifier.fillMaxWidth(), label = { Text("搜索内容或说话人") }, singleLine = true)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                MemoryFilterMenu(ownerFilter, ownerOptions) { ownerFilter = it }
                MemoryFilterMenu(levelFilter, listOf("全部等级", "L1", "L2", "L3")) { levelFilter = it }
                MemoryFilterMenu(statusFilter, listOf("正在使用", "已归档", "全部状态")) { statusFilter = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                MemoryFilterMenu(sourceFilter, listOf("全部来源", "私聊", "群聊", "动态", "评论", "日记", "手动添加")) { sourceFilter = it }
                Text("共 ${filtered.size} 条", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 12.dp))
            }
            if (message.isNotBlank()) Text(message, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else if (filtered.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无符合条件的统一记忆", color = TextSecondary) }
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { item ->
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.content, fontSize = 14.sp, color = TextPrimary)
                            Spacer(Modifier.height(5.dp))
                            Text("${levelLabel(item.memoryLevel)} · ${ownerLabel(item, operatorNames, groupNames)} · ${sourceLabel(item)} · ${if (item.status == "active") "正在使用" else "已归档"}", fontSize = 11.sp, color = TextSecondary)
                            if (item.eventTime?.isNotBlank() == true || item.scheduledTime?.isNotBlank() == true) {
                                Text(listOfNotNull(item.eventTime?.takeIf { it.isNotBlank() }, item.scheduledTime?.takeIf { it.isNotBlank() }?.let { "约定：$it" }).joinToString(" · "), fontSize = 11.sp, color = TextSecondary)
                            }
                            Text("重要性 ${item.importance} · ${if (item.vectorId.isBlank()) "未建立索引" else "已建立索引"}", fontSize = 11.sp, color = TextSecondary)
                            TextButton(enabled = !working, onClick = { pendingDelete = item }) { Text("删除", color = ErrorRed) }
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条记忆？") },
            text = { Text("删除后，这条记忆及由它形成的长期认知会停止被角色使用。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        working = true
                        runCatching { viewModel.deleteUnifiedMemoryItem(item) }
                            .onSuccess { reload(); message = "记忆已删除，相关派生认知已停止使用" }
                            .onFailure { message = "删除失败：${it.message?.take(60) ?: "未知错误"}" }
                        working = false
                        pendingDelete = null
                    }
                }) { Text("删除", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }
    pendingClear?.let { targets ->
        AlertDialog(
            onDismissRequest = { pendingClear = null },
            title = { Text("清除当前筛选的 ${targets.size} 条记忆？") },
            text = { Text("这会删除当前列表中的记忆，并停用相关派生长期认知。此操作不能撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        working = true
                        runCatching { viewModel.deleteUnifiedMemoryItems(targets) }
                            .onSuccess { reload(); message = "已清除 ${targets.size} 条记忆" }
                            .onFailure { message = "清除失败：${it.message?.take(60) ?: "未知错误"}" }
                        working = false
                        pendingClear = null
                    }
                }) { Text("确认清除", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { pendingClear = null }) { Text("取消") } }
        )
    }
    if (pendingRebuild) {
        AlertDialog(
            onDismissRequest = { if (!working) pendingRebuild = false },
            title = { Text("重建全部记忆索引") },
            text = {
                val progress = rebuildProgress
                if (working && progress != null) {
                    Text("正在重建：${progress.first} / ${progress.second}")
                } else Text("重新为所有角色的有效记忆生成向量索引。\n\n什么情况下需要做：\n• 更换了向量模型后，旧向量和新模型不兼容\n• 部分记忆显示“未建立索引”\n• 你觉得角色召回记忆不准确\n\n什么情况下不需要做：\n• 日常使用中，新增的记忆会自动索引\n• 删除记忆后，对应的索引会自动清理\n\n注意：使用了阿里等付费 embedding 服务时，重建会对每条有效记忆发起一次 API 请求，可能产生费用。")
            },
            confirmButton = {
                TextButton(enabled = !working, onClick = {
                    scope.launch {
                        working = true
                        val result = runCatching { viewModel.rebuildAllMemoryIndexes { done, total -> rebuildProgress = done to total } }.getOrNull()
                        message = result?.let { "索引重建完成：成功 ${it.succeeded}，失败 ${it.failed}" } ?: "索引重建失败"
                        rebuildProgress = null
                        working = false
                        pendingRebuild = false
                        reload()
                    }
                }) { Text("开始重建") }
            },
            dismissButton = { TextButton(enabled = !working, onClick = { pendingRebuild = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun MemoryFilterMenu(selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(selected, fontSize = 12.sp) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}

private fun levelLabel(level: MemoryLevel): String = when (level) {
    MemoryLevel.L1 -> "近期记录"
    MemoryLevel.L2 -> "长期记忆"
    MemoryLevel.L3 -> "角色记得的你"
}

private fun ownerLabel(item: MemoryItem, operatorNames: Map<String, String>, groupNames: Map<String, String>): String = when (item.ownerType) {
    "operator" -> "角色：${operatorNames[item.ownerId] ?: item.ownerId}"
    "group" -> "群：${groupNames[item.ownerId] ?: item.ownerId}"
    "global" -> "公开资料"
    else -> "${item.ownerType}:${item.ownerId}"
}

private fun sourceLabel(item: MemoryItem): String = when (item.sourceKind.name) {
    "PRIVATE_CHAT" -> "私聊"
    "GROUP_CHAT" -> "群聊"
    "MOMENT" -> "动态"
    "MOMENT_COMMENT" -> "评论"
    "DIARY" -> "日记"
    "MANUAL_MEMORY" -> "手动添加"
    else -> item.sourceKind.name
}
