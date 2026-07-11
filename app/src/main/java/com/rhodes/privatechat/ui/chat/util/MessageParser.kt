package com.rhodes.privatechat.ui.chat.util

import androidx.compose.ui.graphics.Color
import com.rhodes.privatechat.data.db.entity.ChatMessageEntity
import com.rhodes.privatechat.ui.chat.model.ChatUiMessage
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.util.ChatTrace
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 将 ChatMessage 列表转换为 ChatUiMessage 列表。
 * 私聊和群聊使用不同的 JSON 解析策略。
 */
object MessageParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param isGroup 是否为群聊
     * @param senderColor 根据发送者名称返回颜色（群聊用）
     * @param senderAvatar 根据发送者名称返回头像 URI
     * @param aiName AI 方的名称（私聊用 operator.name，群聊不用）
     * @param aiAvatarUri AI 方的头像 URI（私聊用）
     * @param userAvatarUri 用户头像 URI
     */
    fun parse(
        messages: List<ChatMessageEntity>,
        isGroup: Boolean,
        senderColor: (String) -> Color = { Primary },
        senderAvatar: (String) -> String = { "" },
        aiName: String = "",
        aiAvatarUri: String = "",
        userAvatarUri: String = "",
        restartAt: Long = 0L
    ): List<ChatUiMessage> {
        ChatTrace.d("Parser", "start isGroup=$isGroup rawCount=${messages.size} ids=${ChatTrace.ids(messages.map { it.id })}")
        val parsed = messages.flatMap { msg ->
            try {
                val mode = msg.mode
                val isOnline = mode == "online"
                val result = when {
                    msg.type == "ai_json" && isGroup -> parseGroupAiJson(msg, isOnline, senderColor, senderAvatar, restartAt)
                    msg.type == "ai_json" && !isGroup -> parsePrivateAiJson(msg, isOnline, aiName, aiAvatarUri, restartAt)
                    msg.type == "image" -> listOf(imageMsg(msg, if (msg.isMe) userAvatarUri else if (isGroup) senderAvatar(msg.senderName) else aiAvatarUri, if (isGroup) senderColor(msg.senderName) else Primary, restartAt))
                    msg.type == "system" || msg.senderName == "系统" || msg.senderName == "" ->
                        listOf(systemMsg(msg, restartAt))
                    msg.type == "narration" ->
                        if (isOnline) emptyList() else listOf(narrationMsg(msg, restartAt))
                    msg.isMe ->
                        listOf(userMsg(msg, userAvatarUri, restartAt))
                    else ->
                        listOf(otherMsg(msg, if (isGroup) senderColor(msg.senderName) else Primary, if (isGroup) senderAvatar(msg.senderName) else aiAvatarUri, restartAt))
                }
                ChatTrace.d("Parser", "msg id=${msg.id} type=${msg.type} out=${result.size}")
                result
            } catch (e: Exception) {
                ChatTrace.e("Parser", "ERROR id=${msg.id} type=${msg.type} sender=${msg.senderName} content=${ChatTrace.short(msg.content)} err=${e.message}", e)
                listOf(ChatUiMessage(msg.id, msg.senderName.ifBlank { "系统" }, Gray100, msg.content.ifBlank { "[消息解析失败]" }, msg.timestamp, isSystem = msg.type == "system", originalMessageId = msg.id))
            }
        }
        ChatTrace.d("Parser", "done isGroup=$isGroup resultCount=${parsed.size} ids=${ChatTrace.ids(parsed.map { it.id })}")
        return parsed
    }

    private fun imageMsg(msg: ChatMessageEntity, avatarUri: String, color: Color, restartAt: Long): ChatUiMessage {
        val root = runCatching { json.parseToJsonElement(msg.content).jsonObject }.getOrNull()
        val imageUri = root?.get("imageUri")?.jsonPrimitive?.contentOrNull.orEmpty()
        val caption = root?.get("caption")?.jsonPrimitive?.contentOrNull.orEmpty()
        return ChatUiMessage(
            id = msg.id,
            senderName = msg.senderName,
            senderColor = color,
            content = caption,
            timestamp = msg.timestamp,
            isMe = msg.isMe,
            avatarUri = avatarUri,
            mode = msg.mode,
            isArchived = isArchived(msg, restartAt),
            imageUri = imageUri,
            originalMessageId = msg.id
        )
    }

    /** 群聊 ai_json：解析 JSON 数组 [{speaker, message, type}] */
    private fun parseGroupAiJson(
        msg: ChatMessageEntity,
        isOnline: Boolean,
        senderColor: (String) -> Color,
        senderAvatar: (String) -> String,
        restartAt: Long
    ): List<ChatUiMessage> {
        return try {
            val root = json.parseToJsonElement(msg.content)
            val arr = when (root) {
                is JsonArray -> root
                is JsonObject -> (root["messages"] as? JsonArray) ?: (root["segments"] as? JsonArray) ?: JsonArray(emptyList())
                else -> JsonArray(emptyList())
            }
            val result = arr.mapIndexedNotNull { idx, el ->
                val obj = el.jsonObject
                val rawName = obj["speaker"]?.jsonPrimitive?.content ?: obj["sender"]?.jsonPrimitive?.content ?: obj["name"]?.jsonPrimitive?.content ?: return@mapIndexedNotNull null
                val rawContent = obj["message"]?.jsonPrimitive?.content ?: obj["content"]?.jsonPrimitive?.content ?: obj["text"]?.jsonPrimitive?.content ?: return@mapIndexedNotNull null
                val normalized = normalizeGroupBubble(rawName, obj["type"]?.jsonPrimitive?.content ?: "dialogue", rawContent)
                val name = normalized.first
                val msgType = normalized.second
                val content = normalized.third
                if (content.isBlank()) return@mapIndexedNotNull null
                if (isOnline && (msgType == "narration" || name == "旁白")) return@mapIndexedNotNull null
                val uid = msg.id * 1000 + idx
                if (msgType == "narration" || name == "旁白") {
                    ChatUiMessage(uid, "旁白", TextTertiary, content, msg.timestamp,
                        isSystem = true, isNarration = true, mode = msg.mode, isArchived = isArchived(msg, restartAt), originalMessageId = msg.id, segmentIndex = idx)
                } else {
                    ChatUiMessage(uid, name, senderColor(name), content, msg.timestamp,
                        avatarUri = senderAvatar(name), mode = msg.mode, isArchived = isArchived(msg, restartAt), originalMessageId = msg.id, segmentIndex = idx)
                }
            }
            result
        } catch (_: Exception) {
            listOf(ChatUiMessage(msg.id, msg.senderName, Gray100, safeDisplayText(msg.content), msg.timestamp,
                avatarUri = senderAvatar(msg.senderName), mode = msg.mode, originalMessageId = msg.id))
        }
    }

    /** 私聊 ai_json：解析 {emotion, segments[{type,content}]} 并展开为多条 */
    private fun parsePrivateAiJson(
        msg: ChatMessageEntity,
        isOnline: Boolean,
        aiName: String,
        aiAvatarUri: String,
        restartAt: Long
    ): List<ChatUiMessage> {
        val (emotion, segments) = parsePrivateJson(msg.content)
        if (emotion == null || segments.isEmpty()) {
            return listOf(ChatUiMessage(msg.id, aiName, Primary, safeDisplayText(msg.content), msg.timestamp,
                avatarUri = aiAvatarUri, mode = msg.mode, emotion = msg.emotion,
                activity = msg.activity, location = msg.location, isArchived = isArchived(msg, restartAt), originalMessageId = msg.id))
        }
        ChatTrace.d("Parser.PrivateJson", "id=${msg.id} segments=${segments.size} mode=${msg.mode}")
        val result = mutableListOf<ChatUiMessage>()
        // 先添加一条元数据消息（包含 emotion/location/activity），但只在有值时
        if (emotion.isNotBlank() || msg.location.isNotBlank() || msg.activity.isNotBlank()) {
            // 不单独添加元数据消息，而是附加到第一条 dialogue 消息上
        }
        var segIdx = 0
        for (seg in segments) {
            if (seg.type == "narration") {
                if (!isOnline) {
                    result.add(ChatUiMessage(
                        msg.id * 1000 + segIdx, "旁白", TextTertiary, seg.content, msg.timestamp,
                        isSystem = true, isNarration = true, mode = msg.mode, isArchived = isArchived(msg, restartAt), originalMessageId = msg.id, segmentIndex = segIdx
                    ))
                }
            } else {
                result.add(ChatUiMessage(
                    msg.id * 1000 + segIdx, aiName, Primary, seg.content, msg.timestamp,
                    avatarUri = aiAvatarUri, mode = msg.mode,
                    emotion = emotion, activity = msg.activity, location = msg.location,
                    isArchived = isArchived(msg, restartAt),
                    originalMessageId = msg.id, segmentIndex = segIdx
                ))
            }
            segIdx++
        }
        return result
    }

    /** 解析私聊 JSON：标准解析优先，失败后用宽松扫描兜底。 */
    private fun parsePrivateJson(content: String): Pair<String?, List<com.rhodes.privatechat.network.Segment>> {
        fun parse(raw: String): Pair<String?, List<com.rhodes.privatechat.network.Segment>> = try {
            val obj = json.parseToJsonElement(raw).jsonObject
            val emotion = obj["emotion"]?.jsonPrimitive?.content ?: ""
            val segments = (obj["segments"] as? JsonArray)?.map {
                json.decodeFromString<com.rhodes.privatechat.network.Segment>(it.toString())
            } ?: emptyList()
            if (segments.isEmpty()) {
                val dialogue = obj["dialogue"]?.jsonPrimitive?.content
                if (!dialogue.isNullOrBlank()) {
                    if (looksLikeJson(dialogue)) {
                        val nested = parsePrivateJson(dialogue)
                        if (nested.second.isNotEmpty()) nested else emotion to listOf(com.rhodes.privatechat.network.Segment(type = "dialogue", content = dialogue))
                    } else {
                        emotion to listOf(com.rhodes.privatechat.network.Segment(type = "dialogue", content = dialogue))
                    }
                } else {
                    emotion to segments
                }
            } else {
                emotion to segments
            }
        } catch (_: Exception) { null to emptyList() }

        var result = parse(content)
        if (result.first == null) {
            val cleaned = content.trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                .replace("，", ",").replace("：", ":")
            result = parse(cleaned)
        }
        if (result.first == null) {
            var s = content.trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                .replace("，", ",").replace("：", ":")
            s = s.replace(", }", "}").replace(",}", "}")
            if (!s.startsWith("{")) { val start = s.indexOf('{'); if (start >= 0) s = s.substring(start) }
            if (!s.endsWith("}")) { val end = s.lastIndexOf('}'); if (end >= 0) s = s.substring(0, end + 1) }
            result = parse(s)
        }
        if (result.first == null || result.second.isEmpty()) {
            result = parsePrivateJsonLenient(content)
        }
        return result
    }

    private fun parsePrivateJsonLenient(content: String): Pair<String?, List<com.rhodes.privatechat.network.Segment>> {
        var s = content.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            .replace("，", ",").replace("：", ":")
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start >= 0 && end > start) s = s.substring(start, end + 1)

        val emotion = extractStringFieldLenient(s, "emotion") ?: ""
        val segments = mutableListOf<com.rhodes.privatechat.network.Segment>()
        val array = extractArrayBlock(s, "segments")
        if (array != null) {
            splitObjectBlocksLenient(array).forEach { obj ->
                val type = extractStringFieldLenient(obj, "type") ?: "dialogue"
                val text = extractStringFieldLenient(obj, "content")
                    ?: extractStringFieldLenient(obj, "message")
                    ?: extractStringFieldLenient(obj, "text")
                if (!text.isNullOrBlank()) {
                    segments.add(com.rhodes.privatechat.network.Segment(type = type.ifBlank { "dialogue" }, content = text))
                }
            }
        }
        if (segments.isEmpty()) {
            extractStringFieldLenient(s, "dialogue")?.takeIf { it.isNotBlank() }?.let {
                segments.add(com.rhodes.privatechat.network.Segment(type = "dialogue", content = it))
            }
        }
        if (segments.isEmpty()) {
            extractStringFieldLenient(s, "narration")?.takeIf { it.isNotBlank() }?.let {
                segments.add(com.rhodes.privatechat.network.Segment(type = "narration", content = it))
            }
        }
        return if (segments.isEmpty()) null to emptyList() else emotion to segments
    }

    private fun normalizeGroupBubble(name: String, type: String, content: String): Triple<String, String, String> {
        val stripped = stripSpeakerPrefix(content)
        var finalName = name.trim().ifBlank { "旁白" }
        var finalType = if (type.equals("narration", true) || type == "旁白") "narration" else "dialogue"
        var finalContent = stripped.second.trim().ifBlank { content.trim() }
        if (stripped.first.isNotBlank() && finalName == "旁白") finalName = stripped.first
        if (finalName == "旁白") finalType = "narration"
        if (finalType == "narration") finalName = "旁白"
        if (finalType == "dialogue" && looksNarrationLike(finalContent)) {
            finalName = "旁白"
            finalType = "narration"
        }
        return Triple(finalName, finalType, finalContent)
    }

    private fun stripSpeakerPrefix(content: String): Pair<String, String> {
        val idx = listOf(content.indexOf('：'), content.indexOf(':')).filter { it in 1..12 }.minOrNull() ?: return "" to content
        val prefix = content.substring(0, idx).trim(' ', '“', '”', '"')
        val rest = content.substring(idx + 1).trim()
        return prefix to rest
    }

    private fun looksNarrationLike(content: String): Boolean {
        val text = content.take(80)
        return listOf("牌桌上", "气氛", "众人", "看向", "走到", "坐在", "站在", "叹了口气", "笑了笑", "皱了皱", "沉默了").any { text.contains(it) }
    }

    private fun safeDisplayText(content: String): String {
        val parsed = parsePrivateJson(content)
        parsed.second.firstOrNull { it.type == "dialogue" }?.content?.takeIf { it.isNotBlank() }?.let { return it }
        parsed.second.firstOrNull()?.content?.takeIf { it.isNotBlank() }?.let { return it }
        extractStringFieldLenient(content, "dialogue")?.takeIf { it.isNotBlank() && !looksLikeJson(it) }?.let { return it }
        extractStringFieldLenient(content, "content")?.takeIf { it.isNotBlank() && !looksLikeJson(it) }?.let { return it }
        extractStringFieldLenient(content, "message")?.takeIf { it.isNotBlank() && !looksLikeJson(it) }?.let { return it }
        return if (looksLikeJson(content)) "[消息格式异常，已隐藏原始结构]" else content.ifBlank { "[消息解析失败]" }
    }

    private fun looksLikeJson(content: String): Boolean {
        val t = content.trim()
        return (t.startsWith("{") || t.startsWith("[")) && (t.contains("segments") || t.contains("dialogue") || t.contains("content") || t.contains("message"))
    }

    private fun extractArrayBlock(raw: String, key: String): String? {
        val keyIndex = raw.indexOf("\"$key\"")
        if (keyIndex < 0) return null
        val start = raw.indexOf('[', keyIndex)
        if (start < 0) return null
        var depth = 0
        for (i in start until raw.length) {
            when (raw[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return raw.substring(start + 1, i)
                }
            }
        }
        val end = raw.lastIndexOf(']')
        return if (end > start) raw.substring(start + 1, end) else null
    }

    private fun splitObjectBlocksLenient(raw: String): List<String> {
        val result = mutableListOf<String>()
        var start = -1
        var depth = 0
        for (i in raw.indices) {
            when (raw[i]) {
                '{' -> { if (depth == 0) start = i; depth++ }
                '}' -> {
                    if (depth > 0) depth--
                    if (depth == 0 && start >= 0) {
                        result.add(raw.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }
        return result
    }

    private fun extractStringFieldLenient(raw: String, key: String): String? {
        val keyIndex = raw.indexOf("\"$key\"")
        if (keyIndex < 0) return null
        val colon = raw.indexOf(':', keyIndex)
        if (colon < 0) return null
        val startQuote = raw.indexOf('"', colon + 1)
        if (startQuote < 0) return null
        findFieldBoundary(raw, startQuote + 1)?.let { end ->
            return unescapeJsonString(raw.substring(startQuote + 1, end).trim())
        }
        var i = startQuote + 1
        while (i < raw.length) {
            if (raw[i] == '"' && raw.getOrNull(i - 1) != '\\') {
                val next = raw.drop(i + 1).firstOrNull { !it.isWhitespace() }
                if (next == ',' || next == '}' || next == ']') {
                    return unescapeJsonString(raw.substring(startQuote + 1, i).trim())
                }
            }
            i++
        }
        return null
    }

    private fun findFieldBoundary(raw: String, start: Int): Int? {
        var i = start
        while (i < raw.length) {
            if (raw[i] == '"' && raw.getOrNull(i - 1) != '\\') {
                var j = i + 1
                while (j < raw.length && raw[j].isWhitespace()) j++
                if (j >= raw.length || raw[j] == ',' || raw[j] == '}' || raw[j] == ']') return i
                if (raw.startsWith(",\"type\"", i + 1) || raw.startsWith(",\"content\"", i + 1) || raw.startsWith(",\"message\"", i + 1)) return i
            }
            i++
        }
        return null
    }

    private fun unescapeJsonString(value: String): String = value
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\\", "\\")

    private fun systemMsg(msg: ChatMessageEntity, restartAt: Long) = ChatUiMessage(
        msg.id, msg.senderName, Gray100, msg.content, msg.timestamp,
        isSystem = true, mode = msg.mode, isArchived = isArchived(msg, restartAt), originalMessageId = msg.id
    )

    private fun narrationMsg(msg: ChatMessageEntity, restartAt: Long) = ChatUiMessage(
        msg.id, "旁白", TextTertiary, msg.content, msg.timestamp,
        isSystem = true, isNarration = true, mode = msg.mode, isArchived = isArchived(msg, restartAt), originalMessageId = msg.id
    )

    private fun userMsg(msg: ChatMessageEntity, userAvatarUri: String, restartAt: Long) = ChatUiMessage(
        msg.id, "我", Primary, msg.content, msg.timestamp,
        isMe = true, avatarUri = userAvatarUri, mode = msg.mode, isArchived = isArchived(msg, restartAt), originalMessageId = msg.id
    )

    private fun otherMsg(msg: ChatMessageEntity, color: Color, avatarUri: String, restartAt: Long) = ChatUiMessage(
        msg.id, msg.senderName, color, msg.content, msg.timestamp,
        avatarUri = avatarUri, mode = msg.mode,
        emotion = msg.emotion, activity = msg.activity, location = msg.location,
        isArchived = isArchived(msg, restartAt), originalMessageId = msg.id
    )

    private fun isArchived(msg: ChatMessageEntity, restartAt: Long): Boolean = restartAt > 0L && msg.timestamp < restartAt
}
