package com.rhodes.privatechat.shared.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Extracts the text users see from supported private and group AI response payloads. */
object AiReplyPreview {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun extract(content: String, maxLength: Int = 50): String? {
        val root = parse(content) ?: return null
        val preview = when (root) {
            is JsonArray -> previewFromMessages(root, includeSpeaker = true)
            is JsonObject -> {
                val messages = root["messages"] as? JsonArray
                if (messages != null) previewFromMessages(messages, includeSpeaker = true)
                else previewFromObject(root)
            }
            else -> null
        }
        return preview?.replace(Regex("\\s+"), " ")?.trim()?.take(maxLength)?.takeIf { it.isNotBlank() }
    }

    private fun previewFromObject(root: JsonObject): String? {
        val segments = root["segments"] as? JsonArray
        if (segments != null) {
            val isGroupPayload = segments.any { element ->
                val item = element as? JsonObject ?: return@any false
                item.string("speaker").isNotBlank() || item.string("sender").isNotBlank() || item.string("name").isNotBlank()
            }
            if (isGroupPayload) previewFromMessages(segments, includeSpeaker = true)?.let { return it }
            previewFromPrivateSegments(segments)?.let { return it }
        }
        return root.string("dialogue").takeIf { it.isNotBlank() }
            ?: root.string("narration").takeIf { it.isNotBlank() }
    }

    private fun previewFromMessages(messages: JsonArray, includeSpeaker: Boolean): String? {
        for (element in messages.asReversed()) {
            val item = element as? JsonObject ?: continue
            if (item.string("recalled").equals("true", ignoreCase = true)) continue
            val text = item.string("message").ifBlank { item.string("content") }.ifBlank { item.string("text") }
            if (text.isBlank()) continue
            if (!includeSpeaker) return text
            val speaker = item.string("speaker").ifBlank { item.string("sender") }.ifBlank { item.string("name") }
            return if (speaker.isBlank()) text else "$speaker：$text"
        }
        return null
    }

    private fun previewFromPrivateSegments(segments: JsonArray): String? {
        val visible = segments.asReversed().mapNotNull { it as? JsonObject }.filterNot {
            it.string("recalled").equals("true", ignoreCase = true)
        }
        return visible.firstNotNullOfOrNull { item ->
            item.string("content").takeIf { text -> text.isNotBlank() && item.string("type").equals("dialogue", ignoreCase = true) }
        } ?: visible.firstNotNullOfOrNull { item ->
            item.string("content").takeIf { it.isNotBlank() }
        }
    }

    private fun JsonObject.string(name: String): String =
        (this[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun parse(content: String): JsonElement? {
        val cleaned = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val parsed = runCatching { json.parseToJsonElement(cleaned) }.getOrNull() ?: return null
        val nested = (parsed as? JsonPrimitive)?.contentOrNull
        return if (nested != null && (nested.trim().startsWith("{") || nested.trim().startsWith("["))) {
            runCatching { json.parseToJsonElement(nested) }.getOrNull() ?: parsed
        } else parsed
    }
}
