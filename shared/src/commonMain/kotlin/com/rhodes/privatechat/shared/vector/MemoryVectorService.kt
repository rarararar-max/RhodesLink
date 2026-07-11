package com.rhodes.privatechat.shared.vector

class MemoryVectorService(
    private val embeddingGateway: EmbeddingGateway,
    private val vectorStoreGateway: VectorStoreGateway,
) {
    suspend fun saveMemory(memory: VectorMemory) {
        val embedding = embeddingGateway.embed(memory.content)
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
        val queryEmbedding = embeddingGateway.embed(query)
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
        val queryEmbedding = embeddingGateway.embed(request.query)
        return vectorStoreGateway.search(request.copy(queryEmbedding = queryEmbedding))
    }
}
