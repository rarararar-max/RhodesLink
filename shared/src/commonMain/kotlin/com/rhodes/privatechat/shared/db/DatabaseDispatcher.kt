package com.rhodes.privatechat.shared.db

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

/**
 * SQLDelight calls are synchronous. Keep critical message and vector operations on one owned
 * lane so a timed-out caller cannot multiply those high-contention driver operations.
 */
object DatabaseDispatcher {
    val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val lock = Any()
    private var runningTask = "idle"
    private var runningSince = 0L
    private var queuedTasks = 0

    data class Snapshot(val runningTask: String, val runningForMs: Long, val queuedTasks: Int)

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(runningTask, if (runningSince == 0L) 0L else (System.currentTimeMillis() - runningSince).coerceAtLeast(0L), queuedTasks)
    }

    suspend fun <T> execute(task: String, block: () -> T): T {
        synchronized(lock) { queuedTasks++ }
        val queuedAt = TimeSource.Monotonic.markNow()
        var started = false
        return try {
            withContext(dispatcher) {
                synchronized(lock) {
                    queuedTasks--
                    started = true
                    runningTask = task
                    runningSince = System.currentTimeMillis()
                }
                try {
                    block()
                } finally {
                    synchronized(lock) {
                        runningTask = "idle"
                        runningSince = 0L
                    }
                }
            }
        } catch (error: Throwable) {
            if (!started) synchronized(lock) { queuedTasks-- }
            throw error
        }
    }
}
