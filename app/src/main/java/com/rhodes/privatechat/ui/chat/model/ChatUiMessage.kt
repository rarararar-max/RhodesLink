package com.rhodes.privatechat.ui.chat.model

import androidx.compose.ui.graphics.Color

/**
 * 统一的 UI 消息模型，私聊和群聊共用。
 * 从 ChatMessage（持久化模型）通过 MessageParser 转换而来。
 */
data class ChatUiMessage(
    val id: Long,
    val senderName: String,
    val senderColor: Color,
    val content: String,
    val timestamp: Long,
    val isMe: Boolean = false,
    val isSystem: Boolean = false,
    val isNarration: Boolean = false,
    val avatarUri: String = "",
    val emotion: String = "",
    val activity: String = "",
    val location: String = "",
    val mode: String = "online",
    val isArchived: Boolean = false,
    val imageUri: String = "",
    /** 原始 ChatMessage 的 id，用于撤回等操作 */
    val originalMessageId: Long = id,
    /** 在原始 JSON segments 数组中的索引，用于单条撤回；-1 表示非多段消息 */
    val segmentIndex: Int = -1,
)
