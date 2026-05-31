package com.rhodes.privatechat.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class MemoryType { SHORT_TERM, DAILY, LONG_TERM }

@Serializable
enum class AnchorType { PLAN, PREFERENCE, TABOO, EVENT, EMOTION, RELATION }

@Serializable
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
