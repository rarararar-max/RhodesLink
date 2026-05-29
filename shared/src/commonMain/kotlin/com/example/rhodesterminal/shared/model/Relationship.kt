package com.example.rhodesterminal.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Relationship(
    val id: Long = 0,
    val operatorId: String,
    val relatedOperatorId: String,
    val relatedOperatorName: String,
    val type: RelationshipType,
    val intimacy: Int = 0,
    val isPreset: Boolean = false,
    val note: String = ""
)
