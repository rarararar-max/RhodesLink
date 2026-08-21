package com.rhodes.privatechat.viewmodel.shared

import com.rhodes.privatechat.shared.model.AnchorType
import com.rhodes.privatechat.shared.model.MemoryAnchor
import com.rhodes.privatechat.shared.model.RelationshipType
import com.rhodes.privatechat.shared.model.AiMessage
import android.util.Log
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.memory.AnchorSourcePolicy
import com.rhodes.privatechat.shared.network.AIService
import com.rhodes.privatechat.shared.network.providers
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.util.DebugLogger
import kotlin.time.TimeSource

internal object CachePromptLayering {
    private val placeholderPattern = Regex("\\{\\{([A-Z0-9_]+)\\}\\}")
    private val runtimeLabels = mapOf(
        "CURRENT_TIME" to "当前时间",
        "CURRENT_DATE" to "当前日期",
        "USER_NAME" to "用户称呼",
        "USER_GENDER" to "用户性别",
        "USER_BIO" to "用户设定",
        "SHORT_TERM_SUMMARY" to "最近聊天进展",
        "PRIVATE_CONTINUITY_STATE" to "当前对话进展",
        "MEMORY_V2_CONTEXT" to "可能相关的过往经历",
        "MEMORY_ANCHORS" to "可能相关的过往经历",
        "SOURCE_AWARE_MEMORIES" to "可能相关的背景",
        "GROUP_CONTEXT" to "从群聊得知的近况",
        "GROUP_SUMMARY" to "最近群聊进展",
        "GROUP_PLOT_SUMMARY" to "当前群聊主线",
        "DAILY_SUMMARY" to "最近的日常回顾",
        "RECENT_SOCIAL_CONTEXT" to "近期公开动态与评论",
        "RECENT_POSTS" to "最近发布过的动态",
        "MEMBER_PRIVATE_CONTEXT" to "本轮明确提起的私聊背景",
        "RELATION_HINTS" to "人物关系",
        "GROUP_RELATION_HINTS" to "人物关系",
        "MEMBER_PROFILES" to "当前成员资料",
        "MEMBER_NAMES" to "当前成员名单",
        "GROUP_RULES" to "群聊约定",
        "POST_CONTENT" to "动态原文",
        "COMMENT_CONTEXT" to "最近评论内容",
        "COMMENT_TASK" to "本轮评论任务",
        "COMMENT_INSTRUCTION" to "评论要求",
        "REPLY_TARGET" to "本轮回复对象",
        "PRIVATE_SUMMARY" to "昨天确认的私聊经历",
        "GROUP_SUMMARIES" to "昨天确认的群聊与公开经历",
        "RECENT_MEMORIES" to "近期回忆与背景",
        "RELATION_EVENTS" to "与他人的相关经历",
        "SELF_STATUS_CHANGES" to "今天的状态",
        "PROACTIVE_TRIGGER_CONTEXT" to "这次主动联系的缘由",
        "PROACTIVE_RECENT_HISTORY" to "最近互动记录",
        "PROACTIVE_UNRESOLVED_TOPIC" to "尚未结束的话题",
        "PROACTIVE_LAST_USER_MESSAGE" to "用户最后说的话",
        "PROACTIVE_LAST_AI_MESSAGE" to "你最后说的话"
    )

    private fun runtimeLabel(key: String): String = runtimeLabels[key] ?: "本轮相关资料"

    fun build(
        template: String,
        replacements: Map<String, String>,
        dynamicKeys: Set<String>,
        render: (Map<String, String>) -> String
    ): SharedUtils.CachePromptLayers {
        val stableReplacements = replacements.mapValues { (key, value) ->
            when {
                key in setOf("USER_CONTENT", "USER_MESSAGE") -> ""
                key in dynamicKeys -> "见本轮资料"
                else -> value
            }
        }
        val referencedKeys = placeholderPattern.findAll(template).map { it.groupValues[1] }.toSet()
        val runtime = dynamicKeys.intersect(referencedKeys).sorted().mapNotNull { key ->
            replacements[key]?.takeIf { it.isNotBlank() && key !in setOf("USER_CONTENT", "USER_MESSAGE") }
                ?.let { "【${runtimeLabel(key)}】\n$it" }
        }.joinToString("\n")
        return SharedUtils.CachePromptLayers(render(stableReplacements), runtime.ifBlank { "【本轮资料】\n暂无与本轮相关的补充资料。" })
    }
}

