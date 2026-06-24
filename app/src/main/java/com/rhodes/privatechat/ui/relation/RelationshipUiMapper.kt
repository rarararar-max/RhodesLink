package com.rhodes.privatechat.ui.relation

import androidx.compose.ui.graphics.Color
import com.rhodes.privatechat.data.db.entity.RelationshipType

object RelationshipUiMapper {
    fun label(type: RelationshipType): String = when (type) {
        RelationshipType.BIG_SISTER -> "姐姐"
        RelationshipType.LITTLE_SISTER -> "妹妹"
        RelationshipType.BIG_BROTHER -> "哥哥"
        RelationshipType.LITTLE_BROTHER -> "弟弟"
        RelationshipType.MOTHER -> "母亲"
        RelationshipType.FATHER -> "父亲"
        RelationshipType.DAUGHTER -> "女儿"
        RelationshipType.SON -> "儿子"
        RelationshipType.GUARDIAN -> "监护人"
        RelationshipType.BOSS -> "上司"
        RelationshipType.SUBORDINATE -> "下属"
        RelationshipType.CAPTAIN -> "队长"
        RelationshipType.MEMBER -> "队员"
        RelationshipType.MENTOR -> "导师"
        RelationshipType.STUDENT -> "学生"
        RelationshipType.CLOSE_FRIEND -> "挚友"
        RelationshipType.FRIEND -> "朋友"
        RelationshipType.COMRADE -> "战友"
        RelationshipType.TEAMMATE -> "队友"
        RelationshipType.RIVAL -> "对手"
        RelationshipType.LOVE_RIVAL -> "情敌"
        RelationshipType.CRUSH -> "暗恋对象"
        RelationshipType.LOVER -> "恋人"
        RelationshipType.FAMILY -> "家人"
        else -> "陌生"
    }

    fun color(type: RelationshipType): Color = when (type) {
        RelationshipType.BIG_SISTER, RelationshipType.LITTLE_SISTER,
        RelationshipType.BIG_BROTHER, RelationshipType.LITTLE_BROTHER,
        RelationshipType.MOTHER, RelationshipType.FATHER,
        RelationshipType.DAUGHTER, RelationshipType.SON,
        RelationshipType.GUARDIAN, RelationshipType.FAMILY -> Color(0xFFE53935)
        RelationshipType.CLOSE_FRIEND, RelationshipType.FRIEND -> Color(0xFF00BCD4)
        RelationshipType.BOSS, RelationshipType.CAPTAIN -> Color(0xFFFFC107)
        RelationshipType.SUBORDINATE, RelationshipType.MEMBER -> Color(0xFFFFB74D)
        RelationshipType.MENTOR, RelationshipType.STUDENT -> Color(0xFF8B5CF6)
        RelationshipType.COMRADE, RelationshipType.TEAMMATE -> Color(0xFF9E9E9E)
        RelationshipType.RIVAL -> Color(0xFFEF4444)
        RelationshipType.LOVE_RIVAL -> Color(0xFFD81B60)
        RelationshipType.CRUSH -> Color(0xFFF48FB1)
        RelationshipType.LOVER -> Color(0xFFE91E63)
        else -> Color(0xFFBDBDBD)
    }

    fun description(operatorName: String, relatedName: String, type: RelationshipType): String =
        "${operatorName}把${relatedName}视为【${label(type)}】"
}
