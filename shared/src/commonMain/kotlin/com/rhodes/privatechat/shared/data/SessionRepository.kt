package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.*
import kotlinx.coroutines.Dispatchers
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class SessionRepository(private val wrapper: DatabaseWrapper) {

    private val db: RhodesDatabase get() = wrapper.database
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // --- Sessions ---
    val allSessions: Flow<List<ChatSession>> =
        db.chatSessionsQueries.getAllSessions { id, operatorId, operatorName, lastMessage, lastTime, mode, isPinned, unreadCount, members, rules, avatarUri, mutedMembers ->
            ChatSession(id, operatorId, operatorName, lastMessage, lastTime, mode, isPinned != 0L, unreadCount.toInt(), members, rules, avatarUri, mutedMembers)
        }.asFlow().mapToList(Dispatchers.Default)

    suspend fun getOrCreateSession(operatorId: String, operatorName: String, avatarUri: String = ""): ChatSession = withContext(Dispatchers.Default) {
        val existing = db.chatSessionsQueries.getSessionByOperator(operatorId) { id, opId, opName, lastMsg, lastTime, mode, isPinned, unreadCount, members, rules, avatar, muted ->
            ChatSession(id, opId, opName, lastMsg, lastTime, mode, isPinned != 0L, unreadCount.toInt(), members, rules, avatar, muted)
        }.executeAsOneOrNull()
        if (existing != null) {
            if (avatarUri.isNotBlank() && existing.avatarUri != avatarUri) {
                db.chatSessionsQueries.insertSession(existing.id, existing.operatorId, existing.operatorName, existing.lastMessage, existing.lastTime, existing.mode, if (existing.isPinned) 1L else 0L, existing.unreadCount.toLong(), existing.members, existing.rules, avatarUri, existing.mutedMembers)
            }
            return@withContext existing
        }
        val sessionId = "session_$operatorId"
        val now = Clock.System.now().toEpochMilliseconds()
        db.chatSessionsQueries.insertSession(sessionId, operatorId, operatorName, "", now, "online", 0, 0, "", "", avatarUri, "")
        ChatSession(id = sessionId, operatorId = operatorId, operatorName = operatorName, avatarUri = avatarUri, lastTime = now)
    }

    suspend fun getSession(id: String): ChatSession? = withContext(Dispatchers.Default) {
        db.chatSessionsQueries.getSession(id) { id_, opId, opName, lastMsg, lastTime, mode, isPinned, unreadCount, members, rules, avatar, muted ->
            ChatSession(id_, opId, opName, lastMsg, lastTime, mode, isPinned != 0L, unreadCount.toInt(), members, rules, avatar, muted)
        }.executeAsOneOrNull()
    }

    suspend fun insertSession(session: ChatSession) = withContext(Dispatchers.Default) {
        db.chatSessionsQueries.insertSession(session.id, session.operatorId, session.operatorName, session.lastMessage, session.lastTime, session.mode, if (session.isPinned) 1L else 0L, session.unreadCount.toLong(), session.members, session.rules, session.avatarUri, session.mutedMembers)
    }

    suspend fun deleteSession(id: String) = withContext(Dispatchers.Default) { db.chatSessionsQueries.deleteSession(id) }

    suspend fun updateSessionMode(sessionId: String, mode: String) = withContext(Dispatchers.Default) {
        db.chatSessionsQueries.updateMode(mode, sessionId)
    }

    suspend fun markAllRead() = withContext(Dispatchers.Default) { db.chatSessionsQueries.markAllRead() }
    suspend fun getSessionCount(): Int = withContext(Dispatchers.Default) { db.chatSessionsQueries.getSessionCount().executeAsOne().toInt() }
    suspend fun getGroupCount(): Int = withContext(Dispatchers.Default) { db.chatSessionsQueries.getGroupCount().executeAsOne().toInt() }

    suspend fun updateLastMessage(sessionId: String, lastMessage: String, lastTime: Long) = withContext(Dispatchers.Default) {
        db.chatSessionsQueries.updateLastMessage(lastMessage, lastTime, sessionId)
    }

    suspend fun getSessionByOperator(operatorId: String): ChatSession? = withContext(Dispatchers.Default) {
        db.chatSessionsQueries.getSessionByOperator(operatorId) { id, opId, opName, lastMsg, lastTime, mode, isPinned, unreadCount, members, rules, avatar, muted ->
            ChatSession(id, opId, opName, lastMsg, lastTime, mode, isPinned != 0L, unreadCount.toInt(), members, rules, avatar, muted)
        }.executeAsOneOrNull()
    }

    suspend fun getLastUserMessageTime(sessionId: String): Long? = withContext(Dispatchers.Default) {
        val msgs = db.chatMessagesQueries.getMessagesSync(sessionId) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList()
        msgs.filter { it.isMe }.maxOfOrNull { it.timestamp }
    }

    // --- Preset groups ---
    suspend fun initPresetGroups() = withContext(Dispatchers.Default) {
        val count = db.chatSessionsQueries.getGroupCount().executeAsOne().toInt()
        if (count > 0) return@withContext
        val now = Clock.System.now().toEpochMilliseconds()
        val groups = listOf(
            ChatSession(id = "group_elite", operatorId = "group_elite", operatorName = "罗德岛精英干员", lastMessage = "欢迎加入", members = "amiya,blaze,rosmontis,kaltsit,exusiai"),
            ChatSession(id = "group_logistics", operatorId = "group_logistics", operatorName = "企鹅物流", lastMessage = "欢迎加入", members = "exusiai,texas,angelina"),
            ChatSession(id = "group_medical", operatorId = "group_medical", operatorName = "医疗组", lastMessage = "欢迎加入", members = "kaltsit,nightingale,shining,ifrit,saria")
        )
        var msgId = 1L
        groups.forEach { g ->
            db.chatSessionsQueries.insertSession(g.id, g.operatorId, g.operatorName, g.lastMessage, now, g.mode, 0, 0, g.members, g.rules, g.avatarUri, g.mutedMembers)
            db.chatMessagesQueries.insertMessage(msgId++, g.id, "", "系统", "欢迎加入群聊", "system", "online", "", "", "", "", "", 0, now, 0)
        }
    }

    // --- Private chat context helpers ---
    suspend fun getPrivateChatSummary(operatorId: String): String? = withContext(Dispatchers.Default) {
        val session = db.chatSessionsQueries.getSessionByOperator(operatorId) { id, opId, opName, lastMsg, lastTime, mode, isPinned, unreadCount, members, rules, avatar, muted ->
            ChatSession(id, opId, opName, lastMsg, lastTime, mode, isPinned != 0L, unreadCount.toInt(), members, rules, avatar, muted)
        }.executeAsOneOrNull() ?: return@withContext null
        val recentMsgs = db.chatMessagesQueries.getMessagesSync(session.id) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList().takeLast(5)
        if (recentMsgs.isEmpty()) return@withContext null
        recentMsgs.joinToString("\n") { "${if (it.isMe) "博士" else it.senderName}：${it.content.take(80)}" }
    }

    suspend fun getPrivateChatContext(operatorId: String): String? = withContext(Dispatchers.Default) {
        val session = db.chatSessionsQueries.getSessionByOperator(operatorId) { id, opId, opName, lastMsg, lastTime, mode, isPinned, unreadCount, members, rules, avatar, muted ->
            ChatSession(id, opId, opName, lastMsg, lastTime, mode, isPinned != 0L, unreadCount.toInt(), members, rules, avatar, muted)
        }.executeAsOneOrNull() ?: return@withContext null
        val impression = db.memoriesQueries.getLatestLongTermImpression(operatorId) { id, sid, opId, type, content, keywords, preferences, taboos, createdAt, expiresAt ->
            Memory(id, sid, opId, try { MemoryType.valueOf(type) } catch (_: Exception) { MemoryType.LONG_TERM }, content, keywords, preferences, taboos, createdAt, expiresAt)
        }.executeAsOneOrNull()?.content?.take(100)
        val recentMsgs = db.chatMessagesQueries.getMessagesSync(session.id) { id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe ->
            ChatMessage(id, sid, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange.toInt(), timestamp, isMe != 0L)
        }.executeAsList().takeLast(2)
        if (recentMsgs.isEmpty() && impression == null) return@withContext null
        val lines = mutableListOf<String>()
        if (impression != null) lines.add("印象：$impression")
        for (m in recentMsgs) {
            val name = if (m.isMe) "博士" else m.senderName
            val text = if (m.type == "ai_json") {
                try {
                    val obj = json.parseToJsonElement(m.content) as? kotlinx.serialization.json.JsonObject
                    val segs = obj?.get("segments") as? kotlinx.serialization.json.JsonArray
                    if (segs != null) {
                        segs.mapNotNull { seg ->
                            val segObj = seg as? kotlinx.serialization.json.JsonObject
                            if ((segObj?.get("type") as? kotlinx.serialization.json.JsonPrimitive)?.content == "dialogue")
                                (segObj["content"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                            else null
                        }.joinToString(" ")
                    } else m.content.take(80)
                } catch (_: Exception) { m.content.take(80) }
            } else m.content.take(80)
            lines.add("$name：$text")
        }
        lines.joinToString("\n")
    }
}
