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
    private val entries = mutableListOf<LogEntry>()
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
        conversationStep(id, surface, "开始", "进行中", "对象=$target，模式=$mode")
        return id
    }

    fun conversationStep(roundId: String, surface: String, stage: String, status: String, details: String = "") {
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
    }

    fun getLogs(): List<LogEntry> = synchronized(lock) { entries.toList() }

    fun getLogText(): String = synchronized(lock) {
        entries.joinToString("\n") { "[${it.formattedTime}][${it.tag}] ${it.message}" }
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
        diagnosticPrefs?.edit()?.remove(DIAGNOSTIC_KEY)?.commit()
    }
}
