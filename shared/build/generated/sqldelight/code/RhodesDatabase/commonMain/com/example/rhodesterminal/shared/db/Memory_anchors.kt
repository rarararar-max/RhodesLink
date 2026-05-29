package com.example.rhodesterminal.shared.db

import kotlin.Long
import kotlin.String

public data class Memory_anchors(
  public val id: Long,
  public val sessionId: String,
  public val operatorId: String,
  public val type: String,
  public val content: String,
  public val isPrivate: Long,
  public val createdAt: Long,
  public val expiresAt: Long,
)
