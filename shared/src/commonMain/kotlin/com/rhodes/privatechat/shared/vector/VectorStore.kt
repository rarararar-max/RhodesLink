package com.rhodes.privatechat.shared.vector

import kotlinx.serialization.Serializable

@Serializable
data class VectorMemory(
    val id: String,
    val ownerType: String,
    val ownerId: String,
    val sourceType: String,
    val sourceId: String,
    val content: String,
    val importance: Double = 0.0,
    val embedding: List<Double> = emptyList(),
    val tags: String = "",
    val visibility: String = "public",
    val createdAt: Long = 0L,
    val expiresAt: Long = Long.MAX_VALUE,
)

@Serializable
data class VectorSearchRequest(
    val ownerType: String,
    val ownerId: String,
    val query: String,
    val queryEmbedding: List<Double> = emptyList(),
    val limit: Int = 6,
    val sourceTypes: List<String> = emptyList(),
    val visibilities: List<String> = emptyList(),
    val minScore: Double = 0.0,
    val now: Long = 0L,
)

interface VectorStoreGateway {
    suspend fun upsert(memory: VectorMemory)
    suspend fun search(request: VectorSearchRequest): List<VectorMemory>
    suspend fun listMemories(ownerType: String, ownerId: String): List<VectorMemory>
    suspend fun delete(memoryId: String)
    suspend fun deleteBySource(ownerType: String, ownerId: String, sourceId: String)
    suspend fun clearOwnerMemory(ownerType: String, ownerId: String)
    suspend fun clearAllMemories()
}

class DisabledVectorStoreGateway : VectorStoreGateway {
    override suspend fun upsert(memory: VectorMemory) = Unit
    override suspend fun search(request: VectorSearchRequest): List<VectorMemory> = emptyList()
    override suspend fun listMemories(ownerType: String, ownerId: String): List<VectorMemory> = emptyList()
    override suspend fun delete(memoryId: String) = Unit
    override suspend fun deleteBySource(ownerType: String, ownerId: String, sourceId: String) = Unit
    override suspend fun clearOwnerMemory(ownerType: String, ownerId: String) = Unit
    override suspend fun clearAllMemories() = Unit
}
