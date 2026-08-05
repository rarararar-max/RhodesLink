package com.rhodes.privatechat.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {
    var enabled: Boolean = false
    var allowSensitiveTrace: Boolean = false
    private const val MAX_ENTRIES = 500
    private val entries = mutableListOf<LogEntry>()
    private val lock = Any()
    private val idCounter = java.util.concurrent.atomic.AtomicInteger(0)

    data class LogEntry(val id: Int, val timestamp: Long, val tag: String, val message: String) {
        val formattedTime: String get() =
            SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
    }

    fun log(tag: String, message: String) {
        if (!enabled) return
        // Never retain user text, prompts, model output or memories in the in-app production log buffer.
        val safeMessage = if (tag.contains("Chat", true) || tag.contains("Memory", true) || tag.contains("Diary", true) || tag.contains("Moment", true) || tag.contains("Vision", true)) {
            message.replace(Regex("(?i)(content|text|prompt|response|raw)=[^,\\n]+"), "$1=[redacted]")
        } else message
        val entry = LogEntry(idCounter.incrementAndGet(), System.currentTimeMillis(), tag, safeMessage)
        synchronized(lock) {
            entries.add(entry)
            if (entries.size > MAX_ENTRIES) {
                entries.removeAt(0)
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

    /** Full local-only prompt/response trace. Only call this after the user explicitly enables debug logging. */
    fun trace(tag: String, message: String) {
        if (!allowSensitiveTrace || !enabled) return
        val entry = LogEntry(idCounter.incrementAndGet(), System.currentTimeMillis(), tag, message)
        synchronized(lock) {
            entries.add(entry)
            if (entries.size > MAX_ENTRIES) entries.removeAt(0)
        }
    }

    fun getLogs(): List<LogEntry> = synchronized(lock) { entries.toList() }

    fun getLogText(): String = synchronized(lock) {
        entries.joinToString("\n") { "[${it.formattedTime}][${it.tag}] ${it.message}" }
    }

    fun clear() { synchronized(lock) { entries.clear() } }
}
