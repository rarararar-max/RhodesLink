package com.rhodes.privatechat.ui.memory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.data.db.entity.OperatorEntity
import com.rhodes.privatechat.shared.model.MemoryItem
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperatorMemoryScreen(viewModel: MainViewModel, operator: OperatorEntity, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<MemoryItem>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }
    var content by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf("private") }
    var importance by remember { mutableStateOf("80") }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<MemoryItem?>(null) }
    var pendingRebuild by remember { mutableStateOf(false) }
    var rebuildProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var eligibleCount by remember { mutableStateOf<Int?>(null) }
    var sourceFilter by remember { mutableStateOf("全部来源") }
    var levelFilter by remember { mutableStateOf("全部等级") }
    var privacyFilter by remember { mutableStateOf("全部权限") }
    var newestFirst by remember { mutableStateOf(true) }
    LaunchedEffect(operator.id) { items = viewModel.getOperatorMemoryItems(operator.id); loading = false }
    Scaffold(
        topBar = { TopAppBar(title = { Text("${operator.name}的记忆") }, navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }, actions = {
            TextButton(enabled = !working, onClick = { scope.launch { eligibleCount = viewModel.countEligibleMemoryIndexes(operator.id); pendingRebuild = true } }) { Text(if (working) "处理中" else "重建索引") }
            TextButton(onClick = { showAdd = true }) { Text("新增") }
        }) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("近期记录、长期记忆和角色记得的你都在这里。删除会同步清除检索索引，角色之后不会再引用这条内容。", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                FilterMenu(sourceFilter, listOf("全部来源", "私聊", "群聊", "动态", "评论", "手动添加")) { sourceFilter = it }
                FilterMenu(levelFilter, listOf("全部等级", "L1", "L2", "L3")) { levelFilter = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterMenu(privacyFilter, listOf("全部权限", "私密", "可传开", "公开")) { privacyFilter = it }
                TextButton(onClick = { newestFirst = !newestFirst }) { Text(if (newestFirst) "最新优先" else "最早优先") }
            }
            if (message.isNotBlank()) Text(message, fontSize = 12.sp, color = TextSecondary)
            if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else {
                val filtered = items.filter { (sourceFilter == "全部来源" || it.sourceKind.name == sourceFilter) && (levelFilter == "全部等级" || it.memoryLevel.name == levelFilter) && (privacyFilter == "全部权限" || (it.privacy ?: "private") == privacyFilter) }.let { if (newestFirst) it.sortedByDescending { item -> item.createdAt } else it.sortedBy { item -> item.createdAt } }
                if (filtered.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("没有符合筛选条件的记忆", color = TextSecondary) }
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { item ->
                        Card {
                            Column(Modifier.padding(12.dp)) {
                                Text(item.content, fontSize = 14.sp, color = TextPrimary)
                                Spacer(Modifier.height(6.dp))
                                Text("${when (item.memoryLevel) { com.rhodes.privatechat.shared.model.MemoryLevel.L1 -> "近期记录"; com.rhodes.privatechat.shared.model.MemoryLevel.L2 -> "长期记忆"; com.rhodes.privatechat.shared.model.MemoryLevel.L3 -> "角色记得的你" }} · 来源 ${item.sourceKind} · 重要性 ${item.importance}" + if (item.vectorId.isBlank()) " · 未建立索引" else " · 已建立索引", fontSize = 11.sp, color = TextSecondary)
                                TextButton(enabled = !working, onClick = { pendingDelete = item }) { Text("删除", color = ErrorRed) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false },
        title = { Text("新增角色记忆") },
        text = { Column {
            OutlinedTextField(content, { content = it }, label = { Text("记忆内容") }, minLines = 3)
            OutlinedTextField(importance, { importance = it.filter(Char::isDigit) }, label = { Text("重要性 0-100") })
            Text("这条记忆由当前角色拥有。角色可在私聊、群聊、动态和评论中自然引用；其他角色不会直接获得。", fontSize = 12.sp, color = TextSecondary)
        } },
        confirmButton = { TextButton(enabled = content.isNotBlank() && !working, onClick = { scope.launch { working = true; val saved = runCatching { viewModel.addManualOperatorMemory(operator.id, content, privacy, importance.toIntOrNull() ?: 80) }.getOrDefault(false); if (saved) { items = viewModel.getOperatorMemoryItems(operator.id); showAdd = false; content = ""; message = "记忆已保存" } else message = "记忆保存失败，请检查内容后重试"; working = false } }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } }
    )
    pendingDelete?.let { item -> AlertDialog(
        onDismissRequest = { pendingDelete = null }, title = { Text("删除这条记忆？") }, text = { Column {
            Text(if (item.sourceRefId.isBlank()) item.content else "${item.content}\n\n此记忆来自 ${item.sourceKind}。可仅删除该条，或删除此来源的全部直接记忆与向量索引。")
            if (item.sourceRefId.isNotBlank() && item.sourceKind != com.rhodes.privatechat.shared.model.MemorySourceKind.GROUP_CHAT) TextButton(enabled = !working, onClick = { scope.launch { working = true; runCatching { viewModel.deleteOperatorMemorySource(item) }.onSuccess { items = viewModel.getOperatorMemoryItems(operator.id); message = "已删除该来源的直接记忆与索引" }.onFailure { message = "删除失败：${it.message?.take(60) ?: "未知错误"}" }; working = false; pendingDelete = null } }) { Text("删除此来源", color = ErrorRed) }
        } },
        confirmButton = { TextButton(onClick = { scope.launch { working = true; runCatching { viewModel.deleteOperatorMemoryItem(item) }.onSuccess { items = viewModel.getOperatorMemoryItems(operator.id); message = "记忆已删除" }.onFailure { message = "删除失败：${it.message?.take(60) ?: "未知错误"}" }; working = false; pendingDelete = null } }) { Text("删除", color = ErrorRed) } },
        dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
    ) }
    if (pendingRebuild) AlertDialog(
        onDismissRequest = { if (!working) pendingRebuild = false },
            title = { Text("重建记忆索引") },
            text = {
                val progress = rebuildProgress
                if (working && progress != null) {
                    Text("正在重建：${progress.first} / ${progress.second}")
                } else Text("重新为 ${operator.name} 的有效记忆生成向量索引。\n\n什么情况下需要做：\n• 更换了向量模型后，旧向量和新模型不兼容\n• 部分记忆显示“未建立索引”\n• 你觉得角色召回记忆不准确\n\n什么情况下不需要做：\n• 日常使用中，新增的记忆会自动索引\n• 删除记忆后，对应的索引会自动清理\n\n注意：使用了阿里等付费 embedding 服务时，重建会对每条有效记忆发起一次 API 请求，可能产生费用。")
        },
        confirmButton = { TextButton(enabled = !working, onClick = { scope.launch { working = true; rebuildProgress = 0 to (eligibleCount ?: 0); val result = runCatching { viewModel.rebuildOperatorMemoryIndexes(operator.id) { done, total -> rebuildProgress = done to total } }.getOrNull(); message = result?.let { "索引重建完成：有效 ${it.eligible}，成功 ${it.succeeded}，失败 ${it.failed}，跳过 ${it.skipped}" + if (it.errors.isNotEmpty()) "。错误：${it.errors.joinToString("；")}" else "" } ?: "索引重建失败，请稍后重试"; items = viewModel.getOperatorMemoryItems(operator.id); rebuildProgress = null; working = false; pendingRebuild = false } }) { Text("开始重建") } },
        dismissButton = { TextButton(enabled = !working, onClick = { pendingRebuild = false }) { Text("取消") } }
    )
}

@Composable
private fun FilterMenu(selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(selected, fontSize = 12.sp) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}
