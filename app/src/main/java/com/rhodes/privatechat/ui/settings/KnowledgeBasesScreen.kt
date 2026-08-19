package com.rhodes.privatechat.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.shared.model.KnowledgeBase
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.theme.BG
import com.rhodes.privatechat.ui.theme.ErrorRed
import com.rhodes.privatechat.ui.theme.Primary
import com.rhodes.privatechat.ui.theme.TextPrimary
import com.rhodes.privatechat.ui.theme.TextSecondary
import com.rhodes.privatechat.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun KnowledgeBasesScreen(onBack: () -> Unit, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val viewModel: MainViewModel = koinViewModel()
    val settings: SettingsRepository = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var books by remember { mutableStateOf<List<KnowledgeBase>>(emptyList()) }
    var bookStats by remember { mutableStateOf<Map<String, Triple<Int, Int, Int>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var creating by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var remoteConfirm by remember { mutableStateOf<KnowledgeBase?>(null) }
    var remoteChunkCount by remember { mutableStateOf(0) }
    var deferredRemoteConfirmationIds by remember { mutableStateOf(emptySet<String>()) }
    var message by remember { mutableStateOf("") }

    suspend fun refresh(initial: Boolean = false) {
        if (initial) loading = true
        val refreshedBooks = runCatching { viewModel.getKnowledgeBases() }
            .getOrElse { emptyList() }
            // The support manual is a system record. Never infer this from its display name.
            .filterNot { it.sourceType == "system_support" || it.id == settings.supportKnowledgeBaseId }
        books = refreshedBooks
        books.filter { it.indexStatus == "processing" }.forEach { viewModel.resumeKnowledgeBaseProcessing(it) }
        bookStats = books.associate { book ->
            val chunks = viewModel.getKnowledgeBaseChunks(book.id)
            val roles = viewModel.getKnowledgeBaseAssignmentsForBook(book.id).count { it.enabled }
            val surfaces = listOf("private_chat", "group_chat", "moment", "comment", "diary").count { settings.isKnowledgeBaseEnabledForBook(book.id, it) }
            book.id to Triple(chunks.size, roles, surfaces)
        }
        if (settings.vectorProviderMode == "third_party" && remoteConfirm == null) {
            books.firstOrNull { it.indexStatus == "pending_confirm" && it.id !in deferredRemoteConfirmationIds }?.let { pending ->
                runCatching { viewModel.planKnowledgeBaseIndex(pending.id).chunkCount }
                    .onSuccess { remoteChunkCount = it; remoteConfirm = pending }
            }
        }
        if (initial) loading = false
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        refresh(initial = true)
        while (true) {
            val hasActiveWork = books.any {
                it.indexStatus == "processing" || it.indexStatus == "pending_confirm" ||
                    it.indexStatus == "indexing" || it.indexStatus.startsWith("indexing:")
            }
            if (hasActiveWork) {
                delay(1_000)
                refresh()
            } else {
                delay(5_000)
            }
        }
    }

    fun startIndex(book: KnowledgeBase, confirmed: Boolean) = scope.launch {
        message = "正在索引《${book.name}》…"
        val result = runCatching { viewModel.indexKnowledgeBase(book.id, confirmed) }.getOrElse {
            message = "索引失败：${it.message?.take(120) ?: "未知错误"}"
            refresh()
            return@launch
        }
        message = "索引完成：成功 ${result.succeeded}，失败 ${result.failed}"
        refresh()
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val fileName = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                ?.ifBlank { null }
                ?: "导入知识库.txt"
            val bytes = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val limit = com.rhodes.privatechat.shared.knowledge.KnowledgeBaseTextProcessor.MAX_FILE_BYTES
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (output.size() + read > limit) throw IllegalArgumentException("知识库文件不能超过 2 MB")
                            output.write(buffer, 0, read)
                        }
                        output.toByteArray()
                    }
                }
            }.getOrNull()
            if (bytes == null) {
                message = "无法读取所选文件"
                return@launch
            }
            val name = fileName.substringBeforeLast('.').ifBlank { "未命名知识库" }
            val book = runCatching { viewModel.importKnowledgeBase(fileName, bytes, name) }.getOrElse {
                message = "导入失败：${it.message?.take(120) ?: "未知错误"}"
                return@launch
            }
        if (settings.vectorProviderMode == "third_party") {
                remoteChunkCount = viewModel.planKnowledgeBaseIndex(book.id).chunkCount
                remoteConfirm = book
        } else message = "已导入《${book.name}》，正在后台建立本地索引"
            refresh()
        }
    }

    SaveableSettingsScaffold(
        title = "知识库",
        onBack = onBack,
        modifier = modifier.fillMaxSize().background(BG).systemBarsPadding(),
        showSave = false,
        icon = { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = Primary) },
    ) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("导入 TXT 或 MD 后会自动分段。知识库仅在关联角色和当前话题相关时才会在后续生成中使用。", fontSize = 12.sp, color = TextSecondary)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { creating = true }, modifier = Modifier.weight(1f)) { Text("新建知识库") }
                Button(onClick = { importer.launch(arrayOf("text/plain", "text/markdown", "text/*")) }, modifier = Modifier.weight(1f)) { Text("导入 TXT / MD") }
            }
            if (message.isNotBlank()) Text(message, modifier = Modifier.padding(top = 8.dp), fontSize = 12.sp, color = TextSecondary)
            if (loading) Text("正在读取知识库…", modifier = Modifier.padding(top = 20.dp), color = TextSecondary)
            books.forEach { book ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp).clickable { onOpen(book.id) }
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.surface).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(book.name, color = TextPrimary)
                        val stats = bookStats[book.id] ?: Triple(0, 0, 0)
                        Text("${book.rawContent.length} 字 · ${stats.first} 个分段 · 关联 ${stats.second} 个角色 · ${stats.third} 个场景", fontSize = 12.sp, color = TextSecondary)
                        Text(indexStatusText(book.indexStatus), fontSize = 12.sp, color = TextSecondary)
                    }
                    Text("›", color = TextSecondary, fontSize = 24.sp)
                }
            }
        }
    }

    if (creating) KnowledgeBaseTextDialog(
        saving = saving,
        onDismiss = { if (!saving) creating = false },
        onSave = { name, content ->
            if (saving) return@KnowledgeBaseTextDialog
            saving = true
            scope.launch {
                val book = runCatching { viewModel.saveKnowledgeBaseTextInBackground(name, content) }.getOrElse {
                    message = "保存失败：${it.message?.take(120) ?: "未知错误"}"
                    saving = false
                    return@launch
                }
                creating = false
                saving = false
                message = "已保存《${book.name}》，正在后台分段"
                refresh()
            }
        }
    )
    remoteConfirm?.let { book -> AlertDialog(
        onDismissRequest = { deferredRemoteConfirmationIds = deferredRemoteConfirmationIds + book.id; remoteConfirm = null },
        title = { Text("确认远程向量化") },
        text = { Text("《${book.name}》预计会发起 $remoteChunkCount 次 Embedding 请求，可能产生服务商费用。") },
        confirmButton = { TextButton(onClick = { remoteConfirm = null; startIndex(book, true) }) { Text("确认并索引", color = Primary) } },
        dismissButton = { TextButton(onClick = { deferredRemoteConfirmationIds = deferredRemoteConfirmationIds + book.id; remoteConfirm = null }) { Text("稍后") } },
    ) }
}

@Composable
private fun KnowledgeBaseTextDialog(
    initialName: String = "",
    initialContent: String = "",
    title: String = "新建知识库",
    saving: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var content by remember(initialContent) { mutableStateOf(initialContent) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column { OutlinedTextField(name, { name = it }, label = { Text("名称") }); OutlinedTextField(content, { content = it }, label = { Text("正文") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 6) } },
        confirmButton = { TextButton(enabled = !saving, onClick = { onSave(name, content) }) { Text(if (saving) "处理中…" else "保存") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("取消") } },
    )
}

fun indexStatusText(status: String): String = when (status) {
    "ready" -> "索引完成"
    "processing" -> "处理中：正在分段"
    "pending_confirm" -> "已分段，等待确认索引"
    "indexing" -> "索引中"
    else -> if (status.startsWith("indexing:")) "索引中：${status.removePrefix("indexing:")}" else when (status) {
    "partial_failed" -> "部分失败，可点此重建"
    "failed" -> "索引失败，可点此重建"
    else -> "等待索引"
    }
}
