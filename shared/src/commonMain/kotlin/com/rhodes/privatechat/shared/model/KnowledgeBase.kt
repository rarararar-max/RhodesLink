package com.rhodes.privatechat.shared.model

data class KnowledgeBase(
    val id: String,
    val name: String,
    val rawContent: String,
    val sourceFileName: String = "",
    val sourceFormat: String = "",
    val sourceType: String = "user",
    val chunkingMode: String = "smart",
    val indexStatus: String = "pending",
    val indexedEmbeddingSignature: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

data class KnowledgeBaseChunk(
    val id: String,
    val knowledgeBaseId: String,
    val ordinal: Int,
    val sourceHeading: String = "",
    val content: String,
    val userKeywords: String = "",
    val enabled: Boolean = true,
    val indexedAt: Long = 0L,
    val indexError: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

data class OperatorKnowledgeBaseAssignment(
    val operatorId: String,
    val knowledgeBaseId: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)
