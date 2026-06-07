package com.rhodes.privatechat.viewmodel.shared

import android.util.Log
import com.rhodes.privatechat.shared.model.AnchorType
import com.rhodes.privatechat.shared.model.MemoryAnchor
import com.rhodes.privatechat.shared.model.RelationshipType
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.network.AIService
import com.rhodes.privatechat.shared.settings.SettingsRepository

class SharedUtils(
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    val aiService: AIService,
    private val operatorsProvider: () -> List<com.rhodes.privatechat.shared.model.Operator> = { emptyList() }
) {
    companion object {
        const val DEBUG = true
    }

    // === AI 调用 ===

    /** 非流式聊天：发送请求，等待完整响应后返回 */
    suspend fun chat(messages: List<AiMessage>, logTag: String = "Chat"): String {
        val temp = settings.aiTemperature
        val prompt = messages.firstOrNull()?.content ?: ""
        logAiCall("→$logTag", prompt, "(requesting...)", messages)
        val result = aiService.chat(
            settings.apiKey, messages, settings.provider, settings.modelName, settings.customUrl, temperature = temp
        )
        if (DEBUG) logAiCall("←$logTag", prompt, result, messages)
        return result
    }

    /** 非流式聊天 + JSON解析重试：解析失败时重新请求，最多重试3次 */
    suspend fun chatWithRetry(messages: List<AiMessage>, logTag: String = "Chat"): com.rhodes.privatechat.shared.model.OfflineModeResponse {
        val temp = settings.aiTemperature
        val prompt = messages.firstOrNull()?.content ?: ""
        logAiCall("→$logTag", prompt, "(requesting with retry...)", messages)
        val result = aiService.chatWithRetry(
            settings.apiKey, messages, settings.provider, settings.modelName, settings.customUrl,
            temperature = temp, logTag = logTag
        )
        if (DEBUG) logAiCall("←$logTag", prompt, result.toString(), messages)
        return result
    }

    fun logAiCall(tag: String, prompt: String, response: String, allMessages: List<AiMessage>? = null) {
        if (!DEBUG) return
        val aiTag = "AI调试输出"
        Log.d(aiTag, "╔══════════════════════════════════════════════")
        Log.d(aiTag, "║ [$tag]")
        Log.d(aiTag, "╠══ SYSTEM PROMPT ════════════════════════════")
        prompt.lines().forEach { Log.d(aiTag, "║ $it") }
        if (allMessages != null && allMessages.size > 1) {
            Log.d(aiTag, "╠══ CHAT HISTORY (${allMessages.size - 1}条) ═══")
            for ((i, msg) in allMessages.withIndex()) {
                if (i == 0) continue
                val label = if (msg.role == "user") "用户" else "AI"
                val preview = msg.content.take(200)
                Log.d(aiTag, "║ [$label] $preview")
                if (msg.content.length > 200) Log.d(aiTag, "║   ...(共${msg.content.length}字)")
            }
        }
        Log.d(aiTag, "╠══ RESPONSE ════════════════════════════════")
        response.lines().forEach { Log.d(aiTag, "║ $it") }
        Log.d(aiTag, "╚══════════════════════════════════════════════")
    }

    fun trackTokens(category: String, prompt: String, response: String) {
        val estimate = ((prompt.length + response.length) / 2).coerceAtLeast(1)
        addTokenEstimate(category, estimate)
    }

    /** 统计完整消息列表的token消耗（包含系统提示词+聊天历史+响应） */
    fun trackTokens(category: String, messages: List<AiMessage>, response: String) {
        val totalInput = messages.sumOf { it.content.length }
        val estimate = ((totalInput + response.length) / 2).coerceAtLeast(1)
        addTokenEstimate(category, estimate)
    }

    private fun addTokenEstimate(category: String, estimate: Int) {
        val current = settings.getTokenCount(category)
        settings.putTokenCount(category, current + estimate)
        val today = beijingSdf("yyyy-MM-dd").format(java.util.Date())
        val dailyCurrent = settings.getDailyTokenCount(category, today)
        settings.putDailyTokenCount(category, today, dailyCurrent + estimate)
    }

    // === 配置访问 ===

    fun getApiKey(): String = settings.apiKey
    fun getProvider(): String = settings.provider
    fun getModelName(): String = settings.modelName
    fun getCustomUrl(): String = settings.customUrl

    // === 纯工具函数 ===

    fun applyTemplate(template: String, replacements: Map<String, String>): String {
        var result = template
        for ((key, value) in replacements) {
            result = result.replace("{{${key}}}", value)
        }
        return result
    }

    /** 移除模板中内容为空的段落及其节标题，减少无效token消耗 */
    fun compactTemplate(text: String): String {
        var result = text
        // 1. 移除只有占位文字（暂无/无/空）的行
        result = result.replace(Regex("""\n[ \t]*(暂无|无)\s*\n"""), "\n")
        // 2. 移除空的可选块（双换行包围的纯空白）
        result = result.replace(Regex("""\n\n\s*\n"""), "\n")
        // 3. 移除后面紧跟空行或另一节标题的孤立节标题
        result = result.replace(Regex("""\n(【[^】]+】)\n(?=\n|【|$)"""), "\n")
        // 4. 清理多余连续空行
        result = result.replace(Regex("""\n{3,}"""), "\n\n")
        return result.trim()
    }

    fun beijingSdf(pattern: String) = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
        .apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai") }

    fun getTimeOfDay(hour: Int): String = when {
        hour in 5..7 -> "清晨"
        hour in 8..11 -> "上午"
        hour in 12..13 -> "中午"
        hour in 14..17 -> "下午"
        hour in 18..21 -> "晚上"
        hour in 22..23 -> "深夜"
        else -> "凌晨"
    }

    fun parseOnlineEmotion(text: String): Pair<String, String> {
        val emo = Regex("\\[([^\\]]+)\\]\\s*$").find(text.trim())
        return if (emo != null) {
            text.trim().removeSuffix(emo.value) to emo.groupValues[1]
        } else text to ""
    }

    // === 锚点工具 ===

    fun anchorTimeLabel(anchor: MemoryAnchor): String {
        val diff = System.currentTimeMillis() - anchor.createdAt
        return when {
            diff < 3_600_000 -> "刚刚"
            diff < 86_400_000 -> "今天"
            diff < 172_800_000 -> "昨天"
            else -> "${diff / 86_400_000}天前"
        }
    }

    fun pickAnchors(anchors: List<MemoryAnchor>, maxCount: Int = 5): List<MemoryAnchor> {
        if (anchors.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val recent = anchors.filter { now - it.createdAt < 86_400_000 }
        val older = anchors.filter { now - it.createdAt >= 86_400_000 }
        val picked = mutableListOf<MemoryAnchor>()
        val priority = listOf(
            AnchorType.PREFERENCE, AnchorType.TABOO, AnchorType.PLAN,
            AnchorType.EVENT, AnchorType.EMOTION, AnchorType.RELATION
        )
        val byType = recent.sortedByDescending { it.createdAt }.groupBy { it.type }
        for (t in priority) {
            if (picked.size >= maxCount) break
            val best = byType[t]?.firstOrNull()
            if (best != null) picked.add(best)
        }
        if (picked.size < maxCount) {
            val remaining = recent.filter { it !in picked }.sortedByDescending { it.createdAt }
            picked.addAll(remaining.take(maxCount - picked.size))
        }
        if (picked.size < maxCount) {
            val oldPicks = older.filter {
                it.type == AnchorType.PREFERENCE || it.type == AnchorType.TABOO || it.type == AnchorType.PLAN
            }.sortedByDescending { it.createdAt }
            picked.addAll(oldPicks.take(maxCount - picked.size))
        }
        return picked.take(maxCount)
    }

    // === 关系工具 ===

    fun relationshipDebugLabel(type: RelationshipType): String = when (type) {
        RelationshipType.BIG_SISTER -> "姐姐"
        RelationshipType.LITTLE_SISTER -> "妹妹"
        RelationshipType.BIG_BROTHER -> "哥哥"
        RelationshipType.LITTLE_BROTHER -> "弟弟"
        RelationshipType.MOTHER -> "母亲"
        RelationshipType.FATHER -> "父亲"
        RelationshipType.DAUGHTER -> "女儿"
        RelationshipType.SON -> "儿子"
        RelationshipType.BOSS -> "上司"
        RelationshipType.SUBORDINATE -> "下属"
        RelationshipType.GUARDIAN -> "监护人"
        RelationshipType.CAPTAIN -> "队长"
        RelationshipType.MEMBER -> "队员"
        RelationshipType.MENTOR -> "导师"
        RelationshipType.STUDENT -> "学生"
        RelationshipType.CLOSE_FRIEND -> "挚友"
        RelationshipType.FRIEND -> "朋友"
        RelationshipType.COMRADE -> "战友"
        RelationshipType.TEAMMATE -> "队友"
        RelationshipType.RIVAL -> "对手"
        RelationshipType.CRUSH -> "暗恋"
        RelationshipType.SIBLING -> "姐妹/兄弟"
        RelationshipType.FAMILY -> "家人"
        else -> "陌生"
    }

    fun relationshipGroupDesc(aName: String, bName: String, type: RelationshipType): String = when (type) {
        RelationshipType.BIG_SISTER -> "${aName}是${bName}的【姐姐】"
        RelationshipType.LITTLE_SISTER -> "${aName}是${bName}的【妹妹】"
        RelationshipType.BIG_BROTHER -> "${aName}是${bName}的【哥哥】"
        RelationshipType.LITTLE_BROTHER -> "${aName}是${bName}的【弟弟】"
        RelationshipType.MOTHER -> "${aName}是${bName}的【母亲】"
        RelationshipType.FATHER -> "${aName}是${bName}的【父亲】"
        RelationshipType.DAUGHTER -> "${aName}是${bName}的【女儿】"
        RelationshipType.SON -> "${aName}是${bName}的【儿子】"
        RelationshipType.BOSS -> "${aName}是${bName}的【上司】"
        RelationshipType.SUBORDINATE -> "${aName}是${bName}的【下属】"
        RelationshipType.MENTOR -> "${aName}是${bName}的【导师】"
        RelationshipType.STUDENT -> "${aName}是${bName}的【学生】"
        RelationshipType.GUARDIAN -> "${aName}是${bName}的【监护人】"
        RelationshipType.CAPTAIN -> "${aName}是${bName}的【队长】"
        RelationshipType.MEMBER -> "${aName}是${bName}的【队员】"
        RelationshipType.CLOSE_FRIEND -> "${aName}是${bName}的【挚友】"
        RelationshipType.FRIEND -> "${aName}是${bName}的【朋友】"
        RelationshipType.COMRADE -> "${aName}是${bName}的【战友】"
        RelationshipType.TEAMMATE -> "${aName}是${bName}的【队友】"
        RelationshipType.RIVAL -> "${aName}是${bName}的【对手】"
        RelationshipType.CRUSH -> "${aName}是${bName}的【暗恋对象】"
        RelationshipType.SIBLING -> "${aName}是${bName}的【姐妹/兄弟】"
        RelationshipType.FAMILY -> "${aName}是${bName}的【家人】"
        else -> ""
    }

    // === 数据查询 ===

    suspend fun getRecentPosts(operatorId: String, limit: Int = 3): String {
        val all = repository.getMomentsPaged(limit, 0)
        val ops = all.filter { it.operatorId == operatorId }.take(limit)
        if (ops.isEmpty()) return ""
        return ops.joinToString("\n") { "- ${it.content.take(50)}" }
    }

    suspend fun getRelationEvents(operatorId: String): String {
        val rels = repository.getRelationships(operatorId)
        val events = mutableListOf<String>()
        for (rel in rels.take(5)) {
            val anchors = repository.getPublicAnchors(rel.relatedOperatorId)
            for (a in pickAnchors(anchors, 2)) {
                events.add("- ${rel.relatedOperatorName}：${a.content}")
            }
        }
        return events.joinToString("\n")
    }

    fun matchOperatorName(input: String): String? {
        val ops = operatorsProvider()
        if (ops.isEmpty()) return null
        val exact = ops.find { it.name == input }
        if (exact != null) return exact.name
        fun lev(a: String, b: String): Int {
            val m = a.length; val n = b.length
            val dp = Array(m + 1) { IntArray(n + 1) }
            for (i in 0..m) dp[i][0] = i
            for (j in 0..n) dp[0][j] = j
            for (i in 1..m) for (j in 1..n) {
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            return dp[m][n]
        }
        return ops.map { it.name to lev(input, it.name) }
            .filter { it.second <= 3 }
            .minByOrNull { it.second }
            ?.first
    }

    // === 通用工具 ===

    fun runBlockingCatching(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { try { block() } catch (_: Exception) { } }
    }
}
