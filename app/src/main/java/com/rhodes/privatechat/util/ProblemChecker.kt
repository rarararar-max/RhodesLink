package com.rhodes.privatechat.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.SystemClock
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        val names = listOf("cleanup_previous_probe", "database_open", "database_schema", "database_counts", "session_integrity", "repository_read", "state_snapshot", "database_copy_write_test", "ai_request", "cleanup")
        current.set(ProblemCheckProgress(checkId, now, now + TOTAL_TIMEOUT_MS, currentStage = "starting", stages = names.associateWith { StageProgress() }))
        scope.launch {
            launch { launchProbe(checkId, "cleanup_previous_probe", LOCAL_TIMEOUT_MS) { cleanupOldProbes(context) } }
            launch { launchProbe(checkId, "database_open", LOCAL_TIMEOUT_MS) { databaseOpen(context) } }
            launch { launchProbe(checkId, "database_schema", LOCAL_TIMEOUT_MS) { databaseSchema(context) } }
            launch { launchProbe(checkId, "database_counts", LOCAL_TIMEOUT_MS) { databaseCounts(context) } }
            launch { launchProbe(checkId, "session_integrity", LOCAL_TIMEOUT_MS) { sessionIntegrity(context) } }
            launch { launchProbe(checkId, "repository_read", LOCAL_TIMEOUT_MS) {
                // Keep the stage name for report compatibility, but use the independent SQLite
                // connection. The diagnostic path must not acquire business locks or repositories.
                databaseCounts(context)
            } }
            launch { launchProbe(checkId, "state_snapshot", LOCAL_TIMEOUT_MS) {
                "operators=${appState.operators.value.size},sessions=${appState.allSessions.value.size}"
            } }
            launch { launchProbe(checkId, "database_copy_write_test", COPY_TIMEOUT_MS) { databaseCopyWrite(context, checkId) } }
            launch { launchProbe(checkId, "ai_request", AI_TIMEOUT_MS) {
                val started = SystemClock.elapsedRealtime()
                val response = withTimeoutOrNull(AI_TIMEOUT_MS) {
                    sharedUtils.chat(listOf(AiMessage("system", "只回复：检查成功"), AiMessage("user", "问题检查")), "ProblemCheckAI", maxOutputTokens = 16)
                } ?: throw SocketTimeoutException("AI timeout")
                if (response.isBlank()) throw IllegalStateException("AI empty response")
                "elapsedMs=${SystemClock.elapsedRealtime() - started},responseLength=${response.length}"
            } }
            launch { launchProbe(checkId, "cleanup", LOCAL_TIMEOUT_MS) { cleanupOldProbes(context) } }
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
            onFailure = { mark(checkId, name, if (it is SocketTimeoutException) ProblemStageStatus.TIMEOUT else ProblemStageStatus.FAILED, elapsed, "errorClass=${errorClass(it)}") }
        )
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
            snapshot.stages.forEach { (name, stage) ->
                appendLine("$name.status=${stage.status.name.lowercase()}")
                appendLine("$name.elapsedMs=${stage.elapsedMs}")
                if (stage.detail.isNotBlank()) appendLine("$name.detail=${stage.detail}")
            }
            appendLine("lastStage=${snapshot.currentStage}")
            appendLine("diagnosis.primary=$primary")
            appendLine("diagnosis.confidence=${if (snapshot.abandoned) "high" else "medium"}")
            appendLine("diagnosis.nextAction=${if (snapshot.abandoned) "根据lastStage和对应阶段detail处理；后台探针已脱离界面" else "检查失败阶段的detail"}")
        }
        return ProblemCheckResult(report, status == "completed" && primary == "NO_DATABASE_PROBLEM_FOUND")
    }

    private fun diagnosis(p: ProblemCheckProgress): String = when {
        p.abandoned -> "${p.currentStage.uppercase()}_PROBE_BLOCKED"
        p.stages["session_integrity"]?.status == ProblemStageStatus.SUCCESS && p.stages["session_integrity"]?.detail?.let { detail ->
            !detail.contains("orphanPrivateSessions=0") ||
                !detail.contains("duplicatePrivateOperators=0") ||
                !detail.contains("orphanMessages=0") ||
                !detail.contains("operatorNameMismatches=0")
        } == true -> "CHAT_SESSION_INTEGRITY_FAILED"
        p.stages["database_copy_write_test"]?.status == ProblemStageStatus.FAILED -> "DATABASE_COPY_WRITE_FAILED"
        p.stages["database_schema"]?.status == ProblemStageStatus.FAILED -> "DATABASE_SCHEMA_FAILED"
        p.stages["ai_request"]?.status == ProblemStageStatus.TIMEOUT -> "AI_REQUEST_TIMEOUT"
        else -> "NO_DATABASE_PROBLEM_FOUND"
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
        dir.mkdirs()
        val copy = File(dir, "rhodes_terminal_$checkId.db")
        FileInputStream(source).use { input -> FileOutputStream(copy).use { output -> input.copyTo(output) } }
        source.resolveSibling("$DB_NAME-wal").takeIf { it.exists() }?.copyTo(copy.resolveSibling("${copy.name}-wal"), true)
        return SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT OR REPLACE INTO operators(id,name) VALUES(?,?)", arrayOf("__probe_$checkId", "probe"))
            db.execSQL("INSERT OR REPLACE INTO chat_sessions(id,operatorId,operatorName,lastMessage,lastTime) VALUES(?,?,?,?,?)", arrayOf("__probe_$checkId", "__probe_$checkId", "probe", "", System.currentTimeMillis()))
            db.execSQL("INSERT OR REPLACE INTO chat_messages(id,sessionId,senderId,senderName,content,timestamp,isMe) VALUES(?,?,?,?,?,?,?)", arrayOf(-checkId.hashCode().toLong(), "__probe_$checkId", "", "probe", "probe", System.currentTimeMillis(), 1))
            "operatorInsert=true,sessionInsert=true,messageInsert=true,readBack=true"
        }.also { copy.delete(); copy.resolveSibling("${copy.name}-wal").delete(); copy.resolveSibling("${copy.name}-shm").delete() }
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
