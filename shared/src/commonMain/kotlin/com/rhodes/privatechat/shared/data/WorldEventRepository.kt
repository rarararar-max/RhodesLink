package com.rhodes.privatechat.shared.data

import android.util.Log
import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.WorldEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class WorldEventRepository(private val wrapper: DatabaseWrapper) {
    private val db: RhodesDatabase get() = wrapper.database

    suspend fun insertWorldEvent(event: WorldEvent): Long = withContext(Dispatchers.Default) {
        try {
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
        } catch (e: Exception) {
            Log.e("WorldEventRepository", "插入世界事件失败", e)
            -1L
        }
    }

    suspend fun getRecentWorldEvents(limit: Int = 20): List<WorldEvent> = withContext(Dispatchers.Default) {
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            db.worldEventsQueries.getRecentWorldEvents(now, limit.toLong()).executeAsList().map { row ->
                mapEvent(row.id, row.type, row.actorId, row.actorName, row.targetId, row.targetName, row.source, row.sourceId, row.content, row.createdAt, row.expiresAt, row.consumedBy, row.originType, row.chainDepth, row.rootEventId)
            }
        } catch (e: Exception) {
            Log.e("WorldEventRepository", "查询最近世界事件失败", e)
            emptyList()
        }
    }

    suspend fun getWorldEventsByType(type: String, limit: Int = 20): List<WorldEvent> = withContext(Dispatchers.Default) {
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            db.worldEventsQueries.getWorldEventsByType(type, now, limit.toLong()).executeAsList().map { row ->
                mapEvent(row.id, row.type, row.actorId, row.actorName, row.targetId, row.targetName, row.source, row.sourceId, row.content, row.createdAt, row.expiresAt, row.consumedBy, row.originType, row.chainDepth, row.rootEventId)
            }
        } catch (e: Exception) {
            Log.e("WorldEventRepository", "按类型查询世界事件失败", e)
            emptyList()
        }
    }

    suspend fun getWorldEventsForOperator(operatorId: String, operatorName: String, limit: Int = 20): List<WorldEvent> = withContext(Dispatchers.Default) {
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            db.worldEventsQueries.getWorldEventsForOperator(now, operatorId, operatorId, "%$operatorName%", limit.toLong()).executeAsList().map { row ->
                mapEvent(row.id, row.type, row.actorId, row.actorName, row.targetId, row.targetName, row.source, row.sourceId, row.content, row.createdAt, row.expiresAt, row.consumedBy, row.originType, row.chainDepth, row.rootEventId)
            }
        } catch (e: Exception) {
            Log.e("WorldEventRepository", "查询操作员世界事件失败", e)
            emptyList()
        }
    }

    suspend fun getUnconsumedWorldEventsForOperator(operatorId: String, operatorName: String, consumer: String, limit: Int = 10): List<WorldEvent> = withContext(Dispatchers.Default) {
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            db.worldEventsQueries.getUnconsumedWorldEventsForOperator(now, consumerPattern(consumer), operatorId, operatorId, "%$operatorName%", limit.toLong()).executeAsList().map { row ->
                mapEvent(row.id, row.type, row.actorId, row.actorName, row.targetId, row.targetName, row.source, row.sourceId, row.content, row.createdAt, row.expiresAt, row.consumedBy, row.originType, row.chainDepth, row.rootEventId)
            }
        } catch (e: Exception) {
            Log.e("WorldEventRepository", "查询未消费操作员事件失败", e)
            emptyList()
        }
    }

    suspend fun getUnconsumedWorldEventsForGroup(groupId: String, memberIds: List<String>, memberNames: List<String>, limit: Int = 10): List<WorldEvent> = withContext(Dispatchers.Default) {
        try {
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
        } catch (e: Exception) {
            Log.e("WorldEventRepository", "查询未消费群组事件失败", e)
            emptyList()
        }
    }

    suspend fun getUnconsumedWorldEventsByType(type: String, consumer: String, limit: Int = 10): List<WorldEvent> = withContext(Dispatchers.Default) {
        try {
            val now = Clock.System.now().toEpochMilliseconds()
            db.worldEventsQueries.getUnconsumedWorldEventsByType(type, now, consumerPattern(consumer), limit.toLong()).executeAsList().map { row ->
                mapEvent(row.id, row.type, row.actorId, row.actorName, row.targetId, row.targetName, row.source, row.sourceId, row.content, row.createdAt, row.expiresAt, row.consumedBy, row.originType, row.chainDepth, row.rootEventId)
            }
        } catch (e: Exception) {
            Log.e("WorldEventRepository", "按类型查询未消费事件失败", e)
            emptyList()
        }
    }

    suspend fun countWorldEventsByTypeSince(type: String, since: Long): Int = withContext(Dispatchers.Default) {
        try {
            db.worldEventsQueries.countWorldEventsByTypeSince(type, since).executeAsOne().toInt()
        } catch (e: Exception) {
            Log.e("WorldEventRepository", "统计世界事件失败", e)
            0
        }
    }

    suspend fun countChainedWorldEventsByTypeSince(type: String, since: Long): Int = withContext(Dispatchers.Default) {
        try {
            db.worldEventsQueries.countChainedWorldEventsByTypeSince(type, since).executeAsOne().toInt()
        } catch (e: Exception) {
            Log.e("WorldEventRepository", "统计链式事件失败", e)
            0
        }
    }

    suspend fun markWorldEventConsumed(eventId: Long, consumer: String) = withContext(Dispatchers.Default) {
        try {
            val event = db.worldEventsQueries.getWorldEvent(eventId).executeAsOneOrNull() ?: return@withContext
            val parts = event.consumedBy.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
            parts.add(consumer)
            db.worldEventsQueries.markWorldEventConsumed(parts.joinToString(",", prefix = ",", postfix = ","), eventId)
        } catch (e: Exception) {
            Log.e("WorldEventRepository", "标记事件已消费失败", e)
        }
    }

    private fun consumerPattern(consumer: String): String = "%,${consumer},%"

    suspend fun getWorldEventCount(): Int = withContext(Dispatchers.Default) {
        try {
            db.worldEventsQueries.getWorldEventCount().executeAsOne().toInt()
        } catch (e: Exception) {
            Log.e("WorldEventRepository", "获取世界事件数量失败", e)
            0
        }
    }

    suspend fun deleteExpiredWorldEvents(cutoff: Long) = withContext(Dispatchers.Default) {
        try {
            db.worldEventsQueries.deleteExpiredWorldEvents(cutoff)
        } catch (e: Exception) {
            Log.e("WorldEventRepository", "删除过期事件失败", e)
        }
    }

    suspend fun deleteAllWorldEvents() = withContext(Dispatchers.Default) {
        try {
            db.worldEventsQueries.deleteAllWorldEvents()
        } catch (e: Exception) {
            Log.e("WorldEventRepository", "删除所有事件失败", e)
        }
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
