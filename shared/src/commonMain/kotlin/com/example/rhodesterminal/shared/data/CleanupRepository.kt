package com.example.rhodesterminal.shared.data

import com.example.rhodesterminal.shared.db.DatabaseWrapper
import com.example.rhodesterminal.shared.db.RhodesDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class CleanupRepository(private val wrapper: DatabaseWrapper) {

    private val db: RhodesDatabase get() = wrapper.database

    // --- Cleanup ---
    suspend fun cleanupExpiredData() = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        db.memoryAnchorsQueries.deleteExpiredAnchors(now)
        db.memoriesQueries.deleteExpired(now)
    }
}
