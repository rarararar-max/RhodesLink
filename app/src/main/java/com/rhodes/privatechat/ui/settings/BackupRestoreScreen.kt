package com.rhodes.privatechat.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card as MaterialCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.data.backup.BackupFileReader
import com.rhodes.privatechat.data.backup.BackupFileWriter
import com.rhodes.privatechat.data.backup.BackupMediaCollector
import com.rhodes.privatechat.data.backup.BackupSnapshotBuilder
import com.rhodes.privatechat.data.backup.BackupValidationResult
import com.rhodes.privatechat.data.backup.OperatorPackage
import com.rhodes.privatechat.data.backup.OperatorPackageReader
import com.rhodes.privatechat.data.backup.OperatorPackageService
import com.rhodes.privatechat.data.backup.OperatorPackageWriter
import com.rhodes.privatechat.data.backup.OperatorImportMode
import com.rhodes.privatechat.data.backup.ReadableChatExporter
import com.rhodes.privatechat.data.backup.BackupRestoreCoordinator
import com.rhodes.privatechat.data.backup.BackupRestoreProgress
import com.rhodes.privatechat.data.backup.RestoreDatabaseExecutor
import com.rhodes.privatechat.viewmodel.MainViewModel
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.theme.BG
import com.rhodes.privatechat.ui.theme.Card as CardBackground
import com.rhodes.privatechat.ui.theme.Primary
import com.rhodes.privatechat.ui.theme.PrimaryContainer
import com.rhodes.privatechat.ui.theme.TextPrimary
import com.rhodes.privatechat.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface BackupUiState {
    data object Idle : BackupUiState
    data class Working(val detail: String) : BackupUiState
    data class Success(val title: String, val detail: String) : BackupUiState
    data class Preview(val manifest: com.rhodes.privatechat.data.backup.BackupManifest) : BackupUiState
    data class RestoreReady(val uri: Uri?, val safetyFile: java.io.File?, val manifest: com.rhodes.privatechat.data.backup.BackupManifest) : BackupUiState
    data class Error(val detail: String) : BackupUiState
}

