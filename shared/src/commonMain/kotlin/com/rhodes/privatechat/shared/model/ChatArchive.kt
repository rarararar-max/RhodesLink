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

/** Kept separate from prompt history limits: saving more rounds must not grow every request. */
object ChatArchivePolicy {
    const val RAW_ROUNDS = 30
    fun completedRounds(messages: List<ChatMessage>): List<List<ChatMessage>> {
        val rounds = mutableListOf<MutableList<ChatMessage>>()
        messages.forEach { message ->
            if (message.isMe || rounds.isEmpty()) rounds.add(mutableListOf())
            rounds.last().add(message)
        }
        return rounds.filter { round -> round.any { it.isMe } && round.any { !it.isMe && it.type == "ai_json" } }
    }
    fun snapshot(messages: List<ChatMessage>): List<ChatMessage> = completedRounds(messages).takeLast(RAW_ROUNDS).flatten()
}
