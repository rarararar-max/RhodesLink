package com.rhodes.privatechat.shared.vector

import com.rhodes.privatechat.shared.settings.SettingsRepository

class MemoryVectorService(
    private val settings: SettingsRepository,
    private val vectorStoreGateway: VectorStoreGateway,
) {
    private var gatewaySignature = ""
    private var gateway: EmbeddingGateway? = null

    private fun activeGateway(): EmbeddingGateway {
        val signature = listOf(settings.vectorProviderMode, settings.vectorBaseUrl, settings.vectorModelName, settings.vectorApiKey, settings.apiKey).joinToString("|")
        if (gateway == null || gatewaySignature != signature) {
            gateway = createEmbeddingGateway(settings)
            gatewaySignature = signature
        }
        return gateway!!
    }

    suspend fun saveMemory(memory: VectorMemory) {
        val embedding = activeGateway().embed(memory.content)
        vectorStoreGateway.upsert(memory.copy(embedding = embedding))
    }

    suspend fun saveMemoryWithEmbedding(memory: VectorMemory) {
        vectorStoreGateway.upsert(memory)
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
        val queryEmbedding = activeGateway().embed(query)
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
            )
        )
    }

    suspend fun search(request: VectorSearchRequest): List<VectorMemory> {
        val queryEmbedding = activeGateway().embed(request.query)
        return vectorStoreGateway.search(request.copy(queryEmbedding = queryEmbedding))
    }

    suspend fun clearAllMemories() = vectorStoreGateway.clearAllMemories()

    suspend fun deleteMemory(memoryId: String) = vectorStoreGateway.delete(memoryId)

    suspend fun listMemories(ownerType: String, ownerId: String): List<VectorMemory> =
        vectorStoreGateway.listMemories(ownerType, ownerId)

    suspend fun clearSessionMemory(ownerType: String, ownerId: String, sourceId: String) =
        vectorStoreGateway.deleteBySource(ownerType, ownerId, sourceId)
}
