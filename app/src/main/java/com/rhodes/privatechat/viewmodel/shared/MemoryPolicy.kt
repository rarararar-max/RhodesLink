package com.rhodes.privatechat.viewmodel.shared

import com.rhodes.privatechat.shared.model.AnchorType
import com.rhodes.privatechat.shared.settings.SettingsRepository

object MemoryPolicy {
    private const val DAY_MS = 86_400_000L

    fun memoryExpiresAt(settings: SettingsRepository): Long = expiresAtFromDays(settings.cleanDays)

    fun anchorExpiresAt(settings: SettingsRepository, type: AnchorType): Long {
        val baseDays = settings.cleanDaysAnchors
        if (baseDays < 0) {
            return if (type == AnchorType.EMOTION) System.currentTimeMillis() + 7 * DAY_MS else Long.MAX_VALUE
        }
        val days = when (type) {
            AnchorType.TABOO -> maxOf(baseDays, settings.cleanDays, 90)
            AnchorType.PREFERENCE -> maxOf(baseDays, settings.cleanDays, 60)
            AnchorType.PLAN -> maxOf(baseDays, 14)
            AnchorType.RELATION -> maxOf(baseDays, 60)
            AnchorType.EMOTION -> minOf(maxOf(baseDays, 3), 14)
            AnchorType.EVENT -> baseDays
        }
        return expiresAtFromDays(days)
    }

    fun groupAnchorContent(mode: String, groupName: String, speaker: String, message: String): String {
        val text = message.take(40)
        return when (mode) {
            "offline" -> "在线下聚会「$groupName」中，$speaker 当面说：$text"
            "director" -> "在场景「$groupName」中，$speaker 表现为：$text"
            "auto" -> "在自动群聊「$groupName」中，$speaker 聊到：$text"
            else -> "在线上群聊「$groupName」中，$speaker 提到：$text"
        }
    }

    fun shouldSaveGroupAnchor(message: String): Boolean {
        val text = message.trim()
        if (text.length < 8) return false
        val weak = listOf("哈哈", "嗯", "哦", "啊", "好吧", "行吧", "+1", "同意")
        if (weak.any { text == it || text == "$it。" || text == "$it！" }) return false
        val strongSignals = listOf("喜欢", "讨厌", "别", "不要", "记得", "明天", "以后", "约", "计划", "秘密", "害怕", "难过", "开心", "生气", "关系", "任务", "派遣", "完成")
        return strongSignals.any { text.contains(it) } || text.length >= 18
    }

    private fun expiresAtFromDays(days: Int): Long =
        if (days < 0) Long.MAX_VALUE else System.currentTimeMillis() + days * DAY_MS
}
