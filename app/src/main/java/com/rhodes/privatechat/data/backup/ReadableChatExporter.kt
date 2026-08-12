package com.rhodes.privatechat.data.backup

import com.rhodes.privatechat.shared.model.ChatMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReadableChatExporter {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    fun markdown(title: String, userName: String, messages: List<ChatMessage>): String = buildString {
        appendLine("# ${markdownSafe(title)}")
        appendLine()
        appendLine("导出时间：${format(System.currentTimeMillis())}")
        var date = ""
        messages.sortedWith(compareBy<ChatMessage> { it.timestamp }.thenBy { it.id }).forEach { message ->
            val nextDate = day(message.timestamp)
            if (nextDate != date) { date = nextDate; appendLine(); appendLine("## $date"); appendLine() }
            appendLine("${markdownSafe(if (message.isMe) userName else message.senderName)}：${markdownSafe(visible(message))}")
            appendLine()
        }
    }

    fun text(title: String, userName: String, messages: List<ChatMessage>): String = buildString {
        appendLine(title)
        appendLine("导出时间：${format(System.currentTimeMillis())}")
        appendLine()
        messages.sortedWith(compareBy<ChatMessage> { it.timestamp }.thenBy { it.id }).forEach { message ->
            appendLine("[${format(message.timestamp)}] ${if (message.isMe) userName else message.senderName}：${visible(message)}")
        }
    }

    private fun visible(message: ChatMessage): String {
        val content = if (message.type == "ai_json") visibleAiSegments(message.content) else message.content
        return content.replace(Regex("\\{\\{[A-Z0-9_]+\\}\\}"), "").trim()
    }

    private fun visibleAiSegments(raw: String): String = runCatching {
        val objectValue = json.parseToJsonElement(raw).jsonObject
        val segments = objectValue["segments"] as? JsonArray ?: return@runCatching raw
        segments.mapNotNull { element ->
            val segment = element.jsonObject
            segment["content"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        }.joinToString("\n").ifBlank { raw }
    }.getOrDefault(raw)
    private fun markdownSafe(value: String): String = value
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("*", "\\*")
        .replace("_", "\\_")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("#", "\\#")
        .replace(">", "\\>")
    private fun day(time: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(time))
    private fun format(time: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))
}
