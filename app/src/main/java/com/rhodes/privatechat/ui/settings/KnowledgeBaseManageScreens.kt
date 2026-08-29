package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.shared.model.KnowledgeBase
import com.rhodes.privatechat.shared.model.KnowledgeBaseChunk
import com.rhodes.privatechat.shared.model.OperatorKnowledgeBaseAssignment
import com.rhodes.privatechat.ui.theme.BG
import com.rhodes.privatechat.ui.theme.TextPrimary
import com.rhodes.privatechat.ui.theme.TextSecondary
import com.rhodes.privatechat.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun KnowledgeBaseEditorScreen(id: String, onBack: () -> Unit) {
    val viewModel: MainViewModel = koinViewModel()
    val scope = rememberCoroutineScope()
    var book by remember { mutableStateOf<KnowledgeBase?>(null) }
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var confirmResplit by remember { mutableStateOf(false) }
    var pendingCompleteSave by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingCancelSave by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(id) { viewModel.getKnowledgeBase(id)?.let { book = it; name = it.name; content = it.rawContent } }
    val current = book ?: return
    fun requestSave(completeSave: () -> Unit = onBack, cancelSave: () -> Unit = {}) {
        if (content != current.rawContent) {
            pendingCompleteSave = completeSave
            pendingCancelSave = cancelSave
            confirmResplit = true
        } else scope.launch {
            runCatching { viewModel.renameKnowledgeBase(id, name); current.copy(name = name.trim()) }
                .onSuccess { completeSave() }
                .onFailure { message = it.message ?: "保存失败"; cancelSave() }
        }
    }
    SaveableSettingsScaffold("编辑知识库", onBack, Modifier.fillMaxSize().background(BG), onSaveRequest = { complete, cancel -> requestSave(complete, cancel) }) {
        Column(Modifier.padding(16.dp).imePadding().verticalScroll(rememberScrollState())) {
            OutlinedTextField(name, { name = it }, label = { Text("知识库名称") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(content, { content = it }, label = { Text("正文") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 280.dp), minLines = 12)
            if (message.isNotBlank()) Text(message, fontSize = 12.sp, color = TextSecondary)
          }
      }
     if (confirmResplit) AlertDialog(
          onDismissRequest = { confirmResplit = false; pendingCancelSave?.invoke(); pendingCancelSave = null },
         title = { Text("重新分段") },
         text = { Text("重新分段将覆盖当前所有手动新增、编辑、启用、停用和删除的分段设置。是否继续？") },
          confirmButton = { TextButton(onClick = { confirmResplit = false; scope.launch { runCatching { viewModel.updateKnowledgeBaseText(current, name, content) }.onSuccess { (pendingCompleteSave ?: onBack).invoke() }.onFailure { message = it.message ?: "保存失败"; pendingCancelSave?.invoke() }; pendingCompleteSave = null; pendingCancelSave = null } }) { Text("确认重新分段") } },
          dismissButton = { TextButton(onClick = { confirmResplit = false; pendingCancelSave?.invoke(); pendingCancelSave = null }) { Text("取消") } }
     )
}

@Composable
fun KnowledgeBaseRolesScreen(id: String, onBack: () -> Unit) {
    val viewModel: MainViewModel = koinViewModel()
    val scope = rememberCoroutineScope()
    val operators by viewModel.operators.collectAsState()
    var selected by remember { mutableStateOf<Set<String>>(linkedSetOf()) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(id) { selected = viewModel.getKnowledgeBaseAssignmentsForBook(id).filter { it.enabled }.mapTo(linkedSetOf()) { it.operatorId } }
    fun saveAndBack() {
        if (saving) return
        scope.launch {
            saving = true
            runCatching { viewModel.replaceKnowledgeBaseRoleAssignments(id, selected.toList()) }
                .onSuccess { onBack() }
                .onFailure { message = "保存失败：${it.message ?: "未知错误"}" }
            saving = false
        }
    }
    SaveableSettingsScaffold("关联角色", ::saveAndBack, Modifier.fillMaxSize().background(BG), showSave = false) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("勾选后返回会自动保存；角色编辑页会显示相同结果。", fontSize = 12.sp, color = TextSecondary)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("已关联 ${selected.size} / ${operators.size} 个角色", color = TextPrimary, modifier = Modifier.weight(1f))
                TextButton(onClick = { selected = operators.mapTo(linkedSetOf()) { it.id } }) { Text("全选") }
                TextButton(onClick = { selected = emptySet() }) { Text("全部取消") }
            }
            operators.forEach { op ->
                Row(Modifier.fillMaxWidth().clickable { selected = selected.toCollection(LinkedHashSet()).also { if (!it.add(op.id)) it.remove(op.id) } }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(op.id in selected, { checked -> selected = selected.toCollection(LinkedHashSet()).also { if (checked) it.add(op.id) else it.remove(op.id) } })
                    Text(op.name, color = TextPrimary)
                }
            }
            if (message.isNotBlank()) Text(message, fontSize = 12.sp, color = TextSecondary)
            TextButton(enabled = !saving, onClick = ::saveAndBack, modifier = Modifier.align(Alignment.End)) { Text(if (saving) "保存中" else "保存并返回") }
        }
    }
}

