package com.rhodes.privatechat.shared.data

import android.util.Log
import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.memory.AnchorSourcePolicy
import com.rhodes.privatechat.shared.model.*
import com.rhodes.privatechat.shared.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class AnchorRepository(private val wrapper: DatabaseWrapper, private val settings: SettingsRepository? = null) {

    private val db: RhodesDatabase get() = wrapper.database

    // --- Memory Anchors ---
    suspend fun saveAnchor(anchor: MemoryAnchor): Boolean = withContext(Dispatchers.Default) {
        try {
            if (isDuplicate(anchor)) return@withContext false
            db.memoryAnchorsQueries.insertAnchor(anchor.sessionId, anchor.operatorId, anchor.type.name, anchor.content, if (anchor.isPrivate) 1L else 0L, anchor.createdAt, anchor.expiresAt, anchor.source, anchor.sourceName, anchor.sourceActor, anchor.sourceTarget, anchor.importance, anchor.knownFrom)
            true
        } catch (e: Exception) {
            Log.e("AnchorRepository", "保存锚点失败", e)
            false
        }
    }

    suspend fun saveAnchors(anchors: List<MemoryAnchor>) = withContext(Dispatchers.Default) {
        anchors.forEach { anchor ->
            try {
                if (isDuplicate(anchor)) return@forEach
                db.memoryAnchorsQueries.insertAnchor(anchor.sessionId, anchor.operatorId, anchor.type.name, anchor.content, if (anchor.isPrivate) 1L else 0L, anchor.createdAt, anchor.expiresAt, anchor.source, anchor.sourceName, anchor.sourceActor, anchor.sourceTarget, anchor.importance, anchor.knownFrom)
            } catch (e: Exception) {
                Log.e("AnchorRepository", "保存锚点失败", e)
            }
        }
    }

    private fun isDuplicate(anchor: MemoryAnchor): Boolean {
        return try {
            val now = Clock.System.now().toEpochMilliseconds()
            val existing = db.memoryAnchorsQueries.getDuplicateCandidates(anchor.operatorId, anchor.type.name, if (anchor.isPrivate) 1L else 0L, anchor.source, anchor.sourceName, now) { id, sid, opId, type, content, isPrivate, createdAt, expiresAt, source, sourceName, sourceActor, sourceTarget, importance, knownFrom ->
                mapAnchor(id, sid, opId, type, content, isPrivate, createdAt, expiresAt, source, sourceName, sourceActor, sourceTarget, importance, knownFrom)
            }.executeAsList()
            val normalized = normalize(anchor.content)
            existing.any { old ->
                val oldNorm = normalize(old.content)
                oldNorm == normalized || isNearDuplicate(anchor.type, oldNorm, normalized)
            }
        } catch (e: Exception) {
            Log.e("AnchorRepository", "检查重复锚点失败", e)
            false
        }
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("""\[[^\]]+]"""), "")
        .replace(" ", "")
        .replace("，", ",")
        .replace("。", "")
        .replace("！", "")
        .replace("？", "")

    private fun isNearDuplicate(type: AnchorType, oldValue: String, newValue: String): Boolean {
        if (oldValue.length < 6 || newValue.length < 6) return false
        if (type != AnchorType.PREFERENCE && type != AnchorType.TABOO && type != AnchorType.PLAN) return false
        val short = if (oldValue.length <= newValue.length) oldValue else newValue
        val long = if (oldValue.length > newValue.length) oldValue else newValue
        if (long.contains(short)) return true
        val oldChars = oldValue.toSet()
        val newChars = newValue.toSet()
        val overlap = oldChars.intersect(newChars).size
        val base = minOf(oldChars.size, newChars.size).coerceAtLeast(1)
        return overlap.toDouble() / base >= 0.82
    }

    suspend fun getPublicAnchors(operatorId: String): List<MemoryAnchor> = withContext(Dispatchers.Default) {
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            if (settings?.distinguishPrivateMemory == false) {
                db.memoryAnchorsQueries.getAllAnchors(operatorId, now) { id, sid, opId, type, content, isPrivate, createdAt, expiresAt, source, sourceName, sourceActor, sourceTarget, importance, knownFrom ->
                    mapAnchor(id, sid, opId, type, content, isPrivate, createdAt, expiresAt, source, sourceName, sourceActor, sourceTarget, importance, knownFrom)
                }.executeAsList()
            } else {
                db.memoryAnchorsQueries.getPublicAnchors(operatorId, now) { id, sid, opId, type, content, isPrivate, createdAt, expiresAt, source, sourceName, sourceActor, sourceTarget, importance, knownFrom ->
                    mapAnchor(id, sid, opId, type, content, isPrivate, createdAt, expiresAt, source, sourceName, sourceActor, sourceTarget, importance, knownFrom)
                }.executeAsList()
            }
        } catch (e: Exception) {
            Log.e("AnchorRepository", "查询公开锚点失败，可能是数据库迁移问题", e)
            emptyList()
        }
    }

    suspend fun getAnchors(operatorId: String): List<MemoryAnchor> = withContext(Dispatchers.Default) {
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            db.memoryAnchorsQueries.getAllAnchors(operatorId, now) { id, sid, opId, type, content, isPrivate, createdAt, expiresAt, source, sourceName, sourceActor, sourceTarget, importance, knownFrom ->
                mapAnchor(id, sid, opId, type, content, isPrivate, createdAt, expiresAt, source, sourceName, sourceActor, sourceTarget, importance, knownFrom)
            }.executeAsList()
        } catch (e: Exception) {
            Log.e("AnchorRepository", "查询锚点失败，可能是数据库迁移问题", e)
            emptyList()
        }
    }

    private fun mapAnchor(
        id: Long,
        sid: String,
        opId: String,
        type: String,
        content: String,
        isPrivate: Long,
        createdAt: Long,
        expiresAt: Long,
        source: String,
        sourceName: String,
        sourceActor: String,
        sourceTarget: String,
        importance: String,
        knownFrom: String
    ): MemoryAnchor = AnchorSourcePolicy.inferLegacy(MemoryAnchor(
        id = id,
        sessionId = sid,
        operatorId = opId,
        type = try { AnchorType.valueOf(type) } catch (_: Exception) { AnchorType.EVENT },
        content = content,
        isPrivate = isPrivate != 0L,
        createdAt = createdAt,
        expiresAt = expiresAt,
        source = source,
        sourceName = sourceName,
        sourceActor = sourceActor,
        sourceTarget = sourceTarget,
        importance = importance,
        knownFrom = knownFrom
    ))

    suspend fun getAnchorCount(): Int = withContext(Dispatchers.Default) {
        try {
            db.memoryAnchorsQueries.getAnchorCount().executeAsOne().toInt()
        } catch (e: Exception) {
            Log.e("AnchorRepository", "获取锚点数量失败", e)
            0
        }
    }

    suspend fun getAllAnchorsForBackup(): List<MemoryAnchor> = withContext(Dispatchers.Default) {
        db.memoryAnchorsQueries.getAllAnchorsForBackup { id, sid, opId, type, content, isPrivate, createdAt, expiresAt, source, sourceName, sourceActor, sourceTarget, importance, knownFrom ->
            mapAnchor(id, sid, opId, type, content, isPrivate, createdAt, expiresAt, source, sourceName, sourceActor, sourceTarget, importance, knownFrom)
        }.executeAsList()
    }
    suspend fun deleteOldAnchors(cutoff: Long) = withContext(Dispatchers.Default) {
        try {
            db.memoryAnchorsQueries.deleteOldAnchors(cutoff)
        } catch (e: Exception) {
            Log.e("AnchorRepository", "删除旧锚点失败", e)
        }
    }
    suspend fun deleteAnchorsBySession(sessionId: String) = withContext(Dispatchers.Default) {
        try {
            db.memoryAnchorsQueries.deleteAnchorsBySession(sessionId)
        } catch (e: Exception) {
            Log.e("AnchorRepository", "按会话删除锚点失败", e)
        }
    }
    suspend fun deleteAnchorsByOperator(operatorId: String) = withContext(Dispatchers.Default) {
        try {
            db.memoryAnchorsQueries.deleteAnchorsByOperator(operatorId)
        } catch (e: Exception) {
            Log.e("AnchorRepository", "按操作员删除锚点失败", e)
        }
    }

    suspend fun enforceAnchorRetain(operatorId: String, keepCount: Int = 200) = withContext(Dispatchers.Default) {
        try {
            db.memoryAnchorsQueries.enforceAnchorRetain(operatorId, operatorId, keepCount.toLong())
        } catch (e: Exception) {
            Log.e("AnchorRepository", "强制保留锚点失败", e)
        }
    }
}
