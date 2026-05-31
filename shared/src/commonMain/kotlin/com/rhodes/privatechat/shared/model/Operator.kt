package com.rhodes.privatechat.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Operator(
    val id: String,
    val name: String,
    val title: String = "",
    val description: String = "",
    val avatarUri: String = "",
    val location: String = "宿舍",
    val activity: String = "休息",
    val emotion: String = "平静",
    val intimacy: Int = 0,
    val privatePrompt: String = "",
    val groupPrompt: String = "",
    val userRelation: String = "",
    val lmb: Int = 10000,
    val attack: Float = 0.5f,
    val defense: Float = 0.5f,
    val meldPref: String = "medium"
)
