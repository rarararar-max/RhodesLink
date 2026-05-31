package com.rhodes.privatechat.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Memory(
    val id: Long = 0,
    val sessionId: String,
    val operatorId: String,
    val type: MemoryType,
    val content: String,
    val keywords: String = "",
    val preferences: String = "",
    val taboos: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = Long.MAX_VALUE
)

@Serializable
data class MemoryAnchor(
    val id: Long = 0,
    val sessionId: String,
    val operatorId: String,
    val type: AnchorType,
    val content: String,
    val isPrivate: Boolean = false,
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L
)
