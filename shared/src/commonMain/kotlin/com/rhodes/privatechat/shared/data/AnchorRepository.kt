package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.memory.AnchorSourcePolicy
import com.rhodes.privatechat.shared.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class AnchorRepository(private val wrapper: DatabaseWrapper) {

    private val db: RhodesDatabase get() = wrapper.database

    // --- Memory Anchors ---
    suspend fun saveAnchor(anchor: MemoryAnchor) = withContext(Dispatchers.Default) {
        if (isDuplicate(anchor)) return@withContext
        db.memoryAnchorsQueries.insertAnchor(anchor.sessionId, anchor.operatorId, anchor.type.name, anchor.content, if (anchor.isPrivate) 1L else 0L, anchor.createdAt, anchor.expiresAt, anchor.source, anchor.sourceName, anchor.sourceActor, anchor.sourceTarget, anchor.importance, anchor.knownFrom)
    }

    suspend fun saveAnchors(anchors: List<MemoryAnchor>) = withContext(Dispatchers.Default) {
        anchors.forEach { anchor ->
            if (isDuplicate(anchor)) return@forEach
            db.memoryAnchorsQueries.insertAnchor(anchor.sessionId, anchor.operatorId, anchor.type.name, anchor.content, if (anchor.isPrivate) 1L else 0L, anchor.createdAt, anchor.expiresAt, anchor.source, anchor.sourceName, anchor.sourceActor, anchor.sourceTarget, anchor.importance, anchor.knownFrom)
        }
    }

    private fun isDuplicate(anchor: MemoryAnchor): Boolean {
        val now = Clock.System.now().toEpochMilliseconds()
        val existing = db.memoryAnchorsQueries.getAllAnchors(anchor.operatorId, now) { id, sid, opId, type, content, isPrivate, createdAt, expiresAt, source, sourceName, sourceActor, sourceTarget, importance, knownFrom ->
            mapAnchor(id, sid, opId, type, content, isPrivate, createdAt, expiresAt, source, sourceName, sourceActor, sourceTarget, importance, knownFrom)
        }.executeAsList()
        val normalized = normalize(anchor.content)
        return existing.any {
            it.type == anchor.type && it.isPrivate == anchor.isPrivate && normalize(it.content) == normalized
        }
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(" ", "")
        .replace("，", ",")
        .replace("。", "")
        .replace("！", "")
        .replace("？", "")

    suspend fun getPublicAnchors(operatorId: String): List<MemoryAnchor> = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        db.memoryAnchorsQueries.getPublicAnchors(operatorId, now) { id, sid, opId, type, content, isPrivate, createdAt, expiresAt, source, sourceName, sourceActor, sourceTarget, importance, knownFrom ->
            mapAnchor(id, sid, opId, type, content, isPrivate, createdAt, expiresAt, source, sourceName, sourceActor, sourceTarget, importance, knownFrom)
        }.executeAsList()
    }

    suspend fun getAnchors(operatorId: String): List<MemoryAnchor> = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        db.memoryAnchorsQueries.getAllAnchors(operatorId, now) { id, sid, opId, type, content, isPrivate, createdAt, expiresAt, source, sourceName, sourceActor, sourceTarget, importance, knownFrom ->
            mapAnchor(id, sid, opId, type, content, isPrivate, createdAt, expiresAt, source, sourceName, sourceActor, sourceTarget, importance, knownFrom)
        }.executeAsList()
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

    suspend fun getAnchorCount(): Int = withContext(Dispatchers.Default) { db.memoryAnchorsQueries.getAnchorCount().executeAsOne().toInt() }
    suspend fun deleteOldAnchors(cutoff: Long) = withContext(Dispatchers.Default) { db.memoryAnchorsQueries.deleteOldAnchors(cutoff) }

    suspend fun enforceAnchorRetain(operatorId: String, keepCount: Int = 200) = withContext(Dispatchers.Default) {
        db.memoryAnchorsQueries.enforceAnchorRetain(operatorId, operatorId, keepCount.toLong())
    }
}
