package com.example.rhodesterminal.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mahjong_saves")
data class MahjongSaveEntity(
    @PrimaryKey val id: String = "current",
    val saveJson: String,
    val ruleType: String = "",
    val savedAt: Long = System.currentTimeMillis()
)
