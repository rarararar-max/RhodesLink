package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class RelationshipRepository(private val wrapper: DatabaseWrapper) {

    private val db: RhodesDatabase get() = wrapper.database

    // --- Relationships ---
    suspend fun migrateOldRelationships() = withContext(Dispatchers.Default) {
        db.relationshipsQueries.deleteByType("FAMILY")
        db.relationshipsQueries.deletePresets()
        insertPresetRelationships()
    }

    suspend fun insertPresetRelationships() = withContext(Dispatchers.Default) {
        if (db.relationshipsQueries.getPresetCount().executeAsOne() > 0L) return@withContext
        val relationships = listOf(
            Relationship(operatorId = "kaltsit", relatedOperatorId = "amiya", relatedOperatorName = "阿米娅", type = RelationshipType.MOTHER, intimacy = 85, isPreset = true, note = "凯尔希是阿米娅的监护人"),
            Relationship(operatorId = "amiya", relatedOperatorId = "kaltsit", relatedOperatorName = "凯尔希", type = RelationshipType.DAUGHTER, intimacy = 85, isPreset = true, note = "阿米娅由凯尔希带大"),
            Relationship(operatorId = "saria", relatedOperatorId = "ifrit", relatedOperatorName = "伊芙利特", type = RelationshipType.BIG_SISTER, intimacy = 75, isPreset = true, note = "塞雷娅照顾伊芙利特"),
            Relationship(operatorId = "ifrit", relatedOperatorId = "saria", relatedOperatorName = "塞雷娅", type = RelationshipType.LITTLE_SISTER, intimacy = 70, isPreset = true, note = "伊芙利特依赖塞雷娅"),
            Relationship(operatorId = "exusiai", relatedOperatorId = "texas", relatedOperatorName = "德克萨斯", type = RelationshipType.TEAMMATE, intimacy = 80, isPreset = true, note = "企鹅物流搭档"),
            Relationship(operatorId = "texas", relatedOperatorId = "exusiai", relatedOperatorName = "能天使", type = RelationshipType.TEAMMATE, intimacy = 80, isPreset = true, note = "企鹅物流搭档"),
            Relationship(operatorId = "shining", relatedOperatorId = "nightingale", relatedOperatorName = "夜莺", type = RelationshipType.CLOSE_FRIEND, intimacy = 90, isPreset = true, note = "闪灵保护夜莺"),
            Relationship(operatorId = "nightingale", relatedOperatorId = "shining", relatedOperatorName = "闪灵", type = RelationshipType.CLOSE_FRIEND, intimacy = 90, isPreset = true, note = "夜莺需要闪灵照顾")
        )
        relationships.forEach { rel ->
            db.relationshipsQueries.insertRelationship(rel.operatorId, rel.relatedOperatorId, rel.relatedOperatorName, rel.type.name, rel.intimacy.toLong(), if (rel.isPreset) 1L else 0L, rel.note)
        }
    }

    private fun mapRelationship(id: Long, operatorId: String, relatedOperatorId: String, relatedOperatorName: String, type: String, intimacy: Long, isPreset: Long, note: String) =
        Relationship(id, operatorId, relatedOperatorId, relatedOperatorName, try { RelationshipType.valueOf(type) } catch (_: Exception) { RelationshipType.STRANGER }, intimacy.toInt(), isPreset != 0L, note)

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
        db.relationshipsQueries.insertRelationship(rel.operatorId, rel.relatedOperatorId, rel.relatedOperatorName, rel.type.name, rel.intimacy.toLong(), if (rel.isPreset) 1L else 0L, rel.note)
    }

    suspend fun deleteRelationshipByOperator(operatorId: String) = withContext(Dispatchers.Default) {
        db.relationshipsQueries.deleteByOperator(operatorId)
    }

    suspend fun bfsRelationGraph(centerId: String): List<BfsNode> = withContext(Dispatchers.Default) {
        val visited = mutableSetOf(centerId)
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.addLast(centerId to 0)
        val result = mutableListOf(BfsNode(centerId, "", 0, ""))
        db.operatorsQueries.getOperator(centerId) { id, name, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ ->
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
                val name = db.operatorsQueries.getOperator(rel.operatorId) { _, name, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> name }.executeAsOneOrNull() ?: rel.operatorId
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
            if (rel.type == RelationshipType.STRANGER) continue
            val now = Clock.System.now().toEpochMilliseconds()
            val anchors = db.memoryAnchorsQueries.getPublicAnchors(rel.relatedOperatorId, now) { id, sid, opId, type, content, isPrivate, createdAt, expiresAt ->
                MemoryAnchor(id, sid, opId, try { AnchorType.valueOf(type) } catch (_: Exception) { AnchorType.EVENT }, content, isPrivate != 0L, createdAt, expiresAt)
            }.executeAsList()
            for (a in anchors) { allAnchors.add(rel.relatedOperatorName to a) }
        }
        allAnchors.sortedByDescending { it.second.createdAt }.take(10).joinToString("\n") { "${it.first}：${it.second.content}" }
    }
}