class SharedUtils(
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    val aiService: AIService,
    private val operatorsProvider: () -> List<com.rhodes.privatechat.shared.model.Operator> = { emptyList() }
) {
    data class CachePromptLayers(val system: String, val runtimeContext: String)

    fun logAiCallText(messages: List<AiMessage>): String = buildString {
        append("【发送给 AI 的完整内容】\n消息数=${messages.size}\n")
        messages.forEachIndexed { index, message ->
            append("\n[${index + 1}] ${message.role} | ${message.content.length}字\n")
            append(message.content)
            append('\n')
        }
    }

    /**
     * Builds the application-owned runtime context that must not depend on a user template
     * mentioning a particular placeholder. Values stay after the reusable system prefix so
     * current time, summaries, recall results, and knowledge-base hits do not reset that prefix.
     */
    fun buildNaturalRuntimeContext(type: String, replacements: Map<String, String>): String {
        // Some optional Java/platform values arrive as the literal text "null". It is not
        // meaningful prompt context and must not compete with actual conversation facts.
        fun value(key: String): String = replacements[key].orEmpty().trim().takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
        fun block(title: String, body: String, empty: String = "暂无相关内容。"): String =
            "【$title】\n${body.ifBlank { empty }}"
        fun addIfRelevant(target: MutableList<String>, title: String, key: String, empty: String = "暂无相关内容。") {
            target += block(title, value(key), empty)
        }

        val blocks = mutableListOf<String>()
        when (type) {
            "private", "private_proactive" -> {
                blocks += block("现在的时间", "现在是北京时间：${value("CURRENT_TIME")}", "当前时间未提供。")
                blocks += block(
                    "用户资料",
                    listOf(
                        "姓名：${value("USER_NAME").ifBlank { "未设置" }}",
                        "性别：${value("USER_GENDER").ifBlank { "未设置" }}",
                        "身份设定：${value("USER_BIO").ifBlank { "未设置" }}"
                    ).joinToString("\n")
                )
                addIfRelevant(blocks, "你与用户的关系", "USER_RELATION", "关系尚未明确。")
                addIfRelevant(blocks, "上一轮互动状态", "PRIVATE_CONTINUITY_STATE", "上一轮没有可用的结构化状态；请以最近聊天内容为准。")
                addIfRelevant(blocks, "最近几次互动的回顾", "SHORT_TERM_SUMMARY")
                addIfRelevant(blocks, "与当前话题有关的过去经历", "MEMORY_V2_CONTEXT", "没有检索到与当前话题直接相关的过去经历。")
                addIfRelevant(blocks, "你从相关群聊中了解到的内容", "GROUP_CONTEXT")
                addIfRelevant(blocks, "本次任务的相关背景设定", "__KNOWLEDGE_BASE_CONTEXT")
            }
            "group" -> {
                blocks += block("现在的时间", "现在是北京时间：${value("CURRENT_TIME")}", "当前时间未提供。")
                blocks += block(
                    "用户资料",
                    listOf(
                        "姓名：${value("USER_NAME").ifBlank { "未设置" }}",
                        "性别：${value("USER_GENDER").ifBlank { "未设置" }}",
                        "身份设定：${value("USER_BIO").ifBlank { "未设置" }}"
                    ).joinToString("\n")
                )
                addIfRelevant(blocks, "群聊约定", "GROUP_RULES", "当前没有额外群聊约定。")
                addIfRelevant(blocks, "之前群聊的回顾", "SHORT_TERM_SUMMARY")
                addIfRelevant(blocks, "当前群聊主线", "GROUP_PLOT_SUMMARY", "当前没有已记录的群聊主线。")
                addIfRelevant(blocks, "群聊每日回顾", "DAILY_SUMMARY")
                addIfRelevant(blocks, "群聊中相关的过去经历", "MEMORY_V2_CONTEXT", "没有检索到与当前话题直接相关的群聊经历。")
                addIfRelevant(blocks, "成员关系背景", "RELATION_HINTS")
                addIfRelevant(blocks, "用户本轮明确提到的相关背景", "MEMBER_PRIVATE_CONTEXT")
                addIfRelevant(blocks, "近期公开动态和评论", "RECENT_SOCIAL_CONTEXT")
                addIfRelevant(blocks, "本次任务的相关背景设定", "__KNOWLEDGE_BASE_CONTEXT")
            }
            "moment" -> {
                blocks += block("今天的时间", "日期：${value("CURRENT_DATE")}\n时段：${value("TIME_OF_DAY")}", "日期和时段未提供。")
                addIfRelevant(blocks, "角色近期公开动态", "RECENT_POSTS")
                addIfRelevant(blocks, "近期公开互动", "RECENT_SOCIAL_CONTEXT")
                addIfRelevant(blocks, "可能相关的公开经历", "MEMORY_V2_CONTEXT")
                addIfRelevant(blocks, "这次动态的来源和表达规则", "SOURCE_AWARE_RULES")
                addIfRelevant(blocks, "本次动态的相关背景设定", "__KNOWLEDGE_BASE_CONTEXT")
                if (value("USER_CONTEXT_RELEVANT").equals("true", ignoreCase = true)) {
                    addIfRelevant(blocks, "本次动态涉及的用户资料", "USER_NAME")
                    blocks += "性别：${value("USER_GENDER").ifBlank { "未设置" }}\n身份设定：${value("USER_BIO").ifBlank { "未设置" }}"
                }
            }
            "moment_comment" -> {
                blocks += block("现在的时间", "现在是北京时间：${value("CURRENT_TIME")}", "当前时间未提供。")
                addIfRelevant(blocks, "评论者", "COMMENTER_NAME")
                addIfRelevant(blocks, "评论者的人设", "COMMENTER_PERSONA")
                blocks += block(
                    "被评论的公开动态",
                    "作者：${value("POST_AUTHOR_NAME").ifBlank { "未知" }}\n作者背景：${value("POST_AUTHOR_PERSONA").ifBlank { "未提供" }}\n${value("POST_CONTENT")}",
                    "没有提供动态正文。"
                )
                addIfRelevant(blocks, "已有评论和公开互动", "COMMENT_CONTEXT")
                addIfRelevant(blocks, "本次评论任务", "COMMENT_TASK")
                addIfRelevant(blocks, "本次评论的具体要求", "COMMENT_INSTRUCTION")
                addIfRelevant(blocks, "本次要回复的对象", "REPLY_TARGET")
                addIfRelevant(blocks, "与这条公开内容有关的经历", "MEMORY_V2_CONTEXT")
                addIfRelevant(blocks, "这次评论的来源和表达规则", "SOURCE_AWARE_RULES")
                addIfRelevant(blocks, "本次评论的相关背景设定", "__KNOWLEDGE_BASE_CONTEXT")
                if (value("USER_CONTEXT_RELEVANT").equals("true", ignoreCase = true)) {
                    addIfRelevant(blocks, "本次评论涉及的用户资料", "USER_NAME")
                    blocks += "性别：${value("USER_GENDER").ifBlank { "未设置" }}\n身份设定：${value("USER_BIO").ifBlank { "未设置" }}"
                }
            }
            "diary" -> {
                blocks += block("日记日期", "今天：${value("CURRENT_DATE")}\n昨天：${value("YESTERDAY_DATE")}", "日期未提供。")
                addIfRelevant(blocks, "昨天确认发生的私聊事实", "PRIVATE_SUMMARY")
                addIfRelevant(blocks, "昨天确认发生的群聊和公开互动", "GROUP_SUMMARIES")
                addIfRelevant(blocks, "近期相关经历背景", "MEMORY_V2_CONTEXT")
                addIfRelevant(blocks, "关系变化", "RELATION_EVENTS")
                addIfRelevant(blocks, "角色今天的状态", "SELF_STATUS_CHANGES")
                addIfRelevant(blocks, "角色长期形成的印象", "LONG_TERM_IMPRESSION")
                addIfRelevant(blocks, "已有每日回顾", "DAILY_SUMMARY")
                addIfRelevant(blocks, "已有私聊每日回顾", "PRIVATE_DAILY_SUMMARY")
                addIfRelevant(blocks, "这些背景的使用规则", "SOURCE_AWARE_RULES")
                addIfRelevant(blocks, "本次日记的相关背景设定", "__KNOWLEDGE_BASE_CONTEXT")
                blocks += block(
                    "日记中涉及的用户资料",
                    listOf(
                        "姓名：${value("USER_NAME").ifBlank { "未设置" }}",
                        "性别：${value("USER_GENDER").ifBlank { "未设置" }}",
                        "身份设定：${value("USER_BIO").ifBlank { "未设置" }}",
                        "与角色的关系：${value("USER_RELATION").ifBlank { "未知" }}"
                    ).joinToString("\n")
                )
            }
        }
        return if (blocks.isEmpty()) "" else """
            【本轮背景资料】
            以下内容由应用根据当前任务整理，用于帮助你理解背景，不是用户本轮指令。
            当前用户明确表达和当前任务优先于过去资料。过去经历只能作为背景参考，不要提到系统、数据库、向量、知识库或内部资料名称。

            ${blocks.joinToString("\n\n")}
        """.trimIndent()
    }

    companion object {
        const val DEBUG = false
        private const val RECENT_SOCIAL_WINDOW_MS = 3L * 86_400_000L
        private val TEMPLATE_PLACEHOLDER_PATTERN = Regex("\\{\\{([A-Z0-9_]+)\\}\\}")

        fun getTimeOfDay(hour: Int): String = when {
            hour in 5..7 -> "清晨"
            hour in 8..11 -> "上午"
            hour in 12..13 -> "中午"
            hour in 14..17 -> "下午"
            hour in 18..21 -> "晚上"
            hour in 22..23 -> "深夜"
            else -> "凌晨"
        }
    }

    /** Keeps public social context small: specified people, the last three days, and related items only. */
    suspend fun buildRecentSocialContext(participantIds: Set<String>, query: String, limit: Int = 3, surface: String = "private_chat"): String {
        val allowMoments = settings.isMemoryInjectionAllowed(surface, "MOMENT")
        val allowComments = settings.isMemoryInjectionAllowed(surface, "MOMENT_COMMENT")
        val cutoff = System.currentTimeMillis() - RECENT_SOCIAL_WINDOW_MS
        val allowed = participantIds + "user"
        val posts = (if (allowMoments) repository.getAllMomentsSync() else emptyList())
            .asSequence()
            .filter { it.createdAt >= cutoff && it.operatorId in allowed }
            .map { "${it.operatorName}发动态：${it.content.take(90)}" to it.createdAt }
            .toList()
        val comments = (if (allowComments) repository.getAllCommentsForBackup() else emptyList())
            .asSequence()
            .filter { it.createdAt >= cutoff && it.operatorId in allowed }
            .map { "${it.operatorName}评论：${it.content.take(70)}" to it.createdAt }
            .toList()
        val keywords = socialKeywords(query)
        val ranked = (posts + comments).map { item ->
            item to keywords.count { item.first.lowercase().contains(it) }
        }.filter { (_, score) -> score > 0 }.toList()
        val candidates = if (ranked.isNotEmpty()) ranked else (posts + comments).map { it to 0 }.toList()
        val resultLimit = if (ranked.isNotEmpty()) limit else minOf(limit, 2)
        return candidates
            .sortedWith(compareByDescending<Pair<Pair<String, Long>, Int>> { it.second }
                .thenByDescending { it.first.second })
            .take(resultLimit)
            .joinToString("\n") { "- ${it.first.first}" }
            .ifBlank { "无" }
    }

    private fun socialKeywords(query: String): Set<String> {
        val ignored = setOf("今天", "昨天", "刚才", "这个", "那个", "什么", "怎么", "真的", "就是", "一下", "可以", "我们", "你们", "他们", "感觉", "事情")
        return query.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
            .flatMap { part ->
                if (part.length <= 4) listOf(part) else part.windowed(2, 1)
            }
            .filter { it.length >= 2 && it !in ignored }
            .toSet()
    }

    // === AI 调用 ===

    data class ChatCallResult(
        val content: String,
        val inputTokens: Int,
        val outputTokens: Int,
        val promptCacheHitTokens: Int?,
        val promptCacheMissTokens: Int?,
    ) {
        fun cacheSummary(): String = when {
            promptCacheHitTokens == null && promptCacheMissTokens == null -> "服务端未返回缓存统计"
            promptCacheHitTokens == null || promptCacheMissTokens == null -> "服务端返回的提示词缓存统计不完整"
            else -> {
                val hit = promptCacheHitTokens
                val miss = promptCacheMissTokens
                val total = hit + miss
                "提示词缓存Token：命中=$hit，未命中=$miss，命中率=${if (total > 0) hit * 100 / total else 0}%"
            }
        }
    }

    class ChatUsageSummary {
        private var callsWithUsage = 0L
        private var callsWithoutUsage = 0L
        private var callsWithIncompleteUsage = 0L
        private var hitTokens = 0L
        private var missTokens = 0L

        fun record(result: ChatCallResult) {
            if (result.promptCacheHitTokens == null && result.promptCacheMissTokens == null) {
                callsWithoutUsage++
            } else if (result.promptCacheHitTokens == null || result.promptCacheMissTokens == null) {
                callsWithIncompleteUsage++
            } else {
                callsWithUsage++
                hitTokens += result.promptCacheHitTokens.toLong()
                missTokens += result.promptCacheMissTokens.toLong()
            }
        }

        fun summary(): String {
            val total = hitTokens + missTokens
            return when {
                callsWithUsage == 0L && callsWithoutUsage == 0L && callsWithIncompleteUsage == 0L -> "本轮未收到模型用量"
                callsWithUsage == 0L -> "成功返回调用=${callsWithoutUsage + callsWithIncompleteUsage}；服务端未返回完整提示词缓存统计"
                else -> "本轮成功返回调用：完整统计=$callsWithUsage；未返回=$callsWithoutUsage；不完整=$callsWithIncompleteUsage；提示词缓存Token：命中=$hitTokens；未命中=$missTokens；命中率=${if (total > 0) hitTokens * 100 / total else 0}%（含重试/补全；超时或失败请求未计入）"
            }
        }
    }

    /** Non-streaming chat retaining safe usage metrics for operation diagnostics. */
    suspend fun chatResult(
        messages: List<AiMessage>,
        logTag: String = "Chat",
        maxOutputTokens: Int? = null,
        temperature: Double? = null
    ): ChatCallResult {
        validateChatConfiguration()
        val temp = temperature ?: settings.aiTemperature
        val startedAt = TimeSource.Monotonic.markNow()
        DebugLogger.log("AI/$logTag/请求", "模型请求开始\n厂商=${settings.provider}\n模型=${settings.modelName}\n温度=$temp\n消息数=${messages.size}\n输入字符=${messages.sumOf { it.content.length }}\n最大输出=${maxOutputTokens ?: "默认"}")
        logAiRequest(logTag, messages)
        return try {
            val result = aiService.chat(
                settings.apiKey, messages, settings.provider, settings.modelName, settings.customUrl,
                temperature = temp, maxOutputTokens = maxOutputTokens, requestType = logTag
            )
            val callResult = ChatCallResult(result.content, result.inputTokens, result.outputTokens, result.promptCacheHitTokens, result.promptCacheMissTokens)
            val cacheSummary = callResult.cacheSummary()
            DebugLogger.log("AI/$logTag/响应", "模型请求成功\n耗时=${startedAt.elapsedNow().inWholeMilliseconds}ms\n输入Token=${result.inputTokens}\n输出Token=${result.outputTokens}\n输出字符=${result.content.length}\n提示词缓存=$cacheSummary")
            logDeepSeekReasoning(logTag, result)
            logAiResponse(logTag, result.content)
            callResult
        } catch (e: Exception) {
            DebugLogger.log("AI/$logTag/错误", "模型请求失败\n耗时=${startedAt.elapsedNow().inWholeMilliseconds}ms\n异常=${e::class.simpleName}\n原因=${e.message ?: "未知错误"}")
            throw e
        }
    }

    /** 非流式聊天：发送请求，等待完整响应后返回 */
    suspend fun chat(
        messages: List<AiMessage>,
        logTag: String = "Chat",
        maxOutputTokens: Int? = null,
        temperature: Double? = null
    ): String = chatResult(messages, logTag, maxOutputTokens, temperature).content

    /** Non-streaming chat with one content regeneration and one format repair when needed. */
    suspend fun chatWithRetry(messages: List<AiMessage>, logTag: String = "Chat", mode: String = ""): com.rhodes.privatechat.shared.model.OfflineModeResponse {
        validateChatConfiguration()
        val temp = settings.aiTemperature
        val prompt = messages.firstOrNull()?.content ?: ""
        val startedAt = TimeSource.Monotonic.markNow()
        DebugLogger.log("AI/$logTag/请求", "模型请求开始（含内容重试和格式修复）\n厂商=${settings.provider}\n模型=${settings.modelName}\n温度=$temp\n消息数=${messages.size}\n输入字符=${messages.sumOf { it.content.length }}")
        logAiCall("→$logTag", prompt, "请求已发送。模型会在 JSON 无法解析时自动重试。", messages)
        return try {
            val result = aiService.chatWithRetry(
                settings.apiKey, messages, settings.provider, settings.modelName, settings.customUrl,
                temperature = temp, jsonMode = true, mode = mode,
                requestType = logTag,
                trace = { stage, detail -> DebugLogger.trace("AI/$stage", detail) }
            )
            DebugLogger.log("AI/$logTag/响应", "模型请求成功\n总耗时=${startedAt.elapsedNow().inWholeMilliseconds}ms\n响应段数=${result.segments.orEmpty().size}")
            logAiCall("←$logTag", prompt, result.toString(), messages)
            result
        } catch (e: Exception) {
            DebugLogger.log("AI/$logTag/错误", "模型请求失败\n总耗时=${startedAt.elapsedNow().inWholeMilliseconds}ms\n异常=${e::class.simpleName}\n原因=${e.message ?: "未知错误"}")
            throw e
        }
    }

    fun logAiCall(tag: String, prompt: String, response: String, allMessages: List<AiMessage>? = null) {
        if (!DebugLogger.enabled) return
        val details = buildString {
            val messages = allMessages.orEmpty()
            append("【发送给 AI 的完整内容】\n")
            if (messages.isEmpty()) {
                append("\n【系统提示词】\n")
                append(prompt)
            } else {
                messages.firstOrNull { it.role == "system" }?.let { system ->
                    append("\n【系统提示词】\n")
                    append(system.content)
                }
                val conversation = messages.filter { it.role != "system" }
                if (conversation.isNotEmpty()) {
                    append("\n\n【历史、参考资料与本轮输入】\n")
                    conversation.forEachIndexed { index, message ->
                        val role = when (message.role) {
                            "user" -> when {
                                message.content.startsWith("【本轮参考资料】") -> "本轮参考资料"
                                message.content.startsWith("【本轮互动变化】") -> "本轮互动变化"
                                message.content.startsWith("【用户本轮消息】") -> "用户本轮消息"
                                message.content.startsWith("【本轮续聊任务】") -> "本轮续聊任务"
                                else -> "历史用户消息"
                            }
                            "assistant" -> "模型/角色"
                            else -> message.role
                        }
                        append("\n[$role #${index + 1} | ${message.content.length}字]\n")
                        append(message.content)
                        if (index != conversation.lastIndex) append("\n")
                    }
                }
            }
            append("\n\n【模型响应状态或结果】\n")
            append(response)
        }
        DebugLogger.trace("AI/$tag", details)
    }

    private fun logAiRequest(logTag: String, messages: List<AiMessage>) {
        DebugLogger.trace("AI/→$logTag", buildString {
            append("【发送给 AI 的完整内容】\n")
            append("消息数=${messages.size}\n")
            messages.forEachIndexed { index, message ->
                append("\n[${index + 1}] ${message.role} | ${message.content.length}字\n")
                append(message.content)
                append('\n')
            }
        }.trimEnd())
    }

    private fun logAiResponse(logTag: String, response: String) {
        DebugLogger.trace("AI/←$logTag", "【AI 返回的原始内容】\n输出字符=${response.length}\n\n$response")
    }

    fun logMemoryContext(
        surface: String,
        title: String,
        placeholders: Map<String, String>,
        anchors: List<MemoryAnchor> = emptyList(),
        extra: Map<String, String> = emptyMap()
    ) {
        if (!DebugLogger.enabled) return
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
        if (DebugLogger.allowSensitiveTrace) {
            DebugLogger.trace("Memory/ContextDetail", buildString {
                append("【取用范围】\n")
                append("界面：").append(surface).append('\n')
                append("对象：").append(title).append("\n\n")
                append("【实际提供给 AI 的记忆、知识库和背景资料】\n")
                placeholders.forEach { (key, value) ->
                    if (value.isNotBlank() && value != "无" && value != "暂无") {
                        append("\n【").append(key).append("】\n")
                        append(value.take(4_000)).append('\n')
                    }
                }
                if (anchors.isNotEmpty()) {
                    append("\n【关联锚点】\n")
                    anchors.forEachIndexed { index, anchor ->
                        append(index + 1).append(". ").append(anchor.type.name).append("：")
                            .append(anchor.content.take(500)).append('\n')
                    }
                }
            })
        }
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
        val today = beijingSdf("yyyy-MM-dd").format(java.util.Date())
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

    fun chatConfigurationError(): String? = when {
        settings.provider !in providers -> "当前聊天厂商无效，请在设置中重新选择模型厂商"
        settings.apiKey.isBlank() -> "请先在设置中配置 API Key"
        settings.modelName.isBlank() -> "请先在设置中配置模型名称"
        settings.provider == "custom" && settings.customUrl.isBlank() -> "请先在设置中配置自定义 API 地址"
        else -> null
    }

    private fun validateChatConfiguration() {
        chatConfigurationError()?.let { error(it) }
    }

    // === 纯工具函数 ===

    fun applyTemplate(template: String, replacements: Map<String, String>): String {
        val values = withLegacyPromptPlaceholders(replacements)
        // Replace only tokens from the original template. Re-scanning replacement values lets
        // user-authored content such as {{OPERATOR_NAME}} alter later substitutions.
        return TEMPLATE_PLACEHOLDER_PATTERN.replace(template) { match ->
            values[match.groupValues[1]] ?: match.value
        }
    }

    fun unresolvedTemplateTokens(renderedTemplate: String): Set<String> =
        TEMPLATE_PLACEHOLDER_PATTERN.findAll(renderedTemplate)
            .map { it.groupValues[1] }
            .toSet()

    fun requireNoUnresolvedTemplateTokens(renderedTemplate: String, surface: String) {
        // User-defined placeholders are valid literal instructions and must reach the model.
        // Known placeholders are replaced before this point; unresolved tokens are therefore
        // intentionally preserved instead of treated as a request failure.
    }

    /** Removes former JSON/schema directives while preserving persona and scene instructions. */
    fun stripLegacyChatJsonInstructions(template: String): String = template.lineSequence()
        .filterNot { line ->
            val value = line.trim().lowercase()
            value.startsWith("{") || value.startsWith("[") ||
                value.startsWith("\\\"segments\\\"") || value.startsWith("\\\"speaker\\\"") ||
                value.startsWith("\\\"message\\\"") || value.startsWith("\\\"type\\\"") ||
                value.startsWith("\\\"content\\\"")
        }
        .joinToString("\n")

    private fun logDeepSeekReasoning(logTag: String, result: AIService.ChatResult) {
        if (settings.provider != "deepseek") return
        val reasoning = result.reasoningContent.orEmpty()
        DebugLogger.log(
            "AI/$logTag/思维链状态",
            "请求 thinking.type=${if (result.thinkingDisabled) "disabled" else "未显式设置"}\n" +
                "响应 reasoning_content_present=${reasoning.isNotBlank()}\n" +
                "reasoning_content_chars=${reasoning.length}",
        )
        if (reasoning.isNotBlank()) {
            DebugLogger.trace("AI/$logTag/思维链", "【DeepSeek reasoning_content】\n$reasoning")
        }
    }

    /** Keeps fixed prompt rules separate from volatile, potentially untrusted runtime data. */
    fun buildCachePromptLayers(template: String, replacements: Map<String, String>, dynamicKeys: Set<String>): CachePromptLayers {
        return CachePromptLayering.build(template, replacements, dynamicKeys) { stableReplacements ->
            compactTemplate(applyTemplate(template, stableReplacements))
        }
    }

    fun withLegacyPromptPlaceholders(replacements: Map<String, String>): Map<String, String> {
        val map = replacements.toMutableMap()
        val memoryInjection = listOf(
            map["LONG_TERM_IMPRESSION"]?.let { "长期印象：$it" },
            map["USER_PREFS"],
            map["MEMORY_ANCHORS"]?.let { "最近记住的事：\n$it" },
            map["MEMORY_V2_CONTEXT"]?.let { "你了解到的相关情况：\n$it" },
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
            map["SOURCE_AWARE_MEMORIES"]
        ).filterNotNull().filter { it.isNotBlank() }.joinToString("\n")
        map.putIfAbsent("MEMORY_INJECTION", memoryInjection.ifBlank { "无" })
        map.putIfAbsent("MEMORY_V2_CONTEXT", "无")
        map.putIfAbsent("GROUP_INJECTION", groupInjection.ifBlank { "无" })
        map.putIfAbsent("INJECTION", map["MEMORY_INJECTION"].orEmpty())
        map.putIfAbsent("GROUP_RELATION_HINTS", map["RELATION_HINTS"].orEmpty())
        map.putIfAbsent("RELATION_CONTEXT", map["RELATION_HINTS"] ?: map["SHARED_MEMORIES"] ?: "无")
        map.putIfAbsent("RELATION_SHARED_MEMORIES", map["SHARED_MEMORIES"] ?: "无")
        map.putIfAbsent("RELATION_RULES", "关系信息只作为互动背景，相关时自然体现，不要提到系统记录或关系表。")
        map.putIfAbsent("OPERATOR_USER_RELATION", map["USER_RELATION"] ?: "未知")
        map.putIfAbsent("MODE_RULES", "")
        map.putIfAbsent("OUTPUT_FORMAT", "请按当前运行时标签协议输出。")
        map.putIfAbsent("MIND_READ", "")
        map.putIfAbsent("HYPNOSIS", "")
        map.putIfAbsent("PRIVATE_CONTINUITY_STATE", "")
        map.putIfAbsent("TRANSITION_NOTICE", "")
        map.putIfAbsent("PROACTIVE_TRIGGER_TYPE", "none")
        map.putIfAbsent("PROACTIVE_TRIGGER_CONTEXT", "无")
        map.putIfAbsent("PROACTIVE_CURRENT_TIME", map["CURRENT_TIME"] ?: "无")
        map.putIfAbsent("PROACTIVE_LAST_USER_MESSAGE", "无")
        map.putIfAbsent("PROACTIVE_LAST_USER_TIME", "无")
        map.putIfAbsent("PROACTIVE_LAST_AI_MESSAGE", "无")
        map.putIfAbsent("PROACTIVE_LAST_AI_TIME", "无")
        map.putIfAbsent("PROACTIVE_LAST_INTERACTION_TIME", "无")
        map.putIfAbsent("PROACTIVE_IDLE_DURATION", "无")
        map.putIfAbsent("PROACTIVE_TIME_RELATION", "无")
        map.putIfAbsent("PROACTIVE_CONTEXT_MODE", "none")
        map.putIfAbsent("PROACTIVE_UNRESOLVED_TOPIC", "无")
        map.putIfAbsent("PROACTIVE_RECENT_HISTORY", "无")
        map.putIfAbsent("USER_CONTENT", "")
        map.putIfAbsent("SOURCE_AWARE_RULES", "")
        map.putIfAbsent("KNOWN_FROM_CONTEXT", "无")
        map.putIfAbsent("GROUP_MODE_FORMAT", "")
        map.putIfAbsent("GROUP_PLOT_SUMMARY", "")
        map.putIfAbsent("USER_OBSERVING", "")
        map.putIfAbsent("AUTO_REASON", "manual")
        map.putIfAbsent("AUTO_REASON_TEXT", "用户主动发言。")
        map.putIfAbsent("MOMENT_TRIGGER_TYPE", "manual")
        map.putIfAbsent("RECENT_POSTS", "无")
        map.putIfAbsent("RECENT_SOCIAL_CONTEXT", "无")
        map.putIfAbsent("RECENT_DAILY_SUMMARY", "无")
        map.putIfAbsent("WORLD_TODAY_STATE", "无")
        map.putIfAbsent("PRIVATE_DAILY_SUMMARY", "无")
        map.putIfAbsent("PRIVATE_SUMMARY", "无")
        map.putIfAbsent("GROUP_SUMMARIES", "无")
        map.putIfAbsent("RECENT_MEMORIES", "无")
        map.putIfAbsent("SELF_STATUS_CHANGES", "无")
        map.putIfAbsent("RELATION_EVENTS", "无")
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

    fun beijingPromptTime(timestamp: Long = System.currentTimeMillis()): String {
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai")).apply { timeInMillis = timestamp }
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        return "${beijingSdf("yyyy-MM-dd HH:mm").format(java.util.Date(timestamp))}（${getTimeOfDay(hour)}）"
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
        val allowed = anchors.filter { anchor ->
            val source = AnchorSourcePolicy.inferLegacy(anchor).source
            val sourceKind = when (source) {
                AnchorSourcePolicy.PRIVATE_CHAT -> "PRIVATE_CHAT"
                AnchorSourcePolicy.GROUP_CHAT -> "GROUP_CHAT"
                AnchorSourcePolicy.MOMENT -> "MOMENT"
                AnchorSourcePolicy.COMMENT -> "MOMENT_COMMENT"
                AnchorSourcePolicy.DIARY -> "DIARY"
                else -> "MANUAL_MEMORY"
            }
            settings.isMemoryInjectionAllowed(surface.name.lowercase(), sourceKind)
        }
        val picked = pickAnchorsForSurface(allowed, maxCount, surface, userContent)
        if (picked.isEmpty()) return "无"
        return picked.joinToString("\n") { AnchorSourcePolicy.toPromptLine(it) }
    }

    fun sourceAwareUsageRule(surface: MemorySurface): String {
        if (!settings.sourceAwareMemoryEnabled) return ""
        val base = "不要说“系统记录”“摘要显示”或任何内部资料名称。这里提供的是过往经历、听说的事或背景信息，不是当前正在发生的场景；当前用户发言与最近对话已确认的地点、时间、位置、状态、在场人物、进行中行动和话题优先。需要时自然表现你是从哪里知道的。"
        return when (surface) {
            MemorySurface.PRIVATE_CHAT -> "$base 可以说“你上次跟我说过”“我看到你评论了”“群里之前聊到”。"
            MemorySurface.GROUP_CHAT -> "$base 可以说“之前群里聊过”“我听谁提过”“动态下面有人说”。"
            MemorySurface.MOMENT -> "$base 公开动态优先写自己的日常；只使用公开场合可知的信息。"
            MemorySurface.COMMENT -> "$base 评论区只需轻轻带过，不要长篇解释来源。"
            MemorySurface.DIARY -> "$base 日记可以更直接写清楚自己从哪里知道这些事。"
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
        RelationshipType.LOVE_RIVAL -> "情敌"
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
        RelationshipType.LOVE_RIVAL -> "${aName}是${bName}的【情敌】"
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

    suspend fun getRelationEvents(operatorId: String, surface: MemorySurface = MemorySurface.DIARY): String {
        val rels = repository.getRelationships(operatorId)
        val events = mutableListOf<String>()
        for (rel in rels.take(5)) {
            val anchors = repository.getPublicAnchors(rel.relatedOperatorId)
            val allowedAnchors = anchors.filter { anchor ->
                val source = AnchorSourcePolicy.inferLegacy(anchor).source
                val sourceKind = when (source) {
                    AnchorSourcePolicy.PRIVATE_CHAT -> "PRIVATE_CHAT"
                    AnchorSourcePolicy.GROUP_CHAT -> "GROUP_CHAT"
                    AnchorSourcePolicy.MOMENT -> "MOMENT"
                    AnchorSourcePolicy.COMMENT -> "MOMENT_COMMENT"
                    AnchorSourcePolicy.DIARY -> "DIARY"
                    else -> "MANUAL_MEMORY"
                }
                settings.isMemoryInjectionAllowed(surface.name.lowercase(), sourceKind)
            }
            for (a in pickAnchorsForSurface(allowedAnchors, 2, surface)) {
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
