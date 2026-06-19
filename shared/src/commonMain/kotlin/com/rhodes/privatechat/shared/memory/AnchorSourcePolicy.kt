package com.rhodes.privatechat.shared.memory

import com.rhodes.privatechat.shared.model.AnchorType
import com.rhodes.privatechat.shared.model.MemoryAnchor

object AnchorSourcePolicy {
    const val PRIVATE_CHAT = "private_chat"
    const val GROUP_CHAT = "group_chat"
    const val MOMENT = "moment"
    const val COMMENT = "comment"
    const val DIARY = "diary"
    const val DISPATCH = "dispatch"
    const val MAHJONG = "mahjong"
    const val STATUS = "status"
    const val LEGACY = "legacy"

    const val STRONG = "strong"
    const val MEDIUM = "medium"
    const val WEAK = "weak"

    fun buildAnchor(
        source: String,
        sourceName: String,
        sourceActor: String,
        sourceTarget: String,
        operatorId: String,
        type: AnchorType,
        content: String,
        importance: String,
        sessionId: String,
        isPrivate: Boolean = false,
        createdAt: Long = 0L,
        expiresAt: Long = Long.MAX_VALUE
    ): MemoryAnchor {
        val actualCreatedAt = if (createdAt > 0L) createdAt else currentTime()
        return MemoryAnchor(
            sessionId = sessionId,
            operatorId = operatorId,
            type = type,
            content = content.trim(),
            isPrivate = isPrivate,
            createdAt = actualCreatedAt,
            expiresAt = expiresAt,
            source = source,
            sourceName = sourceName,
            sourceActor = sourceActor,
            sourceTarget = sourceTarget,
            importance = normalizeImportance(importance),
            knownFrom = knownFrom(source, sourceName, sourceActor)
        )
    }

    fun inferLegacy(anchor: MemoryAnchor): MemoryAnchor {
        if (anchor.source.isNotBlank() && anchor.knownFrom.isNotBlank()) return anchor
        val inferredSource = when {
            anchor.content.contains("[私聊]") -> PRIVATE_CHAT
            anchor.content.contains("[群聊") -> GROUP_CHAT
            anchor.content.contains("[动态]") -> MOMENT
            anchor.content.contains("[评论]") -> COMMENT
            anchor.content.contains("[日记]") -> DIARY
            anchor.content.contains("派遣") || anchor.sessionId.startsWith("dispatch") -> DISPATCH
            anchor.content.contains("麻将") || anchor.sessionId.contains("mahjong") -> MAHJONG
            anchor.sessionId.startsWith("nearby_") -> STATUS
            else -> LEGACY
        }
        val inferredImportance = when {
            anchor.importance.isNotBlank() -> anchor.importance
            anchor.content.contains("[强]") -> STRONG
            anchor.content.contains("[中]") -> MEDIUM
            anchor.content.contains("[弱]") -> WEAK
            else -> MEDIUM
        }
        val cleanContent = anchor.content
            .replace(Regex("""\[[^\]]+]"""), "")
            .trim(' ', '，', '。', ',', ';', '；')
        return anchor.copy(
            source = anchor.source.ifBlank { inferredSource },
            importance = normalizeImportance(inferredImportance),
            content = cleanContent.ifBlank { anchor.content },
            knownFrom = anchor.knownFrom.ifBlank { knownFrom(inferredSource, anchor.sourceName, anchor.sourceActor) }
        )
    }

    fun knownFrom(source: String, sourceName: String, sourceActor: String): String {
        val name = sourceName.ifBlank { sourceActor }
        return when (source) {
            PRIVATE_CHAT -> if (name.isNotBlank()) "你是在${name}中知道的" else "你是在私聊中知道的"
            GROUP_CHAT -> if (name.isNotBlank()) "你是在「$name」里听到的" else "你是在群聊里听到的"
            MOMENT -> if (name.isNotBlank()) "你是从${name}看到的" else "你是从动态里看到的"
            COMMENT -> if (name.isNotBlank()) "你是从${name}的评论区看到的" else "你是从评论区看到的"
            DIARY -> "这是你自己在日记里整理出的想法"
            DISPATCH -> if (name.isNotBlank()) "这是${name}留下的任务记录" else "这是派遣任务留下的记录"
            MAHJONG -> "这是麻将对局后留下的记忆"
            STATUS -> "这是你在罗德岛日常活动中注意到的"
            else -> "这是你过去记住的事"
        }
    }

    fun toPromptLine(anchor: MemoryAnchor): String {
        val normalized = inferLegacy(anchor)
        val prefix = normalized.knownFrom.ifBlank { knownFrom(normalized.source, normalized.sourceName, normalized.sourceActor) }
        return "- $prefix：${normalized.content}"
    }

    fun normalizeImportance(value: String): String = when (value.lowercase()) {
        "强", STRONG -> STRONG
        "弱", WEAK -> WEAK
        "中", MEDIUM -> MEDIUM
        else -> MEDIUM
    }

    private fun currentTime(): Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
}
