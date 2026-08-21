package com.rhodes.privatechat.util

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {
    var enabled: Boolean = false
    var allowSensitiveTrace: Boolean = false
    private const val MAX_ENTRIES = 500
    private const val DIAGNOSTIC_PREFS = "rhodes_diagnostics"
    private const val DIAGNOSTIC_KEY = "entries_v1"
    private const val MAX_PERSISTED_DIAGNOSTICS = 100
    private const val MAX_OPERATIONS = 120
    private val entries = mutableListOf<LogEntry>()
    private val operations = mutableListOf<DebugOperation>()
    private val lock = Any()
    private val idCounter = java.util.concurrent.atomic.AtomicInteger(0)
    private val roundCounter = java.util.concurrent.atomic.AtomicInteger(0)
    private var diagnosticPrefs: android.content.SharedPreferences? = null

    data class LogEntry(val id: Int, val timestamp: Long, val tag: String, val message: String) {
        val formattedTime: String get() =
            SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
    }

    fun log(tag: String, message: String) {
        if (!enabled) return
        addEntry(tag, message)
    }

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(DIAGNOSTIC_PREFS, Context.MODE_PRIVATE)
        diagnosticPrefs = prefs
        val restored = prefs.getString(DIAGNOSTIC_KEY, "").orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.split('\t', limit = 3)
                val timestamp = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                val tag = parts.getOrNull(1) ?: return@mapNotNull null
                val message = parts.getOrNull(2) ?: return@mapNotNull null
                LogEntry(idCounter.incrementAndGet(), timestamp, tag, message)
            }
            .toList()
        synchronized(lock) {
            if (entries.isEmpty()) entries.addAll(restored.takeLast(MAX_PERSISTED_DIAGNOSTICS))
        }
    }

    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val frames = error.stackTrace.take(8).joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
            diagnostic("Crash/Uncaught", "thread=${thread.name}, error=${error.javaClass.simpleName}:${error.message?.take(160)}, frames=$frames")
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Records recovery-critical failures even when optional debug logging is disabled.
     * Callers must only include identifiers, counts and exception summaries, never chat content or secrets.
     */
    fun diagnostic(tag: String, message: String) {
        addEntry("Diagnostic/$tag", message.replace('\n', ' ').replace('\t', ' '), persist = true)
    }

    private fun addEntry(tag: String, message: String, persist: Boolean = false) {
        val entry = LogEntry(idCounter.incrementAndGet(), System.currentTimeMillis(), tag, message)
        synchronized(lock) {
            entries.add(entry)
            if (entries.size > MAX_ENTRIES) {
                entries.removeAt(0)
            }
            if (persist) {
                val encoded = entries.asSequence()
                    .filter { it.tag.startsWith("Diagnostic/") }
                    .toList()
                    .takeLast(MAX_PERSISTED_DIAGNOSTICS)
                    .joinToString("\n") { "${it.timestamp}\t${it.tag}\t${it.message}" }
                diagnosticPrefs?.edit()?.putString(DIAGNOSTIC_KEY, encoded)?.commit()
            }
        }
    }

    /** Compact lifecycle records for the in-app debug screen; never include chat content. */
    fun chatEvent(surface: String, event: String, status: String, details: String = "") {
        log("ChatEvent/$surface", buildString {
            append("$surface | $event | $status")
            if (details.isNotBlank()) append(" | $details")
        })
    }

    data class DebugOperationStep(
        val timestamp: Long,
        val label: String,
        val status: String,
        val details: String = ""
    )

    data class DebugOperation(
        val id: String,
        val surface: String,
        val target: String,
        val mode: String,
        val startedAt: Long,
        val finishedAt: Long = 0L,
        val result: String = "进行中",
        val summary: String = "",
        val steps: List<DebugOperationStep> = emptyList(),
        val modules: Map<String, String> = emptyMap()
    ) {
        val formattedTime: String get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(startedAt))
        val durationMs: Long get() = (if (finishedAt > 0L) finishedAt else System.currentTimeMillis()) - startedAt
    }

    /** Records a privacy-safe context count without storing memory or knowledge-base text. */
    fun contextUsed(surface: String, memoryCount: Int = 0, knowledgeCount: Int = 0, injectedCount: Int? = null) {
        log("Context/$surface", buildString {
            append("记忆或知识库：${memoryCount + knowledgeCount} 条")
            if (memoryCount > 0) append("，记忆=$memoryCount 条")
            if (knowledgeCount > 0) append("，知识库=$knowledgeCount 条")
            if (injectedCount != null) append("，最终注入=$injectedCount 条")
        })
    }

    fun countContextBlocks(context: String): Int = context
        .lineSequence()
        .count { it.trimStart().startsWith("-") || it.trimStart().startsWith("•") }
        .takeIf { it > 0 }
        ?: if (context.isNotBlank() && context != "无") 1 else 0

    /** Human-readable lifecycle records for one private or group reply round. */
    fun startConversationRound(surface: String, target: String, mode: String): String {
        val id = "${System.currentTimeMillis().toString(36)}-${roundCounter.incrementAndGet()}"
        beginOperation(id, surface, target, mode)
        conversationStep(id, surface, "开始", "进行中", "对象=$target，模式=$mode")
        return id
    }

    fun beginOperation(surface: String, target: String, mode: String = ""): String {
        val id = "${System.currentTimeMillis().toString(36)}-${roundCounter.incrementAndGet()}"
        beginOperation(id, surface, target, mode)
        return id
    }

    private fun beginOperation(id: String, surface: String, target: String, mode: String) {
        if (!enabled) return
        synchronized(lock) {
            operations.removeAll { it.id == id }
            operations += DebugOperation(id, surface, target, mode, System.currentTimeMillis())
            if (operations.size > MAX_OPERATIONS) operations.removeAt(0)
        }
    }

    fun finishOperation(id: String, result: String, summary: String) {
        if (!enabled) return
        synchronized(lock) {
            val index = operations.indexOfLast { it.id == id }
            if (index < 0) return
            val operation = operations[index]
            operations[index] = operation.copy(finishedAt = System.currentTimeMillis(), result = result, summary = summary)
        }
    }

    /** Stores a named diagnostic module for the operation detail view. Sensitive payloads remain opt-in. */
    fun attachOperationModule(id: String, name: String, content: String, sensitive: Boolean = false) {
        if (!enabled || (sensitive && !allowSensitiveTrace)) return
        synchronized(lock) {
            val index = operations.indexOfLast { it.id == id }
            if (index < 0) return
            val operation = operations[index]
            operations[index] = operation.copy(modules = operation.modules + (name to content))
        }
    }

    fun conversationStep(roundId: String, surface: String, stage: String, status: String, details: String = "") {
        if (enabled) synchronized(lock) {
            val index = operations.indexOfLast { it.id == roundId }
            if (index >= 0) {
                val operation = operations[index]
                operations[index] = operation.copy(steps = operation.steps + DebugOperationStep(System.currentTimeMillis(), stage, status, details))
                if (stage == "本轮总览") {
                    val result = when (status) {
                        "成功" -> "成功"
                        "失败" -> "失败"
                        else -> status
                    }
                    operations[index] = operations[index].copy(finishedAt = System.currentTimeMillis(), result = result, summary = details)
                }
            }
        }
        log("Round/$roundId/$surface/$stage/$status", buildString {
            append("[$surface][$stage] $status")
            if (details.isNotBlank()) append("\n$details")
        })
    }

    /** Full local-only prompt/response trace. Only call this after the user explicitly enables debug logging. */
    fun trace(tag: String, message: String) {
        if (!allowSensitiveTrace || !enabled) return
        val entry = LogEntry(idCounter.incrementAndGet(), System.currentTimeMillis(), tag, message)
        synchronized(lock) {
            entries.add(entry)
            if (entries.size > MAX_ENTRIES) entries.removeAt(0)
        }
    }

    /** Records the exact normalized result written to the local chat database. */
    fun traceFinalSaved(surface: String, roundId: String, content: String) {
        trace("AI/✓$surface#$roundId", "【最终保存内容】\n$content")
        attachOperationModule(roundId, "最终保存", content, sensitive = true)
    }

    fun getLogs(): List<LogEntry> = synchronized(lock) { entries.toList() }

    fun getOperations(): List<DebugOperation> = synchronized(lock) { operations.toList() }

    fun getLogText(): String = synchronized(lock) {
        entries.joinToString("\n") { "[${it.formattedTime}][${it.tag}] ${it.message}" }
    }

    fun clear() {
        synchronized(lock) { entries.clear(); operations.clear() }
        diagnosticPrefs?.edit()?.remove(DIAGNOSTIC_KEY)?.commit()
    }
}
