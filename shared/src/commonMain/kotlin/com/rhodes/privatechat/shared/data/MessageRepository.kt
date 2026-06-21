package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.*
import kotlinx.coroutines.Dispatchers
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class MessageRepository(private val wrapper: DatabaseWrapper) {

    private val db: RhodesDatabase get() = wrapper.database
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val idMutex = Mutex()
    private var nextMessageId: Long? = null

    // --- Messages ---
    fun getMessages(sessionId: String): Flow<List<ChatMessage>> =
        db.chatMessagesQueries.getMessages(sessionId) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.asFlow().mapToList(Dispatchers.Default)

    fun getRecentMessages(sessionId: String, limit: Long): Flow<List<ChatMessage>> =
        db.chatMessagesQueries.getRecentMessages(sessionId, limit) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.asFlow().mapToList(Dispatchers.Default)

    suspend fun getMessagesSync(sessionId: String): List<ChatMessage> = withContext(Dispatchers.Default) {
        db.chatMessagesQueries.getMessagesSync(sessionId) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList()
    }

    suspend fun getRecentMessagesSync(sessionId: String, limit: Long): List<ChatMessage> = withContext(Dispatchers.Default) {
        db.chatMessagesQueries.getRecentMessages(sessionId, limit) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList()
    }

    suspend fun updateMessageContent(id: Long, content: String) = withContext(Dispatchers.Default) {
        db.chatMessagesQueries.updateContent(content, id)
    }

    suspend fun sendMessage(sessionId: String, message: ChatMessage) = withContext(Dispatchers.Default) {
        val ts = if (message.timestamp > 0) message.timestamp else Clock.System.now().toEpochMilliseconds()
        db.chatMessagesQueries.insertMessage(
            message.id, message.sessionId, message.senderId, message.senderName, message.content,
            message.type, message.mode, message.emotion, message.activity, message.location,
            message.narration, message.segmentGroup, message.intimacyChange.toLong(), ts,
            if (message.isMe) 1L else 0L
        )
        val preview = if (message.type == "ai_json") {
            try {
                val obj = json.parseToJsonElement(message.content) as? kotlinx.serialization.json.JsonObject
                val segArray = obj?.get("segments") as? kotlinx.serialization.json.JsonArray
                if (segArray != null && segArray.isNotEmpty()) {
                    val last = segArray.last() as? kotlinx.serialization.json.JsonObject
                    (last?.get("content") as? kotlinx.serialization.json.JsonPrimitive)?.content?.take(50)
                        ?: message.content.take(50)
                } else message.content.take(50)
            } catch (_: Exception) { message.content.take(50) }
        } else message.content.take(50)
        db.chatSessionsQueries.updateLastMessage(preview, ts, sessionId)
    }

    suspend fun getNextMessageId(): Long = idMutex.withLock {
        withContext(Dispatchers.Default) {
            val next = nextMessageId ?: ((db.chatMessagesQueries.getMaxId().executeAsOne().MAX ?: 0) + 1)
            nextMessageId = next + 1
            next
        }
    }

    suspend fun deleteSessionMessages(sessionId: String) = withContext(Dispatchers.Default) {
        db.chatMessagesQueries.deleteSessionMessages(sessionId)
    }

    suspend fun deleteMessage(id: Long) = withContext(Dispatchers.Default) { db.chatMessagesQueries.deleteMessage(id) }
    suspend fun deleteOldMessages(cutoff: Long) = withContext(Dispatchers.Default) { db.chatMessagesQueries.deleteOldMessages(cutoff) }
    suspend fun getMessageCount(): Int = withContext(Dispatchers.Default) { db.chatMessagesQueries.getMessageCount().executeAsOne().toInt() }

    suspend fun getMessageCountPerSender(): List<SenderCount> = withContext(Dispatchers.Default) {
        db.chatMessagesQueries.getMessageCountPerSender().executeAsList().map { SenderCount(it.senderName, it.cnt) }
    }

    suspend fun getMessageCountPerSenderSince(since: Long): List<SenderCount> = withContext(Dispatchers.Default) {
        db.chatMessagesQueries.getMessageCountPerSenderSince(since).executeAsList().map { SenderCount(it.senderName, it.cnt) }
    }

    suspend fun getMessagesInRange(start: Long, end: Long): List<ChatMessage> = withContext(Dispatchers.Default) {
        db.chatMessagesQueries.getMessagesInRange(start, end) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList()
    }
}
