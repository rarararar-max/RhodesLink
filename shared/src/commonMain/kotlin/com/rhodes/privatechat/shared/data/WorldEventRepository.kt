package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.WorldEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class WorldEventRepository(private val wrapper: DatabaseWrapper) {
    private val db: RhodesDatabase get() = wrapper.database

    suspend fun insertWorldEvent(event: WorldEvent): Long = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        db.worldEventsQueries.insertWorldEvent(
            event.type,
            event.actorId,
            event.actorName,
            event.targetId,
            event.targetName,
            event.source,
            event.sourceId,
            event.content,
            if (event.createdAt > 0L) event.createdAt else now,
            event.expiresAt,
            event.consumedBy,
            event.originType,
            event.chainDepth.toLong(),
            event.rootEventId
        )
        db.worldEventsQueries.getLastInsertRowId().executeAsOne()
    }

    suspend fun getRecentWorldEvents(limit: Int = 20): List<WorldEvent> = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        db.worldEventsQueries.getRecentWorldEvents(now, limit.toLong()).executeAsList().map { row ->
            mapEvent(row.id, row.type, row.actorId, row.actorName, row.targetId, row.targetName, row.source, row.sourceId, row.content, row.createdAt, row.expiresAt, row.consumedBy, row.originType, row.chainDepth, row.rootEventId)
        }
    }

    suspend fun getWorldEventsByType(type: String, limit: Int = 20): List<WorldEvent> = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        db.worldEventsQueries.getWorldEventsByType(type, now, limit.toLong()).executeAsList().map { row ->
            mapEvent(row.id, row.type, row.actorId, row.actorName, row.targetId, row.targetName, row.source, row.sourceId, row.content, row.createdAt, row.expiresAt, row.consumedBy, row.originType, row.chainDepth, row.rootEventId)
        }
    }

    suspend fun getWorldEventsForOperator(operatorId: String, operatorName: String, limit: Int = 20): List<WorldEvent> = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        db.worldEventsQueries.getWorldEventsForOperator(now, operatorId, operatorId, "%$operatorName%", limit.toLong()).executeAsList().map { row ->
            mapEvent(row.id, row.type, row.actorId, row.actorName, row.targetId, row.targetName, row.source, row.sourceId, row.content, row.createdAt, row.expiresAt, row.consumedBy, row.originType, row.chainDepth, row.rootEventId)
        }
    }

    suspend fun getUnconsumedWorldEventsForOperator(operatorId: String, operatorName: String, consumer: String, limit: Int = 10): List<WorldEvent> = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        db.worldEventsQueries.getUnconsumedWorldEventsForOperator(now, consumerPattern(consumer), operatorId, operatorId, "%$operatorName%", limit.toLong()).executeAsList().map { row ->
            mapEvent(row.id, row.type, row.actorId, row.actorName, row.targetId, row.targetName, row.source, row.sourceId, row.content, row.createdAt, row.expiresAt, row.consumedBy, row.originType, row.chainDepth, row.rootEventId)
        }
    }

    suspend fun getUnconsumedWorldEventsForGroup(groupId: String, memberIds: List<String>, memberNames: List<String>, limit: Int = 10): List<WorldEvent> = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        val consumer = "group:$groupId"
        val keywords = (memberIds + memberNames + groupId).filter { it.isNotBlank() }
        val seen = mutableSetOf<Long>()
        keywords.take(24).flatMap { key ->
            val keyword = "%$key%"
            db.worldEventsQueries.getUnconsumedWorldEventsForGroup(now, consumerPattern(consumer), keyword, keyword, keyword, keyword, keyword, limit.toLong()).executeAsList().map { row ->
                mapEvent(row.id, row.type, row.actorId, row.actorName, row.targetId, row.targetName, row.source, row.sourceId, row.content, row.createdAt, row.expiresAt, row.consumedBy, row.originType, row.chainDepth, row.rootEventId)
            }
        }.filter { seen.add(it.id) }.sortedByDescending { it.createdAt }.take(limit)
    }

    suspend fun getUnconsumedWorldEventsByType(type: String, consumer: String, limit: Int = 10): List<WorldEvent> = withContext(Dispatchers.Default) {
        val now = Clock.System.now().toEpochMilliseconds()
        db.worldEventsQueries.getUnconsumedWorldEventsByType(type, now, consumerPattern(consumer), limit.toLong()).executeAsList().map { row ->
            mapEvent(row.id, row.type, row.actorId, row.actorName, row.targetId, row.targetName, row.source, row.sourceId, row.content, row.createdAt, row.expiresAt, row.consumedBy, row.originType, row.chainDepth, row.rootEventId)
        }
    }

    suspend fun countWorldEventsByTypeSince(type: String, since: Long): Int = withContext(Dispatchers.Default) {
        db.worldEventsQueries.countWorldEventsByTypeSince(type, since).executeAsOne().toInt()
    }

    suspend fun countChainedWorldEventsByTypeSince(type: String, since: Long): Int = withContext(Dispatchers.Default) {
        db.worldEventsQueries.countChainedWorldEventsByTypeSince(type, since).executeAsOne().toInt()
    }

    suspend fun markWorldEventConsumed(eventId: Long, consumer: String) = withContext(Dispatchers.Default) {
        val event = db.worldEventsQueries.getWorldEvent(eventId).executeAsOneOrNull() ?: return@withContext
        val parts = event.consumedBy.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
        parts.add(consumer)
        db.worldEventsQueries.markWorldEventConsumed(parts.joinToString(",", prefix = ",", postfix = ","), eventId)
    }

    private fun consumerPattern(consumer: String): String = "%,${consumer},%"

    suspend fun getWorldEventCount(): Int = withContext(Dispatchers.Default) {
        db.worldEventsQueries.getWorldEventCount().executeAsOne().toInt()
    }

    suspend fun deleteExpiredWorldEvents(cutoff: Long) = withContext(Dispatchers.Default) {
        db.worldEventsQueries.deleteExpiredWorldEvents(cutoff)
    }

    suspend fun deleteAllWorldEvents() = withContext(Dispatchers.Default) {
        db.worldEventsQueries.deleteAllWorldEvents()
    }

    private fun mapEvent(
        id: Long,
        type: String,
        actorId: String,
        actorName: String,
        targetId: String,
        targetName: String,
        source: String,
        sourceId: String,
        content: String,
        createdAt: Long,
        expiresAt: Long,
        consumedBy: String,
        originType: String,
        chainDepth: Long,
        rootEventId: Long
    ): WorldEvent = WorldEvent(id, type, actorId, actorName, targetId, targetName, source, sourceId, content, createdAt, expiresAt, consumedBy, originType, chainDepth.toInt(), rootEventId)
}
