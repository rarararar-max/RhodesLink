package com.rhodes.privatechat.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.SystemClock
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.shared.model.ChatMessage
import com.rhodes.privatechat.shared.model.ChatSession
import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.data.backup.BackupContentFilter
import com.rhodes.privatechat.data.backup.BackupContentSelection
import com.rhodes.privatechat.data.backup.BackupFileReader
import com.rhodes.privatechat.data.backup.BackupFileWriter
import com.rhodes.privatechat.data.backup.BackupMediaCollector
import com.rhodes.privatechat.data.backup.BackupSnapshotBuilder
import com.rhodes.privatechat.data.backup.PortableSettings
import com.rhodes.privatechat.viewmodel.shared.PromptTemplates
import com.rhodes.privatechat.viewmodel.AiSupportMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.rhodes.privatechat.ui.support.AiSupportContract
import com.rhodes.privatechat.shared.vector.MemoryVectorService
import com.rhodes.privatechat.shared.vector.VectorSearchRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap

data class ProblemCheckResult(val report: String, val summary: String, val success: Boolean)

enum class ProblemStageStatus { PENDING, RUNNING, SUCCESS, FAILED, TIMEOUT, ABANDONED, NOT_RUN }

data class StageProgress(
    val status: ProblemStageStatus = ProblemStageStatus.PENDING,
    val startedAt: Long = 0L,
    val finishedAt: Long = 0L,
    val elapsedMs: Long = 0L,
    val detail: String = ""
)

data class ProblemCheckProgress(
    val checkId: String = "",
    val startedAt: Long = 0L,
    val deadlineAt: Long = 0L,
    val finishedAt: Long = 0L,
    val currentStage: String = "idle",
    val stages: Map<String, StageProgress> = emptyMap(),
    val abandoned: Boolean = false
)

/** Diagnostic work is deliberately fire-and-forget. UI never awaits a probe. */
object ProblemChecker {
    // A complete export diagnosis includes multiple independently timed backup reads.
    private const val TOTAL_TIMEOUT_MS = 300_000L
    private const val LOCAL_TIMEOUT_MS = 15_000L
    private const val COPY_TIMEOUT_MS = 30_000L
    private const val MODEL_PROBE_TIMEOUT_MS = 60_000L
    private const val DB_NAME = "rhodes_terminal.db"
    private val checkScopes = mutableMapOf<String, CoroutineScope>()
    private val current = AtomicReference<ProblemCheckProgress>()
    private val active = AtomicReference<String?>(null)
    private val diagnosticEmbeddings = ConcurrentHashMap<String, List<Double>>()

    fun progress(): ProblemCheckProgress = current.get() ?: ProblemCheckProgress()

    private fun checkScope(checkId: String): CoroutineScope = synchronized(checkScopes) {
        checkScopes.getOrPut(checkId) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    }

    private fun clearCheckScope(checkId: String) {
        synchronized(checkScopes) { checkScopes.remove(checkId) }?.cancel("problem check finished")
        diagnosticEmbeddings.remove(checkId)
    }

    fun start(
        context: Context,
        repository: ChatRepository,
        sharedUtils: SharedUtils,
        appState: AppStateHolder,
        versionName: String,
        versionCode: Int
    ): String {
        val checkId = UUID.randomUUID().toString().replace("-", "").take(8)
        if (!active.compareAndSet(null, checkId)) return active.get() ?: checkId
        val now = System.currentTimeMillis()
        val names = listOf("cleanup_previous_probe", "db_native_read_probe", "db_native_backup_tables", "db_repository_read_probe", "embedding_compute_probe", "vector_diagnostics_probe", "memory_items_stats_probe", "backup_snapshot_timing_probe", "database_open", "database_schema", "database_counts", "persistent_state_probe", "prompt_template_integrity", "kb_sql_count", "kb_sql_metadata", "kb_sql_content_size", "knowledge_base_metadata", "knowledge_base_assignments", "knowledge_base_chunks", "knowledge_base_readiness", "vector_sql_count", "vector_sql_signature_size", "vector_sql_invalid_rows", "vector_config_probe", "vector_embedding_gateway_probe", "vector_store_query_probe", "vector_local_search_probe", "support_manual_probe", "support_transcript_probe", "session_integrity", "session_message_audit", "contacts_recovery", "database_copy_write_test", "backup_roles_sessions", "backup_relationships", "backup_messages", "backup_knowledge_bases", "backup_memories", "backup_anchors", "backup_memory_items", "backup_moments", "backup_moment_likes", "backup_moment_comments", "backup_diaries", "backup_archives_display", "backup_gifts", "backup_dispatches", "backup_shared_experiences", "backup_mahjong", "backup_settings_snapshot", "backup_snapshot_probe", "backup_file_probe", "private_message_probe", "private_pipeline_history", "private_pipeline_context", "private_pipeline_reply_parse", "private_pipeline_last_state", "group_message_probe", "group_pipeline_roster_history", "group_pipeline_context", "group_pipeline_reply_parse", "private_ai_probe", "group_ai_probe", "cleanup")
        current.set(ProblemCheckProgress(checkId, now, now + TOTAL_TIMEOUT_MS, currentStage = "starting", stages = names.associateWith { StageProgress() }))
        startDetachedLocalProbe(checkId, "cleanup_previous_probe", LOCAL_TIMEOUT_MS) {
            val files = cleanupOldProbes(context)
            val cleanup = cleanupStaleChatProbes(repository)
            "$files,$cleanup"
        }
        // Network work has its own deadline and does not wait for a database lane.
        startDetachedStructuredPrivateAiProbe(checkId, sharedUtils)
        startDetachedAiProbe(checkId, "group_ai_probe", sharedUtils, "ProblemCheckGroup")
        startDetachedLocalProbe(checkId, "vector_config_probe", LOCAL_TIMEOUT_MS) { vectorConfigProbe() }
        startDetachedLocalProbe(checkId, "db_native_read_probe", LOCAL_TIMEOUT_MS) { nativeDatabaseReadProbe(context) }
        startDetachedLocalProbe(checkId, "db_native_backup_tables", LOCAL_TIMEOUT_MS) { nativeBackupTableProbe(context) }
        startDetachedLocalProbe(checkId, "db_repository_read_probe", LOCAL_TIMEOUT_MS) { repositoryDatabaseReadProbe(repository) }
        startDetachedLocalProbe(checkId, "embedding_compute_probe", LOCAL_TIMEOUT_MS) { localEmbeddingComputeProbe() }
        startDetachedLocalProbe(checkId, "vector_diagnostics_probe", LOCAL_TIMEOUT_MS) { vectorDiagnosticsProbe() }
        startDetachedLocalProbe(checkId, "memory_items_stats_probe", LOCAL_TIMEOUT_MS) { memoryItemsStatsProbe(context) }
        startDetachedLocalProbe(checkId, "vector_embedding_gateway_probe", COPY_TIMEOUT_MS) { vectorEmbeddingGatewayProbe(checkId) }
        // Candidate SQL is already measured without user data by vector_diagnostics_probe. Do not
        // run a second production-store search: a stuck platform SQLite call cannot be cancelled
        // reliably and would block interactive message writes on the shared dispatcher.
        mark(checkId, "vector_store_query_probe", ProblemStageStatus.NOT_RUN, detail = "reason=covered_by_vector_diagnostics_probe;avoids_production_dispatcher_contention")
        // A production-store search needs an isolated seeded vector to prove ranking semantics.
        // Do not write diagnostic vectors into user namespaces; configuration and embedding are
        // still checked above, while store ranking is covered by unit tests.
        mark(checkId, "vector_local_search_probe", ProblemStageStatus.NOT_RUN, detail = "reason=requires_isolated_seeded_vector_store;production_data_not_modified")
        mark(checkId, "backup_file_probe", ProblemStageStatus.NOT_RUN, detail = "depends_on=backup_snapshot_probe;run backup export separately when snapshot check passes")
        mark(checkId, "cleanup", ProblemStageStatus.NOT_RUN, detail = "temporary chat probes clean up their own data")
        checkScope(checkId).launch { runDatabaseLane(checkId, context, repository, sharedUtils, appState) }
        checkScope(checkId).launch {
            while (active.get() == checkId) {
                delay(100L)
                val snapshot = progress()
                val done = snapshot.stages.values.all {
                    it.status in setOf(
                        ProblemStageStatus.SUCCESS,
                        ProblemStageStatus.FAILED,
                        ProblemStageStatus.TIMEOUT,
                        ProblemStageStatus.NOT_RUN
                    )
                }
                if (done) {
                    current.compareAndSet(snapshot, snapshot.copy(finishedAt = System.currentTimeMillis()))
                    active.compareAndSet(checkId, null)
                    clearCheckScope(checkId)
                    break
                }
            }
        }
        checkScope(checkId).launch {
            delay(TOTAL_TIMEOUT_MS)
            abandon(checkId)
        }
        return checkId
    }

    fun abandon(checkId: String) {
        if (active.get() != checkId) return
        val old = progress()
        val now = System.currentTimeMillis()
        val stages = old.stages.mapValues { (_, value) ->
            when (value.status) {
                ProblemStageStatus.PENDING -> value.copy(status = ProblemStageStatus.NOT_RUN, finishedAt = now, elapsedMs = 0L, detail = "reason=global_deadline_before_dispatch")
                ProblemStageStatus.RUNNING -> value.copy(status = ProblemStageStatus.ABANDONED, finishedAt = now, elapsedMs = if (value.startedAt == 0L) 0L else now - value.startedAt, detail = value.detail.ifBlank { "reason=global_deadline_worker_may_continue" })
                else -> value
            }
        }
        current.set(old.copy(finishedAt = now, currentStage = old.currentStage, stages = stages, abandoned = true))
        active.compareAndSet(checkId, null)
        clearCheckScope(checkId)
    }

    private suspend fun launchProbe(checkId: String, name: String, timeoutMs: Long, block: suspend () -> Any?) {
        if (progress().abandoned) return
        mark(checkId, name, ProblemStageStatus.RUNNING)
        val started = SystemClock.elapsedRealtime()
        val result = runCatching { withTimeoutOrNull(timeoutMs) { block() } ?: throw SocketTimeoutException("stage timeout") }
        val elapsed = SystemClock.elapsedRealtime() - started
        result.fold(
            onSuccess = { mark(checkId, name, ProblemStageStatus.SUCCESS, elapsed, it?.toString().orEmpty()) },
            onFailure = {
                val message = it.message.orEmpty().replace('\n', ' ').replace('\t', ' ').take(240)
                val timeout = it is SocketTimeoutException
                val errorClass = if (timeout && it.message == "stage timeout") "STAGE_TIMEOUT" else errorClass(it)
                mark(checkId, name, if (timeout) ProblemStageStatus.TIMEOUT else ProblemStageStatus.FAILED, elapsed, "errorClass=$errorClass,errorMessage=$message")
            }
        )
    }

    /** AI gateways can ignore coroutine cancellation. Mark their result from a separate watcher so
     * a stalled network call never blocks the local database report. */
    private fun startDetachedAiProbe(checkId: String, name: String, sharedUtils: SharedUtils, logTag: String) {
        mark(checkId, name, ProblemStageStatus.RUNNING)
        val started = SystemClock.elapsedRealtime()
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        checkScope(checkId).launch {
            val result = runCatching {
                sharedUtils.chat(
                    listOf(AiMessage("system", "只回复：检测成功"), AiMessage("user", "请确认聊天模型可用。")),
                    logTag,
                    maxOutputTokens = 16
                ).trim()
            }
            if (!completed.compareAndSet(false, true)) return@launch
            val elapsed = SystemClock.elapsedRealtime() - started
            result.fold(
                onSuccess = { response ->
                    if (response.isBlank()) mark(checkId, name, ProblemStageStatus.FAILED, elapsed, "errorClass=AI_EMPTY_RESPONSE,errorMessage=AI returned empty response")
                    else mark(checkId, name, ProblemStageStatus.SUCCESS, elapsed, "responseLength=${response.length}")
                },
                onFailure = { error ->
                    mark(checkId, name, ProblemStageStatus.FAILED, elapsed, "errorClass=${errorClass(error)},errorMessage=${error.message.orEmpty().replace('\n', ' ').take(160)}")
                }
            )
        }
        checkScope(checkId).launch {
            delay(MODEL_PROBE_TIMEOUT_MS)
            if (completed.compareAndSet(false, true)) {
                mark(checkId, name, ProblemStageStatus.TIMEOUT, MODEL_PROBE_TIMEOUT_MS, "errorClass=AI_TIMEOUT,errorMessage=AI request exceeded ${MODEL_PROBE_TIMEOUT_MS / 1000}s")
            }
        }
    }

