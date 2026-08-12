package com.rhodes.privatechat.data.backup

import java.util.concurrent.atomic.AtomicLong

/** Process-local write fence. Workers check it before creating model requests or writing results. */
object BackupRestoreMaintenance {
    private val generation = AtomicLong(0L)

    @Volatile
    var active: Boolean = false
        private set

    fun begin(): Long {
        active = true
        return generation.incrementAndGet()
    }

    fun finish(): Long {
        val next = generation.incrementAndGet()
        active = false
        return next
    }

    fun currentGeneration(): Long = generation.get()
}
