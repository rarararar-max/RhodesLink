package com.example.rhodesterminal.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.rhodesterminal.data.db.entity.AnchorType
import com.example.rhodesterminal.data.db.entity.MemoryAnchorEntity
import com.example.rhodesterminal.data.db.entity.MemoryEntity
import com.example.rhodesterminal.data.db.entity.MemoryType
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE sessionId = :sessionId AND type = :memType ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestMemory(sessionId: String, memType: MemoryType): MemoryEntity?

    @Query("SELECT * FROM memories WHERE sessionId = :sessionId AND type = :memType ORDER BY createdAt DESC")
    fun getMemories(sessionId: String, memType: MemoryType): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE operatorId = :operatorId AND type = 'LONG_TERM' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestLongTermImpression(operatorId: String): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM memory_anchors WHERE operatorId = :operatorId AND isPrivate = 0 AND expiresAt > :now ORDER BY createdAt DESC")
    suspend fun getPublicAnchors(operatorId: String, now: Long = System.currentTimeMillis()): List<MemoryAnchorEntity>

    @Query("SELECT * FROM memory_anchors WHERE operatorId = :operatorId AND expiresAt > :now ORDER BY createdAt DESC")
    suspend fun getAllAnchors(operatorId: String, now: Long = System.currentTimeMillis()): List<MemoryAnchorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnchor(anchor: MemoryAnchorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnchors(anchors: List<MemoryAnchorEntity>)

    @Query("DELETE FROM memory_anchors WHERE expiresAt < :now")
    suspend fun deleteExpiredAnchors(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM memory_anchors WHERE createdAt < :cutoff")
    suspend fun deleteOldAnchors(cutoff: Long)

    @Query("SELECT * FROM memory_anchors WHERE sessionId = :sessionId AND type = :anchorType AND createdAt > :since ORDER BY createdAt DESC")
    suspend fun getRecentAnchors(sessionId: String, anchorType: AnchorType, since: Long): List<MemoryAnchorEntity>

    @Query("SELECT COUNT(*) FROM memory_anchors")
    suspend fun getAnchorCount(): Int

    @Query("SELECT * FROM memories WHERE type = 'LONG_TERM' ORDER BY createdAt DESC")
    suspend fun getAllLongTerm(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE type = 'DAILY' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestDaily(): MemoryEntity?

    @Query("SELECT * FROM memories WHERE sessionId = :sessionId AND type = :memType ORDER BY createdAt ASC")
    suspend fun getMemoriesBySession(sessionId: String, memType: MemoryType): List<MemoryEntity>

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: Long)

    @Query("DELETE FROM memories WHERE type = 'LONG_TERM'")
    suspend fun deleteAllLongTerm()
}
