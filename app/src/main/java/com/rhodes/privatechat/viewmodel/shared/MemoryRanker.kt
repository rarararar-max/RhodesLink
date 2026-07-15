package com.rhodes.privatechat.viewmodel.shared

import com.rhodes.privatechat.shared.model.AnchorType
import com.rhodes.privatechat.shared.model.MemoryAnchor
import com.rhodes.privatechat.shared.memory.AnchorSourcePolicy

object MemoryRanker {
    fun pick(
        anchors: List<MemoryAnchor>,
        maxCount: Int,
        surface: MemorySurface,
        userContent: String = ""
    ): List<MemoryAnchor> {
        if (maxCount <= 0 || anchors.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        return anchors
            .map { AnchorSourcePolicy.inferLegacy(it) }
            .filter { it.content.isNotBlank() && !isWeakContent(it) && isAllowedForSurface(it, surface) }
            .distinctBy { it.type to normalize(it.content) }
            .sortedWith(compareByDescending<MemoryAnchor> { score(it, surface, userContent, now) }.thenByDescending { it.createdAt })
            .take(maxCount)
    }

    private fun isAllowedForSurface(anchor: MemoryAnchor, surface: MemorySurface): Boolean {
        if (surface == MemorySurface.PRIVATE_CHAT) return true
        if (anchor.isPrivate || anchor.source == AnchorSourcePolicy.PRIVATE_CHAT) return false
        return when (surface) {
            MemorySurface.PRIVATE_CHAT -> true
            MemorySurface.GROUP_CHAT -> anchor.source in setOf(
                AnchorSourcePolicy.GROUP_CHAT,
                AnchorSourcePolicy.MOMENT,
                AnchorSourcePolicy.COMMENT,
                AnchorSourcePolicy.STATUS,
                AnchorSourcePolicy.DISPATCH,
                AnchorSourcePolicy.MAHJONG
            )
            MemorySurface.MOMENT -> anchor.source in setOf(
                AnchorSourcePolicy.MOMENT,
                AnchorSourcePolicy.COMMENT,
                AnchorSourcePolicy.STATUS,
                AnchorSourcePolicy.DISPATCH
            )
            MemorySurface.COMMENT -> anchor.source in setOf(
                AnchorSourcePolicy.MOMENT,
                AnchorSourcePolicy.COMMENT,
                AnchorSourcePolicy.STATUS,
                AnchorSourcePolicy.DISPATCH
            )
            MemorySurface.DIARY -> anchor.source in setOf(
                AnchorSourcePolicy.GROUP_CHAT,
                AnchorSourcePolicy.MOMENT,
                AnchorSourcePolicy.COMMENT,
                AnchorSourcePolicy.DIARY,
                AnchorSourcePolicy.STATUS,
                AnchorSourcePolicy.DISPATCH,
                AnchorSourcePolicy.MAHJONG
            )
        }
    }

    private fun score(anchor: MemoryAnchor, surface: MemorySurface, userContent: String, now: Long): Int {
        val normalized = anchor
        val sourceScore = when {
            normalized.source == AnchorSourcePolicy.PRIVATE_CHAT -> if (surface == MemorySurface.PRIVATE_CHAT) 45 else 20
            normalized.source == AnchorSourcePolicy.COMMENT -> if (surface == MemorySurface.COMMENT || surface == MemorySurface.PRIVATE_CHAT) 50 else 25
            normalized.source == AnchorSourcePolicy.MOMENT -> if (surface == MemorySurface.MOMENT || surface == MemorySurface.PRIVATE_CHAT) 35 else 20
            normalized.source == AnchorSourcePolicy.GROUP_CHAT -> if (surface == MemorySurface.GROUP_CHAT || surface == MemorySurface.DIARY) 45 else 25
            normalized.source == AnchorSourcePolicy.DIARY -> if (surface == MemorySurface.DIARY || surface == MemorySurface.PRIVATE_CHAT) 40 else 15
            else -> 10
        }
        val importanceScore = when {
            normalized.importance == AnchorSourcePolicy.STRONG -> 40
            normalized.importance == AnchorSourcePolicy.MEDIUM -> 20
            normalized.importance == AnchorSourcePolicy.WEAK -> 5
            else -> 10
        }
        val typeScore = when (surface) {
            MemorySurface.PRIVATE_CHAT -> when (anchor.type) {
                AnchorType.TABOO -> 60; AnchorType.PLAN -> 55; AnchorType.PREFERENCE -> 45
                AnchorType.RELATION -> 40; AnchorType.EMOTION -> 30; AnchorType.EVENT -> 25
            }
            MemorySurface.GROUP_CHAT -> when (anchor.type) {
                AnchorType.PLAN -> 55; AnchorType.RELATION -> 45; AnchorType.EVENT -> 35
                AnchorType.EMOTION -> 30; AnchorType.PREFERENCE -> 20; AnchorType.TABOO -> 10
            }
            MemorySurface.MOMENT -> when (anchor.type) {
                AnchorType.EVENT -> 45; AnchorType.PLAN -> 40; AnchorType.EMOTION -> 30
                AnchorType.RELATION -> 25; AnchorType.PREFERENCE -> 15; AnchorType.TABOO -> 0
            }
            MemorySurface.COMMENT -> when (anchor.type) {
                AnchorType.EVENT -> 45; AnchorType.RELATION -> 35; AnchorType.EMOTION -> 30
                AnchorType.PLAN -> 25; AnchorType.PREFERENCE -> 10; AnchorType.TABOO -> 0
            }
            MemorySurface.DIARY -> when (anchor.type) {
                AnchorType.EMOTION -> 55; AnchorType.RELATION -> 50; AnchorType.PLAN -> 45
                AnchorType.EVENT -> 35; AnchorType.PREFERENCE -> 25; AnchorType.TABOO -> 25
            }
        }
        val age = now - anchor.createdAt
        val recentScore = when {
            age < 86_400_000L -> 35
            age < 3 * 86_400_000L -> 25
            age < 7 * 86_400_000L -> 15
            else -> 0
        }
        val relevanceScore = relevance(anchor.content, userContent)
        return sourceScore + importanceScore + typeScore + recentScore + relevanceScore
    }

    private fun relevance(content: String, userContent: String): Int {
        val text = userContent.trim()
        if (text.isBlank()) return 0
        val chars = text.filter { !it.isWhitespace() && !commonChineseChars.contains(it) }.toSet()
        val overlap = chars.count { content.contains(it) }.coerceAtMost(10) * 5
        val direct = if (text.length >= 2 && content.contains(text.take(2))) 35 else 0
        return overlap + direct
    }

    private fun isWeakContent(anchor: MemoryAnchor): Boolean {
        val text = anchor.content.trim().removePrefix("[弱]").trim(' ', '。', '！', '，', ',', '.', '!')
        val weak = setOf("哈哈", "嗯", "哦", "啊", "好吧", "行吧", "+1", "同意")
        if (anchor.type == AnchorType.PREFERENCE || anchor.type == AnchorType.TABOO || anchor.type == AnchorType.PLAN) {
            return text in weak
        }
        return text.length < 6 || text in weak
    }

    private val commonChineseChars = setOf('的', '了', '是', '我', '你', '他', '她', '它', '们', '在', '有', '和', '就', '不', '都', '也', '很', '还', '说', '要', '这', '那', '吗', '啊', '呢')

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("""\[[^\]]+]"""), "")
        .replace(" ", "")
        .replace("，", ",")
        .replace("。", "")
        .replace("！", "")
        .replace("？", "")
}
