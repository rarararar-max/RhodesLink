package com.example.rhodesterminal.shared.db

import kotlin.Long
import kotlin.String

public data class Dispatch_records(
  public val id: String,
  public val taskType: String,
  public val durationHours: Long,
  public val budget: Long,
  public val netProfit: Long,
  public val operatorIds: String,
  public val logChain: String,
  public val status: String,
  public val startTime: Long,
  public val endTime: Long,
  public val totalSegments: Long,
  public val segmentInterval: Long,
  public val items: String,
)
