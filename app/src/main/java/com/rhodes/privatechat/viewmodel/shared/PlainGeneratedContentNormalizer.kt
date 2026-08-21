package com.rhodes.privatechat.viewmodel.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Normalizes accidental model wrappers without turning malformed output into new content. */
internal object PlainGeneratedContentNormalizer {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val contentKeys = listOf("content", "text", "moment", "comment", "diary", "line")
    private val explanationPrefixes = listOf(
        "以下是动态", "下面是动态", "以下是评论", "下面是评论", "以下是日记", "下面是日记",
        "动态如下", "评论如下", "日记如下", "今日动态：", "发布："
    )

    fun normalize(raw: String, @Suppress("UNUSED_PARAMETER") minChars: Int = 0, @Suppress("UNUSED_PARAMETER") maxChars: Int = 0): String? {
        var content = raw.trim()
        if (content.isBlank()) return null
        content = unwrapFence(content)
        content = unwrapJson(content) ?: return null
        content = unwrapQuotes(content)
        content = removeExplanationPrefix(content)
        content = content.trim()
        if (content.startsWith("作为AI") || content.startsWith("作为 AI") || content.startsWith("As an AI", true) || content.contains("{{")) return null
        return content
    }

    private fun unwrapFence(text: String): String {
        val trimmed = text.trim()
        val match = Regex("""^\s*```(?:json|text|markdown)?\s*\n([\s\S]*?)\n?```\s*$""", RegexOption.IGNORE_CASE)
            .matchEntire(trimmed) ?: return trimmed
        return match.groupValues[1].trim()
    }

    private fun unwrapJson(text: String): String? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return trimmed
        val element = runCatching { json.parseToJsonElement(trimmed) }.getOrNull()
        // A normal sentence may legitimately start with a bracket. Reject only text that is a
        // valid but unsupported JSON wrapper; malformed prose remains ordinary text.
            ?: return trimmed
        val objectValue = element as? JsonObject ?: return null
        return contentKeys.asSequence()
            .mapNotNull { key ->
                (objectValue[key] as? JsonPrimitive)
                    ?.takeIf { it.isString }
                    ?.content
            }
            .firstOrNull { value -> value.isNotBlank() }
    }

    private fun unwrapQuotes(text: String): String {
        var result = text.trim()
        while (result.length > 1 && result.startsWith('"') && result.endsWith('"')) {
            result = result.substring(1, result.length - 1).trim()
        }
        return result
    }

    private fun removeExplanationPrefix(text: String): String {
        val firstLine = text.lineSequence().firstOrNull()?.trim().orEmpty()
        val prefix = explanationPrefixes.sortedByDescending { it.length }
            .firstOrNull { firstLine.startsWith(it) } ?: return text
        val remainder = firstLine.removePrefix(prefix).trimStart('：', ':', ' ', '\t')
        val remainingLines = text.lineSequence().drop(1).joinToString("\n").trim()
        return listOf(remainder, remainingLines).filter { it.isNotBlank() }.joinToString("\n")
    }

}
