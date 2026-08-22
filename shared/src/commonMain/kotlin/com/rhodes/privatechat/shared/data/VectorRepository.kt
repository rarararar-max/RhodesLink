package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.vector.VectorMemory
import com.rhodes.privatechat.shared.vector.VectorSearchRequest
import com.rhodes.privatechat.shared.vector.VectorStoreGateway

class VectorRepository(
    private val wrapper: DatabaseWrapper,
    private val gateway: VectorStoreGateway,
) {
    // The configured gateway owns persistence. Writing through both layers duplicated every vector.
    suspend fun upsert(memory: VectorMemory) = gateway.upsert(memory)
    suspend fun search(request: VectorSearchRequest): List<VectorMemory> = gateway.search(request)
    suspend fun list(ownerType: String, ownerId: String): List<VectorMemory> = gateway.listMemories(ownerType, ownerId)
    suspend fun delete(memoryId: String) = gateway.delete(memoryId)
    suspend fun clearOwnerMemory(ownerType: String, ownerId: String) = gateway.clearOwnerMemory(ownerType, ownerId)
}
