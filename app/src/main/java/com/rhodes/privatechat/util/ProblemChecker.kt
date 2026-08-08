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
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

data class ProblemCheckResult(val report: String, val success: Boolean)

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
    private const val TOTAL_TIMEOUT_MS = 120_000L
    private const val LOCAL_TIMEOUT_MS = 15_000L
    private const val COPY_TIMEOUT_MS = 30_000L
    private const val AI_TIMEOUT_MS = 60_000L
    private const val FUNCTION_AI_TIMEOUT_MS = 40_000L
    private const val DB_NAME = "rhodes_terminal.db"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val current = AtomicReference<ProblemCheckProgress>()
    private val active = AtomicReference<String?>(null)

    fun progress(): ProblemCheckProgress = current.get() ?: ProblemCheckProgress()

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
        val names = listOf("cleanup_previous_probe", "database_open", "database_schema", "database_counts", "session_integrity", "session_message_audit", "contacts_recovery", "database_copy_write_test", "private_ai_probe", "group_ai_probe", "cleanup")
        current.set(ProblemCheckProgress(checkId, now, now + TOTAL_TIMEOUT_MS, currentStage = "starting", stages = names.associateWith { StageProgress() }))
        scope.launch {
            // Cleanup used to run alongside the copy/write probe and could delete the probe's
            // database between copy and open, producing a false SQLITE_CANTOPEN failure.
            launchProbe(checkId, "cleanup_previous_probe", LOCAL_TIMEOUT_MS) {
                val files = cleanupOldProbes(context)
                cleanupStaleChatProbes(repository, appState)
                "$files,databaseProbeCleanup=true"
            }
            launchProbe(checkId, "database_open", LOCAL_TIMEOUT_MS) { databaseOpen(context) }
            launchProbe(checkId, "database_schema", LOCAL_TIMEOUT_MS) { databaseSchema(context) }
            launchProbe(checkId, "database_counts", LOCAL_TIMEOUT_MS) { databaseCounts(context) }
            launchProbe(checkId, "session_integrity", LOCAL_TIMEOUT_MS) { sessionIntegrity(context) }
            launchProbe(checkId, "session_message_audit", LOCAL_TIMEOUT_MS) { sessionMessageAudit(repository) }
            launchProbe(checkId, "contacts_recovery", LOCAL_TIMEOUT_MS) { recoverContacts(repository, appState) }
            startDetachedStructuredPrivateAiProbe(checkId, sharedUtils)
            startDetachedAiProbe(checkId, "group_ai_probe", sharedUtils, "ProblemCheckGroup")
            launchProbe(checkId, "database_copy_write_test", COPY_TIMEOUT_MS) { databaseCopyWrite(context, checkId) }
            // The copy probe cleans up only its own files in finally. Do not race it with a
            // directory-wide cleanup task.
            mark(checkId, "cleanup", ProblemStageStatus.NOT_RUN, detail = "temporary chat probes clean up their own data")
        }
        scope.launch {
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
                    break
                }
            }
        }
        scope.launch {
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
            if (value.status == ProblemStageStatus.PENDING || value.status == ProblemStageStatus.RUNNING) value.copy(status = ProblemStageStatus.ABANDONED, finishedAt = now, elapsedMs = now - value.startedAt)
            else value
        }
        current.set(old.copy(finishedAt = now, currentStage = old.currentStage, stages = stages, abandoned = true))
        active.compareAndSet(checkId, null)
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
                mark(checkId, name, if (it is SocketTimeoutException) ProblemStageStatus.TIMEOUT else ProblemStageStatus.FAILED, elapsed, "errorClass=${errorClass(it)},errorMessage=$message")
            }
        )
    }

    /** AI gateways can ignore coroutine cancellation. Mark their result from a separate watcher so
     * a stalled network call never blocks the local database report. */
    private fun startDetachedAiProbe(checkId: String, name: String, sharedUtils: SharedUtils, logTag: String) {
        mark(checkId, name, ProblemStageStatus.RUNNING)
        val started = SystemClock.elapsedRealtime()
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        scope.launch {
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
        scope.launch {
            delay(FUNCTION_AI_TIMEOUT_MS)
            if (completed.compareAndSet(false, true)) {
                mark(checkId, name, ProblemStageStatus.TIMEOUT, FUNCTION_AI_TIMEOUT_MS, "errorClass=AI_TIMEOUT,errorMessage=AI request exceeded ${FUNCTION_AI_TIMEOUT_MS / 1000}s")
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
            appendLine("reportVersion=7")
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
            appendLine("diagnosis.confidence=${if (snapshot.abandoned) "high" else "medium"}")
            appendLine("diagnosis.nextAction=${when { status == "running" -> "请保持应用在前台，等待status=completed后再复制"; snapshot.abandoned -> "根据lastStage和对应阶段detail处理；后台探针已脱离界面"; else -> "检查失败阶段的detail" }}")
        }
        return ProblemCheckResult(report, status == "completed" && primary == "NO_DATABASE_PROBLEM_FOUND")
    }

    private fun diagnosis(p: ProblemCheckProgress): String = when {
        p.stages.values.any { it.status == ProblemStageStatus.PENDING || it.status == ProblemStageStatus.RUNNING } -> "CHECK_RUNNING"
        p.abandoned -> "${p.currentStage.uppercase()}_PROBE_BLOCKED"
        p.stages["contacts_recovery"]?.status != ProblemStageStatus.SUCCESS -> "CONTACTS_RECOVERY_FAILED"
        p.stages["session_message_audit"]?.status == ProblemStageStatus.SUCCESS &&
            p.stages["session_message_audit"]?.detail?.contains("issues=") == true &&
            !p.stages["session_message_audit"]!!.detail.contains("issues=0") -> "SESSION_MESSAGE_MAPPING_FAILED"
        p.stages["private_ai_probe"]?.status != ProblemStageStatus.SUCCESS -> "PRIVATE_AI_RESPONSE_FAILED"
        p.stages["group_ai_probe"]?.status != ProblemStageStatus.SUCCESS -> "GROUP_AI_RESPONSE_FAILED"
        p.stages["session_integrity"]?.status == ProblemStageStatus.SUCCESS && p.stages["session_integrity"]?.detail?.let { detail ->
            !detail.contains("orphanPrivateSessions=0") ||
                !detail.contains("duplicatePrivateOperators=0") ||
                !detail.contains("orphanMessages=0") ||
                !detail.contains("operatorNameMismatches=0")
        } == true -> "CHAT_SESSION_INTEGRITY_FAILED"
        p.stages["database_copy_write_test"]?.status == ProblemStageStatus.FAILED -> "DATABASE_COPY_WRITE_FAILED"
        p.stages["database_schema"]?.status == ProblemStageStatus.FAILED -> "DATABASE_SCHEMA_FAILED"
        else -> "NO_DATABASE_PROBLEM_FOUND"
    }

    private fun sharedPipelineState(): String = try {
        org.koin.java.KoinJavaComponent.get<com.rhodes.privatechat.shared.settings.SettingsRepository>(com.rhodes.privatechat.shared.settings.SettingsRepository::class.java)
            .getString("private_reply_pipeline_last", "none")
    } catch (_: Exception) { "unavailable" }

    /** Uses the same structured response shape required by a real private reply. */
    private fun startDetachedStructuredPrivateAiProbe(checkId: String, sharedUtils: SharedUtils) {
        mark(checkId, "private_ai_probe", ProblemStageStatus.RUNNING)
        val started = SystemClock.elapsedRealtime()
        val completed = java.util.concurrent.atomic.AtomicBoolean(false)
        scope.launch {
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
        scope.launch {
            delay(FUNCTION_AI_TIMEOUT_MS)
            if (completed.compareAndSet(false, true)) mark(checkId, "private_ai_probe", ProblemStageStatus.TIMEOUT, FUNCTION_AI_TIMEOUT_MS, "errorClass=AI_TIMEOUT,errorMessage=structured private reply exceeded ${FUNCTION_AI_TIMEOUT_MS / 1000}s")
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
    private suspend fun cleanupStaleChatProbes(repository: ChatRepository, appState: AppStateHolder) {
        val sessions = repository.getAllSessionsSync()
        val staleSessions = sessions.filter { it.id.startsWith("session___probe_private_") || it.id.startsWith("group___probe_") }
        val staleOperators = repository.getAllOperatorsSync().filter {
            it.id.startsWith("__probe_private_") || it.id.startsWith("__probe_group_member_")
        }
        staleSessions.forEach { session -> runCatching { withTimeout(1_500L) { repository.deleteSession(session.id) } } }
        staleOperators.forEach { operator ->
            runCatching { withTimeout(1_500L) { repository.deleteOperator(operator.id) } }
        }
        if (staleSessions.isNotEmpty() || staleOperators.isNotEmpty()) {
            appState.reloadFromDatabase("problem_check_stale_probe_cleanup")
            DebugLogger.diagnostic("ProblemCheck/StaleProbeCleanup", "sessions=${staleSessions.size},operators=${staleOperators.size}")
        }
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

    private suspend fun privateChatProbe(repository: ChatRepository, appState: AppStateHolder, checkId: String): String {
        val operatorId = "__probe_private_$checkId"
        val sessionId = "session_$operatorId"
        try {
            repository.insertOperator(Operator(id = operatorId, name = "检测角色", description = "问题检测临时角色"))
            repository.insertSession(ChatSession(id = sessionId, operatorId = operatorId, operatorName = "检测角色", lastTime = System.currentTimeMillis()))
            if (!appState.reloadFromDatabase("problem_check_private_create")) throw IllegalStateException("private contact state refresh failed")
            if (appState.operators.value.none { it.id == operatorId } || appState.allSessions.value.none { it.id == sessionId }) {
                throw IllegalStateException("created private contact is not visible in UI state")
            }
            val userId = repository.getNextMessageId()
            repository.sendMessage(sessionId, ChatMessage(id = userId, sessionId = sessionId, senderName = "我", content = "问题检测私聊", isMe = true, timestamp = System.currentTimeMillis()))
            val userReadBack = repository.getMessagesSync(sessionId).any { it.id == userId && it.isMe }
            if (!userReadBack) throw IllegalStateException("private user message was not readable after save")
            return "contactCreate=true,contactVisible=true,userMessageSave=true,userMessageVisible=true"
        } finally {
            runCatching { withTimeout(1_500L) { repository.deleteSessionMessages(sessionId) } }
            runCatching { withTimeout(1_500L) { repository.deleteSession(sessionId) } }
            runCatching { withTimeout(1_500L) { repository.deleteRelationshipByOperator(operatorId) } }
            runCatching { withTimeout(1_500L) { repository.deleteOperator(operatorId) } }
            runCatching {
                appState.reloadFromDatabase("problem_check_private_cleanup")
            }
        }
    }

    private suspend fun groupChatProbe(repository: ChatRepository, appState: AppStateHolder, checkId: String): String {
        val operatorId = "__probe_group_member_$checkId"
        val groupId = "group___probe_$checkId"
        try {
            repository.insertOperator(Operator(id = operatorId, name = "检测群成员", description = "问题检测临时成员"))
            repository.insertSession(ChatSession(id = groupId, operatorId = groupId, operatorName = "检测群聊", members = operatorId, lastTime = System.currentTimeMillis()))
            if (!appState.reloadFromDatabase("problem_check_group_create")) throw IllegalStateException("group state refresh failed")
            if (appState.allSessions.value.none { it.id == groupId }) throw IllegalStateException("created group is not visible in UI state")
            val userId = repository.getNextMessageId()
            repository.sendMessage(groupId, ChatMessage(id = userId, sessionId = groupId, senderName = "我", content = "问题检测群聊", isMe = true, timestamp = System.currentTimeMillis()))
            if (repository.getMessagesSync(groupId).none { it.id == userId && it.isMe }) throw IllegalStateException("group user message was not readable after save")
            return "groupCreate=true,groupVisible=true,userMessageSave=true,userMessageVisible=true"
        } finally {
            runCatching { withTimeout(1_500L) { repository.deleteSessionMessages(groupId) } }
            runCatching { withTimeout(1_500L) { repository.deleteSession(groupId) } }
            runCatching { withTimeout(1_500L) { repository.deleteRelationshipByOperator(operatorId) } }
            runCatching { withTimeout(1_500L) { repository.deleteOperator(operatorId) } }
            runCatching {
                appState.reloadFromDatabase("problem_check_group_cleanup")
            }
        }
    }

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
