package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.KnowledgeBase
import com.rhodes.privatechat.shared.model.KnowledgeBaseChunk
import com.rhodes.privatechat.shared.model.OperatorKnowledgeBaseAssignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.random.Random

class KnowledgeBaseRepository(private val wrapper: DatabaseWrapper) {
    private val db: RhodesDatabase get() = wrapper.database

    suspend fun getAll(): List<KnowledgeBase> = withContext(Dispatchers.Default) {
        db.knowledgeBasesQueries.getAllKnowledgeBases(::mapKnowledgeBase).executeAsList()
    }

    suspend fun get(id: String): KnowledgeBase? = withContext(Dispatchers.Default) {
        db.knowledgeBasesQueries.getKnowledgeBase(id, ::mapKnowledgeBase).executeAsOneOrNull()
    }

    suspend fun save(knowledgeBase: KnowledgeBase, chunks: List<KnowledgeBaseChunk>) = withContext(Dispatchers.Default) {
        require(knowledgeBase.id.isNotBlank()) { "知识库 ID 不能为空" }
        require(knowledgeBase.name.trim().isNotBlank()) { "知识库名称不能为空" }
        require(chunks.all { it.knowledgeBaseId == knowledgeBase.id && it.content.isNotBlank() }) { "知识库分段无效" }
        require(chunks.map { it.ordinal }.distinct().size == chunks.size) { "知识库分段序号重复" }
        db.transaction {
            db.knowledgeBasesQueries.insertKnowledgeBase(
                knowledgeBase.id, knowledgeBase.name.trim(), knowledgeBase.rawContent, knowledgeBase.sourceFileName,
                knowledgeBase.sourceFormat, knowledgeBase.sourceType, knowledgeBase.chunkingMode, knowledgeBase.indexStatus,
                knowledgeBase.indexedEmbeddingSignature, knowledgeBase.createdAt, knowledgeBase.updatedAt,
            )
            db.knowledgeBasesQueries.deleteChunksByKnowledgeBase(knowledgeBase.id)
            chunks.sortedBy { it.ordinal }.forEach { chunk ->
                db.knowledgeBasesQueries.insertKnowledgeBaseChunk(
                    chunk.id, chunk.knowledgeBaseId, chunk.ordinal.toLong(), chunk.sourceHeading, chunk.content,
                    chunk.userKeywords, if (chunk.enabled) 1L else 0L, chunk.indexedAt, chunk.indexError,
                    chunk.createdAt, chunk.updatedAt,
                )
            }
        }
    }

    suspend fun getChunks(knowledgeBaseId: String): List<KnowledgeBaseChunk> = withContext(Dispatchers.Default) {
        db.knowledgeBasesQueries.getChunksByKnowledgeBase(knowledgeBaseId, ::mapChunk).executeAsList()
    }

    suspend fun getAllChunksForBackup(): List<KnowledgeBaseChunk> = withContext(Dispatchers.Default) {
        db.knowledgeBasesQueries.getAllKnowledgeBaseChunks(::mapChunk).executeAsList()
    }

    suspend fun updateIndexStatus(id: String, status: String, signature: String = "", updatedAt: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.Default) {
            db.knowledgeBasesQueries.updateKnowledgeBaseIndexStatus(status, signature, updatedAt, id)
        }

    suspend fun rename(id: String, name: String, updatedAt: Long = System.currentTimeMillis()) = withContext(Dispatchers.Default) {
        require(name.trim().isNotBlank()) { "知识库名称不能为空" }
        db.knowledgeBasesQueries.renameKnowledgeBase(name.trim(), updatedAt, id)
    }

    suspend fun updateChunkIndex(chunkId: String, indexedAt: Long, error: String = "", updatedAt: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.Default) {
            db.knowledgeBasesQueries.updateKnowledgeBaseChunkIndex(indexedAt, error.take(500), updatedAt, chunkId)
        }

    suspend fun clearChunkIndexes(knowledgeBaseId: String, updatedAt: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.Default) {
            db.knowledgeBasesQueries.clearKnowledgeBaseChunkIndexes(updatedAt, knowledgeBaseId)
        }

    suspend fun updateChunkEnabled(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis()) = withContext(Dispatchers.Default) {
        db.knowledgeBasesQueries.updateKnowledgeBaseChunkEnabled(if (enabled) 1L else 0L, updatedAt, id)
    }

    suspend fun updateChunkContent(id: String, content: String, updatedAt: Long = System.currentTimeMillis()) = withContext(Dispatchers.Default) {
        require(content.trim().isNotBlank()) { "分段正文不能为空" }
        db.knowledgeBasesQueries.updateKnowledgeBaseChunkContent(content.trim(), updatedAt, id)
    }

    suspend fun updateChunk(id: String, heading: String, content: String, keywords: String, updatedAt: Long = System.currentTimeMillis()) = withContext(Dispatchers.Default) {
        require(content.trim().isNotBlank()) { "分段正文不能为空" }
        db.knowledgeBasesQueries.updateKnowledgeBaseChunk(heading.trim(), content.trim(), keywords.trim(), updatedAt, id)
    }

