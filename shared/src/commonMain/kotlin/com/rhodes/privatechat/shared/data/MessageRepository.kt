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

    suspend fun getMessagesBefore(sessionId: String, beforeTimestamp: Long, beforeId: Long, limit: Long): List<ChatMessage> = withContext(Dispatchers.Default) {
        db.chatMessagesQueries.getMessagesBefore(sessionId, beforeTimestamp, beforeTimestamp, beforeId, limit) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
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
        // System markers, failures and transient placeholders should not replace a real chat preview.
        if (message.type != "system" && !message.content.startsWith("正在重新生成") && !message.content.startsWith("上下文超限")) {
            val preview = when (message.type) {
                "ai_json" -> previewFromAiJson(message.content)
                "image" -> "[图片]"
                else -> message.content.take(50)
            }
            db.chatSessionsQueries.updateLastMessage(preview, ts, sessionId)
        }
    }

    private fun previewFromAiJson(content: String): String {
        return try {
            when (val root = json.parseToJsonElement(content)) {
                is kotlinx.serialization.json.JsonArray -> {
                    val last = root.lastOrNull() as? kotlinx.serialization.json.JsonObject
                    val speaker = (last?.get("speaker") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                    val text = (last?.get("message") as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: (last?.get("content") as? kotlinx.serialization.json.JsonPrimitive)?.content
                    if (!text.isNullOrBlank()) listOf(speaker.takeIf { it.isNotBlank() }, text.take(50)).filterNotNull().joinToString("：") else content.take(50)
                }
                is kotlinx.serialization.json.JsonObject -> {
                    val segArray = root["segments"] as? kotlinx.serialization.json.JsonArray
                    val lastText = segArray?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
                        ?.asReversed()
                        ?.firstNotNullOfOrNull { obj ->
                            val type = (obj["type"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                            (obj["content"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                                ?.takeIf { it.isNotBlank() && !type.equals("narration", true) }
                        }
                    lastText?.take(50)
                        ?: (root["dialogue"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.take(50)
                        ?: content.take(50)
                }
                else -> content.take(50)
            }
        } catch (_: Exception) {
            Regex("\"(?:content|message)\"\\s*:\\s*\"([^\"]{1,80})").findAll(content).lastOrNull()?.groupValues?.getOrNull(1)?.take(50)
                ?: content.take(50)
        }
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

    suspend fun clearSessionPreview(sessionId: String, timestamp: Long = Clock.System.now().toEpochMilliseconds()) = withContext(Dispatchers.Default) {
        db.chatSessionsQueries.updateLastMessage("", timestamp, sessionId)
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

    suspend fun searchMessagesInSession(sessionId: String, keyword: String, limit: Long = 200): List<ChatMessage> = withContext(Dispatchers.Default) {
        val like = "%${keyword.replace("%", "\\%").replace("_", "\\_")}%"
        db.chatMessagesQueries.searchMessagesInSession(sessionId, like, limit) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList()
    }

    suspend fun getMessagesBySessionInRange(sessionId: String, start: Long, end: Long): List<ChatMessage> = withContext(Dispatchers.Default) {
        db.chatMessagesQueries.getMessagesBySessionInRange(sessionId, start, end) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList()
    }

    suspend fun getMessageDatesBySession(sessionId: String): List<String> = withContext(Dispatchers.Default) {
        db.chatMessagesQueries.getMessageDatesBySession(sessionId).executeAsList().mapNotNull { it as? String }
    }
}
