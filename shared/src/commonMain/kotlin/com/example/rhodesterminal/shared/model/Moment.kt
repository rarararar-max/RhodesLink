package com.example.rhodesterminal.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Moment(
    val id: Long = 0,
    val operatorId: String,
    val operatorName: String,
    val content: String,
    val isUserPost: Boolean = false,
    val mentionedOperatorIds: String = "",
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val createdAt: Long = 0L
)

@Serializable
data class MomentComment(
    val id: Long = 0,
    val momentId: Long,
    val operatorId: String,
    val operatorName: String,
    val content: String,
    val parentCommentId: Long = 0,
    val replyToName: String = "",
    val createdAt: Long = 0L,
    val isRead: Boolean = false
)

@Serializable
data class MomentLike(
    val id: Long = 0,
    val momentId: Long,
    val operatorId: String,
    val operatorName: String,
    val createdAt: Long = 0L
)
