package com.rhodes.privatechat.shared.vector

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.DatabaseDispatcher
import com.rhodes.privatechat.shared.db.RhodesDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class LocalVectorStoreGateway(
    private val wrapper: DatabaseWrapper,
) : VectorStoreGateway {
    data class DiagnosticMetrics(
        val candidateCount: Int,
        val decodedCount: Int,
        val decodeFailures: Int,
        val dimensionMismatches: Int,
        val selectedCount: Int,
        val sqlMs: Long,
        val decodeScoreMs: Long,
    )
    private val db: RhodesDatabase get() = wrapper.database
    private data class RawVectorRow(
        val id: String, val ownerType: String, val ownerId: String, val sourceType: String,
        val sourceId: String, val content: String, val importance: Double, val embeddingJson: String,
        val tags: String, val visibility: String, val embeddingSignature: String, val createdAt: Long, val expiresAt: Long,
    )

    private fun rawRow(
        id: String, ownerType: String, ownerId: String, sourceType: String, sourceId: String,
        content: String, importance: Double, embeddingJson: String, tags: String, visibility: String,
        embeddingSignature: String, createdAt: Long, expiresAt: Long,
    ) = RawVectorRow(id, ownerType, ownerId, sourceType, sourceId, content, importance, embeddingJson, tags, visibility, embeddingSignature, createdAt, expiresAt)

    override suspend fun upsert(memory: VectorMemory) = withContext(DatabaseDispatcher.dispatcher) {
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
            embeddingSignature = memory.embeddingSignature,
            createdAt = memory.createdAt,
            expiresAt = memory.expiresAt,
        )
    }

    override suspend fun search(request: VectorSearchRequest): List<VectorMemory> {
        val (rows, now, sourceKinds) = withContext(DatabaseDispatcher.dispatcher) {
            loadCandidates(request)
        }
        // Keep SQLite serialized, but never make message persistence wait for JSON decoding,
        // cosine scoring, or sorting large candidate sets.
        return withContext(kotlinx.coroutines.Dispatchers.Default) {
            scoreCandidates(rows, request, now, sourceKinds)
        }
    }

    /** Read-only aggregate timing for diagnostics; never returns record IDs, text, or vectors. */
    suspend fun diagnose(request: VectorSearchRequest): DiagnosticMetrics {
        val sqlStarted = kotlin.time.TimeSource.Monotonic.markNow()
        val (rows, now, sourceKinds) = withContext(DatabaseDispatcher.dispatcher) { loadCandidates(request) }
        val sqlMs = sqlStarted.elapsedNow().inWholeMilliseconds
        val scoreStarted = kotlin.time.TimeSource.Monotonic.markNow()
        var decoded = 0
        var failures = 0
        var mismatches = 0
        val results = rows.asSequence()
            .filter { request.sourceTypes.isEmpty() || it.sourceType in request.sourceTypes }
            .filter { sourceKinds.isEmpty() || it.tags.split(',').any { kind -> kind in sourceKinds } }
            .filter { request.visibilities.isEmpty() || it.visibility in request.visibilities }
            .filter { request.embeddingSignature.isBlank() || it.embeddingSignature == request.embeddingSignature }
            .filter { it.expiresAt > now }
            .mapNotNull { row ->
                val embedding = runCatching { json.decodeFromString<List<Double>>(row.embeddingJson) }.getOrElse { failures++; emptyList() }
                if (embedding.isNotEmpty()) decoded++
                if (request.queryEmbedding.isNotEmpty() && embedding.isNotEmpty() && request.queryEmbedding.size != embedding.size) {
                    mismatches++
                    return@mapNotNull null
                }
                val score = if (request.queryEmbedding.isNotEmpty() && embedding.isNotEmpty()) cosineSimilarity(request.queryEmbedding, embedding)
                else keywordScore(request.query, row.content) + row.importance * 0.05
                score.takeIf { it >= request.minScore }
            }.toList()
        return DiagnosticMetrics(rows.size, decoded, failures, mismatches, results.size.coerceAtMost(request.limit), sqlMs, scoreStarted.elapsedNow().inWholeMilliseconds)
    }

    private fun loadCandidates(request: VectorSearchRequest): Triple<List<RawVectorRow>, Long, List<String>> {
        val now = request.now.takeIf { it > 0L } ?: System.currentTimeMillis()
        val minCreatedAt = request.minCreatedAt.coerceAtLeast(0L)
        val sourceKinds = request.sourceKinds.distinct()
        require(request.candidateLimit >= 0) { "candidateLimit must not be negative" }
        val perSourceLimit = if (sourceKinds.isEmpty()) request.candidateLimit else {
            ((request.candidateLimit + sourceKinds.size - 1) / sourceKinds.size).coerceAtLeast(1)
        }
        val rows = if (request.candidateSourceType.isNotBlank() && sourceKinds.isNotEmpty()) {
            sourceKinds.flatMap { sourceKind ->
                db.vectorMemoriesQueries.getVectorCandidatesByOwnerAndSourceTypeAndSourceKind(
                    request.ownerType, request.ownerId, request.candidateSourceType, sourceKind, sourceKind,
                    now, minCreatedAt, request.maxCreatedAt, request.minImportance, request.embeddingSignature,
                    if (request.preferRecentCandidates) 1L else 0L,
                    if (request.preferRecentCandidates) 1L else 0L,
                    perSourceLimit.toLong(),
                    mapper = ::rawRow,
                ).executeAsList()
            }
        } else if (request.candidateSourceType.isNotBlank()) {
            db.vectorMemoriesQueries.getVectorCandidatesByOwnerAndSourceType(
                request.ownerType,
                request.ownerId,
                request.candidateSourceType,
                now,
                minCreatedAt,
                request.maxCreatedAt,
                request.minImportance,
                request.embeddingSignature,
                if (request.preferRecentCandidates) 1L else 0L,
                if (request.preferRecentCandidates) 1L else 0L,
                request.candidateLimit.toLong(),
                mapper = ::rawRow,
            ).executeAsList()
        } else if (request.candidateLimit > 0 && sourceKinds.isNotEmpty()) {
            sourceKinds.flatMap { sourceKind ->
                db.vectorMemoriesQueries.getVectorCandidatesByOwnerAndSourceKind(
                    request.ownerType, request.ownerId, sourceKind, sourceKind, now, minCreatedAt,
                    request.minImportance, request.embeddingSignature, perSourceLimit.toLong(),
                    mapper = ::rawRow,
                ).executeAsList()
            }
        } else if (request.candidateLimit > 0) {
            db.vectorMemoriesQueries.getVectorCandidatesByOwner(
                request.ownerType,
                request.ownerId,
                now,
                minCreatedAt,
                request.minImportance,
                request.embeddingSignature,
                request.candidateLimit.toLong(),
                mapper = ::rawRow,
            ).executeAsList()
        } else {
            db.vectorMemoriesQueries.getVectorMemoriesByOwner(request.ownerType, request.ownerId, ::rawRow).executeAsList()
        }
        return Triple(
            rows.distinctBy { it.id }.let { candidates ->
                if (request.candidateLimit > 0) candidates.take(request.candidateLimit) else candidates
            },
            now,
            sourceKinds,
        )
    }

    private fun scoreCandidates(
        rows: List<RawVectorRow>,
        request: VectorSearchRequest,
        now: Long,
        sourceKinds: List<String>,
    ): List<VectorMemory> {
        val filtered = rows
            .asSequence()
            .filter { row -> request.sourceTypes.isEmpty() || row.sourceType in request.sourceTypes }
            .filter { row ->
                sourceKinds.isEmpty() || row.tags.split(',').any { it in sourceKinds }
            }
            .filter { row -> request.visibilities.isEmpty() || row.visibility in request.visibilities }
            .filter { row -> request.embeddingSignature.isBlank() || row.embeddingSignature == request.embeddingSignature }
            .filter { row -> row.expiresAt > now }
            .toList()
        return filtered
            .mapNotNull { row ->
                val embedding = runCatching { json.decodeFromString<List<Double>>(row.embeddingJson) }.getOrDefault(emptyList())
                val score = if (request.queryEmbedding.isNotEmpty() && embedding.isNotEmpty() && request.queryEmbedding.size == embedding.size) {
                    cosineSimilarity(request.queryEmbedding, embedding)
                } else if (request.queryEmbedding.isEmpty() || embedding.isEmpty()) {
                    keywordScore(request.query, row.content) + row.importance * 0.05
                } else {
                    return@mapNotNull null
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
                    embeddingSignature = row.embeddingSignature,
                    createdAt = row.createdAt,
                    expiresAt = row.expiresAt,
                    similarity = score,
                ) to score
            }
            .sortedByDescending { it.second }
            .take(request.limit)
            .map { it.first }
    }

    override suspend fun listMemories(ownerType: String, ownerId: String): List<VectorMemory> = withContext(DatabaseDispatcher.dispatcher) {
        db.vectorMemoriesQueries.getVectorMemoriesByOwner(ownerType, ownerId).executeAsList().map { row ->
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
                embeddingSignature = row.embeddingSignature,
                createdAt = row.createdAt,
                expiresAt = row.expiresAt,
            )
        }
    }

    override suspend fun delete(memoryId: String) = withContext(DatabaseDispatcher.dispatcher) {
        db.vectorMemoriesQueries.deleteVectorMemory(memoryId)
    }

    override suspend fun deleteBySource(ownerType: String, ownerId: String, sourceId: String) = withContext(DatabaseDispatcher.dispatcher) {
        db.vectorMemoriesQueries.deleteVectorMemoriesBySource(ownerType, ownerId, sourceId)
    }

    override suspend fun clearOwnerMemory(ownerType: String, ownerId: String) = withContext(DatabaseDispatcher.dispatcher) {
        db.vectorMemoriesQueries.deleteVectorMemoriesByOwner(ownerType, ownerId)
    }

    override suspend fun clearAllMemories() = withContext(DatabaseDispatcher.dispatcher) {
        db.vectorMemoriesQueries.deleteAllVectorMemories()
    }

    private fun keywordScore(query: String, content: String): Double {
        val queryChars = query.toSet()
        if (queryChars.isEmpty()) return 0.0
        return content.count { it in queryChars }.toDouble() / queryChars.size
    }

    private fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
        if (a.size != b.size) return 0.0
        val size = a.size
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
