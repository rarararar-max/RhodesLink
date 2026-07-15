package com.rhodes.privatechat.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Diary(
    val id: Long = 0,
    val operatorId: String,
    val operatorName: String,
    val content: String,
    val date: String,
    val version: Int = 1,
    val createdAt: Long = 0L
)
