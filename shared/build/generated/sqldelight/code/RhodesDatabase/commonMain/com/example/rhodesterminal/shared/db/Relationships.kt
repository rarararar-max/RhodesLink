package com.example.rhodesterminal.shared.db

import kotlin.Long
import kotlin.String

public data class Relationships(
  public val id: Long,
  public val operatorId: String,
  public val relatedOperatorId: String,
  public val relatedOperatorName: String,
  public val type: String,
  public val intimacy: Long,
  public val isPreset: Long,
  public val note: String,
)
