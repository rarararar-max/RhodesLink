package com.example.rhodesterminal.shared.db

import kotlin.Long
import kotlin.String

public data class Moments(
  public val id: Long,
  public val operatorId: String,
  public val operatorName: String,
  public val content: String,
  public val isUserPost: Long,
  public val mentionedOperatorIds: String,
  public val likeCount: Long,
  public val commentCount: Long,
  public val createdAt: Long,
)
