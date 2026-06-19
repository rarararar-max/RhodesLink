package com.rhodes.privatechat.viewmodel.shared

import android.util.Log
import com.rhodes.privatechat.shared.model.AnchorType
import com.rhodes.privatechat.shared.model.MemoryAnchor
import com.rhodes.privatechat.shared.model.RelationshipType
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.memory.AnchorSourcePolicy
import com.rhodes.privatechat.shared.network.AIService
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.util.DebugLogger

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
    suspend fun chat(messages: List<AiMessage>, logTag: String = "Chat", category: String = ""): String {
        val temp = settings.aiTemperature
        val prompt = messages.firstOrNull()?.content ?: ""
        logAiCall("→$logTag", prompt, "(requesting...)", messages)
        val result = aiService.chat(
            settings.apiKey, messages, settings.provider, settings.modelName, settings.customUrl, temperature = temp
        )
        if (DEBUG) logAiCall("←$logTag", prompt, result.content, messages)
        if (category.isNotBlank() && (result.inputTokens > 0 || result.outputTokens > 0)) {
            trackTokens(category, result.inputTokens, result.outputTokens)
        }
        return result.content
    }

    /** 非流式聊天 + JSON解析重试：解析失败时重新请求，最多重试3次 */
    suspend fun chatWithRetry(messages: List<AiMessage>, logTag: String = "Chat", category: String = ""): com.rhodes.privatechat.shared.model.OfflineModeResponse {
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

    fun logMemoryContext(
        surface: String,
        title: String,
        placeholders: Map<String, String>,
        anchors: List<MemoryAnchor> = emptyList(),
        extra: Map<String, String> = emptyMap()
    ) {
        if (!DEBUG) return
        val sb = StringBuilder()
        sb.append("surface=").append(surface).append(" title=").append(title).append('\n')
        if (extra.isNotEmpty()) {
            sb.append("extra:\n")
            extra.forEach { (k, v) -> sb.append("- ").append(k).append("=").append(v).append('\n') }
        }
        sb.append("placeholders:\n")
        placeholders.forEach { (key, value) ->
            val lines = value.lines().count { it.isNotBlank() }
            val state = if (value.isBlank() || value == "无" || value == "暂无") "empty" else "chars=${value.length}, lines=$lines"
            sb.append("- ").append(key).append(": ").append(state).append('\n')
        }
        if (anchors.isNotEmpty()) {
            sb.append("anchors:\n")
            anchors.take(8).forEachIndexed { idx, anchor ->
                sb.append(idx + 1)
                    .append(". ").append(anchor.type.name)
                    .append(" private=").append(anchor.isPrivate)
                    .append(" ").append(anchorTimeLabel(anchor))
                    .append(" ").append(anchor.content.take(120))
                    .append('\n')
            }
            if (anchors.size > 8) sb.append("... +").append(anchors.size - 8).append(" more\n")
        }
        DebugLogger.log("Memory/Context", sb.toString().take(2500))
    }

    fun trackTokens(category: String, prompt: String, response: String) {
        val input = (prompt.length / 2).coerceAtLeast(1)
        val output = (response.length / 2).coerceAtLeast(1)
        val total = input + output
        addTokenCounts(category, input, output, total)
    }

    /** 统计完整消息列表的token消耗（包含系统提示词+聊天历史+响应） */
    fun trackTokens(category: String, messages: List<AiMessage>, response: String) {
        val totalInput = messages.sumOf { it.content.length }
        val input = (totalInput / 2).coerceAtLeast(1)
        val output = (response.length / 2).coerceAtLeast(1)
        val total = input + output
        addTokenCounts(category, input, output, total)
    }

    /** 真实API token消耗（输入/输出分离） */
    fun trackTokens(category: String, inputTokens: Int, outputTokens: Int) {
        addTokenCounts(category, inputTokens, outputTokens, inputTokens + outputTokens)
    }

    private fun addTokenCounts(category: String, input: Int, output: Int, total: Int) {
        // 旧字段（总计，兼容旧UI）
        val oldTotal = settings.getTokenCount(category)
        settings.putTokenCount(category, oldTotal + total)
        val today = beijingSdf("yyyy-MM-dd").format(java.util.Date())
        val oldDaily = settings.getDailyTokenCount(category, today)
        settings.putDailyTokenCount(category, today, oldDaily + total)
        // 新字段（输入/输出分离）
        settings.addInputTokenCount(category, input)
        settings.addOutputTokenCount(category, output)
        settings.addDailyInputTokenCount(category, today, input)
        settings.addDailyOutputTokenCount(category, today, output)
    }

    // === 配置访问 ===

    fun getApiKey(): String = settings.apiKey
    fun getProvider(): String = settings.provider
    fun getModelName(): String = settings.modelName
    fun getCustomUrl(): String = settings.customUrl

    // === 纯工具函数 ===

    fun applyTemplate(template: String, replacements: Map<String, String>): String {
        var result = template
        for ((key, value) in withLegacyPromptPlaceholders(replacements)) {
            result = result.replace("{{${key}}}", value)
        }
        return result
    }

    fun withLegacyPromptPlaceholders(replacements: Map<String, String>): Map<String, String> {
        val map = replacements.toMutableMap()
        val memoryInjection = listOf(
            map["LONG_TERM_IMPRESSION"]?.let { "长期印象：$it" },
            map["USER_PREFS"],
            map["MEMORY_ANCHORS"]?.let { "近期关键记忆：\n$it" },
            map["SOURCE_AWARE_MEMORIES"]?.let { "记忆来源：\n$it" },
            map["SHARED_MEMORIES"]?.let { "关系共享记忆：\n$it" },
            map["DAILY_SUMMARY"]?.let { "昨日摘要：$it" },
            map["SHORT_TERM_SUMMARY"]?.let { "近期摘要：$it" },
            map["GROUP_CONTEXT"]?.let { "群聊回顾：\n$it" },
            map["NEARBY_OPERATORS"]?.let { "附近干员：\n$it" }
        ).filterNotNull().filter { it.isNotBlank() && !it.endsWith("：无") && !it.endsWith("：暂无") }.joinToString("\n")
        val groupInjection = listOf(
            map["RELATION_HINTS"], map["GROUP_RELATION_HINTS"], map["MEMBER_PRIVATE_CONTEXT"],
            map["GROUP_SUMMARY"], map["DAILY_SUMMARY"], map["LONG_TERM_IMPRESSION"],
            map["SOURCE_AWARE_MEMORIES"], map["GROUP_UNCONSUMED_EVENTS"]
        ).filterNotNull().filter { it.isNotBlank() }.joinToString("\n")
        map.putIfAbsent("MEMORY_INJECTION", memoryInjection.ifBlank { "无" })
        map.putIfAbsent("GROUP_INJECTION", groupInjection.ifBlank { "无" })
        map.putIfAbsent("INJECTION", map["MEMORY_INJECTION"].orEmpty())
        map.putIfAbsent("GROUP_RELATION_HINTS", map["RELATION_HINTS"].orEmpty())
        map.putIfAbsent("RELATION_CONTEXT", map["RELATION_HINTS"] ?: map["SHARED_MEMORIES"] ?: "无")
        map.putIfAbsent("RELATION_SHARED_MEMORIES", map["SHARED_MEMORIES"] ?: "无")
        map.putIfAbsent("RELATION_RULES", "关系信息只作为互动背景，相关时自然体现，不要提到系统记录或关系表。")
        map.putIfAbsent("OPERATOR_USER_RELATION", map["USER_RELATION"] ?: "未知")
        map.putIfAbsent("MODE_RULES", "")
        map.putIfAbsent("OUTPUT_FORMAT", "请按当前模板要求的JSON格式输出。")
        return map
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

    fun trimContextBlock(text: String, maxChars: Int): String {
        if (maxChars <= 0 || text.length <= maxChars) return text
        val lines = text.lines().filter { it.isNotBlank() }
        val picked = mutableListOf<String>()
        var used = 0
        for (line in lines) {
            val next = line.take(maxChars)
            if (used + next.length + 1 > maxChars) break
            picked.add(next)
            used += next.length + 1
        }
        return picked.joinToString("\n").ifBlank { text.take(maxChars) }
    }

    fun contextBlockLimit(weight: Int = 1): Int {
        val base = when (settings.contextMode) {
            "economy" -> 500
            "full" -> 1400
            else -> 900
        }
        return (base * weight).coerceAtLeast(200)
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

    fun formatAnchorContent(
        source: String,
        importance: String = "中",
        actorName: String = "",
        action: String = "",
        content: String,
        sourceName: String = ""
    ): String {
        val sourceLabel = if (sourceName.isBlank()) source else "$source:$sourceName"
        val prefix = "[$sourceLabel][$importance]"
        val actor = actorName.takeIf { it.isNotBlank() }?.let { "$it" } ?: ""
        val verb = action.takeIf { it.isNotBlank() }?.let { "$it：" } ?: ""
        return "$prefix $actor$verb${content.take(80)}".trim()
    }

    fun anchorTimeLabel(anchor: MemoryAnchor): String {
        val diff = System.currentTimeMillis() - anchor.createdAt
        return when {
            diff < 3_600_000 -> "刚刚"
            diff < 86_400_000 -> "今天"
            diff < 172_800_000 -> "昨天"
            else -> "${diff / 86_400_000}天前"
        }
    }

    fun pickAnchors(anchors: List<MemoryAnchor>, maxCount: Int = 5, userContent: String = ""): List<MemoryAnchor> {
        if (anchors.isEmpty()) return emptyList()
        return MemoryRanker.pick(anchors, maxCount, MemorySurface.PRIVATE_CHAT, userContent)
    }

    fun pickAnchorsForSurface(anchors: List<MemoryAnchor>, maxCount: Int, surface: MemorySurface, userContent: String = ""): List<MemoryAnchor> {
        return MemoryRanker.pick(anchors, maxCount, surface, userContent)
    }

    fun buildSourceAwareMemoryContext(
        anchors: List<MemoryAnchor>,
        maxCount: Int,
        surface: MemorySurface,
        userContent: String = ""
    ): String {
        if (!settings.sourceAwareMemoryEnabled || maxCount <= 0) return "无"
        val picked = pickAnchorsForSurface(anchors, maxCount, surface, userContent)
        if (picked.isEmpty()) return "无"
        return picked.joinToString("\n") { AnchorSourcePolicy.toPromptLine(it) }
    }

    fun sourceAwareUsageRule(surface: MemorySurface): String {
        if (!settings.sourceAwareMemoryEnabled) return ""
        val base = "不要说“系统记录”“记忆锚点”“摘要显示”。需要时自然表现你是从哪里知道的。"
        return when (surface) {
            MemorySurface.PRIVATE_CHAT -> "$base 可以说“你上次跟我说过”“我看到你评论了”“群里之前聊到”。"
            MemorySurface.GROUP_CHAT -> "$base 可以说“之前群里聊过”“我听谁提过”“动态下面有人说”。"
            MemorySurface.MOMENT -> "$base 公开动态优先写自己的日常；来自私聊的信息只含蓄影响语气，不要像公告一样复述。"
            MemorySurface.COMMENT -> "$base 评论区只需轻轻带过，不要长篇解释来源。"
            MemorySurface.DIARY -> "$base 日记可以更直接写清楚自己从哪里知道这些事。"
        }
    }

    suspend fun buildWorldEventContext(operatorId: String = "", operatorName: String = "", limit: Int = 5): String {
        val events = if (operatorId.isNotBlank()) {
            repository.getWorldEventsForOperator(operatorId, operatorName, limit)
        } else {
            repository.getRecentWorldEvents(limit)
        }
        if (events.isEmpty()) return "无"
        return events.joinToString("\n") { event ->
            val who = event.actorName.ifBlank { event.actorId.ifBlank { "罗德岛" } }
            "- ${anchorTimeLabel(MemoryAnchor(sessionId = event.sourceId, operatorId = event.actorId, type = AnchorType.EVENT, content = event.content, createdAt = event.createdAt))} $who：${event.content.take(100)}"
        }
    }

    suspend fun buildUnconsumedEventContextForOperator(
        operatorId: String,
        operatorName: String,
        consumer: String,
        limit: Int = 5,
        markConsumed: Boolean = false
    ): String {
        val events = repository.getUnconsumedWorldEventsForOperator(operatorId, operatorName, consumer, limit)
        if (events.isEmpty()) return "无"
        if (markConsumed) events.forEach { repository.markWorldEventConsumed(it.id, consumer) }
        return events.joinToString("\n") { event ->
            val who = event.actorName.ifBlank { event.actorId.ifBlank { "罗德岛" } }
            "- ${anchorTimeLabel(MemoryAnchor(sessionId = event.sourceId, operatorId = event.actorId, type = AnchorType.EVENT, content = event.content, createdAt = event.createdAt))} $who：${event.content.take(120)}"
        }
    }

    suspend fun buildUnconsumedEventContextForGroup(
        groupId: String,
        memberIds: List<String>,
        memberNames: List<String>,
        limit: Int = 8,
        markConsumed: Boolean = false
    ): String {
        val events = repository.getUnconsumedWorldEventsForGroup(groupId, memberIds, memberNames, limit)
        if (events.isEmpty()) return "无"
        if (markConsumed) events.forEach { repository.markWorldEventConsumed(it.id, "group:$groupId") }
        return events.joinToString("\n") { event ->
            val who = event.actorName.ifBlank { event.actorId.ifBlank { "罗德岛" } }
            "- ${anchorTimeLabel(MemoryAnchor(sessionId = event.sourceId, operatorId = event.actorId, type = AnchorType.EVENT, content = event.content, createdAt = event.createdAt))} $who：${event.content.take(120)}"
        }
    }

    @Suppress("unused")
    private fun pickAnchorsLegacy(anchors: List<MemoryAnchor>, maxCount: Int = 5, userContent: String = ""): List<MemoryAnchor> {
        if (anchors.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        if (userContent.isNotBlank()) {
            return anchors.sortedByDescending { anchorScore(it, userContent, now) }
                .distinctBy { it.type to it.content.trim() }
                .take(maxCount)
        }
        val recent = anchors.filter { now - it.createdAt < 86_400_000 }
        val older = anchors.filter { now - it.createdAt >= 86_400_000 }
        val picked = mutableListOf<MemoryAnchor>()
        val priority = listOf(
            AnchorType.TABOO, AnchorType.PLAN, AnchorType.PREFERENCE,
            AnchorType.RELATION, AnchorType.EMOTION, AnchorType.EVENT
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
        // 限制事件类和情绪类锚点最多 2 条，防止刷屏
        listOf(AnchorType.EVENT, AnchorType.EMOTION).forEach { t ->
            val overLimit = picked.filter { it.type == t }.drop(2)
            picked.removeAll(overLimit)
        }
        return picked.take(maxCount)
    }

    private fun anchorScore(anchor: MemoryAnchor, userContent: String, now: Long): Int {
        val typeScore = when (anchor.type) {
            AnchorType.TABOO -> 100
            AnchorType.PLAN -> 85
            AnchorType.PREFERENCE -> 75
            AnchorType.RELATION -> 65
            AnchorType.EMOTION -> 50
            AnchorType.EVENT -> 45
        }
        val recentScore = when (now - anchor.createdAt) {
            in Long.MIN_VALUE until 86_400_000L -> 30
            in 86_400_000L until 3 * 86_400_000L -> 20
            in 3 * 86_400_000L until 7 * 86_400_000L -> 10
            else -> 0
        }
        val text = userContent.trim()
        val content = anchor.content
        val chars = text.filter { !it.isWhitespace() }.toSet()
        val overlap = chars.count { content.contains(it) }.coerceAtMost(10) * 4
        val direct = if (text.length >= 2 && content.contains(text.take(2))) 30 else 0
        return typeScore + recentScore + overlap + direct
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
        RelationshipType.LOVER -> "恋人"
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
        RelationshipType.LOVER -> "${aName}是${bName}的【恋人】"
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
