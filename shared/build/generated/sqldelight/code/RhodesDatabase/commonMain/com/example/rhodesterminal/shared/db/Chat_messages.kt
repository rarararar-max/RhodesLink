package com.example.rhodesterminal.shared.db

import kotlin.Long
import kotlin.String

public data class Chat_messages(
  public val id: Long,
  public val sessionId: String,
  public val senderId: String,
  public val senderName: String,
  public val content: String,
  public val type: String,
  public val mode: String,
  public val emotion: String,
  public val activity: String,
  public val location: String,
  public val narration: String,
  public val segmentGroup: String,
  public val intimacyChange: Long,
  public val timestamp: Long,
  public val isMe: Long,
)
