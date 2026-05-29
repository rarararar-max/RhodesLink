package com.example.rhodesterminal.shared.db

import kotlin.Long
import kotlin.String

public data class Moment_comments(
  public val id: Long,
  public val momentId: Long,
  public val operatorId: String,
  public val operatorName: String,
  public val content: String,
  public val parentCommentId: Long,
  public val replyToName: String,
  public val createdAt: Long,
  public val isRead: Long,
)
