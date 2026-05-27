package com.example.rhodesterminal.data.db

import androidx.room.TypeConverter
import com.example.rhodesterminal.data.db.entity.AnchorType
import com.example.rhodesterminal.data.db.entity.MemoryType
import com.example.rhodesterminal.data.db.entity.RelationshipType

class Converters {
    @TypeConverter
    fun fromMemoryType(value: MemoryType): String = value.name

    @TypeConverter
    fun toMemoryType(value: String): MemoryType = MemoryType.valueOf(value)

    @TypeConverter
    fun fromAnchorType(value: AnchorType): String = value.name

    @TypeConverter
    fun toAnchorType(value: String): AnchorType = AnchorType.valueOf(value)

    @TypeConverter
    fun fromRelationshipType(value: RelationshipType): String = value.name

    @TypeConverter
    fun toRelationshipType(value: String): RelationshipType = RelationshipType.valueOf(value)
}
