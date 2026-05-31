package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class AnchorRepository(private val wrapper: DatabaseWrapper) {

    private val db: RhodesDatabase get() = wrapper.database

    // --- Memory Anchors ---
    suspend fun saveAnchor(anchor: MemoryAnchor) = withContext(Dispatchers.Default) {
        db.memoryAnchorsQueries.insertAnchor(anchor.sessionId, anchor.operatorId, anchor.type.name, anchor.content, if (anchor.isPrivate) 1L else 0L, anchor.createdAt, anchor.expiresAt)
    }

    suspend fun saveAnchors(anchors: List<MemoryAnchor>) = withContext(Dispatchers.Default) {
        anchors.forEach { anchor ->
            db.memoryAnchorsQueries.insertAnchor(anchor.sessionId, anchor.operatorId, anchor.type.name, anchor.content, if (anchor.isPrivate) 1L else 0L, anchor.createdAt, anchor.expiresAt)
        }
    }

    suspend fun getPublicAnchors(operatorId: String): List<MemoryAnchor> = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        db.memoryAnchorsQueries.getPublicAnchors(operatorId, now) { id, sid, opId, type, content, isPrivate, createdAt, expiresAt ->
            MemoryAnchor(id, sid, opId, try { AnchorType.valueOf(type) } catch (_: Exception) { AnchorType.EVENT }, content, isPrivate != 0L, createdAt, expiresAt)
        }.executeAsList()
    }

    suspend fun getAnchors(operatorId: String): List<MemoryAnchor> = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        db.memoryAnchorsQueries.getAllAnchors(operatorId, now) { id, sid, opId, type, content, isPrivate, createdAt, expiresAt ->
            MemoryAnchor(id, sid, opId, try { AnchorType.valueOf(type) } catch (_: Exception) { AnchorType.EVENT }, content, isPrivate != 0L, createdAt, expiresAt)
        }.executeAsList()
    }

    suspend fun getAnchorCount(): Int = withContext(Dispatchers.Default) { db.memoryAnchorsQueries.getAnchorCount().executeAsOne().toInt() }
    suspend fun deleteOldAnchors(cutoff: Long) = withContext(Dispatchers.Default) { db.memoryAnchorsQueries.deleteOldAnchors(cutoff) }
}
