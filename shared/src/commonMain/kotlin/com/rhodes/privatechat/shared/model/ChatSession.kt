package com.rhodes.privatechat.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatSession(
    val id: String,
    val operatorId: String,
    val operatorName: String,
    val lastMessage: String = "",
    val lastTime: Long = 0L,
    val mode: String = "online",
    val isPinned: Boolean = false,
    val unreadCount: Int = 0,
    val members: String = "",
    val rules: String = "",
    val avatarUri: String = "",
    val mutedMembers: String = ""
)
