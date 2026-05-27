package com.example.rhodesterminal.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dispatch_records")
data class DispatchRecordEntity(
    @PrimaryKey val id: String,
    val taskType: String,
    val durationHours: Int,
    val budget: Int,
    val netProfit: Int = 0,
    val operatorIds: String = "",
    val logChain: String = "",        // JSON 数组，新版一次性生成
    val status: String = "active",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = 0,
    val totalSegments: Int = 0,
    val segmentInterval: Long = 0,    // 每段间隔(毫秒)
    val items: String = ""            // JSON 数组 ["物品1","物品2"]
)
