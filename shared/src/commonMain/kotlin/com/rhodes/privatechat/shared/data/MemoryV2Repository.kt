package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.DatabaseDispatcher
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.MemoryBatch
import com.rhodes.privatechat.shared.model.MemoryItem
import com.rhodes.privatechat.shared.model.MemoryLevel
import com.rhodes.privatechat.shared.model.MemoryLink
import com.rhodes.privatechat.shared.model.MemorySourceItem
import com.rhodes.privatechat.shared.model.MemorySourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

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

    suspend fun getMemoryItemsByOwner(ownerType: String, ownerId: String): List<MemoryItem> = withContext(DatabaseDispatcher.dispatcher) {
        db.memoryItemsQueries.getMemoryItemsByOwner(ownerType, ownerId) { id, ownerType_, ownerId_, memoryLevel, memoryType, sourceKind, sourceRefId, sessionId, content, nickname, importance, privacy, unmetNeed, location, emotionValence, eventTime, createdAt, updatedAt, expiresAt, status, scheduledTime, action, careType, topicKey, sourceActor, sourceTarget, lastUsedAt, usedCount, confidence, rawJson, vectorId ->
            MemoryItem(id, ownerType_, ownerId_, try { MemoryLevel.valueOf(memoryLevel) } catch (_: Exception) { MemoryLevel.L1 }, memoryType, try { MemorySourceKind.valueOf(sourceKind) } catch (_: Exception) { MemorySourceKind.PRIVATE_CHAT }, sourceRefId, sessionId, content, nickname, importance.toInt(), privacy, unmetNeed != 0L, location, emotionValence, eventTime, createdAt, updatedAt, expiresAt, status, scheduledTime, action, careType, topicKey, sourceActor, sourceTarget, lastUsedAt, usedCount.toInt(), confidence, rawJson, vectorId)
        }.executeAsList()
    }

    suspend fun getAllMemoryItems(): List<MemoryItem> = withContext(DatabaseDispatcher.dispatcher) {
        db.memoryItemsQueries.getAllMemoryItems { id, ownerType_, ownerId_, memoryLevel, memoryType, sourceKind, sourceRefId, sessionId, content, nickname, importance, privacy, unmetNeed, location, emotionValence, eventTime, createdAt, updatedAt, expiresAt, status, scheduledTime, action, careType, topicKey, sourceActor, sourceTarget, lastUsedAt, usedCount, confidence, rawJson, vectorId ->
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
        // Retries and duplicate UI events can submit the same extraction window more than once.
        // Reuse its unfinished task so it keeps one lease and one retry history.
        db.transactionWithResult {
            fun existingId() = db.memorySourceQueueQueries.getUnfinishedMemorySourceId(
                source.sourceKind.name, source.ownerType, source.ownerId, source.sourceRefId,
                source.contentText, source.timestamp,
            ).executeAsOneOrNull()
            existingId()?.let { return@transactionWithResult it }
            db.memorySourceQueueQueries.insertMemorySource(
                source.sourceKind.name, source.ownerType, source.ownerId, source.sourceRefId,
                source.contentText, source.timestamp, if (source.processedL1) 1L else 0L,
                if (source.processedVector) 1L else 0L, source.status, source.retryCount.toLong(),
                source.nextRetryAt, source.leaseUntil, source.claimToken, source.lastError, source.createdAt,
            )
            db.memorySourceQueueQueries.getMemorySourceId(
                source.sourceKind.name, source.ownerType, source.ownerId, source.sourceRefId,
                source.contentText, source.timestamp,
            ).executeAsOne()
        }
    }

    suspend fun claimPendingSources(now: Long, limit: Int, leaseMs: Long = 10 * 60_000L): List<Pair<MemorySourceItem, String>> = withContext(Dispatchers.Default) {
        db.memorySourceQueueQueries.getPendingMemorySources(now, now, limit.toLong()).executeAsList().mapNotNull {
            val token = "memory-${now}-${Random.nextLong()}"
            db.memorySourceQueueQueries.claimMemorySource(now + leaseMs, token, it.id, now, now)
            if (db.memorySourceQueueQueries.getMemorySourceClaimToken(it.id).executeAsOneOrNull() != token) return@mapNotNull null
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
                status = "running",
                retryCount = it.retryCount.toInt(),
                nextRetryAt = it.nextRetryAt,
                leaseUntil = now + leaseMs,
                claimToken = token,
                lastError = it.lastError,
                createdAt = it.createdAt,
            ) to token
        }
    }

    suspend fun claimSource(id: Long, now: Long, leaseMs: Long = 10 * 60_000L): String? = withContext(Dispatchers.Default) {
        val token = "memory-${now}-${Random.nextLong()}"
        db.memorySourceQueueQueries.claimMemorySourceById(now + leaseMs, token, id, now)
        token.takeIf { db.memorySourceQueueQueries.getMemorySourceClaimToken(id).executeAsOneOrNull() == it }
    }

    suspend fun isMemorySourceFinished(id: Long): Boolean = withContext(Dispatchers.Default) {
        db.memorySourceQueueQueries.getMemorySourceStatus(id).executeAsOneOrNull()?.let { state ->
            state.processedL1 != 0L || state.status == "succeeded" || state.status == "skipped"
        } == true
    }

    suspend fun markSourceProcessedL1(id: Long) = withContext(Dispatchers.Default) { db.memorySourceQueueQueries.markMemorySourceProcessedL1(id) }
    suspend fun markSourceProcessedVector(id: Long) = withContext(Dispatchers.Default) { db.memorySourceQueueQueries.markMemorySourceProcessedVector(id) }
    suspend fun deleteMemorySource(id: Long) = withContext(Dispatchers.Default) { db.memorySourceQueueQueries.deleteMemorySource(id) }
    suspend fun completeSource(id: Long, token: String) = withContext(Dispatchers.Default) { db.memorySourceQueueQueries.completeMemorySource(id, token) }
    suspend fun retrySource(id: Long, token: String, nextRetryAt: Long, error: String) = withContext(Dispatchers.Default) { db.memorySourceQueueQueries.retryMemorySource(nextRetryAt, error, id, token) }
    suspend fun skipSource(id: Long, token: String, error: String) = withContext(Dispatchers.Default) { db.memorySourceQueueQueries.skipMemorySource(error, id, token) }
    suspend fun renewSourceLease(id: Long, token: String, now: Long, leaseMs: Long = 10 * 60_000L) = withContext(Dispatchers.Default) {
        db.memorySourceQueueQueries.renewMemorySourceLease(now + leaseMs, id, token)
    }

    suspend fun updateMemoryItemVectorId(id: Long, vectorId: String, updatedAt: Long) = withContext(Dispatchers.Default) {
        db.memoryItemsQueries.updateMemoryItemVectorId(vectorId, updatedAt, id)
    }

    suspend fun getActiveMemoryItemsMissingVector(now: Long, limit: Int): List<MemoryItem> = withContext(Dispatchers.Default) {
        db.memoryItemsQueries.getActiveMemoryItemsMissingVector(now, limit.toLong()) { id, ownerType_, ownerId_, memoryLevel, memoryType, sourceKind, sourceRefId, sessionId, content, nickname, importance, privacy, unmetNeed, location, emotionValence, eventTime, createdAt, updatedAt, expiresAt, status, scheduledTime, action, careType, topicKey, sourceActor, sourceTarget, lastUsedAt, usedCount, confidence, rawJson, vectorId ->
            MemoryItem(id, ownerType_, ownerId_, try { MemoryLevel.valueOf(memoryLevel) } catch (_: Exception) { MemoryLevel.L1 }, memoryType, try { MemorySourceKind.valueOf(sourceKind) } catch (_: Exception) { MemorySourceKind.PRIVATE_CHAT }, sourceRefId, sessionId, content, nickname, importance.toInt(), privacy, unmetNeed != 0L, location, emotionValence, eventTime, createdAt, updatedAt, expiresAt, status, scheduledTime, action, careType, topicKey, sourceActor, sourceTarget, lastUsedAt, usedCount.toInt(), confidence, rawJson, vectorId)
        }.executeAsList()
    }

    suspend fun updateMemoryItemContent(id: Long, content: String, updatedAt: Long) = withContext(Dispatchers.Default) {
        db.memoryItemsQueries.updateMemoryItemContent(content, updatedAt, id)
    }

    suspend fun updateActiveMemoryExpiry(expiresAt: Long, updatedAt: Long) = withContext(Dispatchers.Default) {
        db.transaction {
            db.memoryItemsQueries.updateActiveMemoryItemExpiry(expiresAt, updatedAt)
            db.vectorMemoriesQueries.updateMemoryItemVectorExpiry(expiresAt)
        }
    }

    suspend fun clearAllMemoryItemVectorIds() = withContext(Dispatchers.Default) {
        db.memoryItemsQueries.clearAllMemoryItemVectorIds()
    }

    suspend fun clearMemoryItemVectorIdsByOwner(ownerType: String, ownerId: String) = withContext(Dispatchers.Default) {
        db.memoryItemsQueries.clearMemoryItemVectorIdsByOwner(ownerType, ownerId)
    }

    suspend fun archiveMemoryItemsByLevel(ownerType: String, ownerId: String, level: MemoryLevel, updatedAt: Long) = withContext(Dispatchers.Default) {
        db.memoryItemsQueries.archiveMemoryItemsByLevel(updatedAt, ownerType, ownerId, level.name)
    }

    suspend fun archiveMemoryItem(id: Long, updatedAt: Long) = withContext(Dispatchers.Default) {
        db.memoryItemsQueries.archiveMemoryItem(updatedAt, id)
    }

    suspend fun deleteMemoryItemsBySession(sessionId: String) = withContext(Dispatchers.Default) {
        deleteBySession(sessionId)
    }

    /** Deletes the structured source records and all direct vector copies of that source. */
    suspend fun deleteBySource(sourceKind: MemorySourceKind, sourceRefId: String) = withContext(Dispatchers.Default) {
        db.transaction {
            val invalidatedVectorIds = invalidateDerivedBySourceInternal(sourceKind, sourceRefId)
            invalidatedVectorIds.forEach { vectorId -> db.vectorMemoriesQueries.deleteVectorMemory(vectorId) }
            // Structured items retain the exact vector IDs, avoiding collisions between
            // independently numbered moments and comments.
            db.vectorMemoriesQueries.deleteVectorsForMemorySource(sourceKind.name, sourceRefId)
            db.memoryItemsQueries.deleteMemoryItemsBySource(sourceKind.name, sourceRefId)
            db.memorySourceQueueQueries.deleteMemorySourcesBySource(sourceKind.name, sourceRefId)
            db.vectorMemoriesQueries.deleteVectorMemoriesBySourceTypeAndId(sourceKind.name.lowercase(), sourceRefId)
            db.memoryLinksQueries.deleteOrphanedMemoryLinks()
        }
    }

    suspend fun deleteGroupChatMemoryByGroupId(groupId: String) = withContext(Dispatchers.Default) {
        val prefix = "$groupId:%"
        // Delete each window through the normal source path so promoted L2/L3 descendants of
        // copies given to group members are invalidated before their L1 evidence disappears.
        val sourceRefIds = db.memoryItemsQueries
            .getSourceRefIdsBySourcePrefix(MemorySourceKind.GROUP_CHAT.name, prefix)
            .executeAsList()
        sourceRefIds.forEach { sourceRefId -> deleteBySource(MemorySourceKind.GROUP_CHAT, sourceRefId) }
        db.transaction {
            db.memoryItemsQueries.deleteVectorsBySourcePrefix(MemorySourceKind.GROUP_CHAT.name, prefix)
            db.memoryItemsQueries.deleteMemoryItemsBySourcePrefix(MemorySourceKind.GROUP_CHAT.name, prefix)
            db.memorySourceQueueQueries.deleteMemorySourcesBySourcePrefix(MemorySourceKind.GROUP_CHAT.name, prefix)
            db.memoryLinksQueries.deleteOrphanedMemoryLinks()
        }
    }

    /**
     * A deleted source invalidates every promoted conclusion that used it as evidence.  Those
     * conclusions are archived rather than silently kept in recall; remaining evidence can form
     * a new conclusion later.
     */
    private fun invalidateDerivedBySourceInternal(sourceKind: MemorySourceKind, sourceRefId: String): List<String> {
        val pending = ArrayDeque<Long>()
        db.memoryItemsQueries.getMemoryIdsBySource(sourceKind.name, sourceRefId).executeAsList().forEach(pending::addLast)
        return invalidateDescendantsInternal(pending)
    }

    private fun invalidateDescendantsInternal(pending: ArrayDeque<Long>): List<String> {
        val visited = mutableSetOf<Long>()
        val vectorIds = mutableListOf<String>()
        val now = System.currentTimeMillis()
        while (pending.isNotEmpty()) {
            val parentId = pending.removeFirst()
            if (!visited.add(parentId)) continue
            db.memoryLinksQueries.getChildMemoryIds(parentId).executeAsList().forEach { childId ->
                if (childId !in visited) {
                    db.memoryItemsQueries.getMemoryVectorId(childId).executeAsOneOrNull()
                        ?.takeIf { it.isNotBlank() }?.let(vectorIds::add)
                    db.memoryItemsQueries.archiveMemoryItem(now, childId)
                    pending.addLast(childId)
                }
            }
        }
        return vectorIds
    }

    suspend fun invalidateDerivedBySession(sessionId: String) = withContext(Dispatchers.Default) {
        db.transaction {
            val pending = ArrayDeque<Long>()
            db.memoryItemsQueries.getMemoryIdsBySession(sessionId).executeAsList().forEach(pending::addLast)
            invalidateDescendantsInternal(pending).forEach { vectorId -> db.vectorMemoriesQueries.deleteVectorMemory(vectorId) }
        }
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

    suspend fun deleteBySession(sessionId: String) = withContext(Dispatchers.Default) {
        invalidateDerivedBySession(sessionId)
        db.transaction {
            db.vectorMemoriesQueries.deleteVectorsForMemorySession(sessionId)
            db.memoryItemsQueries.deleteMemoryItemsBySession(sessionId)
            db.memorySourceQueueQueries.deleteMemorySourcesBySession(sessionId)
            db.memoryLinksQueries.deleteOrphanedMemoryLinks()
        }
    }

    suspend fun deleteMemoryItem(id: Long) = withContext(Dispatchers.Default) {
        db.transaction {
            db.memoryItemsQueries.getMemoryVectorId(id).executeAsOneOrNull()
                ?.takeIf { it.isNotBlank() }?.let { vectorId -> db.vectorMemoriesQueries.deleteVectorMemory(vectorId) }
            val pending = ArrayDeque<Long>()
            pending.addLast(id)
            invalidateDescendantsInternal(pending).forEach { vectorId -> db.vectorMemoriesQueries.deleteVectorMemory(vectorId) }
        }
        db.memoryLinksQueries.deleteMemoryLinksByParent(id)
        db.memoryLinksQueries.deleteMemoryLinksByChild(id)
        db.memoryItemsQueries.deleteMemoryItem(id)
    }

    suspend fun deleteByOwnerAndSourceKind(ownerType: String, ownerId: String, sourceKind: MemorySourceKind) = withContext(Dispatchers.Default) {
        val vectorIds = db.memoryItemsQueries.getMemoryVectorIdsByOwnerAndSourceKind(ownerType, ownerId, sourceKind.name)
            .executeAsList().filter { it.isNotBlank() }
        vectorIds.forEach { vectorId -> db.vectorMemoriesQueries.deleteVectorMemory(vectorId) }
        if (sourceKind == MemorySourceKind.PRIVATE_CHAT) {
            db.memoryLinksQueries.deleteMemoryLinksForOwnerPrivateSource(ownerType, ownerId, ownerType, ownerId)
            db.memoryBatchesQueries.deletePrivateMemoryBatchesByOwner(ownerType, ownerId)
        }
        db.memoryItemsQueries.deleteMemoryItemsByOwnerAndSourceKind(ownerType, ownerId, sourceKind.name)
        db.memorySourceQueueQueries.deleteMemorySourcesByOwnerAndKind(ownerType, ownerId, sourceKind.name)
    }

}
