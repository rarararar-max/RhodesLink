package com.rhodes.privatechat.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class WorldEvent(
    val id: Long = 0,
    val type: String,
    val actorId: String = "",
    val actorName: String = "",
    val targetId: String = "",
    val targetName: String = "",
    val source: String = "",
    val sourceId: String = "",
    val content: String,
    val createdAt: Long = 0L,
    val expiresAt: Long = Long.MAX_VALUE,
    val consumedBy: String = "",
    val originType: String = "",
    val chainDepth: Int = 0,
    val rootEventId: Long = 0L
)

object WorldEventType {
    const val STATUS_CHANGED = "status_changed"
    const val MOMENT_POSTED = "moment_posted"
    const val COMMENT_POSTED = "comment_posted"
    const val GROUP_TOPIC = "group_topic"
    const val PRIVATE_TRIGGER = "private_trigger"
    const val DIARY_WRITTEN = "diary_written"
    const val DISPATCH_EVENT = "dispatch_event"
    const val MAHJONG_EVENT = "mahjong_event"
    const val USER_INTERACTION = "user_interaction"
}
