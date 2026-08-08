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

data class ChatDisplayEvent(
    val messageId: Long,
    val segmentIndex: Int,
    val revealOrder: Long,
)

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

    suspend fun getMessagesSync(sessionId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        db.chatMessagesQueries.getMessagesSync(sessionId) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList()
    }

    suspend fun getRecentMessagesSync(sessionId: String, limit: Long): List<ChatMessage> = withContext(Dispatchers.IO) {
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

    suspend fun updateMessageType(id: Long, type: String) = withContext(Dispatchers.Default) {
        db.chatMessagesQueries.updateType(type, id)
    }

    suspend fun updateMessageContentAndPreview(sessionId: String, id: Long, content: String, timestamp: Long) = withContext(Dispatchers.Default) {
        db.chatMessagesQueries.updateContent(content, id)
        // Regenerating an older reply must not replace a newer chat-list preview or move the session backward.
        db.chatSessionsQueries.updateLastMessageIfNotNewer(previewFromAiJson(content), timestamp, sessionId, timestamp)
    }

    suspend fun getDisplayEvents(sessionId: String): List<ChatDisplayEvent> = withContext(Dispatchers.Default) {
        db.chatDisplayEventsQueries.getDisplayEvents(sessionId) { messageId, segmentIndex, revealOrder ->
            ChatDisplayEvent(messageId, segmentIndex.toInt(), revealOrder)
        }.executeAsList()
    }

    suspend fun addDisplayEventIfAbsent(sessionId: String, messageId: Long, segmentIndex: Int): Long = idMutex.withLock {
        withContext(Dispatchers.Default) {
            db.chatDisplayEventsQueries.getDisplayEventOrder(messageId, segmentIndex.toLong()).executeAsOneOrNull()?.let { return@withContext it }
            val nextOrder = (db.chatDisplayEventsQueries.getNextRevealOrder(sessionId).executeAsOne().MAX ?: 0L) + 1L
            db.chatDisplayEventsQueries.insertDisplayEventIfAbsent(messageId, segmentIndex.toLong(), sessionId, nextOrder)
            db.chatDisplayEventsQueries.getDisplayEventOrder(messageId, segmentIndex.toLong()).executeAsOneOrNull() ?: nextOrder
        }
    }

    suspend fun deleteMessageDisplayEvents(messageId: Long) = withContext(Dispatchers.Default) {
        db.chatDisplayEventsQueries.deleteMessageDisplayEvents(messageId)
    }

    suspend fun deleteDisplayEvent(messageId: Long, segmentIndex: Int) = withContext(Dispatchers.Default) {
        db.chatDisplayEventsQueries.deleteDisplayEvent(messageId, segmentIndex.toLong())
    }

    suspend fun sendMessage(sessionId: String, message: ChatMessage) = idMutex.withLock { withContext(Dispatchers.IO) {
        nextMessageId = maxOf(nextMessageId ?: 0L, message.id + 1)
        val ts = if (message.timestamp > 0) message.timestamp else Clock.System.now().toEpochMilliseconds()
        db.chatMessagesQueries.insertMessage(
            message.id, sessionId, message.senderId, message.senderName, message.content,
            message.type, message.mode, message.emotion, message.activity, message.location,
            message.narration, message.segmentGroup, message.intimacyChange.toLong(), ts,
            if (message.isMe) 1L else 0L
        )
        // System markers, failures and transient placeholders should not replace a real chat preview.
        if (message.type != "system" && !message.content.startsWith("正在重新生成") && !message.content.startsWith("上下文超限")) {
            val preview = when (message.type) {
                "ai_json" -> previewFromAiJson(message.content)
                "image" -> "[图片]"
                "gift_hidden", "gift_reply_failed" -> previewFromGiftPayload(message.content)
                else -> message.content.take(50)
            }
            // A delayed reply or retry must never move a newer home preview backwards.
            db.chatSessionsQueries.updateLastMessageIfNotNewer(preview, ts, sessionId, ts)
        }
    } }

    /** Restores backups without allowing an old row ID to overwrite newer local content. */
    suspend fun restoreMessage(message: ChatMessage) = idMutex.withLock { withContext(Dispatchers.Default) {
        nextMessageId = maxOf(nextMessageId ?: 0L, message.id + 1)
        val ts = if (message.timestamp > 0) message.timestamp else Clock.System.now().toEpochMilliseconds()
        db.chatMessagesQueries.insertMessageIfAbsent(
            message.id, message.sessionId, message.senderId, message.senderName, message.content,
            message.type, message.mode, message.emotion, message.activity, message.location,
            message.narration, message.segmentGroup, message.intimacyChange.toLong(), ts,
            if (message.isMe) 1L else 0L
        )
    } }

    /** Repairs previews written by older builds that could not parse otherwise valid AI payloads. */
    suspend fun repairAiSessionPreviews() = withContext(Dispatchers.Default) {
        val sessions = db.chatSessionsQueries.getAllSessions { id, operatorId, operatorName, lastMessage, lastTime, mode, isPinned, unreadCount, members, rules, avatarUri, mutedMembers ->
            ChatSession(id, operatorId, operatorName, lastMessage, lastTime, mode, isPinned != 0L, unreadCount.toInt(), members, rules, avatarUri, mutedMembers)
        }.executeAsList()
        sessions.forEach { session ->
            val latest = db.chatMessagesQueries.getRecentMessages(session.id, 1) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
                ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
            }.executeAsOneOrNull()
            if (latest == null || latest.timestamp <= 0L) return@forEach
            val preview = when (latest.type) {
                "ai_json" -> AiReplyPreview.extract(latest.content)
                "image" -> "[图片]"
                "gift_hidden", "gift_reply_failed" -> previewFromGiftPayload(latest.content)
                "system" -> null
                else -> latest.content.take(50)
            } ?: return@forEach
            db.chatSessionsQueries.updateLastMessageIfNotNewer(preview, latest.timestamp, session.id, latest.timestamp)
        }
    }

    private fun previewFromAiJson(content: String): String {
        return AiReplyPreview.extract(content) ?: "AI 回复"
    }

    private fun previewFromGiftPayload(content: String): String = runCatching {
        val root = json.parseToJsonElement(content) as? kotlinx.serialization.json.JsonObject
        val giftName = (root?.get("giftName") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
        if (giftName.isBlank()) "[送出了礼物]" else "[送出了礼物：${giftName.take(30)}]"
    }.getOrDefault("[送出了礼物]")

    suspend fun getNextMessageId(): Long = idMutex.withLock {
        withContext(Dispatchers.IO) {
            val next = nextMessageId ?: ((db.chatMessagesQueries.getMaxId().executeAsOne().MAX ?: 0) + 1)
            nextMessageId = next + 1
            next
        }
    }

    suspend fun deleteSessionMessages(sessionId: String) = withContext(Dispatchers.Default) {
        db.chatDisplayEventsQueries.deleteSessionDisplayEvents(sessionId)
        db.chatMessagesQueries.deleteSessionMessages(sessionId)
    }

    suspend fun clearSessionPreview(sessionId: String, timestamp: Long = Clock.System.now().toEpochMilliseconds()) = withContext(Dispatchers.Default) {
        db.chatSessionsQueries.updateLastMessage("", timestamp, sessionId)
    }

    suspend fun deleteMessage(id: Long) = withContext(Dispatchers.Default) {
        db.chatDisplayEventsQueries.deleteMessageDisplayEvents(id)
        db.chatMessagesQueries.deleteMessage(id)
    }
    suspend fun deleteOldMessages(cutoff: Long) = withContext(Dispatchers.Default) {
        db.chatDisplayEventsQueries.deleteOldDisplayEvents(cutoff)
        db.chatMessagesQueries.deleteOldMessages(cutoff)
    }
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
