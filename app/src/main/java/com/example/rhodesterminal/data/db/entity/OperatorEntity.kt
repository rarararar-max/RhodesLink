package com.example.rhodesterminal.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.compose.runtime.Immutable

@Immutable
@Entity(tableName = "operators")
data class OperatorEntity(
    @PrimaryKey val id: String,
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
