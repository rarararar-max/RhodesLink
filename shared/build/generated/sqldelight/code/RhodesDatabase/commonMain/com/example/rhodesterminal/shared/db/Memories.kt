package com.example.rhodesterminal.shared.db

import kotlin.Long
import kotlin.String

public data class Memories(
  public val id: Long,
  public val sessionId: String,
  public val operatorId: String,
  public val type: String,
  public val content: String,
  public val keywords: String,
  public val preferences: String,
  public val taboos: String,
  public val createdAt: Long,
  public val expiresAt: Long,
)
