package com.rhodes.privatechat.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatArchive(
    val id: String,
    val sessionId: String,
    val operatorId: String,
    val title: String,
    val note: String = "",
    val mode: String,
    val messagesJson: String,
    val summary: String = "",
    val stateJson: String = "",
    val status: String = STATUS_PENDING,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_READY = "ready"
        const val STATUS_FAILED = "failed"
    }
}

@Serializable
data class ChatHistorySegment(
    val id: String,
    val sessionId: String,
    val title: String,
    val reason: String,
    val messagesJson: String,
    val createdAt: Long,
)

@Serializable
data class ChatArchiveContext(
    val turnState: PrivateTurnState? = null,
    val previousSummary: String = "",
    val sourceMessagesJson: String = "",
)