    private fun mark(checkId: String, name: String, status: ProblemStageStatus, elapsed: Long = 0L, detail: String = "") {
        while (true) {
            val old = progress()
            if (old.checkId != checkId || old.abandoned) return
            val now = System.currentTimeMillis()
            val previous = old.stages[name] ?: StageProgress()
            val stage = if (status == ProblemStageStatus.RUNNING) previous.copy(status = status, startedAt = now) else previous.copy(status = status, finishedAt = now, elapsedMs = elapsed, detail = detail)
            if (current.compareAndSet(old, old.copy(currentStage = name, stages = old.stages + (name to stage)))) {
                DebugLogger.diagnostic("Special/ProblemCheck", "checkId=$checkId,stage=$name,status=${status.name.lowercase()},elapsedMs=$elapsed,$detail")
                return
            }
        }
    }

    fun report(versionName: String, versionCode: Int): ProblemCheckResult {
        val snapshot = progress()
        val now = System.currentTimeMillis()
        val status = if (snapshot.abandoned) "timeout" else if (snapshot.stages.values.all { it.status in setOf(ProblemStageStatus.SUCCESS, ProblemStageStatus.FAILED, ProblemStageStatus.TIMEOUT, ProblemStageStatus.NOT_RUN) }) "completed" else "running"
        val primary = diagnosis(snapshot)
        val report = buildString {
            appendLine("RHODES_PROBLEM_CHECK")
            appendLine("reportVersion=10")
            appendLine("checkId=${snapshot.checkId}")
            appendLine("status=$status")
            appendLine("elapsedMs=${if (snapshot.startedAt == 0L) 0 else now - snapshot.startedAt}")
            appendLine("appVersion=$versionName($versionCode)")
            appendLine("androidSdk=${Build.VERSION.SDK_INT}")
            appendLine("private_reply_pipeline.last=${sharedPipelineState()}")
            snapshot.stages.forEach { (name, stage) ->
                appendLine("$name.status=${stage.status.name.lowercase()}")
                appendLine("$name.elapsedMs=${stage.elapsedMs}")
                if (stage.detail.isNotBlank()) appendLine("$name.detail=${stage.detail}")
            }
            appendLine("lastStage=${snapshot.currentStage}")
            appendLine("diagnosis.primary=$primary")
            appendLine("diagnosis.detail=${diagnosisDetail(snapshot, primary)}")
            appendLine("diagnosis.confidence=${if (snapshot.abandoned) "high" else "medium"}")
            appendLine("diagnosis.nextAction=${when { status == "running" -> "请保持应用在前台，等待status=completed后再复制"; snapshot.abandoned -> "根据lastStage和对应阶段detail处理；后台探针已脱离界面"; else -> "检查失败阶段的detail" }}")
        }
        val required = listOf("database_open", "database_schema", "database_counts", "persistent_state_probe", "prompt_template_integrity", "knowledge_base_readiness", "session_integrity", "database_copy_write_test", "backup_snapshot_probe", "support_manual_probe", "support_transcript_probe")
        val localSuccess = required.all { snapshot.stages[it]?.status == ProblemStageStatus.SUCCESS }
        val modelSuccess = listOf("private_ai_probe", "group_ai_probe").all { snapshot.stages[it]?.status == ProblemStageStatus.SUCCESS }
        return ProblemCheckResult(report, conciseReport(snapshot, status, versionName, versionCode), status == "completed" && localSuccess && modelSuccess && primary == "NO_DATABASE_PROBLEM_FOUND")
    }

    private fun diagnosisDetail(progress: ProblemCheckProgress, primary: String): String = when (primary) {
        "KNOWLEDGE_BASE_SQLDELIGHT_READ_FAILED" -> "native_sqlite=success,sqldelight_repository_getAll=timeout,likely=application_db_dispatcher_or_shared_driver_blocked"
        "VECTOR_TABLE_ACCESS_FAILED", "VECTOR_EMBEDDING_DATA_READ_FAILED" -> "native_vector_sqlite=failed,likely=vector_memories_table_or_driver_access"
        "EMBEDDING_GATEWAY_FAILED" -> "vector_mode=${runCatching { settingsForProbe().vectorProviderMode }.getOrDefault("unknown")},likely=embedding_gateway_or_network_path"
        "VECTOR_STORE_QUERY_FAILED" -> "embedding_gateway=completed;compare=vector_diagnostics_probe_and_db_native_backup_tables;likely=DatabaseDispatcher_queue_or_vector_memories_query"
        else -> "see_primary_stage_detail"
    }

