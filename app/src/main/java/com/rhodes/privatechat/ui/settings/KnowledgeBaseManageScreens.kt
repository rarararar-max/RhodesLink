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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    LaunchedEffect(id) { viewModel.getKnowledgeBase(id)?.let { book = it; name = it.name; content = it.rawContent } }
    val current = book ?: return
    SaveableSettingsScaffold("编辑知识库", onBack, Modifier.fillMaxSize().background(BG), showSave = false) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(name, { name = it }, label = { Text("知识库名称") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(content, { content = it }, label = { Text("正文") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 280.dp), minLines = 12)
            if (message.isNotBlank()) Text(message, fontSize = 12.sp, color = TextSecondary)
            TextButton(onClick = { scope.launch {
                val result = runCatching {
                    if (content == current.rawContent) { viewModel.renameKnowledgeBase(id, name); current.copy(name = name.trim()) }
                    else viewModel.updateKnowledgeBaseText(current, name, content)
                }
                result.onSuccess { onBack() }.onFailure { message = it.message ?: "保存失败" }
            } }, modifier = Modifier.align(Alignment.End)) { Text("保存") }
        }
    }
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
    SaveableSettingsScaffold("关联角色", onBack, Modifier.fillMaxSize().background(BG), showSave = false) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("勾选即关联并启用；角色编辑页会显示相同结果。", fontSize = 12.sp, color = TextSecondary)
            operators.forEach { op ->
                Row(Modifier.fillMaxWidth().clickable { selected = selected.toCollection(LinkedHashSet()).also { if (!it.add(op.id)) it.remove(op.id) } }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(op.id in selected, { checked -> selected = selected.toCollection(LinkedHashSet()).also { if (checked) it.add(op.id) else it.remove(op.id) } })
                    Text(op.name, color = TextPrimary)
                }
            }
            if (message.isNotBlank()) Text(message, fontSize = 12.sp, color = TextSecondary)
            TextButton(enabled = !saving, onClick = { scope.launch {
                saving = true
                runCatching { viewModel.replaceKnowledgeBaseRoleAssignments(id, selected.toList()) }
                    .onSuccess { onBack() }
                    .onFailure { message = "保存失败：${it.message ?: "未知错误"}" }
                saving = false
            } }, modifier = Modifier.align(Alignment.End)) { Text(if (saving) "保存中" else "保存关联") }
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
    fun refresh() = scope.launch { chunks = viewModel.getKnowledgeBaseChunks(id) }
    LaunchedEffect(id) { refresh() }
    SaveableSettingsScaffold("分段管理", onBack, Modifier.fillMaxSize().background(BG), showSave = false) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("编辑、启用或禁用分段后，知识库需要重新索引。", fontSize = 12.sp, color = TextSecondary)
            chunks.forEach { chunk ->
                Row(Modifier.fillMaxWidth().clickable { editing = chunk; editingText = chunk.content }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("片段 ${chunk.ordinal}${chunk.sourceHeading.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}", color = TextPrimary)
                        Text(chunk.content.take(80), fontSize = 12.sp, color = TextSecondary)
                    }
                    Switch(chunk.enabled, { enabled -> scope.launch { viewModel.updateKnowledgeBaseChunkEnabled(id, chunk.id, enabled); refresh() } })
                }
            }
        }
    }
    editing?.let { chunk -> androidx.compose.material3.AlertDialog(
        onDismissRequest = { editing = null }, title = { Text("编辑片段 ${chunk.ordinal}") },
        text = { OutlinedTextField(editingText, { editingText = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp)) },
        confirmButton = { TextButton(onClick = { scope.launch { viewModel.updateKnowledgeBaseChunkContent(id, chunk.id, editingText); editing = null; refresh() } }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } }
    ) }
}
