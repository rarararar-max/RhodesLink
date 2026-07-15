package com.rhodes.privatechat.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class MemoryItem(
    val id: Long = 0,
    val ownerType: String,
    val ownerId: String,
    val memoryLevel: MemoryLevel,
    val memoryType: String,
    val sourceKind: MemorySourceKind,
    val sourceRefId: String = "",
    val sessionId: String = "",
    val content: String,
    val nickname: String = "",
    val importance: Int = 0,
    val privacy: String? = null,
    val unmetNeed: Boolean = false,
    val location: String? = null,
    val emotionValence: String = "neutral",
    val eventTime: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val expiresAt: Long = Long.MAX_VALUE,
    val status: String = "active",
    val scheduledTime: String? = null,
    val action: String = "",
    val careType: String = "",
    val topicKey: String = "",
    val sourceActor: String = "",
    val sourceTarget: String = "",
    val lastUsedAt: Long = 0L,
    val usedCount: Int = 0,
    val confidence: Double = 0.8,
    val rawJson: String = "",
    val vectorId: String = ""
)

@Serializable
data class MemoryBatch(
    val id: Long = 0,
    val ownerType: String,
    val ownerId: String,
    val sourceKind: MemorySourceKind,
    val targetLevel: MemoryLevel,
    val inputCount: Int,
    val outputCount: Int,
    val windowStart: Long,
    val windowEnd: Long,
    val promptVersion: String = "v1",
    val status: String = "done",
    val createdAt: Long = 0L
)

@Serializable
data class MemoryLink(
    val id: Long = 0,
    val parentMemoryId: Long,
    val childMemoryId: Long,
    val linkType: String = "merge",
    val createdAt: Long = 0L
)

@Serializable
data class MemorySourceItem(
    val id: Long = 0,
    val sourceKind: MemorySourceKind,
    val ownerType: String,
    val ownerId: String,
    val sourceRefId: String,
    val contentText: String,
    val timestamp: Long,
    val processedL1: Boolean = false,
    val processedVector: Boolean = false,
    val createdAt: Long = 0L
)

@Serializable
data class SharedExperience(
    val id: Long = 0,
    val sourceKind: String,
    val sourceRefId: String,
    val groupId: String,
    val content: String,
    val importance: Int = 50,
    val status: String = "active",
    val createdAt: Long = 0L,
    val expiresAt: Long = Long.MAX_VALUE,
)