@Composable
fun KnowledgeBaseChunksScreen(id: String, onBack: () -> Unit) {
    val viewModel: MainViewModel = koinViewModel()
    val scope = rememberCoroutineScope()
    var chunks by remember { mutableStateOf<List<KnowledgeBaseChunk>>(emptyList()) }
    var editing by remember { mutableStateOf<KnowledgeBaseChunk?>(null) }
    var editingText by remember { mutableStateOf("") }
    var editingHeading by remember { mutableStateOf("") }
    var editingKeywords by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<KnowledgeBaseChunk?>(null) }
    var adding by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    fun refresh() = scope.launch { chunks = viewModel.getKnowledgeBaseChunks(id) }
    LaunchedEffect(id) { refresh() }
    SaveableSettingsScaffold("分段管理", onBack, Modifier.fillMaxSize().background(BG), showSave = false) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("新增、修改或重新启用分段需要补充索引；删除或停用会立即从检索中移除，不影响其他分段。", fontSize = 12.sp, color = TextSecondary)
            if (message.isNotBlank()) Text(message, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("使用中 ${chunks.count { it.enabled }} / ${chunks.size}", color = TextPrimary, modifier = Modifier.weight(1f))
                Button(onClick = { editingHeading = ""; editingText = ""; editingKeywords = ""; adding = true }) { Text("新增分段") }
            }
            chunks.forEach { chunk ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("片段 ${chunk.ordinal}${chunk.sourceHeading.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}", color = TextPrimary)
                        Text(chunk.content.take(80), fontSize = 12.sp, color = TextSecondary)
                    }
                    Text(if (chunk.enabled) "使用中" else "未使用", color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp))
                    TextButton(onClick = { editing = chunk; editingHeading = chunk.sourceHeading; editingText = chunk.content; editingKeywords = chunk.userKeywords }) { Text("编辑") }
                    TextButton(onClick = { scope.launch { runCatching { viewModel.updateKnowledgeBaseChunkEnabled(id, chunk.id, !chunk.enabled) }.onSuccess { message = if (chunk.enabled) "已停用分段并移除其索引" else "已启用分段；等待补充索引确认"; refresh() }.onFailure { message = "更新失败：${it.message ?: "未知错误"}" } } }) { Text(if (chunk.enabled) "停用" else "启用") }
                    TextButton(onClick = { deleting = chunk }) { Text("删除") }
                }
            }
        }
    }
    editing?.let { chunk -> androidx.compose.material3.AlertDialog(
        onDismissRequest = { editing = null }, title = { Text("编辑片段 ${chunk.ordinal}") },
        text = { Column(Modifier.imePadding().verticalScroll(rememberScrollState())) { OutlinedTextField(editingHeading, { editingHeading = it }, label = { Text("来源标题") }); OutlinedTextField(editingText, { editingText = it }, label = { Text("正文") }, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp)); OutlinedTextField(editingKeywords, { editingKeywords = it }, label = { Text("关键词") }) } },
        confirmButton = { TextButton(onClick = { scope.launch { runCatching { viewModel.updateKnowledgeBaseChunk(id, chunk.id, editingHeading, editingText, editingKeywords) }.onSuccess { editing = null; message = "已更新分段；等待补充索引确认"; refresh() }.onFailure { message = "保存失败：${it.message ?: "未知错误"}" } } }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } }
    ) }
    if (adding) androidx.compose.material3.AlertDialog(
        onDismissRequest = { adding = false }, title = { Text("新增分段") },
        text = { Column(Modifier.imePadding().verticalScroll(rememberScrollState())) { OutlinedTextField(editingHeading, { editingHeading = it }, label = { Text("来源标题") }); OutlinedTextField(editingText, { editingText = it }, label = { Text("正文") }, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp)); OutlinedTextField(editingKeywords, { editingKeywords = it }, label = { Text("关键词") }) } },
        confirmButton = { TextButton(onClick = { scope.launch { runCatching { viewModel.addKnowledgeBaseChunk(id, editingHeading, editingText, editingKeywords) }.onSuccess { adding = false; message = "已新增分段；等待补充索引确认"; refresh() }.onFailure { message = "保存失败：${it.message ?: "未知错误"}" } } }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { adding = false }) { Text("取消") } }
    )
    deleting?.let { chunk -> AlertDialog(
        onDismissRequest = { deleting = null }, title = { Text("删除分段") },
        text = { Text("确定删除片段 ${chunk.ordinal} 吗？删除后不会修改原文正文，但该分段将不再参与检索。") },
        confirmButton = { TextButton(onClick = { scope.launch { runCatching { viewModel.deleteKnowledgeBaseChunk(id, chunk.id) }.onSuccess { deleting = null; message = "已删除分段并移除其索引；其他分段不受影响"; refresh() }.onFailure { message = "删除失败：${it.message ?: "未知错误"}" } } }) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } }
    ) }
}
