package com.example.rhodesterminal.shared.db

import kotlin.Long
import kotlin.String

public data class Diaries(
  public val id: Long,
  public val operatorId: String,
  public val operatorName: String,
  public val content: String,
  public val date: String,
  public val createdAt: Long,
)
