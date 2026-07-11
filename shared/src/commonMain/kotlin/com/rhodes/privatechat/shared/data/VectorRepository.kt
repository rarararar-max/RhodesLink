package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.vector.VectorMemory
import com.rhodes.privatechat.shared.vector.VectorSearchRequest
import com.rhodes.privatechat.shared.vector.VectorStoreGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class VectorRepository(
    private val wrapper: DatabaseWrapper,
    private val gateway: VectorStoreGateway,
) {
    private val db: RhodesDatabase get() = wrapper.database

    suspend fun upsert(memory: VectorMemory) = withContext(Dispatchers.Default) {
        gateway.upsert(memory)
        db.vectorMemoriesQueries.insertVectorMemory(
            memory.id,
            memory.ownerType,
            memory.ownerId,
            memory.sourceType,
            memory.sourceId,
            memory.content,
            memory.importance,
            Json.encodeToString(memory.embedding),
            memory.tags,
            memory.visibility,
            memory.createdAt,
            memory.expiresAt,
        )
    }

    suspend fun search(request: VectorSearchRequest): List<VectorMemory> = withContext(Dispatchers.Default) {
        gateway.search(request)
    }

    suspend fun list(ownerType: String, ownerId: String): List<VectorMemory> = withContext(Dispatchers.Default) {
        gateway.listMemories(ownerType, ownerId)
    }

    suspend fun delete(memoryId: String) = withContext(Dispatchers.Default) {
        gateway.delete(memoryId)
        db.vectorMemoriesQueries.deleteVectorMemory(memoryId)
    }

    suspend fun clearOwnerMemory(ownerType: String, ownerId: String) = withContext(Dispatchers.Default) {
        gateway.clearOwnerMemory(ownerType, ownerId)
        db.vectorMemoriesQueries.deleteVectorMemoriesByOwner(ownerType, ownerId)
    }
}
