package com.rhodes.privatechat.viewmodel.shared

import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.shared.vector.MemoryVectorService
import com.rhodes.privatechat.util.DebugLogger

class MemoryContextBuilder(
    private val settings: SettingsRepository,
    private val memoryVectorService: MemoryVectorService?,
) {
    suspend fun privateVectorContext(operatorId: String, query: String): String {
        val service = memoryVectorService ?: return "无"
        if (query.isBlank()) return "无"
        val wantsRecall = UnifiedMemoryContext.shouldIncludeTimeSummary(query)
        val privateLimit = if (wantsRecall) settings.privateAnchorCount else settings.privateAnchorCount.coerceAtMost(3)
        val globalLimit = if (wantsRecall) settings.globalPublicMemoryCount else settings.globalPublicMemoryCount.coerceAtMost(2)
        if (privateLimit <= 0 && globalLimit <= 0) return "无"
        return try {
            val now = System.currentTimeMillis()
            val operatorMemories = if (privateLimit > 0) service.recall(
                ownerType = "operator",
                ownerId = operatorId,
                query = query,
                limit = privateLimit,
                visibilities = listOf("private", "shared", "public"),
                minScore = if (wantsRecall) 0.14 else 0.24,
                now = now
            ) else emptyList()
            val globalMemories = if (settings.globalPublicMemoryEnabled && globalLimit > 0) service.recall(
                ownerType = "global",
                ownerId = "public",
                query = query,
                limit = globalLimit,
                visibilities = listOf("public"),
                minScore = if (wantsRecall) 0.14 else 0.26,
                now = now
            ) else emptyList()
            formatVectorMemories(operatorMemories + globalMemories, now, 100)
        } catch (e: Exception) {
            DebugLogger.log("Vector/Recall", "私聊记忆上下文构建失败: ${e.message?.take(80)}")
            "无"
        }
    }

    suspend fun groupVectorContext(groupId: String, query: String): String {
        val service = memoryVectorService ?: return "无"
        if (query.isBlank()) return "无"
        val wantsRecall = UnifiedMemoryContext.shouldIncludeTimeSummary(query)
        if (settings.groupMemberMemoryCount <= 0) return "无"
        val limit = if (wantsRecall) settings.groupMemberMemoryCount.coerceAtLeast(2) else settings.groupMemberMemoryCount.coerceAtMost(3)
        return try {
            val now = System.currentTimeMillis()
            val groupMemories = service.recall(
                ownerType = "group",
                ownerId = groupId,
                query = query,
                limit = limit,
                visibilities = listOf("public", "shared"),
                minScore = if (wantsRecall) 0.14 else 0.24,
                now = now
            )
            formatVectorMemories(groupMemories, now, 100, "群聊相关回忆")
        } catch (e: Exception) {
            DebugLogger.log("Vector/Recall", "群聊记忆上下文构建失败: ${e.message?.take(80)}")
            "无"
        }
    }

    private fun formatVectorMemories(memories: List<com.rhodes.privatechat.shared.vector.VectorMemory>, now: Long, limitChars: Int, label: String = "相关回忆"): String {
        return memories
            .filter { it.content.isNotBlank() && it.expiresAt > now }
            .sortedWith(compareByDescending<com.rhodes.privatechat.shared.vector.VectorMemory> { it.importance }.thenByDescending { it.createdAt })
            .distinctBy { normalize(it.content) }
            .joinToString("\n") { "- $label：${it.content.take(limitChars)}" }
            .ifBlank { "无" }
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("""[\s，。！？；,.!?;：:\"'“”‘’【】\[\]（）()]"""), "")
        .take(80)
}
