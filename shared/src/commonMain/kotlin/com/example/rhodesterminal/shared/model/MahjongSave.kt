package com.example.rhodesterminal.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class MahjongSave(
    val id: String = "current",
    val saveJson: String,
    val ruleType: String = "",
    val savedAt: Long = 0L
)
