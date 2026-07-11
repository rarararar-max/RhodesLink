package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemoryRepository(private val wrapper: DatabaseWrapper) {

    private val db: RhodesDatabase get() = wrapper.database

    // --- Memories ---
    suspend fun getShortTermMemory(sessionId: String): Memory? = withContext(Dispatchers.Default) {
        db.memoriesQueries.getLatestMemory(sessionId, MemoryType.SHORT_TERM.name) { id, sid, opId, type, content, keywords, preferences, taboos, createdAt, expiresAt ->
            Memory(id, sid, opId, try { MemoryType.valueOf(type) } catch (_: Exception) { MemoryType.SHORT_TERM }, content, keywords, preferences, taboos, createdAt, expiresAt)
        }.executeAsOneOrNull()
    }

    suspend fun getLongTermImpression(operatorId: String): Memory? = withContext(Dispatchers.Default) {
        db.memoriesQueries.getLatestLongTermImpression(operatorId) { id, sid, opId, type, content, keywords, preferences, taboos, createdAt, expiresAt ->
            Memory(id, sid, opId, try { MemoryType.valueOf(type) } catch (_: Exception) { MemoryType.LONG_TERM }, content, keywords, preferences, taboos, createdAt, expiresAt)
        }.executeAsOneOrNull()
    }

    suspend fun saveMemory(memory: Memory) = withContext(Dispatchers.Default) {
        db.memoriesQueries.insertMemory(memory.sessionId, memory.operatorId, memory.type.name, memory.content, memory.keywords, memory.preferences, memory.taboos, memory.createdAt, memory.expiresAt)
    }

    suspend fun getAllLongTermImpressions(): List<Memory> = withContext(Dispatchers.Default) {
        db.memoriesQueries.getAllLongTerm() { id, sid, opId, type, content, keywords, preferences, taboos, createdAt, expiresAt ->
            Memory(id, sid, opId, try { MemoryType.valueOf(type) } catch (_: Exception) { MemoryType.LONG_TERM }, content, keywords, preferences, taboos, createdAt, expiresAt)
        }.executeAsList()
    }

    suspend fun getAllMemoriesForBackup(): List<Memory> = withContext(Dispatchers.Default) {
        db.memoriesQueries.getAllMemoriesForBackup() { id, sid, opId, type, content, keywords, preferences, taboos, createdAt, expiresAt ->
            Memory(id, sid, opId, try { MemoryType.valueOf(type) } catch (_: Exception) { MemoryType.SHORT_TERM }, content, keywords, preferences, taboos, createdAt, expiresAt)
        }.executeAsList()
    }

    suspend fun getLatestDaily(): Memory? = withContext(Dispatchers.Default) {
        db.memoriesQueries.getLatestDaily() { id, sid, opId, type, content, keywords, preferences, taboos, createdAt, expiresAt ->
            Memory(id, sid, opId, try { MemoryType.valueOf(type) } catch (_: Exception) { MemoryType.DAILY }, content, keywords, preferences, taboos, createdAt, expiresAt)
        }.executeAsOneOrNull()
    }

    suspend fun getLatestDailyBySession(sessionId: String): Memory? = withContext(Dispatchers.Default) {
        db.memoriesQueries.getLatestMemory(sessionId, MemoryType.DAILY.name) { id, sid, opId, type, content, keywords, preferences, taboos, createdAt, expiresAt ->
            Memory(id, sid, opId, try { MemoryType.valueOf(type) } catch (_: Exception) { MemoryType.DAILY }, content, keywords, preferences, taboos, createdAt, expiresAt)
        }.executeAsOneOrNull()
    }

    suspend fun getLatestPrivateDaily(operatorId: String): Memory? = withContext(Dispatchers.Default) {
        db.memoriesQueries.getLatestPrivateDaily(operatorId) { id, sid, opId, type, content, keywords, preferences, taboos, createdAt, expiresAt ->
            Memory(id, sid, opId, try { MemoryType.valueOf(type) } catch (_: Exception) { MemoryType.DAILY }, content, keywords, preferences, taboos, createdAt, expiresAt)
        }.executeAsOneOrNull()
    }

    suspend fun enforceMemoryRetain(sessionId: String, keepCount: Int) = withContext(Dispatchers.Default) {
        if (keepCount <= 0) return@withContext
        val all = db.memoriesQueries.getMemoriesBySession(sessionId, MemoryType.SHORT_TERM.name) { id, sid, opId, type, content, keywords, preferences, taboos, createdAt, expiresAt ->
            Memory(id, sid, opId, try { MemoryType.valueOf(type) } catch (_: Exception) { MemoryType.SHORT_TERM }, content, keywords, preferences, taboos, createdAt, expiresAt)
        }.executeAsList()
        if (all.size > keepCount) {
            val toDelete = all.take(all.size - keepCount)
            for (m in toDelete) db.memoriesQueries.deleteMemory(m.id)
        }
    }

    suspend fun deleteAllImpressions() = withContext(Dispatchers.Default) { db.memoriesQueries.deleteAllLongTerm() }
    suspend fun deleteMemoriesBySession(sessionId: String) = withContext(Dispatchers.Default) { db.memoriesQueries.deleteMemoriesBySession(sessionId) }
    suspend fun deleteMemoriesByOperator(operatorId: String) = withContext(Dispatchers.Default) { db.memoriesQueries.deleteMemoriesByOperator(operatorId) }
}
