package com.rhodes.privatechat.util

import android.util.Log
import java.io.StringWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {
    private const val MAX_ENTRIES = 500
    private val entries = mutableListOf<LogEntry>()
    private val lock = Any()
    private val idCounter = java.util.concurrent.atomic.AtomicInteger(0)

    init {
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            pw.flush()
            log("CRASH", "${throwable.message}\n${sw.toString().take(2000)}")
            oldHandler?.uncaughtException(thread, throwable)
        }
    }

    data class LogEntry(val id: Int, val timestamp: Long, val tag: String, val message: String) {
        val formattedTime: String get() =
            SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
    }

    fun log(tag: String, message: String) {
        val entry = LogEntry(idCounter.incrementAndGet(), System.currentTimeMillis(), tag, message)
        synchronized(lock) {
            entries.add(entry)
            if (entries.size > MAX_ENTRIES) {
                entries.removeAt(0)
            }
        }
        Log.d("Debug/$tag", message)
    }

    fun getLogs(): List<LogEntry> = synchronized(lock) { entries.toList().reversed() }

    fun getLogText(): String = synchronized(lock) {
        entries.joinToString("\n") { "[${it.formattedTime}][${it.tag}] ${it.message}" }
    }

    fun clear() { synchronized(lock) { entries.clear() } }
}
