package com.example.rhodesterminal.shared.db

import kotlin.Long
import kotlin.String

public data class GetMessageCountPerSender(
  public val senderName: String,
  public val cnt: Long,
)
