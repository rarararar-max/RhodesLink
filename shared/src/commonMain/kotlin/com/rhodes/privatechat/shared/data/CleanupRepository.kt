package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
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
        db.memoryItemsQueries.deleteExpiredMemoryItems(now, now)
        db.vectorMemoriesQueries.deleteExpiredVectorMemories(now)
        // Extraction sources are temporary work records, not another permanent chat archive.
        db.memorySourceQueueQueries.deleteProcessedMemorySourcesBefore(now - 30L * 86_400_000L)
    }
}
