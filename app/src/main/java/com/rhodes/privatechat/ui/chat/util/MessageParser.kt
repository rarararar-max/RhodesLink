package com.rhodes.privatechat.ui.chat.util

import androidx.compose.ui.graphics.Color
import com.rhodes.privatechat.data.db.entity.ChatMessageEntity
import com.rhodes.privatechat.ui.chat.model.ChatUiMessage
import com.rhodes.privatechat.ui.theme.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        userAvatarUri: String = ""
    ): List<ChatUiMessage> {
        return messages.flatMap { msg ->
            val mode = msg.mode
            val isOnline = mode == "online"
            when {
                msg.type == "ai_json" && isGroup -> parseGroupAiJson(msg, isOnline, senderColor, senderAvatar)
                msg.type == "ai_json" && !isGroup -> parsePrivateAiJson(msg, isOnline, aiName, aiAvatarUri)
                msg.type == "system" || msg.senderName == "系统" || msg.senderName == "" ->
                    listOf(systemMsg(msg))
                msg.type == "narration" ->
                    if (isOnline) emptyList()
                    else listOf(narrationMsg(msg))
                msg.isMe ->
                    listOf(userMsg(msg, userAvatarUri))
                else ->
                    listOf(otherMsg(msg, if (isGroup) senderColor(msg.senderName) else Primary, if (isGroup) senderAvatar(msg.senderName) else aiAvatarUri))
            }
        }
    }

    /** 群聊 ai_json：解析 JSON 数组 [{speaker, message, type}] */
    private fun parseGroupAiJson(
        msg: ChatMessageEntity,
        isOnline: Boolean,
        senderColor: (String) -> Color,
        senderAvatar: (String) -> String
    ): List<ChatUiMessage> {
        return try {
            val arr = json.parseToJsonElement(msg.content) as JsonArray
            arr.mapIndexedNotNull { idx, el ->
                val obj = el.jsonObject
                val name = obj["speaker"]?.jsonPrimitive?.content ?: return@mapIndexedNotNull null
                val content = obj["message"]?.jsonPrimitive?.content ?: return@mapIndexedNotNull null
                val msgType = obj["type"]?.jsonPrimitive?.content ?: "dialogue"
                if (content.isBlank()) return@mapIndexedNotNull null
                if (isOnline && (msgType == "narration" || name == "旁白")) return@mapIndexedNotNull null
                val uid = msg.id * 1000 + idx
                if (msgType == "narration" || name == "旁白") {
                    ChatUiMessage(uid, "旁白", TextTertiary, content, msg.timestamp,
                        isSystem = true, isNarration = true, mode = msg.mode, originalMessageId = msg.id, segmentIndex = idx)
                } else {
                    ChatUiMessage(uid, name, senderColor(name), content, msg.timestamp,
                        avatarUri = senderAvatar(name), mode = msg.mode, originalMessageId = msg.id, segmentIndex = idx)
                }
            }
        } catch (_: Exception) {
            listOf(ChatUiMessage(msg.id, msg.senderName, Gray100, msg.content, msg.timestamp,
                avatarUri = senderAvatar(msg.senderName), mode = msg.mode, originalMessageId = msg.id))
        }
    }

    /** 私聊 ai_json：解析 {emotion, segments[{type,content}]} 并展开为多条 */
    private fun parsePrivateAiJson(
        msg: ChatMessageEntity,
        isOnline: Boolean,
        aiName: String,
        aiAvatarUri: String
    ): List<ChatUiMessage> {
        val (emotion, segments) = parsePrivateJson(msg.content)
        if (emotion == null || segments.isEmpty()) {
            // JSON 解析失败，作为普通文本
            return listOf(ChatUiMessage(msg.id, aiName, Primary, msg.content, msg.timestamp,
                avatarUri = aiAvatarUri, mode = msg.mode, emotion = msg.emotion,
                activity = msg.activity, location = msg.location, originalMessageId = msg.id))
        }
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
                        isSystem = true, isNarration = true, mode = msg.mode, originalMessageId = msg.id, segmentIndex = segIdx
                    ))
                }
            } else {
                val isFirstDialogue = result.none { !it.isNarration && !it.isSystem }
                result.add(ChatUiMessage(
                    msg.id * 1000 + segIdx, aiName, Primary, seg.content, msg.timestamp,
                    avatarUri = aiAvatarUri, mode = msg.mode,
                    emotion = if (isFirstDialogue) emotion else "",
                    activity = if (isFirstDialogue) msg.activity else "",
                    location = if (isFirstDialogue) msg.location else "",
                    originalMessageId = msg.id, segmentIndex = segIdx
                ))
            }
            segIdx++
        }
        return result
    }

    /** 解析私聊 JSON 的三层容错逻辑 */
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
                    emotion to listOf(com.rhodes.privatechat.network.Segment(type = "dialogue", content = dialogue))
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
        return result
    }

    private fun systemMsg(msg: ChatMessageEntity) = ChatUiMessage(
        msg.id, msg.senderName, Gray100, msg.content, msg.timestamp,
        isSystem = true, mode = msg.mode, originalMessageId = msg.id
    )

    private fun narrationMsg(msg: ChatMessageEntity) = ChatUiMessage(
        msg.id, "旁白", TextTertiary, msg.content, msg.timestamp,
        isSystem = true, isNarration = true, mode = msg.mode, originalMessageId = msg.id
    )

    private fun userMsg(msg: ChatMessageEntity, userAvatarUri: String) = ChatUiMessage(
        msg.id, "我", Primary, msg.content, msg.timestamp,
        isMe = true, avatarUri = userAvatarUri, mode = msg.mode, originalMessageId = msg.id
    )

    private fun otherMsg(msg: ChatMessageEntity, color: Color, avatarUri: String) = ChatUiMessage(
        msg.id, msg.senderName, color, msg.content, msg.timestamp,
        avatarUri = avatarUri, mode = msg.mode,
        emotion = msg.emotion, activity = msg.activity, location = msg.location,
        originalMessageId = msg.id
    )
}
