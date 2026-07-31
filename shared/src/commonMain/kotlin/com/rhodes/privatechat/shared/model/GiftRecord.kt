package com.rhodes.privatechat.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class GiftRecord(
    val id: Long,
    val operatorId: String,
    val imageUri: String,
    val giftName: String,
    val senderName: String,
    val createdAt: Long
)