@Composable
fun BackupRestoreScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository: ChatRepository = koinInject()
    val settings: SettingsRepository = koinInject()
    val mainViewModel: MainViewModel = koinInject()
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<BackupUiState>(BackupUiState.Idle) }
    var showOperatorPicker by remember { mutableStateOf(false) }
    var pendingOperatorCard by remember { mutableStateOf<OperatorPackage?>(null) }
    var selectingUpdateTarget by remember { mutableStateOf(false) }
    var includeBackupMedia by remember { mutableStateOf(true) }
    var pendingSafetyExport by remember { mutableStateOf<java.io.File?>(null) }
    val writer = remember { BackupFileWriter(appVersion(context), schemaVersion = 1) }
    val reader = remember { BackupFileReader() }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            state = BackupUiState.Working("正在整理数据和图片")
            runCatching {
                withContext(Dispatchers.IO) {
                    val snapshot = BackupSnapshotBuilder(repository, settings).build()
                    val (payload, media) = if (includeBackupMedia) {
                        BackupMediaCollector(context).attachCollectedMedia(snapshot)
                    } else snapshot to BackupMediaCollector.Result(emptyList(), emptyList(), emptyList())
                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        writer.writeFullBackup(output, payload, media.sources, media.sources.isNotEmpty())
                    } ?: error("无法写入所选位置")
                    buildString {
                        append("备份已保存：${payload.content.operators.orEmpty().size} 个角色、${payload.content.messages.orEmpty().size} 条消息、${media.items.size} 个本机图片")
                        if (media.skippedUris.isNotEmpty()) append("。另有 ${media.skippedUris.size} 个外部图片未包含，换机后可能无法显示")
                    }
                }
            }.onSuccess { state = BackupUiState.Success("备份已保存", it) }
                .onFailure {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                    state = BackupUiState.Error(it.message ?: "创建备份失败")
                }
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            state = BackupUiState.Working("正在校验备份文件")
            val next = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    when (val result = reader.validate(input)) {
                        is BackupValidationResult.Valid -> BackupUiState.RestoreReady(uri, null, result.manifest)
                        is BackupValidationResult.Invalid -> BackupUiState.Error(result.reason)
                    }
                } ?: BackupUiState.Error("无法读取所选文件")
            }
            state = next
        }
    }
    var selectedOperatorId by remember { mutableStateOf<String?>(null) }
    var selectedChatExport by remember { mutableStateOf<com.rhodes.privatechat.shared.model.ChatSession?>(null) }
    var selectedChatExportFormat by remember { mutableStateOf("markdown") }
    var showChatPicker by remember { mutableStateOf(false) }
    var availableSessions by remember { mutableStateOf(emptyList<com.rhodes.privatechat.shared.model.ChatSession>()) }
    var availableOperators by remember { mutableStateOf(emptyList<com.rhodes.privatechat.shared.model.Operator>()) }
    LaunchedEffect(Unit) {
        availableOperators = withContext(Dispatchers.IO) { repository.getAllOperatorsSync() }
        availableSessions = withContext(Dispatchers.IO) { repository.getAllSessionsSync() }
    }
    val createOperatorCard = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val operatorId = selectedOperatorId
        selectedOperatorId = null
        if (uri == null || operatorId == null) return@rememberLauncherForActivityResult
        scope.launch {
            state = BackupUiState.Working("正在创建角色卡")
            runCatching {
                withContext(Dispatchers.IO) {
                    val (payload, avatar) = OperatorPackageService(repository, settings).exportCard(context, operatorId)
                    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        OperatorPackageWriter(appVersion(context)).write(output, payload, avatar)
                    } ?: error("无法写入所选位置")
                    "角色卡已保存：${payload.operator.name}"
                }
            }.onSuccess { state = BackupUiState.Success("角色设定卡已保存", it) }
                .onFailure {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                    state = BackupUiState.Error(it.message ?: "创建角色设定卡失败")
                }
        }
    }
    val openOperatorCard = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            state = BackupUiState.Working("正在校验角色卡")
            runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { OperatorPackageReader().readCompatible(it) } ?: error("无法读取所选文件") }
            }.onSuccess { pendingOperatorCard = it; state = BackupUiState.Idle }
                .onFailure { state = BackupUiState.Error(it.message ?: "角色卡无效") }
        }
    }
    val createReadableChat = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val session = selectedChatExport
        selectedChatExport = null
        if (uri == null || session == null) return@rememberLauncherForActivityResult
        scope.launch {
            state = BackupUiState.Working("正在导出可阅读聊天记录")
            runCatching {
                withContext(Dispatchers.IO) {
                    val messages = repository.getMessagesSync(session.id)
                    val content = if (selectedChatExportFormat == "text") {
                        ReadableChatExporter.text("与${session.operatorName}的聊天记录", settings.userName, messages)
                    } else ReadableChatExporter.markdown("与${session.operatorName}的聊天记录", settings.userName, messages)
                    context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { it.write(content) } ?: error("无法写入所选位置")
                    "聊天记录已导出：${messages.size} 条消息"
                }
            }.onSuccess { state = BackupUiState.Success("聊天记录已保存", it) }
                .onFailure {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                    state = BackupUiState.Error(it.message ?: "导出聊天记录失败")
                }
        }
    }
    val exportSafetyBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val source = pendingSafetyExport
        pendingSafetyExport = null
        if (uri == null || source == null) return@rememberLauncherForActivityResult
        scope.launch {
            state = BackupUiState.Working("正在导出恢复前备份")
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { output -> source.inputStream().use { it.copyTo(output) } }
                        ?: error("无法写入所选位置")
                }
            }.onSuccess { state = BackupUiState.Success("恢复前备份已导出", source.name) }
                .onFailure { state = BackupUiState.Error(it.message ?: "导出恢复前备份失败") }
        }
    }
    fun refreshSafetyBackups(): List<java.io.File> =
        java.io.File(context.filesDir, "restore-safety-backups").listFiles()
            ?.filter { it.isFile && it.extension == "rbackup" }?.sortedByDescending { it.lastModified() }.orEmpty()
    var safetyBackups by remember { mutableStateOf(refreshSafetyBackups()) }

    Column(modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Text("备份与迁移", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BackupSection(
                icon = Icons.Default.Backup,
                title = "完整备份",
                description = "换手机、刷机前使用。保存角色、聊天、记忆、日记、动态和全部非敏感设置。不会保存账号密码或模型密钥。",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { includeBackupMedia = true; createBackup.launch("明日方舟完整备份_${timestamp()}.rbackup") }, modifier = Modifier.weight(1f)) { Text("包含本机已保存图片") }
                    OutlinedButton(onClick = { includeBackupMedia = false; createBackup.launch("明日方舟文字备份_${timestamp()}.rbackup") }, modifier = Modifier.weight(1f)) { Text("仅文字和资料") }
                }
            }
            BackupSection(
                icon = Icons.Default.Description,
                title = "保存聊天记录",
                description = "把一段私聊或群聊保存成可阅读文件，方便分享或整理故事。这个文件不能用于找回数据。",
            ) {
                OutlinedButton(onClick = { showChatPicker = true }, modifier = Modifier.fillMaxWidth()) { Text("选择聊天记录") }
            }
            BackupSection(
                icon = Icons.Default.FolderOpen,
                title = "导入完整备份",
                description = "用于迁移到新设备或恢复以前导出的完整备份。导入会完全替换当前数据。",
            ) {
                OutlinedButton(onClick = { openBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("选择完整备份文件")
                }
            }
            BackupSection(
                icon = Icons.Default.Backup,
                title = "恢复前备份",
                description = "每次导入前自动保留当前数据。可再次恢复或导出到设备保存，最多保留最近 5 份。",
            ) {
                if (safetyBackups.isEmpty()) Text("暂无恢复前备份", fontSize = 12.sp, color = TextSecondary)
                else safetyBackups.forEach { backup ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${formatTime(backup.lastModified())} · ${formatBytes(backup.length())}", modifier = Modifier.weight(1f), fontSize = 12.sp, color = TextSecondary)
                        TextButton(onClick = { pendingSafetyExport = backup; exportSafetyBackup.launch(backup.name) }) { Text("导出") }
                        TextButton(onClick = {
                            scope.launch {
                                state = BackupUiState.Working("正在校验恢复前备份")
                                state = withContext(Dispatchers.IO) {
                                    backup.inputStream().use { input ->
                                        when (val result = reader.validate(input)) {
                                            is BackupValidationResult.Valid -> BackupUiState.RestoreReady(null, backup, result.manifest)
                                            is BackupValidationResult.Invalid -> BackupUiState.Error(result.reason)
                                        }
                                    }
                                }
                            }
                        }) { Text("恢复") }
                    }
                }
            }
            BackupSection(
                icon = Icons.Default.Description,
                title = "角色设定分享",
                description = "分享角色人设和头像。关系仅在接收方已有对应角色时导入；聊天、记忆、日记和动态不会包含。",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { showOperatorPicker = true }, modifier = Modifier.weight(1f)) { Text("导出角色设定") }
                    OutlinedButton(onClick = { openOperatorCard.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "*/*")) }, modifier = Modifier.weight(1f)) { Text("导入角色设定") }
                }
            }
            BackupSection(
                icon = Icons.Default.Description,
                title = "使用说明",
                description = "导入完整备份会替换当前所有数据，开始前会自动保存当前数据。外部图片可能无法迁移；换手机后需要重新填写模型服务信息。",
            ) {}
            when (val current = state) {
                is BackupUiState.Working -> Text(current.detail, color = Primary, fontWeight = FontWeight.Medium)
                is BackupUiState.Success -> ResultCard(current.title, current.detail)
                is BackupUiState.Error -> ResultCard("操作失败", current.detail, error = true)
                else -> Unit
            }
        }
    }
    if (state is BackupUiState.RestoreReady) {
        val ready = state as BackupUiState.RestoreReady
        val manifest = ready.manifest
        AlertDialog(
            onDismissRequest = { state = BackupUiState.Idle },
            title = { Text(if (ready.safetyFile == null) "发现完整备份" else "恢复前备份") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("创建时间：${formatTime(manifest.createdAt)}")
                    Text("角色：${manifest.recordCounts["operators"] ?: 0}  会话：${manifest.recordCounts["sessions"] ?: 0}")
                    Text("消息：${manifest.recordCounts["messages"] ?: 0}  日记：${manifest.recordCounts["diaries"] ?: 0}")
                    Text("动态：${manifest.recordCounts["moments"] ?: 0}  图片：${manifest.recordCounts["mediaFiles"] ?: 0}")
                    Text("继续后会清空当前角色、聊天、记忆、日记和动态，再恢复此备份。恢复前会再创建一份当前数据的安全备份，并最多保留最近 5 份；可在本页导出或再次恢复。若还需保存到设备，请先取消并使用“完整备份”。API Key 和模型密钥不会恢复。", color = TextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    val coordinator = BackupRestoreCoordinator(
                        context, repository, settings, BackupSnapshotBuilder(repository, settings), writer,
                        restoreExecutor = { payload, progress -> RestoreDatabaseExecutor(repository).restore(payload) },
                    )
                    val result = withContext(Dispatchers.IO) {
                        coordinator.restore(
                            openInput = {
                                ready.safetyFile?.inputStream()
                                    ?: ready.uri?.let { context.contentResolver.openInputStream(it) }
                                    ?: error("无法再次读取备份文件")
                            },
                            onProgress = { progress: BackupRestoreProgress -> state = BackupUiState.Working(progress.detail) },
                        )
                    }
                    if (result.success) {
                        state = BackupUiState.Working("正在重建记忆检索索引")
                        val indexResult = withContext(Dispatchers.IO) { mainViewModel.rebuildAllMemoryIndexes() }
                        safetyBackups = refreshSafetyBackups()
                        state = BackupUiState.Success("恢复完成", "已恢复备份中的数据。记忆索引：成功 ${indexResult.succeeded}，失败 ${indexResult.failed}。恢复前安全备份：${result.safetyBackup?.name ?: "已创建"}，可在本页导出或再次恢复。请重新配置模型服务。")
                    } else state = BackupUiState.Error(result.reason)
                }
            }) { Text("清空并恢复") } },
            dismissButton = { TextButton(onClick = { state = BackupUiState.Idle }) { Text("取消") } },
        )
    }
    if (showOperatorPicker) {
        AlertDialog(
            onDismissRequest = { showOperatorPicker = false },
            title = { Text("选择要导出的角色") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(availableOperators, key = { it.id }) { operator ->
                        TextButton(onClick = {
                            selectedOperatorId = operator.id
                            showOperatorPicker = false
                            createOperatorCard.launch("${operator.name}_角色包_${timestamp()}.roperator")
                        }) { Text(operator.name) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showOperatorPicker = false }) { Text("取消") } },
        )
    }
    pendingOperatorCard?.let { card ->
        AlertDialog(
            onDismissRequest = { pendingOperatorCard = null },
            title = { Text("角色设定卡：${card.payload.operator.name}") },
            text = { Text("包含角色资料、1/2/3 号私聊和群聊人设槽、${card.payload.relationships.size} 条关系${if (card.avatarBytes != null) "和头像" else "（未包含头像）"}。不会导入私聊、记忆、日记、动态、评论、聊天背景、角色音量或权限开关。关系仅按角色 ID 匹配；其余会跳过。") },
            confirmButton = { TextButton(onClick = {
                scope.launch { val result = withContext(Dispatchers.IO) { OperatorPackageService(repository, settings).importCard(context, card, OperatorImportMode.NEW) }; availableOperators = withContext(Dispatchers.IO) { repository.getAllOperatorsSync() }; state = BackupUiState.Success("角色已导入", "已创建新角色；已导入 1/2/3 号私聊和群聊人设槽；导入关系 ${result.importedRelationships} 条，跳过 ${result.skippedRelationships} 条。"); pendingOperatorCard = null }
            }) { Text("创建副本") } },
            dismissButton = { TextButton(onClick = {
                selectingUpdateTarget = true
            }) { Text("覆盖现有角色") } },
        )
    }
    if (selectingUpdateTarget) {
        val card = pendingOperatorCard
        val targets = card?.let { availableOperators }.orEmpty()
        AlertDialog(
            onDismissRequest = { selectingUpdateTarget = false },
            title = { Text("选择要覆盖的角色") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(targets, key = { it.id }) { target ->
                        TextButton(onClick = {
                            val source = pendingOperatorCard ?: return@TextButton
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { OperatorPackageService(repository, settings).importCard(context, source, OperatorImportMode.FULL_REPLACE, target.id) }
                                availableOperators = withContext(Dispatchers.IO) { repository.getAllOperatorsSync() }
                                state = BackupUiState.Success("角色资料已覆盖", "已覆盖 ${target.name} 的人设、状态、好感度、关系、语音和角色数值；导入关系 ${result.importedRelationships} 条，跳过 ${result.skippedRelationships} 条。聊天和共同经历没有改动。")
                                selectingUpdateTarget = false
                                pendingOperatorCard = null
                            }
                        }) {
                            Column { Text(target.name); Text("ID：${target.id.take(12)}", fontSize = 11.sp, color = TextSecondary) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectingUpdateTarget = false }) { Text("取消") } },
        )
    }
    if (showChatPicker) {
        AlertDialog(
            onDismissRequest = { showChatPicker = false },
            title = { Text("选择要导出的聊天") },
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(availableSessions, key = { it.id }) { session ->
                        TextButton(onClick = {
                            selectedChatExport = session
                            showChatPicker = false
                            selectedChatExportFormat = "markdown"
                            createReadableChat.launch("${session.operatorName}_聊天记录_${timestamp()}.md")
                        }) { Text("导出「${session.operatorName}」聊天（Markdown .md）") }
                        TextButton(onClick = {
                            selectedChatExport = session
                            showChatPicker = false
                            selectedChatExportFormat = "text"
                            createReadableChat.launch("${session.operatorName}_聊天记录_${timestamp()}.txt")
                        }) { Text("导出「${session.operatorName}」聊天（纯文本 .txt）") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChatPicker = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun BackupSection(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, action: @Composable () -> Unit) {
    MaterialCard(colors = CardDefaults.cardColors(containerColor = CardBackground), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(10.dp))
                Text(title, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
            Spacer(Modifier.height(8.dp))
            Text(description, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            action()
        }
    }
}

@Composable
private fun ResultCard(title: String, detail: String, error: Boolean = false) {
    MaterialCard(colors = CardDefaults.cardColors(containerColor = if (error) MaterialTheme.colorScheme.errorContainer else PrimaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(detail, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault()).format(Date())
private fun formatTime(value: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
private fun formatBytes(value: Long): String = if (value < 1024 * 1024) "${value / 1024} KB" else "%.1f MB".format(Locale.getDefault(), value / 1024f / 1024f)
private fun appVersion(context: Context): String = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