    suspend fun addChunk(knowledgeBaseId: String, heading: String, content: String, keywords: String = ""): KnowledgeBaseChunk = withContext(Dispatchers.Default) {
        require(get(knowledgeBaseId) != null) { "知识库不存在" }
        require(content.trim().isNotBlank()) { "分段正文不能为空" }
        val now = Clock.System.now().toEpochMilliseconds()
        val chunk = KnowledgeBaseChunk(
            id = "kbc-${Random.nextLong().toString().replace("-", "")}",
            knowledgeBaseId = knowledgeBaseId,
            ordinal = db.knowledgeBasesQueries.getNextChunkOrdinal(knowledgeBaseId).executeAsOne().toInt(),
            sourceHeading = heading.trim(), content = content.trim(), userKeywords = keywords.trim(),
            createdAt = now, updatedAt = now,
        )
        db.knowledgeBasesQueries.insertKnowledgeBaseChunk(chunk.id, chunk.knowledgeBaseId, chunk.ordinal.toLong(), chunk.sourceHeading, chunk.content, chunk.userKeywords, 1L, 0L, "", now, now)
        chunk
    }

    suspend fun deleteChunk(knowledgeBaseId: String, chunkId: String) = withContext(Dispatchers.Default) {
        require(getChunks(knowledgeBaseId).any { it.id == chunkId }) { "分段不存在或不属于当前知识库" }
        db.knowledgeBasesQueries.deleteChunk(chunkId)
    }

    suspend fun replaceAssignments(operatorId: String, assignments: List<OperatorKnowledgeBaseAssignment>) = withContext(Dispatchers.Default) {
        require(operatorId.isNotBlank()) { "角色 ID 不能为空" }
        require(assignments.all { it.operatorId == operatorId && it.knowledgeBaseId.isNotBlank() }) { "知识库关联无效" }
        require(assignments.map { it.knowledgeBaseId }.distinct().size == assignments.size) { "知识库不能重复关联" }
        db.transaction {
            db.knowledgeBasesQueries.deleteKnowledgeBaseAssignmentsByOperator(operatorId)
            assignments.sortedBy { it.sortOrder }.forEach { assignment ->
                db.knowledgeBasesQueries.insertKnowledgeBaseAssignment(
                    assignment.operatorId, assignment.knowledgeBaseId, if (assignment.enabled) 1L else 0L,
                    assignment.sortOrder.toLong(),
                )
            }
        }
    }

    suspend fun getAssignments(operatorId: String): List<OperatorKnowledgeBaseAssignment> = withContext(Dispatchers.Default) {
        db.knowledgeBasesQueries.getKnowledgeBaseAssignmentsByOperator(operatorId) { opId, knowledgeBaseId, enabled, sortOrder ->
            OperatorKnowledgeBaseAssignment(opId, knowledgeBaseId, enabled != 0L, sortOrder.toInt())
        }.executeAsList()
    }

    suspend fun getAllAssignmentsForBackup(): List<OperatorKnowledgeBaseAssignment> = withContext(Dispatchers.Default) {
        db.knowledgeBasesQueries.getAllKnowledgeBaseAssignments(::mapAssignment).executeAsList()
    }

    suspend fun getAssignmentsForKnowledgeBase(knowledgeBaseId: String): List<OperatorKnowledgeBaseAssignment> = withContext(Dispatchers.Default) {
        db.knowledgeBasesQueries.getKnowledgeBaseAssignmentsByKnowledgeBase(knowledgeBaseId, ::mapAssignment).executeAsList()
    }

    suspend fun replaceAssignmentsForKnowledgeBase(knowledgeBaseId: String, operatorIds: List<String>) = withContext(Dispatchers.Default) {
        require(knowledgeBaseId.isNotBlank()) { "知识库 ID 不能为空" }
        db.transaction {
            db.knowledgeBasesQueries.deleteKnowledgeBaseAssignmentsByKnowledgeBase(knowledgeBaseId)
            operatorIds.distinct().forEachIndexed { index, operatorId ->
                db.knowledgeBasesQueries.insertKnowledgeBaseAssignmentsForBook(operatorId, knowledgeBaseId, 1L, index.toLong())
            }
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.Default) {
        db.transaction {
            db.knowledgeBasesQueries.deleteKnowledgeBaseAssignmentsByKnowledgeBase(id)
            db.knowledgeBasesQueries.deleteChunksByKnowledgeBase(id)
            // Future indexing stores all derived vectors in this dedicated owner partition.
            db.vectorMemoriesQueries.deleteVectorMemoriesByOwner("knowledge_base", id)
            db.knowledgeBasesQueries.deleteKnowledgeBase(id)
        }
    }

    suspend fun deleteAssignmentsForOperator(operatorId: String) = withContext(Dispatchers.Default) {
        db.knowledgeBasesQueries.deleteKnowledgeBaseAssignmentsByOperator(operatorId)
    }

    private fun mapKnowledgeBase(
        id: String, name: String, rawContent: String, sourceFileName: String, sourceFormat: String, sourceType: String,
        chunkingMode: String, indexStatus: String, indexedEmbeddingSignature: String, createdAt: Long, updatedAt: Long,
    ) = KnowledgeBase(id, name, rawContent, sourceFileName, sourceFormat, sourceType, chunkingMode, indexStatus, indexedEmbeddingSignature, createdAt, updatedAt)

    private fun mapChunk(
        id: String, knowledgeBaseId: String, ordinal: Long, sourceHeading: String, content: String,
        userKeywords: String, enabled: Long, indexedAt: Long, indexError: String, createdAt: Long, updatedAt: Long,
    ) = KnowledgeBaseChunk(id, knowledgeBaseId, ordinal.toInt(), sourceHeading, content, userKeywords, enabled != 0L, indexedAt, indexError, createdAt, updatedAt)

    private fun mapAssignment(operatorId: String, knowledgeBaseId: String, enabled: Long, sortOrder: Long) =
        OperatorKnowledgeBaseAssignment(operatorId, knowledgeBaseId, enabled != 0L, sortOrder.toInt())
}
