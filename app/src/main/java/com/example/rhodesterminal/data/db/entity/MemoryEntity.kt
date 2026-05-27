package com.example.rhodesterminal.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MemoryType { SHORT_TERM, DAILY, LONG_TERM }
enum class AnchorType { PLAN, PREFERENCE, TABOO, EVENT, EMOTION, RELATION }

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val operatorId: String,
    val type: MemoryType,
    val content: String,
    val keywords: String = "",
    val preferences: String = "",
    val taboos: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = Long.MAX_VALUE
)

@Entity(tableName = "memory_anchors")
data class MemoryAnchorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val operatorId: String,
    val type: AnchorType,
    val content: String,
    val isPrivate: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000L
)
