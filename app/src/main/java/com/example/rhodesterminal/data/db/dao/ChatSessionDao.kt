package com.example.rhodesterminal.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.rhodesterminal.data.db.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY lastTime DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE operatorId = :operatorId LIMIT 1")
    suspend fun getSessionByOperator(operatorId: String): ChatSessionEntity?

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getSession(id: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ChatSessionEntity)

    @Query("UPDATE chat_sessions SET lastMessage = :message, lastTime = :time WHERE id = :id")
    suspend fun updateLastMessage(id: String, message: String, time: Long)

    @Query("UPDATE chat_sessions SET mode = :mode WHERE id = :id")
    suspend fun updateMode(id: String, mode: String)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM chat_sessions WHERE operatorId LIKE 'group_%'")
    suspend fun getGroupCount(): Int

    @Query("SELECT COUNT(*) FROM chat_sessions")
    suspend fun getSessionCount(): Int

    @Query("UPDATE chat_sessions SET unreadCount = 0")
    suspend fun markAllRead()

    @Query("DELETE FROM chat_sessions WHERE operatorId NOT LIKE 'group_%'")
    suspend fun deleteAllSessions()
}
