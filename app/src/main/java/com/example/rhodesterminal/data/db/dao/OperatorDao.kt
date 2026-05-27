package com.example.rhodesterminal.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.rhodesterminal.data.db.entity.OperatorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OperatorDao {
    @Query("SELECT * FROM operators ORDER BY name ASC")
    fun getAllOperators(): Flow<List<OperatorEntity>>

    @Query("SELECT * FROM operators WHERE id = :id")
    suspend fun getOperator(id: String): OperatorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operator: OperatorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(operators: List<OperatorEntity>)

    @Update
    suspend fun update(operator: OperatorEntity)

    @Query("UPDATE operators SET intimacy = :value WHERE id = :id")
    suspend fun updateIntimacy(id: String, value: Int)

    @Query("SELECT * FROM operators WHERE id IN (SELECT DISTINCT operatorId FROM chat_sessions) ORDER BY (SELECT lastTime FROM chat_sessions WHERE operatorId = operators.id ORDER BY lastTime DESC LIMIT 1) DESC")
    fun getRecentChatOperators(): Flow<List<OperatorEntity>>

    @Query("SELECT COUNT(*) FROM operators")
    suspend fun getCount(): Int

    @Query("DELETE FROM operators WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE operators SET privatePrompt = :privatePrompt, groupPrompt = :groupPrompt WHERE id = :id")
    suspend fun updatePrompts(id: String, privatePrompt: String, groupPrompt: String)
}
