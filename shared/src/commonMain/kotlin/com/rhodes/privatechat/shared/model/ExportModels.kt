package com.rhodes.privatechat.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportPayload(
    val version: Int = 1,
    val type: String,
    val timestamp: Long = 0L,
    val operators: List<OperatorExport>? = null,
    val relationships: List<RelationshipExport>? = null,
    val session: SessionExport? = null,
    val messages: List<MessageExport>? = null
)

@Serializable
data class OperatorExport(
    val id: String,
    val name: String,
    val title: String = "",
    val description: String = "",
    val avatarUri: String = "",
    val location: String = "宿舍",
    val activity: String = "休息",
    val emotion: String = "平静",
    val intimacy: Int = 0,
    val privatePrompt: String = "",
    val groupPrompt: String = "",
    val userRelation: String = "",
    val lmb: Int = 10000,
    val attack: Float = 0.5f,
    val defense: Float = 0.5f,
    val meldPref: String = "medium"
)

@Serializable
data class RelationshipExport(
    val operatorId: String,
    val relatedOperatorId: String,
    val relatedOperatorName: String,
    val type: String,
    val intimacy: Int = 0,
    val isPreset: Boolean = false,
    val note: String = ""
)

@Serializable
data class SessionExport(
    val id: String,
    val operatorId: String,
    val operatorName: String,
    val lastMessage: String = "",
    val lastTime: Long = 0,
    val mode: String = "online",
    val isPinned: Boolean = false,
    val unreadCount: Int = 0,
    val members: String = "",
    val rules: String = "",
    val avatarUri: String = "",
    val mutedMembers: String = ""
)

@Serializable
data class MessageExport(
    val id: Long,
    val sessionId: String,
    val senderId: String = "",
    val senderName: String,
    val content: String,
    val type: String = "text",
    val mode: String = "online",
    val emotion: String = "",
    val activity: String = "",
    val location: String = "",
    val narration: String = "",
    val segmentGroup: String = "",
    val intimacyChange: Int = 0,
    val timestamp: Long = 0,
    val isMe: Boolean
)
