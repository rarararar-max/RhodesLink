package com.rhodes.privatechat.shared.model

data class ReplyTurn(
    val id: String,
    val sessionId: String,
    val surface: String,
    val triggerKind: String,
    val sourceMessageId: Long?,
    val autoPlanToken: String,
    val mode: String,
    val status: String,
    val attemptCount: Int,
    val nextAttemptAt: Long,
    val leaseToken: String,
    val leaseUntil: Long,
    val responseMessageId: Long?,
    val lastError: String,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long,
)

object ReplyTurnStatus {
    const val PENDING = "pending"
    const val RUNNING = "running"
    const val SUCCEEDED = "succeeded"
    const val FAILED = "failed"
}
