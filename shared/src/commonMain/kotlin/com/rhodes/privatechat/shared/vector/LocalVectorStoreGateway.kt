package com.rhodes.privatechat.shared.vector

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.sqrt

class LocalVectorStoreGateway(
    private val wrapper: DatabaseWrapper,
) : VectorStoreGateway {
    private val db: RhodesDatabase get() = wrapper.database

    override suspend fun upsert(memory: VectorMemory) {
        db.vectorMemoriesQueries.insertVectorMemory(
            id = memory.id,
            ownerType = memory.ownerType,
            ownerId = memory.ownerId,
            sourceType = memory.sourceType,
            sourceId = memory.sourceId,
            content = memory.content,
            importance = memory.importance,
            embeddingJson = json.encodeToString(memory.embedding),
            tags = memory.tags,
            visibility = memory.visibility,
            createdAt = memory.createdAt,
            expiresAt = memory.expiresAt,
        )
    }

    override suspend fun search(request: VectorSearchRequest): List<VectorMemory> {
        val rows = db.vectorMemoriesQueries.getVectorMemoriesByOwner(request.ownerType, request.ownerId).executeAsList()
        val filtered = rows
            .asSequence()
            .filter { row -> request.sourceTypes.isEmpty() || row.sourceType in request.sourceTypes }
            .filter { row -> request.visibilities.isEmpty() || row.visibility in request.visibilities }
            .filter { row -> request.now <= 0L || row.expiresAt > request.now }
            .toList()
        return filtered
            .mapNotNull { row ->
                val embedding = runCatching { json.decodeFromString<List<Double>>(row.embeddingJson) }.getOrDefault(emptyList())
                val score = if (request.queryEmbedding.isNotEmpty() && embedding.isNotEmpty()) {
                    cosineSimilarity(request.queryEmbedding, embedding)
                } else {
                    keywordScore(request.query, row.content) + row.importance * 0.05
                }
                if (score < request.minScore) return@mapNotNull null
                VectorMemory(
                    id = row.id,
                    ownerType = row.ownerType,
                    ownerId = row.ownerId,
                    sourceType = row.sourceType,
                    sourceId = row.sourceId,
                    content = row.content,
                    importance = row.importance,
                    embedding = embedding,
                    tags = row.tags,
                    visibility = row.visibility,
                    createdAt = row.createdAt,
                    expiresAt = row.expiresAt,
                ) to score
            }
            .sortedByDescending { it.second }
            .take(request.limit)
            .map { it.first }
    }

    override suspend fun listMemories(ownerType: String, ownerId: String): List<VectorMemory> {
        return db.vectorMemoriesQueries.getVectorMemoriesByOwner(ownerType, ownerId).executeAsList().map { row ->
            val embedding = runCatching { json.decodeFromString<List<Double>>(row.embeddingJson) }.getOrDefault(emptyList())
            VectorMemory(
                id = row.id,
                ownerType = row.ownerType,
                ownerId = row.ownerId,
                sourceType = row.sourceType,
                sourceId = row.sourceId,
                content = row.content,
                importance = row.importance,
                embedding = embedding,
                tags = row.tags,
                visibility = row.visibility,
                createdAt = row.createdAt,
                expiresAt = row.expiresAt,
            )
        }
    }

    override suspend fun delete(memoryId: String) {
        db.vectorMemoriesQueries.deleteVectorMemory(memoryId)
    }

    override suspend fun clearOwnerMemory(ownerType: String, ownerId: String) {
        db.vectorMemoriesQueries.deleteVectorMemoriesByOwner(ownerType, ownerId)
    }

    private fun keywordScore(query: String, content: String): Double {
        val queryChars = query.toSet()
        if (queryChars.isEmpty()) return 0.0
        return content.count { it in queryChars }.toDouble() / queryChars.size
    }

    private fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
        val size = minOf(a.size, b.size)
        if (size == 0) return 0.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        repeat(size) { index ->
            dot += a[index] * b[index]
            normA += a[index] * a[index]
            normB += b[index] * b[index]
        }
        if (normA == 0.0 || normB == 0.0) return 0.0
        return dot / (sqrt(normA) * sqrt(normB))
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
