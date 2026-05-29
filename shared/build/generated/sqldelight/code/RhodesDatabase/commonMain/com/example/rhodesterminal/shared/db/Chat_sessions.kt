package com.example.rhodesterminal.shared.db

import kotlin.Long
import kotlin.String

public data class Chat_sessions(
  public val id: String,
  public val operatorId: String,
  public val operatorName: String,
  public val lastMessage: String,
  public val lastTime: Long,
  public val mode: String,
  public val isPinned: Long,
  public val unreadCount: Long,
  public val members: String,
  public val rules: String,
  public val avatarUri: String,
  public val mutedMembers: String,
)