    private fun conciseReport(snapshot: ProblemCheckProgress, status: String, versionName: String, versionCode: Int): String = buildString {
        val hasProblem = snapshot.stages.values.any { it.status in setOf(ProblemStageStatus.FAILED, ProblemStageStatus.TIMEOUT, ProblemStageStatus.ABANDONED) }
        appendLine("自检结论：${when (status) { "running" -> "检查中"; "timeout" -> "检查未在 ${TOTAL_TIMEOUT_MS / 1000} 秒内完成"; else -> if (hasProblem) "发现需要处理的问题" else "通过" }}")
        appendLine("版本：$versionName($versionCode) · 检查ID：${snapshot.checkId}")
        if (status == "running") appendLine("当前步骤：${snapshot.currentStage}")
        appendPipeline(snapshot, "私聊发送与存储", listOf("private_message_probe", "private_pipeline_history", "private_pipeline_reply_parse", "private_pipeline_last_state"))
        appendPipeline(snapshot, "提示词模板与知识库本地完整性", listOf("prompt_template_integrity", "kb_sql_count", "kb_sql_metadata", "kb_sql_content_size", "knowledge_base_metadata", "knowledge_base_assignments", "knowledge_base_chunks", "knowledge_base_readiness"))
        appendPipeline(snapshot, "私聊上下文与提示词前置条件", listOf("private_pipeline_context"))
        appendPipeline(snapshot, "私聊 AI 结构化回复", listOf("private_ai_probe"))
        appendPipeline(snapshot, "群聊发送与存储", listOf("group_message_probe", "group_pipeline_roster_history", "group_pipeline_reply_parse"))
        appendPipeline(snapshot, "群聊上下文与提示词前置条件", listOf("group_pipeline_context"))
        appendPipeline(snapshot, "群聊 AI 回复", listOf("group_ai_probe"))
        appendPipeline(snapshot, "向量化与记忆检索", listOf("vector_config_probe", "embedding_compute_probe", "vector_diagnostics_probe", "memory_items_stats_probe", "vector_sql_count", "vector_sql_signature_size", "vector_sql_invalid_rows", "vector_embedding_gateway_probe"))
        appendPipeline(snapshot, "客服本地能力", listOf("support_manual_probe", "support_transcript_probe"))
        appendPipeline(snapshot, "本地数据库与备份", listOf("database_open", "database_schema", "db_native_backup_tables", "persistent_state_probe", "session_integrity", "database_copy_write_test", "backup_snapshot_timing_probe", "backup_roles_sessions", "backup_relationships", "backup_messages", "backup_knowledge_bases", "backup_memories", "backup_anchors", "backup_memory_items", "backup_moments", "backup_moment_likes", "backup_moment_comments", "backup_diaries", "backup_archives_display", "backup_gifts", "backup_dispatches", "backup_shared_experiences", "backup_mahjong", "backup_settings_snapshot", "backup_snapshot_probe"))
        if (status != "running") appendLine("建议：${when {
            snapshot.stages["knowledge_base_metadata"]?.status == ProblemStageStatus.TIMEOUT && snapshot.stages["kb_sql_metadata"]?.status == ProblemStageStatus.SUCCESS -> "原生 SQLite 已正常读取知识库表，但应用 SQLDelight 仓库读取超时；已定位为应用数据库调度/共享连接路径，请查看 diagnosis.detail。"
            snapshot.stages["backup_moments"]?.status == ProblemStageStatus.TIMEOUT || snapshot.stages["backup_moment_likes"]?.status == ProblemStageStatus.TIMEOUT || snapshot.stages["backup_moment_comments"]?.status == ProblemStageStatus.TIMEOUT || snapshot.stages["backup_diaries"]?.status == ProblemStageStatus.TIMEOUT -> "备份已定位到具体社交数据分类超时；请导出技术报告并查看该步骤的记录数、文本大小和耗时。"
            snapshot.stages["backup_snapshot_probe"]?.status == ProblemStageStatus.TIMEOUT -> "备份快照构建超时；聊天与模型探针已独立继续执行。请查看技术详情中的备份数据分类。"
            snapshot.stages["private_ai_probe"]?.status == ProblemStageStatus.TIMEOUT || snapshot.stages["group_ai_probe"]?.status == ProblemStageStatus.TIMEOUT -> "模型自检超过 60 秒；本地消息保存若通过，请检查网络、模型服务节点或更换更快模型。"
            else -> "如有失败项，请查看技术详情中的失败步骤和原因。"
        }}")
    }

    private fun StringBuilder.appendPipeline(snapshot: ProblemCheckProgress, label: String, names: List<String>) {
        val stages = names.mapNotNull { name -> snapshot.stages[name]?.let { name to it } }
        val bad = stages.firstOrNull { (_, stage) -> stage.status in setOf(ProblemStageStatus.FAILED, ProblemStageStatus.TIMEOUT, ProblemStageStatus.ABANDONED) }
        val skipped = stages.any { (_, stage) -> stage.detail.startsWith("skipped=") }
        when {
            stages.isEmpty() || stages.any { (_, stage) -> stage.status == ProblemStageStatus.PENDING || stage.status == ProblemStageStatus.RUNNING } -> if (snapshot.abandoned) appendLine("$label：未完成\n  原因：总自检截止前未获得完整结果") else appendLine("$label：检查中")
            bad != null -> {
                val (name, stage) = bad
                appendLine("$label：${if (stage.status == ProblemStageStatus.TIMEOUT) "超时" else "失败"}")
                appendLine("  问题步骤：${stageDisplayName(name)}")
                appendLine("  原因：${conciseReason(stage.detail)}")
            }
            skipped -> appendLine("$label：未检查（当前没有可用会话）")
            else -> Unit
        }
    }

    /** A timed-out probe receives cancellation. Native SQLite/file calls may still need to drain. */
    private fun startDetachedLocalProbe(checkId: String, name: String, timeoutMs: Long, block: suspend () -> Any?) {
        mark(checkId, name, ProblemStageStatus.RUNNING)
        val started = SystemClock.elapsedRealtime()
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        val worker = checkScope(checkId).launch {
            val result = runCatching { block() }
            if (!completed.compareAndSet(false, true)) return@launch
            val elapsed = SystemClock.elapsedRealtime() - started
            result.fold(
                onSuccess = { mark(checkId, name, ProblemStageStatus.SUCCESS, elapsed, it?.toString().orEmpty()) },
                onFailure = { error -> mark(checkId, name, ProblemStageStatus.FAILED, elapsed, "errorClass=${errorClass(error)},errorMessage=${error.message.orEmpty().replace('\n', ' ').take(160)}") }
            )
        }
        checkScope(checkId).launch {
            delay(timeoutMs)
            if (completed.compareAndSet(false, true)) {
                worker.cancel("problem check stage timeout")
                mark(checkId, name, ProblemStageStatus.TIMEOUT, timeoutMs, "errorClass=STAGE_TIMEOUT,errorMessage=stage exceeded ${timeoutMs / 1000}s;cancellationRequested=true")
            }
        }
    }

    private suspend fun awaitReportedStage(checkId: String, name: String) {
        while (active.get() == checkId) {
            val status = progress().stages[name]?.status
            if (status in setOf(ProblemStageStatus.SUCCESS, ProblemStageStatus.FAILED, ProblemStageStatus.TIMEOUT, ProblemStageStatus.NOT_RUN, ProblemStageStatus.ABANDONED)) return
            delay(100L)
        }
    }

    /** Run every probe after a failure too. A blocked dispatcher is itself the finding, but later
     * probes identify which independent application paths remain healthy. */
    private suspend fun runDatabaseLane(
        checkId: String,
        context: Context,
        repository: ChatRepository,
        sharedUtils: SharedUtils,
        appState: AppStateHolder,
    ) {
        val stages = listOf(
            Triple("database_open", LOCAL_TIMEOUT_MS, suspend { databaseOpen(context) }),
            Triple("database_schema", LOCAL_TIMEOUT_MS, suspend { databaseSchema(context) }),
            Triple("database_counts", LOCAL_TIMEOUT_MS, suspend { databaseCounts(context) }),
            Triple("persistent_state_probe", LOCAL_TIMEOUT_MS, suspend { persistentStateProbe(context, repository) }),
            Triple("prompt_template_integrity", LOCAL_TIMEOUT_MS, suspend { promptTemplateIntegrityProbe(repository) }),
            Triple("kb_sql_count", LOCAL_TIMEOUT_MS, suspend { knowledgeBaseSqlCount(context) }),
            Triple("kb_sql_metadata", LOCAL_TIMEOUT_MS, suspend { knowledgeBaseSqlMetadata(context) }),
            Triple("kb_sql_content_size", LOCAL_TIMEOUT_MS, suspend { knowledgeBaseSqlContentSize(context) }),
            Triple("knowledge_base_metadata", LOCAL_TIMEOUT_MS, suspend { knowledgeBaseMetadataProbe(repository) }),
            Triple("knowledge_base_assignments", LOCAL_TIMEOUT_MS, suspend { knowledgeBaseAssignmentsProbe(repository) }),
            Triple("knowledge_base_chunks", COPY_TIMEOUT_MS, suspend { knowledgeBaseChunksProbe(repository) }),
            Triple("knowledge_base_readiness", LOCAL_TIMEOUT_MS, suspend { knowledgeBaseReadinessProbe(repository) }),
            Triple("vector_sql_count", LOCAL_TIMEOUT_MS, suspend { vectorSqlCount(context) }),
            Triple("vector_sql_signature_size", LOCAL_TIMEOUT_MS, suspend { vectorSqlSignatureSize(context) }),
            Triple("vector_sql_invalid_rows", LOCAL_TIMEOUT_MS, suspend { vectorSqlInvalidRows(context) }),
            Triple("support_manual_probe", LOCAL_TIMEOUT_MS, suspend { supportManualProbe(context) }),
            Triple("support_transcript_probe", LOCAL_TIMEOUT_MS, suspend { supportTranscriptProbe() }),
            Triple("session_integrity", LOCAL_TIMEOUT_MS, suspend { sessionIntegrity(context) }),
            Triple("session_message_audit", LOCAL_TIMEOUT_MS, suspend { sessionMessageAudit(repository) }),
            Triple("contacts_recovery", LOCAL_TIMEOUT_MS, suspend { recoverContacts(repository, appState) }),
            Triple("private_message_probe", COPY_TIMEOUT_MS, suspend { privateChatProbe(context, checkId) }),
            Triple("private_pipeline_history", LOCAL_TIMEOUT_MS, suspend { privatePipelineHistoryProbe(repository) }),
            Triple("private_pipeline_context", LOCAL_TIMEOUT_MS, suspend { privatePipelineContextProbe(repository) }),
            Triple("private_pipeline_reply_parse", LOCAL_TIMEOUT_MS, suspend { privatePipelineReplyParseProbe(repository, sharedUtils) }),
            Triple("private_pipeline_last_state", LOCAL_TIMEOUT_MS, suspend { privatePipelineLastStateProbe() }),
            Triple("group_message_probe", COPY_TIMEOUT_MS, suspend { groupChatProbe(context, checkId) }),
            Triple("group_pipeline_roster_history", LOCAL_TIMEOUT_MS, suspend { groupPipelineRosterHistoryProbe(repository) }),
            Triple("group_pipeline_context", LOCAL_TIMEOUT_MS, suspend { groupPipelineContextProbe(repository) }),
            Triple("group_pipeline_reply_parse", LOCAL_TIMEOUT_MS, suspend { groupPipelineReplyParseProbe(repository) }),
            Triple("database_copy_write_test", COPY_TIMEOUT_MS, suspend { databaseCopyWrite(context, checkId) }),
            Triple("backup_roles_sessions", LOCAL_TIMEOUT_MS, suspend { backupRolesSessionsProbe(repository) }),
            Triple("backup_relationships", LOCAL_TIMEOUT_MS, suspend { backupRelationshipsProbe(repository) }),
            Triple("backup_messages", COPY_TIMEOUT_MS, suspend { backupMessagesProbe(repository) }),
            Triple("backup_knowledge_bases", COPY_TIMEOUT_MS, suspend { backupKnowledgeBasesProbe(repository) }),
            Triple("backup_memories", COPY_TIMEOUT_MS, suspend { backupMemoriesProbe(repository) }),
            Triple("backup_anchors", COPY_TIMEOUT_MS, suspend { backupAnchorsProbe(repository) }),
            Triple("backup_memory_items", COPY_TIMEOUT_MS, suspend { backupMemoryItemsProbe(repository) }),
            // All repository reads share one Android SQLite driver. Keep these distinct probes
            // serial so the diagnostic itself cannot create a driver-contention false positive.
            Triple("backup_moments", COPY_TIMEOUT_MS, suspend { backupMomentsProbe(repository) }),
            Triple("backup_moment_likes", COPY_TIMEOUT_MS, suspend { backupMomentLikesProbe(repository) }),
            Triple("backup_moment_comments", COPY_TIMEOUT_MS, suspend { backupMomentCommentsProbe(repository) }),
            Triple("backup_diaries", COPY_TIMEOUT_MS, suspend { backupDiariesProbe(repository) }),
            Triple("backup_archives_display", COPY_TIMEOUT_MS, suspend { backupArchivesDisplayProbe(repository) }),
            Triple("backup_gifts", LOCAL_TIMEOUT_MS, suspend { backupGiftsProbe(repository) }),
            Triple("backup_dispatches", LOCAL_TIMEOUT_MS, suspend { backupDispatchesProbe(repository) }),
            Triple("backup_shared_experiences", LOCAL_TIMEOUT_MS, suspend { backupSharedExperiencesProbe(repository) }),
            Triple("backup_mahjong", LOCAL_TIMEOUT_MS, suspend { backupMahjongProbe(repository) }),
            Triple("backup_settings_snapshot", COPY_TIMEOUT_MS, suspend { backupSettingsProbe(repository) }),
        )
        for ((name, timeout, action) in stages) {
            if (progress().abandoned) return
            startDetachedLocalProbe(checkId, name, timeout) { action() }
            awaitReportedStage(checkId, name)
            if (progress().stages[name]?.status != ProblemStageStatus.SUCCESS) {
                val remaining = stages.dropWhile { it.first != name }.drop(1).map { it.first } + "backup_snapshot_probe"
                remaining.forEach { pending ->
                    mark(checkId, pending, ProblemStageStatus.NOT_RUN, detail = "reason=skipped_after_first_database_failure,blockedAt=$name;inspect_db_native_backup_tables_for_table_and_field_metrics")
                }
                return
            }
        }
        startDetachedLocalProbe(checkId, "backup_snapshot_probe", COPY_TIMEOUT_MS) { backupSnapshotTimingProbe(repository, appState) }
        mark(checkId, "backup_snapshot_timing_probe", ProblemStageStatus.NOT_RUN, detail = "reason=reported_by_backup_snapshot_probe;avoids_concurrent_full_snapshot")
    }

    private fun stageDisplayName(name: String): String = when (name) {
        "memory_items_stats_probe" -> "Memory V2 数据量与大字段统计"
        "vector_diagnostics_probe" -> "向量 SQL、解码与评分"
        "backup_snapshot_timing_probe" -> "备份快照读取与序列化估算"
        "backup_memories" -> "备份传统记忆读取"
        "backup_anchors" -> "备份记忆锚点读取"
        "db_native_read_probe" -> "原生 SQLite 基础读取"
        "db_native_backup_tables" -> "原生 SQLite 备份表与字段统计"
        "db_repository_read_probe" -> "应用数据库读取"
        "embedding_compute_probe" -> "本地向量计算"
        "private_message_probe", "group_message_probe" -> "消息保存与 AI 回复写回"
        "private_pipeline_history" -> "会话与历史对话读取"
        "private_pipeline_context" -> "角色、记忆、知识库与提示词前置条件"
        "private_pipeline_reply_parse" -> "已保存 AI 回复 JSON 解析与读回"
        "private_pipeline_last_state" -> "最近真实私聊管线状态"
        "group_pipeline_roster_history" -> "群会话、成员与历史对话读取"
        "group_pipeline_context" -> "成员、记忆、知识库与提示词前置条件"
        "group_pipeline_reply_parse" -> "已保存群聊 AI 回复 JSON 解析与读回"
        "knowledge_base_metadata" -> "知识库元数据读取"
        "knowledge_base_assignments" -> "知识库绑定关系读取"
        "knowledge_base_chunks" -> "知识库分块读取"
        "knowledge_base_readiness" -> "知识库索引状态汇总"
        "kb_sql_count" -> "知识库表原生 SQLite 计数"
        "kb_sql_metadata" -> "知识库表原生 SQLite 最小元数据读取"
        "kb_sql_content_size" -> "知识库原文大小统计"
        "vector_config_probe" -> "向量模型本地配置"
        "vector_embedding_gateway_probe" -> "固定诊断文本向量生成"
        "vector_store_query_probe" -> "向量表候选查询（已由只读 SQL 诊断覆盖）"
        "vector_local_search_probe" -> "本地向量检索"
        "vector_sql_count" -> "向量表原生 SQLite 计数"
        "vector_sql_signature_size" -> "向量签名与 JSON 大小统计"
        "vector_sql_invalid_rows" -> "向量表无效记录检查"
        "private_ai_probe", "group_ai_probe" -> "模型请求"
        "support_manual_probe" -> "客服说明书与本地检索"
        "support_transcript_probe" -> "客服会话保存"
        else -> name
    }

    private fun conciseReason(detail: String): String = when {
        detail.contains("AI_TIMEOUT") -> "模型在 ${MODEL_PROBE_TIMEOUT_MS / 1000} 秒内未返回可用结果"
        detail.contains("STAGE_TIMEOUT") -> "本地检查超过允许时间"
        detail.contains("errorMessage=") -> detail.substringAfter("errorMessage=").take(160)
        else -> detail.take(160).ifBlank { "未提供详细原因" }
    }

    private fun diagnosis(p: ProblemCheckProgress): String = when {
        p.stages.values.any { it.status == ProblemStageStatus.PENDING || it.status == ProblemStageStatus.RUNNING } && !p.abandoned -> "CHECK_RUNNING"
        p.stages["db_native_read_probe"]?.status in setOf(ProblemStageStatus.FAILED, ProblemStageStatus.TIMEOUT) -> "NATIVE_DATABASE_READ_FAILED"
        p.stages["db_repository_read_probe"]?.status in setOf(ProblemStageStatus.FAILED, ProblemStageStatus.TIMEOUT) -> "SHARED_DATABASE_DRIVER_OR_DISPATCHER_BLOCKED"
        p.stages["kb_sql_count"]?.status in setOf(ProblemStageStatus.FAILED, ProblemStageStatus.TIMEOUT) -> "KNOWLEDGE_BASE_TABLE_ACCESS_FAILED"
        p.stages["kb_sql_metadata"]?.status in setOf(ProblemStageStatus.FAILED, ProblemStageStatus.TIMEOUT) -> "KNOWLEDGE_BASE_METADATA_READ_FAILED"
        p.stages["kb_sql_content_size"]?.status in setOf(ProblemStageStatus.FAILED, ProblemStageStatus.TIMEOUT) -> "KNOWLEDGE_BASE_CONTENT_SIZE_READ_FAILED"
        p.stages["knowledge_base_metadata"]?.status in setOf(ProblemStageStatus.FAILED, ProblemStageStatus.TIMEOUT) -> "KNOWLEDGE_BASE_SQLDELIGHT_READ_FAILED"
        p.stages["vector_sql_count"]?.status in setOf(ProblemStageStatus.FAILED, ProblemStageStatus.TIMEOUT) -> "VECTOR_TABLE_ACCESS_FAILED"
        p.stages["vector_sql_signature_size"]?.status in setOf(ProblemStageStatus.FAILED, ProblemStageStatus.TIMEOUT) -> "VECTOR_EMBEDDING_DATA_READ_FAILED"
        p.stages["vector_sql_invalid_rows"]?.status in setOf(ProblemStageStatus.FAILED, ProblemStageStatus.TIMEOUT) -> "VECTOR_RECORD_VALIDATION_FAILED"
        p.stages["vector_embedding_gateway_probe"]?.status in setOf(ProblemStageStatus.FAILED, ProblemStageStatus.TIMEOUT) -> "EMBEDDING_GATEWAY_FAILED"
        p.stages["vector_store_query_probe"]?.status in setOf(ProblemStageStatus.FAILED, ProblemStageStatus.TIMEOUT) -> "VECTOR_STORE_QUERY_FAILED"
        p.stages["contacts_recovery"]?.status != ProblemStageStatus.SUCCESS -> "CONTACTS_RECOVERY_FAILED"
        p.stages["session_message_audit"]?.status == ProblemStageStatus.SUCCESS &&
            p.stages["session_message_audit"]?.detail?.contains("issues=") == true &&
            !p.stages["session_message_audit"]!!.detail.contains("issues=0") -> "SESSION_MESSAGE_MAPPING_FAILED"
        p.stages["private_message_probe"]?.status != ProblemStageStatus.SUCCESS -> "PRIVATE_MESSAGE_PIPELINE_FAILED"
        p.stages["group_message_probe"]?.status != ProblemStageStatus.SUCCESS -> "GROUP_MESSAGE_PIPELINE_FAILED"
        p.stages["private_ai_probe"]?.status != ProblemStageStatus.SUCCESS -> "PRIVATE_AI_RESPONSE_FAILED"
        p.stages["group_ai_probe"]?.status != ProblemStageStatus.SUCCESS -> "GROUP_AI_RESPONSE_FAILED"
        p.stages["persistent_state_probe"]?.status != ProblemStageStatus.SUCCESS -> "PERSISTENT_STATE_FAILED"
        p.stages["support_manual_probe"]?.status != ProblemStageStatus.SUCCESS -> "SUPPORT_MANUAL_FAILED"
        p.stages["support_transcript_probe"]?.status != ProblemStageStatus.SUCCESS -> "SUPPORT_TRANSCRIPT_FAILED"
        p.stages["backup_snapshot_probe"]?.status != ProblemStageStatus.SUCCESS -> "BACKUP_SNAPSHOT_FAILED"
        p.stages["backup_file_probe"]?.status in setOf(ProblemStageStatus.FAILED, ProblemStageStatus.TIMEOUT, ProblemStageStatus.ABANDONED) -> "BACKUP_FILE_VALIDATION_FAILED"
        p.stages["session_integrity"]?.status == ProblemStageStatus.SUCCESS && p.stages["session_integrity"]?.detail?.let { detail ->
            !detail.contains("orphanPrivateSessions=0") ||
                !detail.contains("duplicatePrivateOperators=0") ||
                !detail.contains("orphanMessages=0") ||
                !detail.contains("operatorNameMismatches=0")
        } == true -> "CHAT_SESSION_INTEGRITY_FAILED"
        p.stages["database_copy_write_test"]?.status == ProblemStageStatus.FAILED -> "DATABASE_COPY_WRITE_FAILED"
        p.stages["database_schema"]?.status == ProblemStageStatus.FAILED -> "DATABASE_SCHEMA_FAILED"
        p.abandoned -> "${p.currentStage.uppercase()}_PROBE_BLOCKED"
        else -> "NO_DATABASE_PROBLEM_FOUND"
    }

    private fun sharedPipelineState(): String = try {
        org.koin.java.KoinJavaComponent.get<com.rhodes.privatechat.shared.settings.SettingsRepository>(com.rhodes.privatechat.shared.settings.SettingsRepository::class.java)
            .getString("private_reply_pipeline_last", "none")
    } catch (_: Exception) { "unavailable" }

    /** Verifies all data categories can be read into the same in-memory payload used by backup export. */
    private suspend fun backupSnapshotProbe(repository: ChatRepository, appState: AppStateHolder): String {
        val payload = BackupContentFilter.apply(BackupSnapshotBuilder(repository, settingsForProbe()).build(), BackupContentSelection.All)
        val content = payload.content
        return "operators=${content.operators.orEmpty().size},sessions=${content.sessions.orEmpty().size},messages=${content.messages.orEmpty().size},moments=${content.moments.orEmpty().size},comments=${content.momentComments.orEmpty().size},diaries=${content.diaries.orEmpty().size},knowledgeBases=${content.knowledgeBases.orEmpty().size},memoryItems=${content.memoryItems.orEmpty().size}"
    }

    private suspend fun backupRolesSessionsProbe(repository: ChatRepository): String {
        val operators = repository.getAllOperatorsSync()
        val sessions = repository.getAllSessionsSync()
        return "operators=${operators.size},sessions=${sessions.size},operatorTextChars=${operators.sumOf { it.description.length + it.privatePrompt.length + it.groupPrompt.length }}"
    }

    private suspend fun backupRelationshipsProbe(repository: ChatRepository): String {
        val relationships = repository.getAllRelationshipsForBackup()
        return "queries=1,relationships=${relationships.size},bulkRead=true"
    }

    private suspend fun backupMessagesProbe(repository: ChatRepository): String {
        val sessions = repository.getAllSessionsSync()
        val perSession = sessions.map { session -> session.id to repository.getMessagesSync(session.id) }
        val messages = perSession.flatMap { it.second }
        val largest = perSession.maxByOrNull { it.second.size }
        return "sessions=${sessions.size},messages=${messages.size},contentChars=${messages.sumOf { it.content.length }},largestSessionMessages=${largest?.second?.size ?: 0}"
    }

    private suspend fun backupKnowledgeBasesProbe(repository: ChatRepository): String {
        val books = repository.knowledgeBases.getAll()
        val chunks = repository.knowledgeBases.getAllChunksForBackup()
        val assignments = repository.knowledgeBases.getAllAssignmentsForBackup()
        return "books=${books.size},chunks=${chunks.size},assignments=${assignments.size},rawContentChars=${books.sumOf { it.rawContent.length }},chunkContentChars=${chunks.sumOf { it.content.length }}"
    }

    private suspend fun backupMemoriesProbe(repository: ChatRepository): String {
        val operators = repository.getAllOperatorsSync().mapTo(mutableSetOf()) { it.id }
        val sessions = repository.getAllSessionsSync().mapTo(mutableSetOf()) { it.id }
        val memories = repository.getAllMemoriesForBackup()
        val exportedMemories = memories.count { memory ->
            (memory.type == com.rhodes.privatechat.shared.model.MemoryType.DAILY && memory.operatorId == "daily" && memory.sessionId.startsWith("daily_")) ||
                (memory.operatorId in operators && memory.sessionId in sessions)
        }
        return "memories=${memories.size},exportedMemories=$exportedMemories"
    }

    private suspend fun backupAnchorsProbe(repository: ChatRepository): String {
        val operators = repository.getAllOperatorsSync().mapTo(mutableSetOf()) { it.id }
        val sessions = repository.getAllSessionsSync().mapTo(mutableSetOf()) { it.id }
        val anchors = repository.getAllAnchorsForBackup()
        val exportedAnchors = anchors.count { it.operatorId in operators && it.sessionId in sessions }
        return "anchors=${anchors.size},exportedAnchors=$exportedAnchors"
    }

    private suspend fun backupMemoryItemsProbe(repository: ChatRepository): String {
        val items = repository.getAllMemoryItems()
        val copied = items.map { it.copy(vectorId = "") }
        return "memoryItems=${items.size},contentChars=${items.sumOf { it.content.length }},rawJsonChars=${items.sumOf { it.rawJson.length }},vectorIdsCleared=${copied.count { it.vectorId.isBlank() }}"
    }

    private suspend fun backupMomentsProbe(repository: ChatRepository): String {
        val moments = repository.getAllMomentsSync()
        return "moments=${moments.size},contentChars=${moments.sumOf { it.content.length }},maxContentChars=${moments.maxOfOrNull { it.content.length } ?: 0}"
    }

    private suspend fun backupMomentLikesProbe(repository: ChatRepository): String {
        val likes = repository.getAllLikesForBackup()
        return "likes=${likes.size}"
    }

    private suspend fun backupMomentCommentsProbe(repository: ChatRepository): String {
        val comments = repository.getAllCommentsForBackup()
        return "comments=${comments.size},contentChars=${comments.sumOf { it.content.length }},maxContentChars=${comments.maxOfOrNull { it.content.length } ?: 0}"
    }

    private suspend fun backupDiariesProbe(repository: ChatRepository): String {
        val diaries = repository.getAllDiariesForBackup()
        return "diaries=${diaries.size},contentChars=${diaries.sumOf { it.content.length }},maxContentChars=${diaries.maxOfOrNull { it.content.length } ?: 0}"
    }

    private suspend fun backupArchivesDisplayProbe(repository: ChatRepository): String {
        val displayEvents = repository.messages.getAllDisplayEvents()
        val archives = repository.archives.getAllArchives()
        val history = repository.archives.getAllHistorySegments()
        return "displayEvents=${displayEvents.size},archives=${archives.size},historySegments=${history.size},archiveJsonChars=${archives.sumOf { it.messagesJson.length + it.stateJson.length } + history.sumOf { it.messagesJson.length }}"
    }

    private suspend fun backupGiftsProbe(repository: ChatRepository): String {
        val gifts = repository.getAllGifts()
        return "gifts=${gifts.size},nameChars=${gifts.sumOf { it.giftName.length }},uriChars=${gifts.sumOf { it.imageUri.length }}"
    }

    private suspend fun backupDispatchesProbe(repository: ChatRepository): String {
        val dispatches = repository.dispatches.getAllDispatches()
        return "dispatches=${dispatches.size},logChars=${dispatches.sumOf { it.logChain.length }},itemsChars=${dispatches.sumOf { it.items.length }}"
    }

    private suspend fun backupSharedExperiencesProbe(repository: ChatRepository): String {
        val experiences = repository.getAllSharedExperiences()
        val participants = repository.getAllSharedExperienceParticipants()
        return "experiences=${experiences.size},participants=${participants.size},contentChars=${experiences.sumOf { it.content.length }}"
    }

    private suspend fun backupMahjongProbe(repository: ChatRepository): String {
        val mahjong = repository.mahjong.getMahjongSave()
        return "mahjongSave=${mahjong != null},saveChars=${mahjong?.saveJson?.length ?: 0}"
    }

    private suspend fun backupSettingsProbe(repository: ChatRepository): String {
        val values = PortableSettings.snapshot(repository, settingsForProbe())
        return "settingsEntries=${values.size},encodedChars=${values.entries.sumOf { it.key.length + it.value.length }}"
    }

    /** Writes the real backup format to cache and reads it back, without media or a system file picker. */
    private suspend fun backupFileProbe(context: Context, repository: ChatRepository, appState: AppStateHolder, versionName: String): String {
        val snapshot = BackupContentFilter.apply(BackupSnapshotBuilder(repository, settingsForProbe()).build(), BackupContentSelection.All)
        val (payload, media) = BackupMediaCollector(context).attachCollectedMedia(snapshot)
        val file = File(context.cacheDir, "problem-probe/backup_${System.currentTimeMillis()}.rbackup")
        file.parentFile?.mkdirs()
        try {
            file.outputStream().use { BackupFileWriter(versionName, schemaVersion = 1).writeFullBackup(it, payload, media.sources, media.sources.isNotEmpty()) }
            val validation = file.inputStream().use { BackupFileReader().validate(it) }
            if (validation !is com.rhodes.privatechat.data.backup.BackupValidationResult.Valid) {
                throw IllegalStateException((validation as com.rhodes.privatechat.data.backup.BackupValidationResult.Invalid).reason)
            }
            return "bytes=${file.length()},operators=${validation.manifest.recordCounts["operators"] ?: 0},messages=${validation.manifest.recordCounts["messages"] ?: 0},media=${media.items.size},skippedExternalMedia=${media.skippedUris.size},issues=${validation.issues.size}"
        } finally {
            file.delete()
        }
    }

    private fun settingsForProbe(): com.rhodes.privatechat.shared.settings.SettingsRepository =
        org.koin.java.KoinJavaComponent.get(com.rhodes.privatechat.shared.settings.SettingsRepository::class.java)

    private suspend fun persistentStateProbe(context: Context, repository: ChatRepository): String {
        val settings = settingsForProbe()
        val sessions = repository.getAllSessionsSync()
        val malformedPrivate = sessions.count { session ->
            val raw = settings.getString("private_turn_state_${session.id}", "")
            raw.isNotBlank() && !session.operatorId.startsWith("group_") && settings.getPrivateTurnState(session.id) == null
        }
        val malformedGroup = sessions.count { session ->
            val raw = settings.getString("group_turn_state_${session.id}", "")
            raw.isNotBlank() && session.operatorId.startsWith("group_") && settings.getGroupTurnState(session.id) == null
        }
        val prefs = context.getSharedPreferences("rhodes_settings", Context.MODE_PRIVATE).all
        val legacyCustomProtocols = prefs.keys.count { key ->
            key.startsWith("prompt_protocol_group") && key.endsWith("_custom") && prefs[key] == true
        }
        val supportRaw = settings.supportConversation
        val supportConversationValid = supportRaw.isBlank() || runCatching {
            Json.decodeFromString<List<AiSupportMessage>>(supportRaw)
        }.isSuccess
        val stalePromptVersions = prefs.keys.count { key ->
            key.startsWith("prompt_") && key.endsWith("_version") && (prefs[key] as? Int ?: 0) < PromptTemplates.VERSION
        }
        if (malformedPrivate > 0 || malformedGroup > 0 || !supportConversationValid) {
            throw IllegalStateException("malformedPrivateStates=$malformedPrivate,malformedGroupStates=$malformedGroup,supportConversationValid=$supportConversationValid")
        }
        return "privateStatesValid=true,groupStatesValid=true,supportConversationValid=true,legacyCustomGroupProtocols=$legacyCustomProtocols,stalePromptVersions=$stalePromptVersions"
    }

    private suspend fun promptTemplateIntegrityProbe(repository: ChatRepository): String {
        val settings = settingsForProbe()
        val surfaces = repository.getAllSessionsSync()
            .map { if (it.operatorId.startsWith("group_")) "group" to it.mode else "private" to it.mode }
            .distinct()
        val malformed = mutableListOf<String>()
        val blankCustom = mutableListOf<String>()
        val tokenPattern = Regex("""\{\{([^{}\r\n]*)\}\}""")
        surfaces.forEach { (type, mode) ->
            val template = settings.resolvePromptTemplate(type, mode, PromptTemplates.get(type, mode), PromptTemplates.VERSION)
            if (settings.isPromptTemplateCustom(type, mode) && template.isBlank()) blankCustom += "$type/$mode"
            tokenPattern.findAll(template).forEach { match ->
                if (match.groupValues[1].trim().isBlank()) malformed += "$type/$mode:blank_token"
            }
            if (template.count { it == '{' } % 2 != 0 || template.count { it == '}' } % 2 != 0) malformed += "$type/$mode:unbalanced_braces"
        }
        check(malformed.isEmpty()) { "prompt template syntax invalid: ${malformed.joinToString(";")}" }
        check(blankCustom.isEmpty()) { "custom prompt template is blank: ${blankCustom.joinToString(";")}" }
        return "surfaces=${surfaces.size},templateSyntax=true,customTemplatesNonBlank=true"
    }

    private suspend fun knowledgeBaseMetadataProbe(repository: ChatRepository): String {
        val books = repository.knowledgeBases.getAll()
        return "books=${books.size},rawContentChars=${books.sumOf { it.rawContent.length }}"
    }

    private suspend fun knowledgeBaseAssignmentsProbe(repository: ChatRepository): String {
        val books = repository.knowledgeBases.getAll().associateBy { it.id }
        val assignments = repository.knowledgeBases.getAllAssignmentsForBackup().filter { it.enabled }
        val missingBooks = assignments.filter { it.knowledgeBaseId !in books }.map { it.knowledgeBaseId }.distinct()
        check(missingBooks.isEmpty()) { "enabled assignment references missing knowledge base" }
        return "books=${books.size},enabledAssignments=${assignments.size},assignmentsResolved=true"
    }

    private suspend fun knowledgeBaseChunksProbe(repository: ChatRepository): String {
        val chunks = repository.knowledgeBases.getAllChunksForBackup()
        val enabled = chunks.filter { it.enabled }
        check(enabled.none { it.content.isBlank() }) { "enabled knowledge-base chunks are blank" }
        return "chunks=${chunks.size},enabledChunks=${enabled.size},chunkContentChars=${chunks.sumOf { it.content.length }}"
    }

    private suspend fun knowledgeBaseReadinessProbe(repository: ChatRepository): String {
        val books = repository.knowledgeBases.getAll().associateBy { it.id }
        val assignments = repository.knowledgeBases.getAllAssignmentsForBackup().filter { it.enabled }
        var enabledChunks = 0
        var unreadyBooks = 0
        books.values.forEach { book ->
            val chunks = repository.knowledgeBases.getChunks(book.id)
            enabledChunks += chunks.count { it.enabled }
            if (assignments.any { it.knowledgeBaseId == book.id } && book.indexStatus !in setOf("ready", "local_ready")) unreadyBooks++
        }
        return "books=${books.size},enabledAssignments=${assignments.size},enabledChunks=$enabledChunks,unreadyAssignedBooks=$unreadyBooks,localIndexReadOnly=true"
    }

    /** Verifies the local fallback that must keep support usable while indexing is unavailable. */
    private fun supportManualProbe(context: Context): String {
        val manual = context.assets.open("support/product_manual_zh.md").bufferedReader().use { it.readText() }
        val sections = manual.split(Regex("(?m)(?=^##\\s+)")).map { it.trim() }.filter { it.isNotBlank() }
        check(sections.isNotEmpty()) { "manual contains no sections" }
        val fallback = AiSupportContract.localReference(sections, "如何发送消息")
        check(fallback != AiSupportContract.noMatch && fallback.contains("消息")) { "local reference did not match the manual" }
        return "manualReadable=true,sections=${sections.size},localFallback=true"
    }

    private fun supportTranscriptProbe(): String {
        val settings = settingsForProbe()
        val sample = listOf(AiSupportMessage(1, "user", "自检消息"), AiSupportMessage(2, "assistant", "自检回复"))
        val restored = Json.decodeFromString<List<AiSupportMessage>>(Json.encodeToString(sample))
        check(restored == sample) { "support transcript round trip failed" }
        val storedConversationValid = settings.supportConversation.isBlank() || runCatching {
            Json.decodeFromString<List<AiSupportMessage>>(settings.supportConversation)
        }.isSuccess
        check(storedConversationValid) { "stored support conversation cannot be decoded" }
        val configuration = runCatching {
            org.koin.java.KoinJavaComponent.get<SharedUtils>(SharedUtils::class.java).chatConfigurationError()
        }
        val configurationState = configuration.fold(
            onSuccess = { if (it == null) "valid" else "invalid" },
            onFailure = { "unavailable" },
        )
        check(configurationState == "valid") { "support model configuration is $configurationState" }
        return "jsonRoundTrip=true,storedConversationPresent=${settings.supportConversation.isNotBlank()},storedConversationValid=true,modelConfiguration=$configurationState"
    }

    /** Uses the same structured response shape required by a real private reply. */
    private fun startDetachedStructuredPrivateAiProbe(checkId: String, sharedUtils: SharedUtils) {
        mark(checkId, "private_ai_probe", ProblemStageStatus.RUNNING)
        val started = SystemClock.elapsedRealtime()
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        checkScope(checkId).launch {
            val result = runCatching {
                val raw = sharedUtils.chat(
                    listOf(
                        AiMessage("system", "只输出合法JSON：{\"segments\":[{\"type\":\"dialogue\",\"content\":\"私聊检测成功\"}]}"),
                        AiMessage("user", "请按要求回复。")
                    ),
                    "ProblemCheckPrivateStructured",
                    maxOutputTokens = 64
                )
                sharedUtils.aiService.parseOfflineResponse(raw)
            }
            if (!completed.compareAndSet(false, true)) return@launch
            val elapsed = SystemClock.elapsedRealtime() - started
            result.fold(
                onSuccess = { parsed ->
                    val visible = parsed.segments.orEmpty().any { !it.type.equals("narration", true) && it.content.isNotBlank() }
                    if (visible) mark(checkId, "private_ai_probe", ProblemStageStatus.SUCCESS, elapsed, "structuredReply=true,visibleDialogue=true")
                    else mark(checkId, "private_ai_probe", ProblemStageStatus.FAILED, elapsed, "errorClass=AI_PROTOCOL,errorMessage=no visible dialogue")
                },
                onFailure = { error -> mark(checkId, "private_ai_probe", ProblemStageStatus.FAILED, elapsed, "errorClass=${errorClass(error)},errorMessage=${error.message.orEmpty().replace('\n', ' ').take(160)}") }
            )
        }
        checkScope(checkId).launch {
            delay(MODEL_PROBE_TIMEOUT_MS)
            if (completed.compareAndSet(false, true)) mark(checkId, "private_ai_probe", ProblemStageStatus.TIMEOUT, MODEL_PROBE_TIMEOUT_MS, "errorClass=AI_TIMEOUT,errorMessage=structured private reply exceeded ${MODEL_PROBE_TIMEOUT_MS / 1000}s")
        }
    }

    private suspend fun recoverContacts(repository: ChatRepository, appState: AppStateHolder): String {
        val beforeOperators = appState.operators.value.size
        val beforeSessions = appState.allSessions.value.size
        val operators = repository.getAllOperatorsSync()
        val sessions = repository.getAllSessionsSync()
        if (!appState.reloadFromDatabase("problem_check_contacts")) throw IllegalStateException("state refresh failed")
        val loaded = appState.operators.value.size == operators.size && appState.allSessions.value.size == sessions.size
        if (!loaded) throw IllegalStateException("state refresh did not retain database values")
        return "databaseOperators=${operators.size},databaseSessions=${sessions.size},beforeUiOperators=$beforeOperators,beforeUiSessions=$beforeSessions,afterUiOperators=${appState.operators.value.size},afterUiSessions=${appState.allSessions.value.size},contactsVisible=true"
    }

    /** Removes only abandoned records created by earlier diagnostic builds. */
    /** Safe to invoke at startup or before a new check. Diagnostic records are never user sessions. */
    suspend fun cleanupStaleChatProbes(repository: ChatRepository): String {
        val sessions = repository.getAllSessionsSync()
        val staleSessions = sessions.filter {
            it.id.startsWith("session___probe_") || it.id.startsWith("group___probe_") ||
                it.operatorId.startsWith("__probe_") || it.operatorId.startsWith("group___probe_")
        }
        val staleOperators = repository.getAllOperatorsSync().filter {
            it.id.startsWith("__probe_private_") || it.id.startsWith("__probe_group_member_")
        }
        var deletedSessions = 0
        var deletedOperators = 0
        staleSessions.forEach { session ->
            runCatching { withTimeout(1_500L) { repository.deleteDiagnosticSession(session.id) } }
                .onSuccess { deletedSessions++ }
                .onFailure { DebugLogger.diagnostic("ProblemCheck/StaleSessionCleanupFailed", "sessionId=${session.id},errorClass=${it.javaClass.simpleName}") }
        }
        staleOperators.forEach { operator ->
            runCatching { withTimeout(1_500L) { repository.deleteOperator(operator.id) } }
                .onSuccess { deletedOperators++ }
                .onFailure { DebugLogger.diagnostic("ProblemCheck/StaleOperatorCleanupFailed", "operatorId=${operator.id},errorClass=${it.javaClass.simpleName}") }
        }
        if (staleSessions.isNotEmpty() || staleOperators.isNotEmpty()) {
            DebugLogger.diagnostic("ProblemCheck/StaleProbeCleanup", "foundSessions=${staleSessions.size},deletedSessions=$deletedSessions,foundOperators=${staleOperators.size},deletedOperators=$deletedOperators")
        }
        return "foundSessions=${staleSessions.size},deletedSessions=$deletedSessions,foundOperators=${staleOperators.size},deletedOperators=$deletedOperators"
    }

    /** Audits real user sessions before AI probes so a preview/message mapping fault is visible immediately. */
    private suspend fun sessionMessageAudit(repository: ChatRepository): String {
        val sessions = repository.getAllSessionsSync()
        val rows = sessions.map { session ->
            val messages = repository.getMessagesSync(session.id)
            val userCount = messages.count { it.isMe }
            val aiCount = messages.count { !it.isMe && it.type == "ai_json" }
            val previewWithoutMessages = session.lastMessage.isNotBlank() && messages.isEmpty()
            val futureCount = messages.count { it.timestamp > System.currentTimeMillis() + 60_000L }
            val issue = when {
                previewWithoutMessages -> "PREVIEW_WITHOUT_MESSAGES"
                messages.isEmpty() -> "EMPTY_SESSION"
                futureCount > 0 -> "FUTURE_TIMESTAMPS"
                else -> "OK"
            }
            "id=${session.id},kind=${if (session.operatorId.startsWith("group_")) "group" else "private"},messages=${messages.size},user=$userCount,ai=$aiCount,preview=${session.lastMessage.isNotBlank()},future=$futureCount,status=$issue"
        }
        val severeIssues = rows.count { it.endsWith("status=PREVIEW_WITHOUT_MESSAGES") || it.endsWith("status=FUTURE_TIMESTAMPS") }
        val emptySessions = rows.count { it.endsWith("status=EMPTY_SESSION") }
        return "sessions=${sessions.size},issues=$severeIssues,emptySessions=$emptySessions\n" + rows.joinToString("\n")
    }

    /** Read-only audit of the persisted inputs consumed before a private prompt is assembled. */
    private suspend fun privatePipelineHistoryProbe(repository: ChatRepository): String {
        val sessions = repository.getAllSessionsSync().filterNot { it.operatorId.startsWith("group_") || isDiagnosticSession(it) }
        if (sessions.isEmpty()) return "skipped=no_existing_private_session"
        val missingReplies = mutableListOf<String>()
        var totalMessages = 0
        sessions.forEach { session ->
            val messages = repository.getMessagesSync(session.id)
            totalMessages += messages.size
            val users = messages.count { it.isMe }
            val replies = messages.count { !it.isMe && it.type == "ai_json" }
            if (users > 0 && replies == 0) missingReplies += session.id
        }
        check(missingReplies.isEmpty()) { "private sessions have user messages but no stored AI replies: ${missingReplies.joinToString(",")}" }
        return "sessions=${sessions.size},historyRead=true,totalMessages=$totalMessages,allUserSessionsHaveReplies=true"
    }

    /** Verifies prompt prerequisites without composing or transmitting user-derived context. */
    private suspend fun privatePipelineContextProbe(repository: ChatRepository): String {
        val sessions = repository.getAllSessionsSync().filterNot { it.operatorId.startsWith("group_") || isDiagnosticSession(it) }
        if (sessions.isEmpty()) return "skipped=no_existing_private_session"
        val settings = settingsForProbe()
        val missingOperators = mutableListOf<String>()
        val missingPersona = mutableListOf<String>()
        var assignedBooks = 0
        sessions.forEach { session ->
            val operator = repository.getOperator(session.operatorId)
            if (operator == null) {
                missingOperators += session.id
            } else {
                if (operator.privatePrompt.ifBlank { operator.description }.isBlank()) missingPersona += session.id
                assignedBooks += repository.knowledgeBases.getAssignments(operator.id)
                    .count { it.enabled && settings.isKnowledgeBaseEnabledForBook(it.knowledgeBaseId, "private_chat") }
            }
        }
        check(missingOperators.isEmpty()) { "private sessions reference missing operators: ${missingOperators.joinToString(",")}" }
        check(missingPersona.isEmpty()) { "private session operators have no persona: ${missingPersona.joinToString(",")}" }
        return "sessions=${sessions.size},operatorsResolved=true,personasReady=true,historyLimit=${settings.historyMessages},memoryV2Enabled=${settings.memoryV2Enabled},knowledgeBooks=$assignedBooks,externalRecallNotSent=true"
    }

    /** Audits the same stored ai_json shape the private history and UI consume, without logging content. */
    private suspend fun privatePipelineReplyParseProbe(repository: ChatRepository, sharedUtils: SharedUtils): String {
        val sessions = repository.getAllSessionsSync().filterNot { it.operatorId.startsWith("group_") || isDiagnosticSession(it) }
        if (sessions.isEmpty()) return "skipped=no_existing_private_session"
        val invalid = mutableListOf<String>()
        var replyCount = 0
        sessions.forEach { session ->
            repository.getMessagesSync(session.id).filter { !it.isMe && it.type == "ai_json" }.forEach { reply ->
                replyCount++
                val parsed = runCatching { sharedUtils.aiService.normalizeOfflineResponse(reply.content) }.getOrElse {
                    invalid += "${session.id}:parse_error"; return@forEach
                }
                if (parsed.segments.orEmpty().none { !it.type.equals("narration", true) && it.content.isNotBlank() }) invalid += "${session.id}:no_visible_dialogue"
            }
        }
        check(invalid.isEmpty()) { "private ai_json readback invalid: ${invalid.joinToString(",")}" }
        return "sessions=${sessions.size},aiReplies=$replyCount,parseable=true,readbackReady=true"
    }

    /** Detects a real turn stalled before prompt completion or model delivery without exposing message text. */
    private fun privatePipelineLastStateProbe(): String {
        val raw = settingsForProbe().getString("private_reply_pipeline_last", "")
        if (raw.isBlank() || raw == "none") return "skipped=no_recent_private_reply_pipeline"
        val fields = raw.split(",").associate { part ->
            val index = part.indexOf('=')
            if (index > 0) part.substring(0, index) to part.substring(index + 1) else part to ""
        }
        val at = fields["at"]?.toLongOrNull() ?: throw IllegalStateException("private reply pipeline timestamp is invalid")
        val step = fields["step"].orEmpty()
        val ageMs = (System.currentTimeMillis() - at).coerceAtLeast(0L)
        if (step in setOf("prompt_build_start", "prompt_ready", "ai_request_started") && ageMs > 120_000L) {
            throw IllegalStateException("private reply stalled at $step for ${ageMs / 1000}s")
        }
        return "lastStep=$step,ageSeconds=${ageMs / 1000},sessionPresent=${fields["sessionId"].isNullOrBlank().not()}"
    }

    private suspend fun groupPipelineRosterHistoryProbe(repository: ChatRepository): String {
        val groups = repository.getAllSessionsSync().filter { it.operatorId.startsWith("group_") && !isDiagnosticSession(it) }
        if (groups.isEmpty()) return "skipped=no_existing_group_session"
        val operators = repository.getAllOperatorsSync()
        val invalid = mutableListOf<String>()
        var totalMessages = 0
        groups.forEach { group ->
            val memberIds = group.members.split(",").map(String::trim).filter(String::isNotBlank)
            if (memberIds.isEmpty()) invalid += "${group.id}:members_empty"
            val resolved = memberIds.mapNotNull { id -> operators.firstOrNull { it.id == id || it.name == id } }
            if (resolved.size != memberIds.size) invalid += "${group.id}:members_unresolved"
            if (resolved.map { it.id }.distinct().size != resolved.size) invalid += "${group.id}:members_duplicate"
            totalMessages += repository.getMessagesSync(group.id).size
        }
        check(invalid.isEmpty()) { "group roster/history invalid: ${invalid.joinToString(",")}" }
        return "groups=${groups.size},rostersResolved=true,historyRead=true,totalMessages=$totalMessages"
    }

    private suspend fun groupPipelineContextProbe(repository: ChatRepository): String {
        val groups = repository.getAllSessionsSync().filter { it.operatorId.startsWith("group_") && !isDiagnosticSession(it) }
        if (groups.isEmpty()) return "skipped=no_existing_group_session"
        val settings = settingsForProbe()
        val operators = repository.getAllOperatorsSync()
        val invalid = mutableListOf<String>()
        var assignedBooks = 0
        var activeMembers = 0
        groups.forEach { group ->
            val muted = group.mutedMembers.split(",").map(String::trim).filter(String::isNotBlank).toSet()
            val members = group.members.split(",").map(String::trim).filter(String::isNotBlank)
                .mapNotNull { id -> operators.firstOrNull { it.id == id || it.name == id } }
            val active = members.filter { it.id !in muted && it.name !in muted }
            if (active.isEmpty()) invalid += "${group.id}:active_members_empty"
            if (active.any { it.groupPrompt.ifBlank { it.description }.isBlank() }) invalid += "${group.id}:member_persona_missing"
            activeMembers += active.size
            assignedBooks += active.flatMap { member -> repository.knowledgeBases.getAssignments(member.id) }
                .count { it.enabled && settings.isKnowledgeBaseEnabledForBook(it.knowledgeBaseId, "group_chat") }
        }
        check(invalid.isEmpty()) { "group prompt prerequisites invalid: ${invalid.joinToString(",")}" }
        return "groups=${groups.size},activeMembers=$activeMembers,memoryV2Enabled=${settings.memoryV2Enabled},knowledgeBooks=$assignedBooks,externalRecallNotSent=true"
    }

    private suspend fun groupPipelineReplyParseProbe(repository: ChatRepository): String {
        val groups = repository.getAllSessionsSync().filter { it.operatorId.startsWith("group_") && !isDiagnosticSession(it) }
        if (groups.isEmpty()) return "skipped=no_existing_group_session"
        val invalid = mutableListOf<String>()
        var replies = 0
        groups.forEach { group ->
            repository.getMessagesSync(group.id).filter { !it.isMe && it.type == "ai_json" }.forEach { reply ->
                replies++
                val root = runCatching { Json.parseToJsonElement(reply.content) }.getOrElse { invalid += "${group.id}:json_parse_error"; return@forEach }
                val entries = when (root) {
                    is JsonArray -> root
                    is JsonObject -> root["segments"] as? JsonArray ?: root["messages"] as? JsonArray
                    else -> null
                }
                if (entries.isNullOrEmpty()) invalid += "${group.id}:segments_missing"
                else if (entries.none { element ->
                    val item = element as? JsonObject
                    ((item?.get("message") ?: item?.get("content")) as? JsonPrimitive)?.content.orEmpty().isNotBlank()
                }) invalid += "${group.id}:visible_segments_empty"
            }
        }
        check(invalid.isEmpty()) { "group ai_json readback invalid: ${invalid.joinToString(",")}" }
        return "groups=${groups.size},aiReplies=$replies,parseable=true,readbackReady=true"
    }

    /** Uses only an app-owned fixed string, never user history, to isolate embedding/vector failures. */
    private fun vectorConfigProbe(): String {
        val settings = settingsForProbe()
        if (!settings.memoryV2Enabled) return "skipped=memory_v2_disabled"
        val mode = settings.vectorProviderMode
        check(mode in setOf("local", "third_party")) { "unsupported vector provider mode: $mode" }
        if (mode == "third_party") {
            check(settings.vectorBaseUrl.isNotBlank()) { "vector base URL is blank" }
            check(settings.vectorModelName.isNotBlank()) { "vector model name is blank" }
            check(settings.vectorApiKey.ifBlank { settings.apiKey }.isNotBlank()) { "vector API key is blank" }
        }
        return "memoryV2Enabled=true,mode=$mode,modelConfigured=${settings.vectorModelName.isNotBlank()},networkProbePending=true"
    }

    private suspend fun vectorEmbeddingGatewayProbe(checkId: String): String {
        val settings = settingsForProbe()
        if (!settings.memoryV2Enabled) return "skipped=memory_v2_disabled"
        val vectorService = runCatching {
            org.koin.java.KoinJavaComponent.get<MemoryVectorService>(MemoryVectorService::class.java)
        }.getOrElse { throw IllegalStateException("vector service unavailable", it) }
        val started = SystemClock.elapsedRealtime()
        val embedding = vectorService.embedForDiagnostics("Rhodes diagnostic vector health probe")
        check(embedding.isNotEmpty() && embedding.all { it.isFinite() }) { "embedding gateway returned an invalid vector" }
        diagnosticEmbeddings[checkId] = embedding
        return "memoryV2Enabled=true,embeddingRequest=true,dimensions=${embedding.size},gatewayMs=${SystemClock.elapsedRealtime() - started},userContentSent=false"
    }

    private suspend fun vectorStoreQueryProbe(checkId: String): String {
        val settings = settingsForProbe()
        if (!settings.memoryV2Enabled) return "skipped=memory_v2_disabled"
        val vectorService = runCatching {
            org.koin.java.KoinJavaComponent.get<MemoryVectorService>(MemoryVectorService::class.java)
        }.getOrElse { throw IllegalStateException("vector service unavailable", it) }
        val embedding = diagnosticEmbeddings[checkId]
            ?: throw IllegalStateException("embedding gateway probe did not complete; inspect vector_embedding_gateway_probe")
        val started = SystemClock.elapsedRealtime()
        val results = vectorService.searchWithEmbedding(
            VectorSearchRequest(
                ownerType = "__diagnostic__",
                ownerId = "__diagnostic__",
                query = "Rhodes diagnostic vector health probe",
                limit = 1,
                candidateLimit = 1,
            ), embedding
        )
        return "memoryV2Enabled=true,vectorSearch=true,resultCount=${results.size},storeAndCacheMs=${SystemClock.elapsedRealtime() - started},userContentSent=false"
    }

    /** Production data remains read-only. Message persistence is validated only in a disposable DB copy. */
    private fun privateChatProbe(context: Context, checkId: String): String = databaseCopyChatRoundTrip(context, checkId, "private")

    /** Group and private probes use separate copies, so neither can leave a user-visible session behind. */
    private fun groupChatProbe(context: Context, checkId: String): String = databaseCopyChatRoundTrip(context, checkId, "group")

    private fun databaseCopyChatRoundTrip(context: Context, checkId: String, kind: String): String {
        val source = context.getDatabasePath(DB_NAME)
        val dir = File(context.cacheDir, "problem-probe")
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("cannot create probe directory")
        val copy = File(dir, "rhodes_${kind}_$checkId.db")
        val sessionId = "__diagnostic_${kind}_$checkId"
        val now = System.currentTimeMillis()
        try {
            // A main DB plus independently copied WAL can describe no real database state.
            // Flush first, then copy only the checkpointed main file into the disposable probe.
            SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { checkpoint ->
                    check(checkpoint.moveToFirst() && checkpoint.getInt(0) == 0) { "cannot checkpoint database for diagnostic copy" }
                }
            }
            FileInputStream(source).use { input -> FileOutputStream(copy).use { output -> input.copyTo(output) } }
            return SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                val maxId = db.rawQuery("SELECT COALESCE(MAX(id), 0) FROM chat_messages", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                }
                val userId = maxId + 1L
                val replyId = maxId + 2L
                db.beginTransaction()
                try {
                    db.execSQL("INSERT INTO chat_sessions(id,operatorId,operatorName,lastMessage,lastTime) VALUES(?,?,?,?,?)", arrayOf(sessionId, "__diagnostic__", "诊断", "", now))
                    db.execSQL("INSERT INTO chat_messages(id,sessionId,senderId,senderName,content,type,mode,timestamp,isMe) VALUES(?,?,?,?,?,?,?,?,?)", arrayOf(userId, sessionId, "", "我", "[diagnostic]", "system", "offline", now, 1))
                    db.execSQL("INSERT INTO chat_messages(id,sessionId,senderId,senderName,content,type,mode,timestamp,isMe) VALUES(?,?,?,?,?,?,?,?,?)", arrayOf(replyId, sessionId, "", "诊断", probeAiReplyJson("诊断"), "ai_json", "offline", now + 1L, 0))
                    val rows = db.rawQuery("SELECT id,type,isMe FROM chat_messages WHERE sessionId=? ORDER BY id", arrayOf(sessionId)).use { cursor ->
                        generateSequence { if (cursor.moveToNext()) Triple(cursor.getLong(0), cursor.getString(1), cursor.getInt(2)) else null }.toList()
                    }
                    check(rows.size == 2 && rows[0].first == userId && rows[0].third == 1 && rows[1].first == replyId && rows[1].second == "ai_json") { "copied database message round trip failed" }
                    db.setTransactionSuccessful()
                    "productionDatabaseReadOnly=true,copyWrite=true,userMessageReadBack=true,aiReplyReadBack=true"
                } finally {
                    db.endTransaction()
                }
            }
        } finally {
            copy.delete()
        }
    }

    private suspend fun vectorDiagnosticsProbe(): String {
        val gateway = runCatching {
            org.koin.java.KoinJavaComponent.get<com.rhodes.privatechat.shared.vector.VectorStoreGateway>(com.rhodes.privatechat.shared.vector.VectorStoreGateway::class.java)
        }.getOrElse { throw IllegalStateException("vector store unavailable", it) }
        val local = gateway as? com.rhodes.privatechat.shared.vector.LocalVectorStoreGateway
            ?: return "skipped=non_local_vector_store"
        val metrics = local.diagnose(VectorSearchRequest(
            ownerType = "__diagnostic__", ownerId = "__diagnostic__", query = "Rhodes vector timing probe", limit = 1, candidateLimit = 1,
        ))
        return "candidateSqlMs=${metrics.sqlMs},decodeScoreMs=${metrics.decodeScoreMs},candidates=${metrics.candidateCount},decoded=${metrics.decodedCount},decodeFailures=${metrics.decodeFailures},dimensionMismatches=${metrics.dimensionMismatches},selected=${metrics.selectedCount},userContentSent=false"
    }

    private fun memoryItemsStatsProbe(context: Context): String = SQLiteDatabase.openDatabase(context.getDatabasePath(DB_NAME).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("SELECT COUNT(*),COALESCE(SUM(LENGTH(content)),0),COALESCE(SUM(LENGTH(rawJson)),0),COALESCE(MAX(LENGTH(content)),0),COALESCE(MAX(LENGTH(rawJson)),0) FROM memory_items", null).use { cursor ->
            check(cursor.moveToFirst()) { "memory_items aggregate returned no row" }
            "items=${cursor.getLong(0)},contentChars=${cursor.getLong(1)},rawJsonChars=${cursor.getLong(2)},maxContentChars=${cursor.getLong(3)},maxRawJsonChars=${cursor.getLong(4)}"
        }
    }

    private suspend fun backupSnapshotTimingProbe(repository: ChatRepository, appState: AppStateHolder): String {
        val started = SystemClock.elapsedRealtime()
        val payload = BackupContentFilter.apply(BackupSnapshotBuilder(repository, settingsForProbe()).build(), BackupContentSelection.All)
        val snapshotMs = SystemClock.elapsedRealtime() - started
        // Do not serialize a second full payload during self-check: that can itself exhaust a
        // troubled device. The estimate is deliberately conservative and content-free.
        val textChars = payload.content.messages.orEmpty().sumOf { it.content.length.toLong() } +
            payload.content.knowledgeBases.orEmpty().sumOf { it.rawContent.length.toLong() } +
            payload.content.knowledgeBaseChunks.orEmpty().sumOf { it.content.length.toLong() } +
            payload.content.memoryItems.orEmpty().sumOf { it.content.length.toLong() + it.rawJson.length.toLong() } +
            payload.chatArchives.sumOf { it.messagesJson.length.toLong() + it.stateJson.length.toLong() } +
            payload.chatHistorySegments.sumOf { it.messagesJson.length.toLong() }
        val records = payload.content.messages.orEmpty().size + payload.content.knowledgeBaseChunks.orEmpty().size +
            payload.content.memoryItems.orEmpty().size + payload.chatArchives.size + payload.chatHistorySegments.size
        val estimatedBytes = textChars * 3L + records * 512L
        return "snapshotMs=$snapshotMs,estimatedArchiveBytes=$estimatedBytes,textChars=$textChars,messages=${payload.content.messages.orEmpty().size},knowledgeBaseChunks=${payload.content.knowledgeBaseChunks.orEmpty().size},memoryItems=${payload.content.memoryItems.orEmpty().size}"
    }

    /** Native reads bypass SQLDelight and identify file/SQLite availability without mutating data. */
    private fun nativeDatabaseReadProbe(context: Context): String = SQLiteDatabase.openDatabase(context.getDatabasePath(DB_NAME).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        fun count(table: String): Long = db.rawQuery("SELECT COUNT(*) FROM [$table]", null).use { cursor ->
            check(cursor.moveToFirst()) { "count query returned no row" }
            cursor.getLong(0)
        }
        buildString {
            append("nativeRead=true,sessions=").append(count("chat_sessions"))
            append(",vectors=").append(count("vector_memories"))
        }
    }

    /** Reads every export-relevant table through an independent read-only SQLite connection.
     * This remains usable when the app's SQLDelight dispatcher is stalled. */
    private fun nativeBackupTableProbe(context: Context): String = SQLiteDatabase.openDatabase(context.getDatabasePath(DB_NAME).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        data class TableSpec(val table: String, val textColumns: List<String> = emptyList())
        val tables = listOf(
            TableSpec("moments", listOf("content")),
            TableSpec("moment_likes"),
            TableSpec("moment_comments", listOf("content", "replyToName")),
            TableSpec("diaries", listOf("content")),
            TableSpec("chat_archives", listOf("messagesJson", "summary", "stateJson")),
            TableSpec("chat_history_segments", listOf("messagesJson", "title", "reason")),
            TableSpec("gift_records", listOf("giftName", "imageUri")),
            TableSpec("dispatch_records", listOf("logChain", "items")),
            TableSpec("shared_experiences", listOf("content")),
            TableSpec("shared_experience_participants"),
            TableSpec("memory_items", listOf("content", "rawJson")),
            TableSpec("vector_memories", listOf("content", "embeddingJson", "tags")),
        )
        tables.joinToString(";") { spec ->
            val columns = spec.textColumns.joinToString(",") { column ->
                "COALESCE(SUM(LENGTH([$column])),0) AS ${column}_sum,COALESCE(MAX(LENGTH([$column])),0) AS ${column}_max"
            }
            val sql = "SELECT COUNT(*) AS rows${if (columns.isBlank()) "" else ",$columns"} FROM [${spec.table}]"
            db.rawQuery(sql, null).use { cursor ->
                check(cursor.moveToFirst()) { "${spec.table} aggregate returned no row" }
                buildString {
                    append("table=").append(spec.table).append(",rows=").append(cursor.getLong(0))
                    spec.textColumns.forEachIndexed { index, column ->
                        append(",").append(column).append("Chars=").append(cursor.getLong(1 + index * 2))
                        append(",max").append(column.replaceFirstChar { it.uppercase() }).append("Chars=").append(cursor.getLong(2 + index * 2))
                    }
                }
            }
        }
    }

    /** This is deliberately tiny: it distinguishes repository/driver queueing from a large export read. */
    private suspend fun repositoryDatabaseReadProbe(repository: ChatRepository): String {
        val sessions = repository.getAllSessionsSync()
        val books = repository.knowledgeBases.getAll()
        return "repositoryRead=true,sessions=${sessions.size},knowledgeBases=${books.size}"
    }

    /** Local hash embedding is CPU-only. It must remain fast even if the vector table is large. */
    private suspend fun localEmbeddingComputeProbe(): String {
        val started = SystemClock.elapsedRealtime()
        val vector = com.rhodes.privatechat.shared.vector.LocalHashEmbeddingGateway().embed("Rhodes local embedding health probe")
        return "localEmbedding=true,dimensions=${vector.size},elapsedMs=${SystemClock.elapsedRealtime() - started}"
    }

    private fun probeAiReplyJson(speaker: String): String = JsonObject(
        mapOf(
            "segments" to JsonArray(
                listOf(
                    JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("dialogue"),
                            "speaker" to JsonPrimitive(speaker),
                            "content" to JsonPrimitive("[本地自检回复]"),
                        )
                    )
                )
            )
        )
    ).toString()

    private fun isDiagnosticSession(session: ChatSession): Boolean =
        session.id.startsWith("session___probe_") ||
            session.id.startsWith("group___probe_") ||
            session.operatorId.startsWith("__probe_") ||
            session.operatorId.startsWith("group___probe_")

    private fun isValidProbeAiReply(content: String): Boolean = runCatching {
        val root = Json.parseToJsonElement(content) as? JsonObject ?: return@runCatching false
        val segment = (root["segments"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return@runCatching false
        (segment["type"] as? JsonPrimitive)?.content == "dialogue" &&
            (segment["content"] as? JsonPrimitive)?.content == "[本地自检回复]"
    }.getOrDefault(false)

    private fun databaseOpen(context: Context) = SQLiteDatabase.openDatabase(context.getDatabasePath(DB_NAME).path, null, SQLiteDatabase.OPEN_READONLY).use { "opened=true" }

    private fun databaseSchema(context: Context): String = SQLiteDatabase.openDatabase(context.getDatabasePath(DB_NAME).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        val required = mapOf("operators" to listOf("id", "name"), "chat_sessions" to listOf("id", "operatorId"), "chat_messages" to listOf("id", "sessionId", "timestamp"))
        val missing = required.flatMap { (table, columns) -> columns.filterNot { column -> db.rawQuery("PRAGMA table_info([$table])", null).use { c -> generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.contains(column) } }.map { "$table.$it" } }
        "missingColumns=${missing.joinToString(";").ifBlank { "none" }}"
    }

    private fun databaseCounts(context: Context): String = SQLiteDatabase.openDatabase(context.getDatabasePath(DB_NAME).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        fun count(table: String) = db.rawQuery("SELECT COUNT(*) FROM [$table]", null).use { c -> if (c.moveToFirst()) c.getLong(0) else -1 }
        "operators=${count("operators")},sessions=${count("chat_sessions")},messages=${count("chat_messages")}" 
    }

    private fun knowledgeBaseSqlCount(context: Context): String = SQLiteDatabase.openDatabase(context.getDatabasePath(DB_NAME).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        val books = db.rawQuery("SELECT COUNT(*) FROM knowledge_bases", null).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else -1L }
        val assignments = db.rawQuery("SELECT COUNT(*) FROM operator_knowledge_bases", null).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else -1L }
        "table=knowledge_bases,books=$books,assignments=$assignments"
    }

    private fun knowledgeBaseSqlMetadata(context: Context): String = SQLiteDatabase.openDatabase(context.getDatabasePath(DB_NAME).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("SELECT id,name,indexStatus,indexedEmbeddingSignature FROM knowledge_bases LIMIT 1", null).use { cursor ->
            val rowReadable = !cursor.moveToFirst() || cursor.getString(0).isNotBlank()
            check(rowReadable) { "knowledge base metadata row is malformed" }
            "table=knowledge_bases,metadataRead=true,hasRow=${cursor.count > 0}"
        }
    }

    private fun knowledgeBaseSqlContentSize(context: Context): String = SQLiteDatabase.openDatabase(context.getDatabasePath(DB_NAME).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("SELECT COUNT(*),COALESCE(SUM(LENGTH(rawContent)),0),COALESCE(MAX(LENGTH(rawContent)),0) FROM knowledge_bases", null).use { cursor ->
            check(cursor.moveToFirst()) { "knowledge base size query returned no row" }
            "table=knowledge_bases,books=${cursor.getLong(0)},rawContentChars=${cursor.getLong(1)},maxRawContentChars=${cursor.getLong(2)}"
        }
    }

    private fun vectorSqlCount(context: Context): String = SQLiteDatabase.openDatabase(context.getDatabasePath(DB_NAME).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("SELECT COUNT(*) FROM vector_memories", null).use { cursor ->
            check(cursor.moveToFirst()) { "vector count query returned no row" }
            "table=vector_memories,vectors=${cursor.getLong(0)}"
        }
    }

    private fun vectorSqlSignatureSize(context: Context): String = SQLiteDatabase.openDatabase(context.getDatabasePath(DB_NAME).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("SELECT COUNT(DISTINCT embeddingSignature),COALESCE(SUM(LENGTH(embeddingJson)),0),COALESCE(MAX(LENGTH(embeddingJson)),0) FROM vector_memories", null).use { cursor ->
            check(cursor.moveToFirst()) { "vector signature query returned no row" }
            "table=vector_memories,signatures=${cursor.getLong(0)},embeddingJsonChars=${cursor.getLong(1)},maxEmbeddingJsonChars=${cursor.getLong(2)}"
        }
    }

    private fun vectorSqlInvalidRows(context: Context): String = SQLiteDatabase.openDatabase(context.getDatabasePath(DB_NAME).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        db.rawQuery("SELECT COUNT(*) FROM vector_memories WHERE ownerType='' OR ownerId='' OR embeddingSignature='' OR embeddingJson='' OR embeddingJson='[]'", null).use { cursor ->
            check(cursor.moveToFirst()) { "vector invalid-row query returned no row" }
            "table=vector_memories,invalidRows=${cursor.getLong(0)}"
        }
    }

    private fun sessionIntegrity(context: Context): String = SQLiteDatabase.openDatabase(context.getDatabasePath(DB_NAME).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
        fun count(sql: String): Long = db.rawQuery(sql, null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        val orphanPrivate = count("SELECT COUNT(*) FROM chat_sessions s WHERE s.operatorId NOT LIKE 'group_%' AND NOT EXISTS (SELECT 1 FROM operators o WHERE o.id = s.operatorId)")
        val duplicateOperators = count("SELECT COUNT(*) FROM (SELECT operatorId FROM chat_sessions WHERE operatorId NOT LIKE 'group_%' GROUP BY operatorId HAVING COUNT(*) > 1)")
        val emptySessions = count("SELECT COUNT(*) FROM chat_sessions s WHERE NOT EXISTS (SELECT 1 FROM chat_messages m WHERE m.sessionId = s.id)")
        val orphanMessages = count("SELECT COUNT(*) FROM chat_messages m WHERE NOT EXISTS (SELECT 1 FROM chat_sessions s WHERE s.id = m.sessionId)")
        val nameMismatches = count("SELECT COUNT(*) FROM chat_sessions s JOIN operators o ON o.id = s.operatorId WHERE s.operatorId NOT LIKE 'group_%' AND trim(s.operatorName) != trim(o.name)")
        "orphanPrivateSessions=$orphanPrivate,duplicatePrivateOperators=$duplicateOperators,emptySessions=$emptySessions,orphanMessages=$orphanMessages,operatorNameMismatches=$nameMismatches"
    }

    private fun databaseCopyWrite(context: Context, checkId: String): String {
        val source = context.getDatabasePath(DB_NAME)
        val dir = File(context.cacheDir, "problem-probe")
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("cannot create probe directory")
        val copy = File(dir, "rhodes_terminal_$checkId.db")
        val wal = copy.resolveSibling("${copy.name}-wal")
        val shm = copy.resolveSibling("${copy.name}-shm")
        try {
            FileInputStream(source).use { input -> FileOutputStream(copy).use { output -> input.copyTo(output) } }
            source.resolveSibling("$DB_NAME-wal").takeIf { it.exists() }?.copyTo(wal, true)
            return SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL("INSERT OR REPLACE INTO operators(id,name) VALUES(?,?)", arrayOf("__probe_$checkId", "probe"))
                db.execSQL("INSERT OR REPLACE INTO chat_sessions(id,operatorId,operatorName,lastMessage,lastTime) VALUES(?,?,?,?,?)", arrayOf("__probe_$checkId", "__probe_$checkId", "probe", "", System.currentTimeMillis()))
                db.execSQL("INSERT OR REPLACE INTO chat_messages(id,sessionId,senderId,senderName,content,timestamp,isMe) VALUES(?,?,?,?,?,?,?)", arrayOf(-checkId.hashCode().toLong(), "__probe_$checkId", "", "probe", "probe", System.currentTimeMillis(), 1))
                "operatorInsert=true,sessionInsert=true,messageInsert=true,readBack=true"
            }
        } finally {
            copy.delete()
            wal.delete()
            shm.delete()
        }
    }

    private fun cleanupOldProbes(context: Context): String {
        val dir = File(context.cacheDir, "problem-probe")
        dir.listFiles()?.filter { it.name.startsWith("rhodes_terminal_") }?.forEach { it.delete() }
        return "retry=true"
    }

    private fun errorClass(error: Throwable): String = when {
        error.message?.contains("locked", true) == true -> "DATABASE_LOCKED"
        error.message?.contains("no such", true) == true -> "SCHEMA_MISMATCH"
        error is SocketTimeoutException -> "NETWORK_TIMEOUT"
        error is java.io.IOException -> "IO_ERROR"
        else -> error.javaClass.simpleName
    }
}
