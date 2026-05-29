package com.example.rhodesterminal.shared.db

import kotlin.Double
import kotlin.Long
import kotlin.String

public data class Operators(
  public val id: String,
  public val name: String,
  public val title: String,
  public val description: String,
  public val avatarUri: String,
  public val location: String,
  public val activity: String,
  public val emotion: String,
  public val intimacy: Long,
  public val privatePrompt: String,
  public val groupPrompt: String,
  public val userRelation: String,
  public val lmb: Long,
  public val attack: Double,
  public val defense: Double,
  public val meldPref: String,
)
