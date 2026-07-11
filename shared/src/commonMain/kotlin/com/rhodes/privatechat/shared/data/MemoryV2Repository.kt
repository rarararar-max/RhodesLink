package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.MemoryBatch
import com.rhodes.privatechat.shared.model.MemoryItem
import com.rhodes.privatechat.shared.model.MemoryLevel
import com.rhodes.privatechat.shared.model.MemoryLink
import com.rhodes.privatechat.shared.model.MemorySourceItem
import com.rhodes.privatechat.shared.model.MemorySourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MemoryV2Repository(private val wrapper: DatabaseWrapper) {
    private val db: RhodesDatabase get() = wrapper.database

    suspend fun insertMemoryItem(item: MemoryItem): Long = withContext(Dispatchers.Default) {
        db.memoryItemsQueries.insertMemoryItem(
            item.ownerType,
            item.ownerId,
            item.memoryLevel.name,
            item.memoryType,
            item.sourceKind.name,
            item.sourceRefId,
            item.sessionId,
            item.content,
            item.nickname,
            item.importance.toLong(),
            item.privacy,
            if (item.unmetNeed) 1L else 0L,
            item.location,
            item.emotionValence,
            item.eventTime,
            item.createdAt,
            item.updatedAt,
            item.expiresAt,
            item.status,
            item.scheduledTime,
            item.action,
            item.careType,
            item.topicKey,
            item.sourceActor,
            item.sourceTarget,
            item.lastUsedAt,
            item.usedCount.toLong(),
            item.confidence,
            item.rawJson,
            item.vectorId,
        )
        db.memoryItemsQueries.getLastInsertedMemoryItemId().executeAsOne()
    }

    suspend fun getMemoryItemsByOwner(ownerType: String, ownerId: String): List<MemoryItem> = withContext(Dispatchers.Default) {
        db.memoryItemsQueries.getMemoryItemsByOwner(ownerType, ownerId) { id, ownerType_, ownerId_, memoryLevel, memoryType, sourceKind, sourceRefId, sessionId, content, nickname, importance, privacy, unmetNeed, location, emotionValence, eventTime, createdAt, updatedAt, expiresAt, status, scheduledTime, action, careType, topicKey, sourceActor, sourceTarget, lastUsedAt, usedCount, confidence, rawJson, vectorId ->
            MemoryItem(id, ownerType_, ownerId_, try { MemoryLevel.valueOf(memoryLevel) } catch (_: Exception) { MemoryLevel.L1 }, memoryType, try { MemorySourceKind.valueOf(sourceKind) } catch (_: Exception) { MemorySourceKind.PRIVATE_CHAT }, sourceRefId, sessionId, content, nickname, importance.toInt(), privacy, unmetNeed != 0L, location, emotionValence, eventTime, createdAt, updatedAt, expiresAt, status, scheduledTime, action, careType, topicKey, sourceActor, sourceTarget, lastUsedAt, usedCount.toInt(), confidence, rawJson, vectorId)
        }.executeAsList()
    }

    suspend fun getMemoryItemsByLevel(ownerType: String, ownerId: String, level: MemoryLevel): List<MemoryItem> = withContext(Dispatchers.Default) {
        db.memoryItemsQueries.getMemoryItemsByLevel(ownerType, ownerId, level.name) { id, ownerType_, ownerId_, memoryLevel, memoryType, sourceKind, sourceRefId, sessionId, content, nickname, importance, privacy, unmetNeed, location, emotionValence, eventTime, createdAt, updatedAt, expiresAt, status, scheduledTime, action, careType, topicKey, sourceActor, sourceTarget, lastUsedAt, usedCount, confidence, rawJson, vectorId ->
            MemoryItem(id, ownerType_, ownerId_, try { MemoryLevel.valueOf(memoryLevel) } catch (_: Exception) { MemoryLevel.L1 }, memoryType, try { MemorySourceKind.valueOf(sourceKind) } catch (_: Exception) { MemorySourceKind.PRIVATE_CHAT }, sourceRefId, sessionId, content, nickname, importance.toInt(), privacy, unmetNeed != 0L, location, emotionValence, eventTime, createdAt, updatedAt, expiresAt, status, scheduledTime, action, careType, topicKey, sourceActor, sourceTarget, lastUsedAt, usedCount.toInt(), confidence, rawJson, vectorId)
        }.executeAsList()
    }

    suspend fun getActiveMemoryItemsByLevel(ownerType: String, ownerId: String, level: MemoryLevel, now: Long): List<MemoryItem> = withContext(Dispatchers.Default) {
        db.memoryItemsQueries.getActiveMemoryItemsByLevel(ownerType, ownerId, level.name, now) { id, ownerType_, ownerId_, memoryLevel, memoryType, sourceKind, sourceRefId, sessionId, content, nickname, importance, privacy, unmetNeed, location, emotionValence, eventTime, createdAt, updatedAt, expiresAt, status, scheduledTime, action, careType, topicKey, sourceActor, sourceTarget, lastUsedAt, usedCount, confidence, rawJson, vectorId ->
            MemoryItem(id, ownerType_, ownerId_, try { MemoryLevel.valueOf(memoryLevel) } catch (_: Exception) { MemoryLevel.L1 }, memoryType, try { MemorySourceKind.valueOf(sourceKind) } catch (_: Exception) { MemorySourceKind.PRIVATE_CHAT }, sourceRefId, sessionId, content, nickname, importance.toInt(), privacy, unmetNeed != 0L, location, emotionValence, eventTime, createdAt, updatedAt, expiresAt, status, scheduledTime, action, careType, topicKey, sourceActor, sourceTarget, lastUsedAt, usedCount.toInt(), confidence, rawJson, vectorId)
        }.executeAsList()
    }

    suspend fun getMemoryItemsByType(ownerType: String, ownerId: String, type: String): List<MemoryItem> = withContext(Dispatchers.Default) {
        db.memoryItemsQueries.getMemoryItemsByType(ownerType, ownerId, type) { id, ownerType_, ownerId_, memoryLevel, memoryType, sourceKind, sourceRefId, sessionId, content, nickname, importance, privacy, unmetNeed, location, emotionValence, eventTime, createdAt, updatedAt, expiresAt, status, scheduledTime, action, careType, topicKey, sourceActor, sourceTarget, lastUsedAt, usedCount, confidence, rawJson, vectorId ->
            MemoryItem(id, ownerType_, ownerId_, try { MemoryLevel.valueOf(memoryLevel) } catch (_: Exception) { MemoryLevel.L1 }, memoryType, try { MemorySourceKind.valueOf(sourceKind) } catch (_: Exception) { MemorySourceKind.PRIVATE_CHAT }, sourceRefId, sessionId, content, nickname, importance.toInt(), privacy, unmetNeed != 0L, location, emotionValence, eventTime, createdAt, updatedAt, expiresAt, status, scheduledTime, action, careType, topicKey, sourceActor, sourceTarget, lastUsedAt, usedCount.toInt(), confidence, rawJson, vectorId)
        }.executeAsList()
    }

    suspend fun getActiveMemoryItemByContent(ownerType: String, ownerId: String, level: MemoryLevel, type: String, content: String): MemoryItem? = withContext(Dispatchers.Default) {
        db.memoryItemsQueries.getActiveMemoryItemByContent(ownerType, ownerId, level.name, type, content) { id, ownerType_, ownerId_, memoryLevel, memoryType, sourceKind, sourceRefId, sessionId, content_, nickname, importance, privacy, unmetNeed, location, emotionValence, eventTime, createdAt, updatedAt, expiresAt, status, scheduledTime, action, careType, topicKey, sourceActor, sourceTarget, lastUsedAt, usedCount, confidence, rawJson, vectorId ->
            MemoryItem(id, ownerType_, ownerId_, try { MemoryLevel.valueOf(memoryLevel) } catch (_: Exception) { MemoryLevel.L1 }, memoryType, try { MemorySourceKind.valueOf(sourceKind) } catch (_: Exception) { MemorySourceKind.PRIVATE_CHAT }, sourceRefId, sessionId, content_, nickname, importance.toInt(), privacy, unmetNeed != 0L, location, emotionValence, eventTime, createdAt, updatedAt, expiresAt, status, scheduledTime, action, careType, topicKey, sourceActor, sourceTarget, lastUsedAt, usedCount.toInt(), confidence, rawJson, vectorId)
        }.executeAsOneOrNull()
    }

    suspend fun getMemoryItemsForBatch(ownerType: String, ownerId: String, level: MemoryLevel, start: Long, end: Long): List<MemoryItem> = withContext(Dispatchers.Default) {
        db.memoryItemsQueries.getMemoryItemsForBatch(ownerType, ownerId, level.name, start, end) { id, ownerType_, ownerId_, memoryLevel, memoryType, sourceKind, sourceRefId, sessionId, content, nickname, importance, privacy, unmetNeed, location, emotionValence, eventTime, createdAt, updatedAt, expiresAt, status, scheduledTime, action, careType, topicKey, sourceActor, sourceTarget, lastUsedAt, usedCount, confidence, rawJson, vectorId ->
            MemoryItem(id, ownerType_, ownerId_, try { MemoryLevel.valueOf(memoryLevel) } catch (_: Exception) { MemoryLevel.L1 }, memoryType, try { MemorySourceKind.valueOf(sourceKind) } catch (_: Exception) { MemorySourceKind.PRIVATE_CHAT }, sourceRefId, sessionId, content, nickname, importance.toInt(), privacy, unmetNeed != 0L, location, emotionValence, eventTime, createdAt, updatedAt, expiresAt, status, scheduledTime, action, careType, topicKey, sourceActor, sourceTarget, lastUsedAt, usedCount.toInt(), confidence, rawJson, vectorId)
        }.executeAsList()
    }

    suspend fun saveBatch(batch: MemoryBatch) = withContext(Dispatchers.Default) {
        db.memoryBatchesQueries.insertMemoryBatch(
            batch.ownerType,
            batch.ownerId,
            batch.sourceKind.name,
            batch.targetLevel.name,
            batch.inputCount.toLong(),
            batch.outputCount.toLong(),
            batch.windowStart,
            batch.windowEnd,
            batch.promptVersion,
            batch.status,
            batch.createdAt,
        )
    }

    suspend fun getBatches(ownerType: String, ownerId: String): List<MemoryBatch> = withContext(Dispatchers.Default) {
        db.memoryBatchesQueries.getMemoryBatchesByOwner(ownerType, ownerId).executeAsList().map {
            MemoryBatch(
                id = it.id,
                ownerType = it.ownerType,
                ownerId = it.ownerId,
                sourceKind = try { MemorySourceKind.valueOf(it.sourceKind) } catch (_: Exception) { MemorySourceKind.PRIVATE_CHAT },
                targetLevel = try { MemoryLevel.valueOf(it.targetLevel) } catch (_: Exception) { MemoryLevel.L1 },
                inputCount = it.inputCount.toInt(),
                outputCount = it.outputCount.toInt(),
                windowStart = it.windowStart,
                windowEnd = it.windowEnd,
                promptVersion = it.promptVersion,
                status = it.status,
                createdAt = it.createdAt,
            )
        }
    }

    suspend fun insertSource(source: MemorySourceItem): Long = withContext(Dispatchers.Default) {
        db.memorySourceQueueQueries.insertMemorySource(
            source.sourceKind.name,
            source.ownerType,
            source.ownerId,
            source.sourceRefId,
            source.contentText,
            source.timestamp,
            if (source.processedL1) 1L else 0L,
            if (source.processedVector) 1L else 0L,
            source.createdAt,
        )
        db.memorySourceQueueQueries.getLastInsertedMemorySourceId().executeAsOne()
    }

    suspend fun getPendingSources(): List<MemorySourceItem> = withContext(Dispatchers.Default) {
        db.memorySourceQueueQueries.getPendingMemorySources().executeAsList().map {
            MemorySourceItem(
                id = it.id,
                sourceKind = try { MemorySourceKind.valueOf(it.sourceKind) } catch (_: Exception) { MemorySourceKind.PRIVATE_CHAT },
                ownerType = it.ownerType,
                ownerId = it.ownerId,
                sourceRefId = it.sourceRefId,
                contentText = it.contentText,
                timestamp = it.timestamp,
                processedL1 = it.processedL1 != 0L,
                processedVector = it.processedVector != 0L,
                createdAt = it.createdAt,
            )
        }
    }

    suspend fun markSourceProcessedL1(id: Long) = withContext(Dispatchers.Default) { db.memorySourceQueueQueries.markMemorySourceProcessedL1(id) }
    suspend fun markSourceProcessedVector(id: Long) = withContext(Dispatchers.Default) { db.memorySourceQueueQueries.markMemorySourceProcessedVector(id) }

    suspend fun updateMemoryItemVectorId(id: Long, vectorId: String, updatedAt: Long) = withContext(Dispatchers.Default) {
        db.memoryItemsQueries.updateMemoryItemVectorId(vectorId, updatedAt, id)
    }

    suspend fun archiveMemoryItemsByLevel(ownerType: String, ownerId: String, level: MemoryLevel, updatedAt: Long) = withContext(Dispatchers.Default) {
        db.memoryItemsQueries.archiveMemoryItemsByLevel(updatedAt, ownerType, ownerId, level.name)
    }

    suspend fun archiveMemoryItem(id: Long, updatedAt: Long) = withContext(Dispatchers.Default) {
        db.memoryItemsQueries.archiveMemoryItem(updatedAt, id)
    }

    suspend fun markMemoryItemUsed(id: Long, now: Long) = withContext(Dispatchers.Default) {
        db.memoryItemsQueries.markMemoryItemUsed(now, now, id)
    }

    suspend fun insertMemoryLink(link: MemoryLink) = withContext(Dispatchers.Default) {
        db.memoryLinksQueries.insertMemoryLink(link.parentMemoryId, link.childMemoryId, link.linkType, link.createdAt)
    }

    suspend fun getMemoryLinksByParent(parentMemoryId: Long): List<MemoryLink> = withContext(Dispatchers.Default) {
        db.memoryLinksQueries.getMemoryLinksByParent(parentMemoryId).executeAsList().map {
            MemoryLink(
                id = it.id,
                parentMemoryId = it.parentMemoryId,
                childMemoryId = it.childMemoryId,
                linkType = it.linkType,
                createdAt = it.createdAt,
            )
        }
    }

    suspend fun deleteByOwner(ownerType: String, ownerId: String) = withContext(Dispatchers.Default) {
        db.memoryItemsQueries.deleteMemoryItemsByOwner(ownerType, ownerId)
        db.memoryBatchesQueries.deleteMemoryBatchesByOwner(ownerType, ownerId)
        db.memorySourceQueueQueries.deleteMemorySourcesByOwner(ownerType, ownerId)
    }

}
