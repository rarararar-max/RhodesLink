package com.example.rhodesterminal.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diaries")
data class DiaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operatorId: String,
    val operatorName: String,
    val content: String,
    val date: String,
    val createdAt: Long = System.currentTimeMillis()
)
