package com.example.rhodesterminal.shared.db

import kotlin.Long
import kotlin.String

public data class Mahjong_saves(
  public val id: String,
  public val saveJson: String,
  public val ruleType: String,
  public val savedAt: Long,
)
