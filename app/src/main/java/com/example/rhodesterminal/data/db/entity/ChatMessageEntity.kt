package com.example.rhodesterminal.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.compose.runtime.Immutable

@Immutable
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: Long,
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
    val timestamp: Long = System.currentTimeMillis(),
    val isMe: Boolean
)
