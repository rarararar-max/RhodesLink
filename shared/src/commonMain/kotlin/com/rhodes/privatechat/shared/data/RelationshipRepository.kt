package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.memory.AnchorSourcePolicy
import com.rhodes.privatechat.shared.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class RelationshipRepository(private val wrapper: DatabaseWrapper) {

    private val db: RhodesDatabase get() = wrapper.database

    // --- Relationships ---
    suspend fun migrateOldRelationships() = withContext(Dispatchers.Default) {
        db.relationshipsQueries.deletePresets()
        insertPresetRelationships()
    }

    suspend fun insertPresetRelationships() = withContext(Dispatchers.Default) {
        // 关系系统改为玩家自建。保留空实现以兼容旧调用链，旧版预设由 migrateOldRelationships() 清理。
    }

    private fun mapRelationship(id: Long, operatorId: String, relatedOperatorId: String, relatedOperatorName: String, type: String, intimacy: Long, isPreset: Long, note: String) =
        Relationship(id, operatorId, relatedOperatorId, relatedOperatorName, try { RelationshipType.valueOf(type) } catch (_: Exception) { RelationshipType.values().first() }, intimacy.toInt(), isPreset != 0L, note)

    suspend fun getRelationships(operatorId: String): List<Relationship> = withContext(Dispatchers.Default) {
        db.relationshipsQueries.getRelationshipsSync(operatorId) { id, opId, relOpId, relOpName, type, intimacy, isPreset, note ->
            mapRelationship(id, opId, relOpId, relOpName, type, intimacy, isPreset, note)
        }.executeAsList()
    }

    suspend fun getReverseRelationships(opId: String): List<Relationship> = withContext(Dispatchers.Default) {
        db.relationshipsQueries.getReverseRelationshipsSync(opId) { id, opId, relOpId, relOpName, type, intimacy, isPreset, note ->
            mapRelationship(id, opId, relOpId, relOpName, type, intimacy, isPreset, note)
        }.executeAsList()
    }

    suspend fun getRelationship(operatorId: String, relatedOperatorId: String): Relationship? = withContext(Dispatchers.Default) {
        db.relationshipsQueries.getRelationship(operatorId, relatedOperatorId) { id, opId, relOpId, relOpName, type, intimacy, isPreset, note ->
            mapRelationship(id, opId, relOpId, relOpName, type, intimacy, isPreset, note)
        }.executeAsOneOrNull()
    }

    suspend fun insertRelationship(rel: Relationship) = withContext(Dispatchers.Default) {
        val existing = db.relationshipsQueries.getRelationship(rel.operatorId, rel.relatedOperatorId) { id, opId, relOpId, relOpName, type, intimacy, isPreset, note ->
            mapRelationship(id, opId, relOpId, relOpName, type, intimacy, isPreset, note)
        }.executeAsOneOrNull()
        if (existing != null) {
            db.relationshipsQueries.updateRelationship(rel.type.name, rel.intimacy.toLong(), rel.note, rel.operatorId, rel.relatedOperatorId)
        } else {
            db.relationshipsQueries.insertRelationship(rel.operatorId, rel.relatedOperatorId, rel.relatedOperatorName, rel.type.name, rel.intimacy.toLong(), if (rel.isPreset) 1L else 0L, rel.note)
        }
    }

    suspend fun deleteRelationshipByOperator(operatorId: String) = withContext(Dispatchers.Default) {
        db.relationshipsQueries.deleteByOperator(operatorId)
    }

    suspend fun bfsRelationGraph(centerId: String): List<BfsNode> = withContext(Dispatchers.Default) {
        val visited = mutableSetOf(centerId)
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.addLast(centerId to 0)
        val result = mutableListOf(BfsNode(centerId, "", 0, ""))
            db.operatorsQueries.getOperator(centerId) { id, name, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ ->
            name
        }.executeAsOneOrNull()?.let { result[0] = result[0].copy(operatorName = it) }
        while (queue.isNotEmpty() && result.size < 15) {
            val (currentId, depth) = queue.removeFirst()
            if (depth >= 4) continue
            for (rel in getRelationships(currentId)) {
                if (rel.relatedOperatorId in visited) continue
                visited.add(rel.relatedOperatorId)
                result.add(BfsNode(rel.relatedOperatorId, rel.relatedOperatorName, depth + 1, currentId, rel.type, false))
                queue.addLast(rel.relatedOperatorId to depth + 1)
                if (result.size >= 15) break
            }
            if (result.size >= 15) break
            for (rel in getReverseRelationships(currentId)) {
                if (rel.operatorId in visited) continue
                visited.add(rel.operatorId)
                val name = db.operatorsQueries.getOperator(rel.operatorId) { _, name, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> name }.executeAsOneOrNull() ?: rel.operatorId
                result.add(BfsNode(rel.operatorId, name, depth + 1, currentId, rel.type, true))
                queue.addLast(rel.operatorId to depth + 1)
                if (result.size >= 15) break
            }
        }
        result
    }

    suspend fun getSharedMemoriesForOperator(operatorId: String): String = withContext(Dispatchers.Default) {
        val relationships = getRelationships(operatorId)
        val allAnchors = mutableListOf<Pair<String, MemoryAnchor>>()
        for (rel in relationships) {
            val allowedTypes = sharedAnchorTypes(rel)
            if (allowedTypes.isEmpty()) continue
            val limit = sharedAnchorLimit(rel)
            if (limit <= 0) continue
            val now = Clock.System.now().toEpochMilliseconds()
            val anchors = db.memoryAnchorsQueries.getPublicAnchors(rel.relatedOperatorId, now)
                .executeAsList()
                .map { row ->
                    AnchorSourcePolicy.inferLegacy(MemoryAnchor(
                        id = row.id,
                        sessionId = row.sessionId,
                        operatorId = row.operatorId,
                        type = try { AnchorType.valueOf(row.type) } catch (_: Exception) { AnchorType.EVENT },
                        content = row.content,
                        isPrivate = row.isPrivate != 0L,
                        createdAt = row.createdAt,
                        expiresAt = row.expiresAt,
                        source = row.source,
                        sourceName = row.sourceName,
                        sourceActor = row.sourceActor,
                        sourceTarget = row.sourceTarget,
                        importance = row.importance,
                        knownFrom = row.knownFrom
                    ))
                }
                .filter { it.type in allowedTypes }
                .take(limit)
            for (a in anchors) { allAnchors.add(rel.relatedOperatorName to a) }
        }
        allAnchors.sortedByDescending { it.second.createdAt }.take(10).joinToString("\n") { "${it.first}：${it.second.content}" }
    }

    private fun sharedAnchorLimit(rel: Relationship): Int {
        val base = when (rel.intimacy) {
            in 0..19 -> 0
            in 20..39 -> 1
            in 40..59 -> 2
            in 60..79 -> 3
            else -> 5
        }
        val bonus = when (rel.type) {
            RelationshipType.LOVER, RelationshipType.FAMILY, RelationshipType.CLOSE_FRIEND -> 1
            RelationshipType.RIVAL, RelationshipType.LOVE_RIVAL -> -1
            else -> 0
        }
        return (base + bonus).coerceIn(0, 5)
    }

    private fun sharedAnchorTypes(rel: Relationship): Set<AnchorType> {
        if (rel.intimacy < 20) return emptySet()
        val base = when (rel.intimacy) {
            in 20..39 -> setOf(AnchorType.EVENT, AnchorType.RELATION)
            in 40..59 -> setOf(AnchorType.EVENT, AnchorType.PLAN, AnchorType.RELATION)
            in 60..79 -> setOf(AnchorType.EVENT, AnchorType.PLAN, AnchorType.PREFERENCE, AnchorType.RELATION)
            else -> setOf(AnchorType.EVENT, AnchorType.PLAN, AnchorType.PREFERENCE, AnchorType.RELATION, AnchorType.EMOTION)
        }
        return when (rel.type) {
            RelationshipType.RIVAL -> base intersect setOf(AnchorType.EVENT, AnchorType.RELATION)
            RelationshipType.LOVE_RIVAL -> base intersect setOf(AnchorType.EVENT, AnchorType.RELATION, AnchorType.EMOTION)
            RelationshipType.BOSS, RelationshipType.SUBORDINATE, RelationshipType.CAPTAIN, RelationshipType.MEMBER -> base intersect setOf(AnchorType.EVENT, AnchorType.PLAN, AnchorType.RELATION)
            RelationshipType.GUARDIAN, RelationshipType.MENTOR, RelationshipType.STUDENT -> base + AnchorType.PLAN
            else -> base
        }
    }
}
