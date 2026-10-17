package com.rhodes.privatechat.shared.model

import kotlinx.serialization.Serializable

/** Payload of a newly scheduled interaction; never reconstructed from old user posts. */
@Serializable
data class MomentReplyTask(
    val momentId: Long,
    val operatorId: String,
    val speakerName: String,
    val userContent: String,
    val parentCommentId: Long,
    val userName: String,
    val customPrompt: String? = null,
    val mustInsert: Boolean = false,
    val sourceCommentId: Long? = null,
)
