package com.rhodes.privatechat.ui.support

import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.viewmodel.AiSupportMessage

object AiSupportContract {
    const val noMatch = "未找到与当前问题直接相关的产品说明章节。"
    const val temperature = 0.3
    const val maxOutputTokens = 700
    private const val maxHistoryChars = 12_000
    private const val maxReferenceChars = 4_000

    private val traditionalToSimplified = mapOf(
        '體' to '体', '問' to '问', '題' to '题', '麼' to '么', '設' to '设', '定' to '定',
        '檔' to '档', '歷' to '历', '匯' to '汇', '備' to '备', '開' to '开', '關' to '关',
        '網' to '网', '絡' to '络', '載' to '载', '圖' to '图', '片' to '片', '語' to '语',
        '識' to '识', '庫' to '库', '群' to '群', '聊' to '聊', '錄' to '录', '節' to '节',
        '頁' to '页', '換' to '换', '樣' to '样', '風' to '风', '顏' to '颜', '色' to '色',
        '閃' to '闪', '退' to '退', '卡' to '卡', '當' to '当', '無' to '无', '法' to '法',
    )
    private val intentTerms = mapOf(
        "闪退" to listOf("回复失败", "常见错误", "模型", "网络"),
        "卡了" to listOf("常见错误", "网络", "模型", "回复失败"),
        "很卡" to listOf("常见错误", "网络", "模型", "回复失败"),
        "换主题" to listOf("外观", "皮肤", "主题"),
        "主题" to listOf("外观", "皮肤"),
        "加群" to listOf("群聊", "群组", "邀请"),
    )

    fun localReference(manualSections: List<String>, query: String): String {
        val terms = retrievalTerms(query)
        val selected: List<Triple<String, Int, Int>> = manualSections.mapIndexed { order: Int, section: String ->
            val normalized = normalize(section)
            val score = terms.fold(0) { total, term -> total + if (normalized.contains(term)) if (term.length >= 3) 3 else 1 else 0 }
            Triple(section, score, order)
        }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third })
            .take(3)
        if (selected.isEmpty()) return noMatch
        var remaining = maxReferenceChars
        return selected.mapNotNull { (section, _, _) ->
            if (remaining < 160) return@mapNotNull null
            val heading = section.lineSequence().firstOrNull { it.startsWith("## ") }?.removePrefix("## ")?.trim().orEmpty()
            val excerpt = relevantExcerpt(section, terms, minOf(1_400, remaining))
            remaining -= excerpt.length
            "- [章节：$heading]\n$excerpt"
        }.joinToString("\n").ifBlank { noMatch }
    }

    fun recentHistory(messages: List<AiMessage>): List<AiMessage> {
        var remaining = maxHistoryChars
        return messages.takeLast(40).asReversed().mapNotNull { message ->
            if (remaining <= 0) return@mapNotNull null
            val text = message.content.take(remaining)
            remaining -= text.length
            message.copy(content = text)
        }.asReversed()
    }

    fun historyAfter(messages: List<AiSupportMessage>, contextStartId: Long): List<AiMessage> =
        recentHistory(messages.asSequence()
            .filter { it.id > contextStartId }
            .map {
                val content = if (it.imageSummary.isBlank()) it.text else buildString {
                    append(it.text)
                    append("\n【用户发送的图片摘要】").append(it.imageSummary)
                }
                AiMessage(if (it.role == "assistant") "assistant" else "user", content)
            }
            .toList())

    fun sources(reference: String): List<String> = reference.lineSequence()
        .filter { it.startsWith("章节：") || it.startsWith("- [章节：") || it.startsWith("- [知识库：") }
        .map { it.trim() }.distinct().toList()

    fun userError(error: Throwable): String = when {
        error is kotlinx.coroutines.CancellationException -> "已停止本次客服请求。"
        error.message.orEmpty().contains("API error 401") || error.message.orEmpty().contains("API error 403") -> "模型服务认证失败，请检查模型设置中的 API Key。"
        error.message.orEmpty().contains("API error 429") -> "模型服务请求过于频繁，请稍后再试。"
        error.message.orEmpty().contains("API error 5") -> "模型服务暂时不可用，请稍后重试。"
        error.message.orEmpty().contains("超时", ignoreCase = true) || error.message.orEmpty().contains("timeout", ignoreCase = true) -> "请求超时，请检查网络后重试。"
        error.message.orEmpty().contains("网络", ignoreCase = true) -> "网络连接失败，请检查网络后重试。"
        else -> "客服请求失败，请检查模型设置和网络连接后重试。"
    }

    fun normalize(value: String): String = buildString(value.length) {
        value.lowercase().filterNot { it.isWhitespace() }.forEach { append(traditionalToSimplified[it] ?: it) }
    }

    private fun retrievalTerms(query: String): Set<String> {
        val normalized = normalize(query)
        val words = normalized.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }
        return buildSet {
            addAll(words)
            intentTerms.forEach { (intent, expansions) -> if (normalized.contains(normalize(intent))) addAll(expansions.map(::normalize)) }
            if (normalized.length >= 2) normalized.windowed(2).forEach(::add)
            if (normalized.length >= 3) normalized.windowed(3).forEach(::add)
        }
    }

    private fun relevantExcerpt(section: String, terms: Set<String>, budget: Int): String {
        val paragraphs = section.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
        val ranked: List<Triple<String, Int, Int>> = paragraphs.mapIndexed { index: Int, paragraph: String ->
            Triple(paragraph, terms.count { normalize(paragraph).contains(it) }, index)
        }.sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third })
        var remaining = budget
        return ranked.mapNotNull { (paragraph, _, _) ->
            if (remaining < 80) return@mapNotNull null
            paragraph.take(remaining).also { remaining -= it.length }
        }.joinToString("\n\n").ifBlank { section.take(budget) }
    }
}
