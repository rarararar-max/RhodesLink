package com.example.rhodesterminal.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.rhodesterminal.data.db.entity.RelationshipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RelationshipDao {
    @Query("SELECT * FROM relationships WHERE operatorId = :operatorId ORDER BY intimacy DESC")
    fun getRelationships(operatorId: String): Flow<List<RelationshipEntity>>

    @Query("SELECT * FROM relationships WHERE operatorId = :operatorId ORDER BY intimacy DESC")
    suspend fun getRelationshipsSync(operatorId: String): List<RelationshipEntity>

    @Query("SELECT * FROM relationships WHERE relatedOperatorId = :opId")
    suspend fun getReverseRelationshipsSync(opId: String): List<RelationshipEntity>

    @Query("SELECT * FROM relationships WHERE operatorId = :operatorId AND relatedOperatorId = :relatedId")
    suspend fun getRelationship(operatorId: String, relatedId: String): RelationshipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(relationship: RelationshipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(relationships: List<RelationshipEntity>)

    @Update
    suspend fun update(relationship: RelationshipEntity)

    @Query("DELETE FROM relationships WHERE operatorId = :operatorId AND relatedOperatorId = :relatedId")
    suspend fun delete(operatorId: String, relatedId: String)

    @Query("DELETE FROM relationships WHERE operatorId = :operatorId")
    suspend fun deleteByOperator(operatorId: String)

    @Query("SELECT COUNT(*) FROM relationships WHERE operatorId = :operatorId")
    suspend fun getRelationshipCount(operatorId: String): Int

    @Query("SELECT COUNT(*) FROM relationships")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM relationships WHERE isPreset = 1")
    suspend fun getPresetCount(): Int

    @Query("DELETE FROM relationships WHERE type = :type")
    suspend fun deleteByType(type: String)

    @Query("DELETE FROM relationships WHERE isPreset = 1")
    suspend fun deletePresets()
}
