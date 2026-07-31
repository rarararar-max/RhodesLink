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
        if (memory.type == MemoryType.LONG_TERM) {
            db.transaction {
                db.memoriesQueries.deleteLongTermByOperator(memory.operatorId)
                db.memoriesQueries.insertMemory(memory.sessionId, memory.operatorId, memory.type.name, memory.content, memory.keywords, memory.preferences, memory.taboos, memory.createdAt, memory.expiresAt)
            }
        } else {
            db.memoriesQueries.insertMemory(memory.sessionId, memory.operatorId, memory.type.name, memory.content, memory.keywords, memory.preferences, memory.taboos, memory.createdAt, memory.expiresAt)
        }
    }

    suspend fun replaceShortTermMemory(memory: Memory) = withContext(Dispatchers.Default) {
        db.transaction {
            db.memoriesQueries.deleteShortTermMemories(memory.sessionId)
            db.memoriesQueries.insertMemory(memory.sessionId, memory.operatorId, MemoryType.SHORT_TERM.name, memory.content, memory.keywords, memory.preferences, memory.taboos, memory.createdAt, memory.expiresAt)
        }
    }

    /** Keeps exactly one current long-term impression for each operator. */
    suspend fun replaceLongTermImpression(memory: Memory) = withContext(Dispatchers.Default) {
        db.transaction {
            db.memoriesQueries.deleteLongTermByOperator(memory.operatorId)
            db.memoriesQueries.insertMemory(memory.sessionId, memory.operatorId, MemoryType.LONG_TERM.name, memory.content, memory.keywords, memory.preferences, memory.taboos, memory.createdAt, memory.expiresAt)
        }
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

    suspend fun getDailyBySessionAndDate(sessionId: String, dateKey: String): Memory? = withContext(Dispatchers.Default) {
        db.memoriesQueries.getDailyBySessionAndDate(sessionId, dateKey) { id, sid, opId, type, content, keywords, preferences, taboos, createdAt, expiresAt ->
            Memory(id, sid, opId, try { MemoryType.valueOf(type) } catch (_: Exception) { MemoryType.DAILY }, content, keywords, preferences, taboos, createdAt, expiresAt)
        }.executeAsOneOrNull()
    }

    suspend fun replaceDailyBySessionAndDate(memory: Memory, dateKey: String) = withContext(Dispatchers.Default) {
        db.transaction {
            db.memoriesQueries.getDailyBySessionAndDate(memory.sessionId, dateKey) { id, _, _, _, _, _, _, _, _, _ -> id }
                .executeAsOneOrNull()
                ?.let { db.memoriesQueries.deleteMemory(it) }
            db.memoriesQueries.replaceDailyBySessionAndDate(
                memory.sessionId, memory.operatorId, memory.content, dateKey, memory.createdAt, memory.expiresAt
            )
        }
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
    suspend fun deleteLongTermByOperator(operatorId: String) = withContext(Dispatchers.Default) { db.memoriesQueries.deleteLongTermByOperator(operatorId) }
    suspend fun deleteMemoriesBySession(sessionId: String) = withContext(Dispatchers.Default) { db.memoriesQueries.deleteMemoriesBySession(sessionId) }

    suspend fun deleteShortTermMemory(sessionId: String) = withContext(Dispatchers.Default) { db.memoriesQueries.deleteShortTermMemories(sessionId) }
    suspend fun deleteMemoriesByOperator(operatorId: String) = withContext(Dispatchers.Default) { db.memoriesQueries.deleteMemoriesByOperator(operatorId) }
}
