package com.example.rhodesterminal.shared.db

import kotlin.Long
import kotlin.String

public data class Moment_likes(
  public val id: Long,
  public val momentId: Long,
  public val operatorId: String,
  public val operatorName: String,
  public val createdAt: Long,
)
