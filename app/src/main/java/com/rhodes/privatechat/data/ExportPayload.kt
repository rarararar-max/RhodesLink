package com.rhodes.privatechat.data

import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.shared.model.Relationship
import com.rhodes.privatechat.shared.model.RelationshipType
import com.rhodes.privatechat.shared.model.ChatSession
import com.rhodes.privatechat.shared.model.ChatMessage
import kotlinx.serialization.Serializable

@Serializable
data class ExportPayload(
    val version: Int = 1,
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val operators: List<OperatorExport>? = null,
    val relationships: List<RelationshipExport>? = null,
    val session: SessionExport? = null,
    val messages: List<MessageExport>? = null
)

@Serializable
data class OperatorExport(
    val id: String, val name: String, val title: String = "",
    val description: String = "", val avatarUri: String = "",
    val location: String = "宿舍", val activity: String = "休息",
    val emotion: String = "平静", val intimacy: Int = 0,
    val privatePrompt: String = "", val groupPrompt: String = "",
    val userRelation: String = "",
    val lmb: Int = 10000, val attack: Float = 0.5f,
    val defense: Float = 0.5f, val meldPref: String = "medium",
    val activityLevel: Float = 0.5f
) {
    fun toEntity() = Operator(id, name, title, description, avatarUri, location, activity, emotion, intimacy,
        privatePrompt, groupPrompt, userRelation, lmb, attack, defense, meldPref, activityLevel)
    companion object {
        fun fromEntity(e: Operator) = OperatorExport(e.id, e.name, e.title, e.description, e.avatarUri,
            e.location, e.activity, e.emotion, e.intimacy,
            e.privatePrompt, e.groupPrompt, e.userRelation, e.lmb, e.attack, e.defense, e.meldPref, e.activityLevel)
    }
}

@Serializable
data class RelationshipExport(
    val operatorId: String, val relatedOperatorId: String,
    val relatedOperatorName: String, val type: String,
    val intimacy: Int = 0, val isPreset: Boolean = false, val note: String = ""
) {
    fun toEntity() = Relationship(operatorId = operatorId, relatedOperatorId = relatedOperatorId,
        relatedOperatorName = relatedOperatorName, type = RelationshipType.valueOf(type),
        intimacy = intimacy, isPreset = isPreset, note = note)
    companion object {
        fun fromEntity(r: Relationship) = RelationshipExport(r.operatorId, r.relatedOperatorId,
            r.relatedOperatorName, r.type.name, r.intimacy, r.isPreset, r.note)
    }
}

@Serializable
data class SessionExport(
    val id: String, val operatorId: String, val operatorName: String,
    val lastMessage: String = "", val lastTime: Long = 0, val mode: String = "online",
    val isPinned: Boolean = false, val unreadCount: Int = 0,
    val members: String = "", val rules: String = "",
    val avatarUri: String = "", val mutedMembers: String = ""
) {
    fun toEntity() = ChatSession(id, operatorId, operatorName, lastMessage, lastTime, mode,
        isPinned = isPinned, unreadCount = unreadCount, members = members,
        rules = rules, avatarUri = avatarUri, mutedMembers = mutedMembers)
    companion object {
        fun fromEntity(s: ChatSession) = SessionExport(s.id, s.operatorId, s.operatorName, s.lastMessage, s.lastTime, s.mode,
            s.isPinned, s.unreadCount, s.members, s.rules, s.avatarUri, s.mutedMembers)
    }
}

@Serializable
data class MessageExport(
    val id: Long, val sessionId: String, val senderId: String = "",
    val senderName: String, val content: String, val type: String = "text",
    val mode: String = "online", val emotion: String = "", val activity: String = "",
    val location: String = "", val narration: String = "", val segmentGroup: String = "",
    val intimacyChange: Int = 0, val timestamp: Long = 0, val isMe: Boolean
) {
    fun toEntity() = ChatMessage(id, sessionId, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe)
    companion object {
        fun fromEntity(m: ChatMessage) = MessageExport(m.id, m.sessionId, m.senderId, m.senderName, m.content, m.type, m.mode, m.emotion, m.activity, m.location, m.narration, m.segmentGroup, m.intimacyChange, m.timestamp, m.isMe)
    }
}
