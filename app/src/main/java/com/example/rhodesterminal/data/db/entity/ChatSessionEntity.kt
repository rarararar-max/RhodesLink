package com.example.rhodesterminal.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val operatorId: String,
    val operatorName: String,
    val lastMessage: String = "",
    val lastTime: Long = System.currentTimeMillis(),
    val mode: String = "online",
    val isPinned: Boolean = false,
    val unreadCount: Int = 0,
    val members: String = "",
    val rules: String = "",
    val avatarUri: String = "",
    val mutedMembers: String = ""
)
