package com.example.rhodesterminal.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "moments")
data class MomentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operatorId: String,
    val operatorName: String,
    val content: String,
    val isUserPost: Boolean = false,
    val mentionedOperatorIds: String = "",
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "moment_comments")
data class MomentCommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val momentId: Long,
    val operatorId: String,
    val operatorName: String,
    val content: String,
    val parentCommentId: Long = 0,
    val replyToName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Entity(tableName = "moment_likes")
data class MomentLikeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val momentId: Long,
    val operatorId: String,
    val operatorName: String,
    val createdAt: Long = System.currentTimeMillis()
)
