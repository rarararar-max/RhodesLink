package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
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
import com.rhodes.privatechat.ui.theme.BG
import com.rhodes.privatechat.ui.theme.ErrorRed
import com.rhodes.privatechat.ui.theme.TextPrimary
import com.rhodes.privatechat.ui.theme.TextSecondary
import com.rhodes.privatechat.viewmodel.MainViewModel
import com.rhodes.privatechat.shared.settings.SettingsRepository
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import kotlinx.coroutines.launch

@Composable
fun KnowledgeBaseDetailScreen(
    knowledgeBaseId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRoles: () -> Unit,
    onChunks: () -> Unit,
) {
    val viewModel: MainViewModel = koinViewModel()
    val settings: SettingsRepository = koinInject()
    var book by remember { mutableStateOf<KnowledgeBase?>(null) }
    var chunkCount by remember { mutableStateOf(0) }
    var enabledChunkCount by remember { mutableStateOf(0) }
    var roleCount by remember { mutableStateOf(0) }
    var deleting by remember { mutableStateOf(false) }
    var confirmRemoteIndex by remember { mutableStateOf(false) }
    var indexing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var enabledSurfaces by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    val assignmentRevision by viewModel.knowledgeBaseAssignmentRevision.collectAsState()
    LaunchedEffect(knowledgeBaseId, assignmentRevision) {
        book = viewModel.getKnowledgeBase(knowledgeBaseId)
        val chunks = viewModel.getKnowledgeBaseChunks(knowledgeBaseId)
        chunkCount = chunks.size
        enabledChunkCount = chunks.count { it.enabled }
        roleCount = viewModel.getKnowledgeBaseAssignmentsForBook(knowledgeBaseId).count { it.enabled }
        enabledSurfaces = listOf("private_chat", "group_chat", "moment", "comment", "diary").associateWith { settings.isKnowledgeBaseEnabledForBook(knowledgeBaseId, it) }
    }
    val current = book ?: return
    SaveableSettingsScaffold("知识库详情", onBack, Modifier.fillMaxSize().background(BG), showSave = false) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(current.name, fontSize = 22.sp, color = TextPrimary)
            Text("${current.rawContent.length} 字 · $chunkCount 个分段 · $enabledChunkCount 个使用中 · 已关联 $roleCount 个角色", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
            Text("索引状态：${if (enabledChunkCount == 0) "没有可用分段" else indexStatusText(current.indexStatus)}", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
            if (current.indexStatus == "pending") Text("内容已变更，需要重新索引后才能参与生成。", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
            DetailRow(if (indexing) "正在索引" else "开始/重建索引", if (settings.vectorProviderMode == "third_party") "远程模式会使用当前配置发起向量请求" else "使用当前本地向量模型建立索引", {
                if (indexing) return@DetailRow
                if (settings.vectorProviderMode == "third_party") confirmRemoteIndex = true
                else scope.launch {
                    indexing = true
                    runCatching { viewModel.indexKnowledgeBase(current.id, true) }
                        .onSuccess { message = "索引完成：成功 ${it.succeeded}，失败 ${it.failed}" }
                        .onFailure { message = "索引失败：${it.message ?: "未知错误"}" }
                    book = viewModel.getKnowledgeBase(current.id)
                    indexing = false
                }
            })
            if (message.isNotBlank()) Text(message, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
            Text("此知识库适用场景", fontSize = 14.sp, color = TextPrimary, modifier = Modifier.padding(top = 12.dp))
            Text("仅当角色关联此知识库且当前场景已启用时，资料才会参与生成。", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
            listOf("private_chat" to "私聊", "group_chat" to "群聊", "moment" to "动态", "comment" to "评论", "diary" to "日记").forEach { (surface, label) ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), color = TextPrimary)
                    Switch(enabledSurfaces[surface] == true, { enabled ->
                        enabledSurfaces = enabledSurfaces + (surface to enabled)
                        settings.setKnowledgeBaseEnabledForBook(current.id, surface, enabled)
                        settings.saveDraft()
                    })
                }
            }
            DetailRow("编辑名称和正文", "修改正文后会重新分段并需要重新索引", onEdit)
            DetailRow("关联角色", "多选角色；与角色编辑页自动同步", onRoles)
            DetailRow("分段管理", "查看、编辑、启用或禁用自动分段", onChunks)
            DetailRow("永久删除", "删除正文、分段、索引和全部角色关联", { deleting = true }, ErrorRed)
        }
    }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false }, title = { Text("永久删除知识库") },
        text = { Text("将永久删除《${current.name}》及其 $chunkCount 个分段、索引和 $roleCount 个角色关联，无法恢复。") },
        confirmButton = { TextButton(onClick = { scope.launch { runCatching { viewModel.deleteKnowledgeBase(current.id) }.onSuccess { onBack() }.onFailure { message = "删除失败：${it.message ?: "未知错误"}"; deleting = false } } }) { Text("永久删除", color = ErrorRed) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } }
    )
    if (confirmRemoteIndex) AlertDialog(
        onDismissRequest = { confirmRemoteIndex = false }, title = { Text("确认远程索引") },
        text = { Text("将为 $chunkCount 个有效分段发起 Embedding 请求，可能产生服务商费用。") },
        confirmButton = { TextButton(enabled = !indexing, onClick = { scope.launch {
            indexing = true
            runCatching { viewModel.indexKnowledgeBase(current.id, true) }
                .onSuccess { message = "索引完成：成功 ${it.succeeded}，失败 ${it.failed}" }
                .onFailure { message = "索引失败：${it.message ?: "未知错误"}" }
            book = viewModel.getKnowledgeBase(current.id)
            indexing = false
            confirmRemoteIndex = false
        } }) { Text(if (indexing) "索引中" else "确认并开始") } },
        dismissButton = { TextButton(onClick = { confirmRemoteIndex = false }) { Text("取消") } }
    )
}

@Composable private fun DetailRow(title: String, subtitle: String, onClick: () -> Unit, color: androidx.compose.ui.graphics.Color = TextPrimary) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = color); Text(subtitle, fontSize = 12.sp, color = TextSecondary) }
        Spacer(Modifier.width(8.dp)); Text("›", fontSize = 22.sp, color = TextSecondary)
    }
}
