package com.rhodes.privatechat.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class DispatchRecord(
    val id: String,
    val taskType: String,
    val durationHours: Int,
    val budget: Int,
    val netProfit: Int = 0,
    val operatorIds: String = "",
    val logChain: String = "",
    val status: String = "active",
    val startTime: Long = 0L,
    val endTime: Long = 0,
    val totalSegments: Int = 0,
    val segmentInterval: Long = 0,
    val items: String = ""
)
