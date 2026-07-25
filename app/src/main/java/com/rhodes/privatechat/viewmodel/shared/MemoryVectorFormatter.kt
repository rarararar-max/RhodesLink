package com.rhodes.privatechat.viewmodel.shared

import com.rhodes.privatechat.shared.model.MemoryItem
import com.rhodes.privatechat.shared.model.MemorySourceKind

object MemoryVectorFormatter {
    fun content(item: MemoryItem): String {
        if (item.sourceKind == MemorySourceKind.MANUAL_MEMORY) return "[手动记忆] ${item.content}"
        val time = item.eventTime?.takeIf { it.isNotBlank() }
            ?: item.scheduledTime?.takeIf { it.isNotBlank() }?.let { "约定 $it" }
            ?: item.createdAt.takeIf { it > 0 }?.let { timestamp ->
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).apply {
                    timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
                }.format(java.util.Date(timestamp))
            }.orEmpty()
        val source = when (item.sourceKind) {
            MemorySourceKind.PRIVATE_CHAT -> "私聊"
            MemorySourceKind.GROUP_CHAT -> "群聊"
            MemorySourceKind.MOMENT -> "动态"
            MemorySourceKind.MOMENT_COMMENT -> "评论"
            MemorySourceKind.DIARY -> "日记"
            MemorySourceKind.MANUAL_MEMORY -> "手动记忆"
        }
        return "[$time·$source] ${item.content}"
    }

    fun vectorId(item: MemoryItem): String = if (item.sourceKind == MemorySourceKind.MANUAL_MEMORY) {
        "manual_memory_operator_${item.ownerId}_${item.id}"
    } else {
        "memory_v2_${item.ownerType}_${item.ownerId}_${item.memoryLevel.name.lowercase()}_${item.id}"
    }

    fun sourceType(item: MemoryItem): String = if (item.sourceKind == MemorySourceKind.MANUAL_MEMORY) {
        "manual_memory"
    } else {
        "memory_v2_${item.memoryLevel.name.lowercase()}"
    }

    fun tags(item: MemoryItem): String = listOf(item.memoryLevel.name, item.memoryType, item.sourceKind.name).joinToString(",")
}
