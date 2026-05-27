package com.example.rhodesterminal.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.rhodesterminal.data.db.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

data class SenderCount(val senderName: String, val cnt: Int)

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessages(sessionId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteSessionMessages(sessionId: String)

    @Query("SELECT MAX(id) FROM chat_messages")
    suspend fun getMaxId(): Long?

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesSync(sessionId: String): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT MAX(timestamp) FROM chat_messages WHERE sessionId = :sessionId AND isMe = 1")
    suspend fun getLastUserMessageTime(sessionId: String): Long?

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun getMessageCount(): Int

    @Query("SELECT senderName, COUNT(*) as cnt FROM chat_messages WHERE isMe = 0 AND senderName != '' AND senderName != '系统' GROUP BY senderName ORDER BY cnt DESC")
    suspend fun getMessageCountPerSender(): List<SenderCount>

    @Query("SELECT * FROM chat_messages WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    suspend fun getMessagesInRange(start: Long, end: Long): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()
}
