package com.example.rhodesterminal.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RelationshipType {
    BIG_SISTER, LITTLE_SISTER, BIG_BROTHER, LITTLE_BROTHER,
    MOTHER, FATHER, DAUGHTER, SON,
    GUARDIAN,
    BOSS, SUBORDINATE, CAPTAIN, MEMBER,
    MENTOR, STUDENT,
    CLOSE_FRIEND, FRIEND, COMRADE, TEAMMATE, RIVAL,
    CRUSH, SIBLING, STRANGER,
    FAMILY
}

@Entity(tableName = "relationships")
data class RelationshipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operatorId: String,
    val relatedOperatorId: String,
    val relatedOperatorName: String,
    val type: RelationshipType,
    val intimacy: Int = 0,
    val isPreset: Boolean = false,
    val note: String = ""
)
