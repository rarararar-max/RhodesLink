package com.rhodes.privatechat.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
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
    val timestamp: Long = 0L,
    val isMe: Boolean
)
