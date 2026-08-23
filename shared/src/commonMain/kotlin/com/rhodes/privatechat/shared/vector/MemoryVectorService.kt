package com.rhodes.privatechat.shared.vector

import com.rhodes.privatechat.shared.settings.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MemoryVectorService(
    private val settings: SettingsRepository,
    private val vectorStoreGateway: VectorStoreGateway,
) {
    private var gatewaySignature = ""
    private var gateway: EmbeddingGateway? = null
    private val embeddingCache = object : LinkedHashMap<String, List<Double>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Double>>?): Boolean = size > 32
    }
    private val embeddingCacheMutex = Mutex()

    private fun activeGateway(): EmbeddingGateway {
        val signature = listOf(settings.vectorProviderMode, settings.vectorBaseUrl, settings.vectorModelName, settings.vectorApiKey, settings.apiKey).joinToString("|")
        if (gateway == null || gatewaySignature != signature) {
            gateway = createEmbeddingGateway(settings)
            gatewaySignature = signature
        }
        return gateway!!
    }

    fun currentEmbeddingSignature(): String = EmbeddingConfigurationSignature.create(
        settings.vectorProviderMode,
        settings.vectorProvider,
        settings.vectorBaseUrl,
        settings.vectorModelName,
    )

    suspend fun saveMemory(memory: VectorMemory) {
        val embedding = embedCached(memory.content)
        require(embedding.isUsableEmbedding()) { "Embedding 服务没有返回有效向量" }
        vectorStoreGateway.upsert(memory.copy(embedding = embedding, embeddingSignature = currentEmbeddingSignature()))
    }

    suspend fun saveMemoryWithEmbedding(memory: VectorMemory) {
        require(memory.embedding.isUsableEmbedding()) { "Embedding 服务没有返回有效向量" }
        vectorStoreGateway.upsert(memory.copy(embeddingSignature = memory.embeddingSignature.ifBlank { currentEmbeddingSignature() }))
    }

    suspend fun recall(
        ownerType: String,
        ownerId: String,
        query: String,
        limit: Int = 6,
        visibilities: List<String> = emptyList(),
        minScore: Double = 0.0,
        now: Long = 0L,
    ): List<VectorMemory> {
        val queryEmbedding = embedCached(query)
        if (!queryEmbedding.isUsableEmbedding()) return emptyList()
        return vectorStoreGateway.search(
            VectorSearchRequest(
                ownerType = ownerType,
                ownerId = ownerId,
                query = query,
                queryEmbedding = queryEmbedding,
                limit = limit,
                visibilities = visibilities,
                minScore = minScore,
                now = now,
                embeddingSignature = currentEmbeddingSignature(),
            )
        )
    }

    suspend fun search(request: VectorSearchRequest): List<VectorMemory> {
        val queryEmbedding = embedCached(request.query)
        return searchWithEmbedding(request, queryEmbedding)
    }

    /** Keeps diagnostics able to distinguish gateway latency from local vector-store latency. */
    suspend fun embedForDiagnostics(text: String): List<Double> = embedCached(text)

    suspend fun searchWithEmbedding(request: VectorSearchRequest, queryEmbedding: List<Double>): List<VectorMemory> {
        if (!queryEmbedding.isUsableEmbedding()) return emptyList()
        return vectorStoreGateway.search(request.copy(queryEmbedding = queryEmbedding, embeddingSignature = currentEmbeddingSignature()))
    }

    suspend fun clearAllMemories() = vectorStoreGateway.clearAllMemories()

    suspend fun deleteMemory(memoryId: String) = vectorStoreGateway.delete(memoryId)

    suspend fun listMemories(ownerType: String, ownerId: String): List<VectorMemory> =
        vectorStoreGateway.listMemories(ownerType, ownerId)

    suspend fun clearSessionMemory(ownerType: String, ownerId: String, sourceId: String) =
        vectorStoreGateway.deleteBySource(ownerType, ownerId, sourceId)

    suspend fun clearOwnerMemory(ownerType: String, ownerId: String) =
        vectorStoreGateway.clearOwnerMemory(ownerType, ownerId)

    private suspend fun embedCached(text: String): List<Double> {
        val normalized = text.trim()
        if (normalized.isBlank()) return emptyList()
        val gateway = activeGateway()
        val key = "$gatewaySignature:$normalized"
        embeddingCacheMutex.withLock { embeddingCache[key] }?.let { return it }
        // Embedding belongs to its caller. A prompt timeout must also cancel this work instead of
        // leaving an application-wide request running after the reply has degraded.
        val embedding = gateway.embed(normalized)
        if (embedding.isUsableEmbedding()) {
            embeddingCacheMutex.withLock { embeddingCache[key] = embedding }
        }
        return embedding
    }

    private fun List<Double>.isUsableEmbedding(): Boolean = isNotEmpty() && all { it.isFinite() }
}
