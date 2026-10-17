package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.DatabaseDispatcher
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.*
import com.rhodes.privatechat.shared.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

data class ChatDisplayEvent(
    val messageId: Long,
    val segmentIndex: Int,
    val revealOrder: Long,
)

/**
 * 2000-01-01. Messages below this are not old data but invalid data (the schema default is 0 and the
 * legacy repair can write 1970-era values), so retention deletion must never treat them as expired.
 */
private const val SANE_TIMESTAMP_FLOOR_MS = 946_684_800_000L

@Serializable
data class BackupChatDisplayEvent(
    val messageId: Long,
    val segmentIndex: Int,
    val sessionId: String,
    val revealOrder: Long,
)

class MessageRepository(
    private val wrapper: DatabaseWrapper,
    private val settings: SettingsRepository? = null,
) {

    private val db: RhodesDatabase get() = wrapper.database
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val idMutex = Mutex()
    private var nextMessageId: Long? = null

    suspend fun getMessageById(id: Long): ChatMessage? = withContext(DatabaseDispatcher.dispatcher) {
        db.chatMessagesQueries.getMessageById(id) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsOneOrNull()
    }

    suspend fun countMessages(sessionId: String): Long = withContext(DatabaseDispatcher.dispatcher) {
        db.chatMessagesQueries.countMessagesBySession(sessionId).executeAsOne()
    }

    suspend fun getMessagesAfterCursor(sessionId: String, cursor: Long, restartAt: Long, limit: Long): List<ChatMessage> = withContext(DatabaseDispatcher.dispatcher) {
        db.chatMessagesQueries.getMessagesAfterCursor(sessionId, cursor, restartAt, limit) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList()
    }

    /** Replaced replies are committed only if the source still exists and has not changed. */
    suspend fun replaceReplyIfUnchanged(original: ChatMessage, replacement: String): Boolean = withContext(DatabaseDispatcher.dispatcher) {
        db.transactionWithResult {
            val current = db.chatMessagesQueries.getMessageById(original.id) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsOneOrNull()
            if (current?.sessionId != original.sessionId || current.content != original.content) return@transactionWithResult false
            db.chatMessagesQueries.replaceMessageContentIfUnchanged(replacement, original.id, original.content)
            db.chatSessionsQueries.updateLastMessageIfNotNewer(previewFromAiJson(replacement), original.timestamp, original.sessionId, original.timestamp)
            db.chatMessagesQueries.getMessageById(original.id) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsOneOrNull()?.content == replacement
        }
    }

    // --- Messages ---
    fun getMessages(sessionId: String): Flow<List<ChatMessage>> =
        db.chatMessagesQueries.getMessages(sessionId) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.asFlow().mapToList(DatabaseDispatcher.dispatcher)

    fun getRecentMessages(sessionId: String, limit: Long): Flow<List<ChatMessage>> =
        db.chatMessagesQueries.getRecentMessages(sessionId, limit) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.asFlow().mapToList(DatabaseDispatcher.dispatcher)

    suspend fun getMessagesSync(sessionId: String): List<ChatMessage> = withContext(DatabaseDispatcher.dispatcher) {
        db.chatMessagesQueries.getMessagesSync(sessionId) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList()
    }

    suspend fun getAllMessagesForBackup(): List<ChatMessage> = withContext(DatabaseDispatcher.dispatcher) {
        db.chatMessagesQueries.getAllMessagesForBackup { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList()
    }

    suspend fun getRecentMessagesSync(sessionId: String, limit: Long): List<ChatMessage> = withContext(DatabaseDispatcher.dispatcher) {
        db.chatMessagesQueries.getRecentMessages(sessionId, limit) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList()
    }

    suspend fun getMessagesBefore(sessionId: String, beforeTimestamp: Long, beforeId: Long, limit: Long): List<ChatMessage> = withContext(DatabaseDispatcher.dispatcher) {
        db.chatMessagesQueries.getMessagesBefore(sessionId, beforeTimestamp, beforeTimestamp, beforeId, limit) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList()
    }

    suspend fun updateMessageContent(id: Long, content: String) = withContext(DatabaseDispatcher.dispatcher) {
        db.chatMessagesQueries.updateContent(content, id)
    }

    suspend fun updateMessageType(id: Long, type: String) = withContext(DatabaseDispatcher.dispatcher) {
        db.chatMessagesQueries.updateType(type, id)
    }

    suspend fun updateMessageContentAndPreview(sessionId: String, id: Long, content: String, timestamp: Long) = withContext(DatabaseDispatcher.dispatcher) {
        db.chatMessagesQueries.updateContent(content, id)
        // Regenerating an older reply must not replace a newer chat-list preview or move the session backward.
        db.chatSessionsQueries.updateLastMessageIfNotNewer(previewFromAiJson(content), timestamp, sessionId, timestamp)
    }

    suspend fun getDisplayEvents(sessionId: String): List<ChatDisplayEvent> = withContext(DatabaseDispatcher.dispatcher) {
        db.chatDisplayEventsQueries.getDisplayEvents(sessionId) { messageId, segmentIndex, revealOrder ->
            ChatDisplayEvent(messageId, segmentIndex.toInt(), revealOrder)
        }.executeAsList()
    }

    suspend fun getAllDisplayEvents(): List<BackupChatDisplayEvent> = withContext(DatabaseDispatcher.dispatcher) {
        db.chatDisplayEventsQueries.getAllDisplayEvents { messageId, segmentIndex, sessionId, revealOrder ->
            BackupChatDisplayEvent(messageId, segmentIndex.toInt(), sessionId, revealOrder)
        }.executeAsList()
    }

    suspend fun addDisplayEventIfAbsent(sessionId: String, messageId: Long, segmentIndex: Int, revealedAt: Long = 0L): Long = idMutex.withLock {
        withContext(DatabaseDispatcher.dispatcher) {
            db.chatDisplayEventsQueries.getDisplayEventOrder(messageId, segmentIndex.toLong()).executeAsOneOrNull()?.let { return@withContext it }
            // Timestamp-based orders allow later-revealed bubbles to follow intervening user
            // messages. Existing small sequence orders remain valid legacy display events.
            val nextOrder = revealedAt.takeIf { it >= 1_000_000_000_000L }
                ?: Clock.System.now().toEpochMilliseconds()
            db.chatDisplayEventsQueries.insertDisplayEventIfAbsent(messageId, segmentIndex.toLong(), sessionId, nextOrder)
            db.chatDisplayEventsQueries.getDisplayEventOrder(messageId, segmentIndex.toLong()).executeAsOneOrNull() ?: nextOrder
        }
    }

    suspend fun deleteMessageDisplayEvents(messageId: Long) = withContext(DatabaseDispatcher.dispatcher) {
        db.chatDisplayEventsQueries.deleteMessageDisplayEvents(messageId)
    }

    suspend fun deleteDisplayEvent(messageId: Long, segmentIndex: Int) = withContext(DatabaseDispatcher.dispatcher) {
        db.chatDisplayEventsQueries.deleteDisplayEvent(messageId, segmentIndex.toLong())
    }

    suspend fun sendMessage(sessionId: String, message: ChatMessage) = idMutex.withLock { withContext(DatabaseDispatcher.dispatcher) {
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

    /** Atomically makes a user message visible and records its durable reply recovery turn. */
    suspend fun sendMessageAndCreateReplyTurn(sessionId: String, message: ChatMessage, turn: ReplyTurn) = idMutex.withLock {
        DatabaseDispatcher.execute("message_write_user") {
            val ts = if (message.timestamp > 0) message.timestamp else Clock.System.now().toEpochMilliseconds()
            db.transaction {
                nextMessageId = maxOf(nextMessageId ?: 0L, message.id + 1)
                db.chatMessagesQueries.insertMessage(
                    message.id, sessionId, message.senderId, message.senderName, message.content,
                    message.type, message.mode, message.emotion, message.activity, message.location,
                    message.narration, message.segmentGroup, message.intimacyChange.toLong(), ts,
                    if (message.isMe) 1L else 0L,
                )
                if (message.type != "system" && !message.content.startsWith("正在重新生成") && !message.content.startsWith("上下文超限")) {
                    val preview = when (message.type) {
                        "ai_json" -> previewFromAiJson(message.content)
                        "image" -> "[图片]"
                        "gift_hidden", "gift_reply_failed" -> previewFromGiftPayload(message.content)
                        else -> message.content.take(50)
                    }
                    db.chatSessionsQueries.updateLastMessageIfNotNewer(preview, ts, sessionId, ts)
                }
                db.replyTurnsQueries.insertReplyTurnIfAbsent(
                    turn.id, turn.sessionId, turn.surface, turn.triggerKind, turn.sourceMessageId,
                    turn.autoPlanToken, turn.mode, turn.nextAttemptAt, turn.createdAt, turn.updatedAt,
                )
            }
        }
    }

    /** A reply row is committed only together with the token-fenced turn completion. */
    suspend fun sendReplyAndCompleteTurn(sessionId: String, message: ChatMessage, turnId: String, leaseToken: String, now: Long): Boolean = idMutex.withLock {
        DatabaseDispatcher.execute("message_write_ai_reply") {
            var completed = false
            db.transaction {
                if (!db.replyTurnsQueries.isReplyTurnOwned(turnId, leaseToken).executeAsOne()) return@transaction
                val ts = if (message.timestamp > 0) message.timestamp else Clock.System.now().toEpochMilliseconds()
                nextMessageId = maxOf(nextMessageId ?: 0L, message.id + 1)
                db.chatMessagesQueries.insertMessage(
                    message.id, sessionId, message.senderId, message.senderName, message.content,
                    message.type, message.mode, message.emotion, message.activity, message.location,
                    message.narration, message.segmentGroup, message.intimacyChange.toLong(), ts,
                    if (message.isMe) 1L else 0L,
                )
                val preview = if (message.type == "ai_json") previewFromAiJson(message.content) else message.content.take(50)
                db.chatSessionsQueries.updateLastMessageIfNotNewer(preview, ts, sessionId, ts)
                db.replyTurnsQueries.completeReplyTurn(now, now, turnId, leaseToken)
                completed = true
            }
            completed
        }
    }

    /** Restores backups without allowing an old row ID to overwrite newer local content. */
    suspend fun restoreMessage(message: ChatMessage) = idMutex.withLock { withContext(DatabaseDispatcher.dispatcher) {
        nextMessageId = maxOf(nextMessageId ?: 0L, message.id + 1)
        rememberMaxUsedMessageId(message.id)
        val ts = if (message.timestamp > 0) message.timestamp else Clock.System.now().toEpochMilliseconds()
        db.chatMessagesQueries.insertMessageIfAbsent(
            message.id, message.sessionId, message.senderId, message.senderName, message.content,
            message.type, message.mode, message.emotion, message.activity, message.location,
            message.narration, message.segmentGroup, message.intimacyChange.toLong(), ts,
            if (message.isMe) 1L else 0L
        )
    } }

    /** Repairs previews written by older builds that could not parse otherwise valid AI payloads. */
    suspend fun repairAiSessionPreviews() = withContext(DatabaseDispatcher.dispatcher) {
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

    /**
     * Highest id ever handed out, persisted outside the database. Without it, deleting the newest rows
     * (recall / archive load / erase) makes `MAX(id) + 1` rewind after a restart, so a new message can
     * reuse an id whose reply_turns row still exists - which silently blocks its reply.
     */
    private fun persistedMaxUsedMessageId(): Long = settings?.getLong("max_used_message_id", 0L) ?: 0L

    private fun rememberMaxUsedMessageId(id: Long) {
        if (id > persistedMaxUsedMessageId()) settings?.putLong("max_used_message_id", id)
    }

    suspend fun getNextMessageId(): Long = idMutex.withLock {
        DatabaseDispatcher.execute("message_allocate_id") {
            val fromDatabase = (db.chatMessagesQueries.getMaxId().executeAsOne().MAX ?: 0L) + 1
            val fromPersisted = persistedMaxUsedMessageId() + 1
            val next = maxOf(nextMessageId ?: 0L, fromDatabase, fromPersisted)
            nextMessageId = next + 1
            rememberMaxUsedMessageId(next)
            next
        }
    }

    suspend fun deleteSessionMessages(sessionId: String) = withContext(DatabaseDispatcher.dispatcher) {
        db.chatDisplayEventsQueries.deleteSessionDisplayEvents(sessionId)
        db.chatMessagesQueries.deleteSessionMessages(sessionId)
        // Turns without their source messages are orphans and can block a reused message id later.
        db.replyTurnsQueries.deleteReplyTurnsBySession(sessionId)
    }

    suspend fun clearSessionPreview(sessionId: String, timestamp: Long = Clock.System.now().toEpochMilliseconds()) = withContext(DatabaseDispatcher.dispatcher) {
        db.chatSessionsQueries.updateLastMessage("", timestamp, sessionId)
    }

    suspend fun deleteMessage(id: Long) = withContext(DatabaseDispatcher.dispatcher) {
        db.chatDisplayEventsQueries.deleteMessageDisplayEvents(id)
        db.chatMessagesQueries.deleteMessage(id)
        // Keep reply_turns in sync with the message rows it points at, otherwise the orphaned turn can
        // reject the lease of a later message that reuses this id.
        db.replyTurnsQueries.deleteReplyTurnsBySourceMessageId(id)
    }
    suspend fun deleteOldMessages(cutoff: Long) = withContext(DatabaseDispatcher.dispatcher) {
        db.chatDisplayEventsQueries.deleteOldDisplayEvents(cutoff)
        db.chatMessagesQueries.deleteOldMessages(cutoff, SANE_TIMESTAMP_FLOOR_MS)
    }
    suspend fun getMessageCount(): Int = withContext(DatabaseDispatcher.dispatcher) { db.chatMessagesQueries.getMessageCount().executeAsOne().toInt() }

    suspend fun getMessageCountPerSender(): List<SenderCount> = withContext(DatabaseDispatcher.dispatcher) {
        db.chatMessagesQueries.getMessageCountPerSender().executeAsList().map { SenderCount(it.senderName, it.cnt) }
    }

    suspend fun getMessageCountPerSenderSince(since: Long): List<SenderCount> = withContext(DatabaseDispatcher.dispatcher) {
        db.chatMessagesQueries.getMessageCountPerSenderSince(since).executeAsList().map { SenderCount(it.senderName, it.cnt) }
    }

    suspend fun getMessagesInRange(start: Long, end: Long): List<ChatMessage> = withContext(DatabaseDispatcher.dispatcher) {
        db.chatMessagesQueries.getMessagesInRange(start, end) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList()
    }

    suspend fun searchMessagesInSession(sessionId: String, keyword: String, limit: Long = 200): List<ChatMessage> = withContext(DatabaseDispatcher.dispatcher) {
        val like = "%${keyword.replace("%", "\\%").replace("_", "\\_")}%"
        db.chatMessagesQueries.searchMessagesInSession(sessionId, like, limit) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList()
    }

    suspend fun getMessagesBySessionInRange(sessionId: String, start: Long, end: Long): List<ChatMessage> = withContext(DatabaseDispatcher.dispatcher) {
        db.chatMessagesQueries.getMessagesBySessionInRange(sessionId, start, end) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList()
    }

    suspend fun getMessageDatesBySession(sessionId: String): List<String> = withContext(DatabaseDispatcher.dispatcher) {
        db.chatMessagesQueries.getMessageDatesBySession(sessionId).executeAsList().mapNotNull { it as? String }
    }
}
