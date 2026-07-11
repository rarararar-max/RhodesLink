package com.rhodes.privatechat.viewmodel.shared

import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.shared.model.ChatMessage
import com.rhodes.privatechat.shared.model.MomentComment
import com.rhodes.privatechat.shared.model.Moment
import com.rhodes.privatechat.shared.model.MemoryBatch
import com.rhodes.privatechat.shared.model.MemoryItem
import com.rhodes.privatechat.shared.model.MemoryLevel
import com.rhodes.privatechat.shared.model.MemorySourceItem
import com.rhodes.privatechat.shared.model.MemorySourceKind
import com.rhodes.privatechat.shared.network.AIService
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.shared.vector.MemoryVectorService
import com.rhodes.privatechat.shared.vector.VectorMemory
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

class MemoryV2Pipeline(
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    private val aiService: AIService,
    private val memoryVectorService: MemoryVectorService? = null,
    private val userNicknameProvider: () -> String,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun ingestPrivateChat(sessionId: String, operatorId: String, operatorName: String, messages: List<ChatMessage>, currentRound: Int) {
        if (messages.isEmpty()) return
        val sourceText = messages.joinToString("\n") { formatPrivateMessage(it) }
        val source = MemorySourceItem(
            sourceKind = MemorySourceKind.PRIVATE_CHAT,
            ownerType = "operator",
            ownerId = operatorId,
            sourceRefId = sessionId,
            contentText = sourceText,
            timestamp = messages.lastOrNull()?.timestamp ?: System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )
        val sourceId = repository.insertMemorySource(source)

        val l1 = extractL1(
            sourceKind = MemorySourceKind.PRIVATE_CHAT,
            text = sourceText,
            ownerType = "operator",
            ownerId = operatorId,
            sourceRefId = sessionId,
            sessionId = sessionId,
            operatorName = operatorName
        )
        if (l1.isNotEmpty()) {
            saveMemoryItems(l1)
            repository.saveMemoryBatch(MemoryBatch(
                ownerType = "operator",
                ownerId = operatorId,
                sourceKind = MemorySourceKind.PRIVATE_CHAT,
                targetLevel = MemoryLevel.L1,
                inputCount = messages.size,
                outputCount = l1.size,
                windowStart = messages.first().timestamp,
                windowEnd = messages.last().timestamp,
                promptVersion = "memory_v2_l1_v1",
                createdAt = System.currentTimeMillis()
            ))
        }
        if (sourceId > 0) repository.markMemorySourceProcessedL1(sourceId)
        maybePromotePrivateMemory(operatorId, thresholdL1 = 20, thresholdL2 = 10)
    }

    suspend fun ingestGroupChat(groupId: String, groupName: String, messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        val sourceText = messages.joinToString("\n") { formatGroupMessage(groupName, it) }
        val sourceId = repository.insertMemorySource(
            MemorySourceItem(
                sourceKind = MemorySourceKind.GROUP_CHAT,
                ownerType = "group",
                ownerId = groupId,
                sourceRefId = groupId,
                contentText = sourceText,
                timestamp = messages.lastOrNull()?.timestamp ?: System.currentTimeMillis(),
                createdAt = System.currentTimeMillis()
            )
        )
        val l1 = extractGroupL1(groupId, groupName, sourceText)
        if (l1.isNotEmpty()) {
            saveMemoryItems(l1)
            repository.saveMemoryBatch(MemoryBatch(
                ownerType = "group",
                ownerId = groupId,
                sourceKind = MemorySourceKind.GROUP_CHAT,
                targetLevel = MemoryLevel.L1,
                inputCount = messages.size,
                outputCount = l1.size,
                windowStart = messages.first().timestamp,
                windowEnd = messages.last().timestamp,
                promptVersion = "memory_v2_group_l1_v1",
                createdAt = System.currentTimeMillis()
            ))
        }
        if (sourceId > 0) repository.markMemorySourceProcessedL1(sourceId)
    }

    suspend fun ingestMoment(moment: Moment, contextGroup: String = "") {
        val sourceText = formatMoment(moment)
        val sourceId = repository.insertMemorySource(
            MemorySourceItem(
                sourceKind = MemorySourceKind.MOMENT,
                ownerType = "operator",
                ownerId = moment.operatorId,
                sourceRefId = moment.id.toString(),
                contentText = sourceText,
                timestamp = moment.createdAt,
                createdAt = System.currentTimeMillis()
            )
        )
        val l1 = extractEventL1(MemorySourceKind.MOMENT, "operator", moment.operatorId, moment.id.toString(), "", sourceText)
        if (l1.isNotEmpty()) {
            saveMemoryItems(l1)
            repository.saveMemoryBatch(MemoryBatch(
                ownerType = "operator",
                ownerId = moment.operatorId,
                sourceKind = MemorySourceKind.MOMENT,
                targetLevel = MemoryLevel.L1,
                inputCount = 1,
                outputCount = l1.size,
                windowStart = moment.createdAt,
                windowEnd = moment.createdAt,
                promptVersion = "memory_v2_moment_l1_v1",
                createdAt = System.currentTimeMillis()
            ))
        }
        if (sourceId > 0) repository.markMemorySourceProcessedL1(sourceId)
    }

    suspend fun ingestMomentComment(comment: MomentComment, momentId: Long) {
        val sourceText = formatMomentComment(comment, momentId)
        val sourceId = repository.insertMemorySource(
            MemorySourceItem(
                sourceKind = MemorySourceKind.MOMENT_COMMENT,
                ownerType = "operator",
                ownerId = comment.operatorId,
                sourceRefId = comment.id.toString(),
                contentText = sourceText,
                timestamp = comment.createdAt,
                createdAt = System.currentTimeMillis()
            )
        )
        val l1 = extractEventL1(MemorySourceKind.MOMENT_COMMENT, "operator", comment.operatorId, comment.id.toString(), "", sourceText)
        if (l1.isNotEmpty()) {
            saveMemoryItems(l1)
            repository.saveMemoryBatch(MemoryBatch(
                ownerType = "operator",
                ownerId = comment.operatorId,
                sourceKind = MemorySourceKind.MOMENT_COMMENT,
                targetLevel = MemoryLevel.L1,
                inputCount = 1,
                outputCount = l1.size,
                windowStart = comment.createdAt,
                windowEnd = comment.createdAt,
                promptVersion = "memory_v2_comment_l1_v1",
                createdAt = System.currentTimeMillis()
            ))
        }
        if (sourceId > 0) repository.markMemorySourceProcessedL1(sourceId)
    }

    suspend fun maybePromotePrivateMemory(operatorId: String, thresholdL1: Int, thresholdL2: Int) {
        val now = System.currentTimeMillis()
        val l1Items = repository.getActiveMemoryItemsByLevel("operator", operatorId, MemoryLevel.L1, now)
        if (l1Items.size >= thresholdL1) {
            val prompt = buildLevelPrompt(MemoryV2PromptTemplates.L2, l1Items)
            val raw = withTimeout(15_000) {
                aiService.chat(
                    settings.apiKey,
                    listOf(AiMessage("system", prompt)),
                    settings.provider,
                    settings.modelName,
                    settings.customUrl,
                    temperature = settings.aiTemperature
                ).content
            }
            val l2Items = parseMemoryItems(raw, "operator", operatorId, MemoryLevel.L2, MemorySourceKind.PRIVATE_CHAT)
            if (l2Items.isNotEmpty()) {
                saveMemoryItems(l2Items)
                repository.archiveMemoryItemsByLevel("operator", operatorId, MemoryLevel.L1, System.currentTimeMillis())
                repository.saveMemoryBatch(MemoryBatch(
                    ownerType = "operator",
                    ownerId = operatorId,
                    sourceKind = MemorySourceKind.PRIVATE_CHAT,
                    targetLevel = MemoryLevel.L2,
                    inputCount = l1Items.size,
                    outputCount = l2Items.size,
                    windowStart = l1Items.minOfOrNull { it.createdAt } ?: System.currentTimeMillis(),
                    windowEnd = l1Items.maxOfOrNull { it.createdAt } ?: System.currentTimeMillis(),
                    promptVersion = "memory_v2_l2_v1",
                    createdAt = System.currentTimeMillis()
                ))
            }
        }

        val l2Items = repository.getActiveMemoryItemsByLevel("operator", operatorId, MemoryLevel.L2, now)
        if (l2Items.size >= thresholdL2) {
            val prompt = buildLevelPrompt(MemoryV2PromptTemplates.L3, l2Items)
            val raw = withTimeout(15_000) {
                aiService.chat(
                    settings.apiKey,
                    listOf(AiMessage("system", prompt)),
                    settings.provider,
                    settings.modelName,
                    settings.customUrl,
                    temperature = settings.aiTemperature
                ).content
            }
            val l3Items = parseMemoryItems(raw, "operator", operatorId, MemoryLevel.L3, MemorySourceKind.PRIVATE_CHAT)
            if (l3Items.isNotEmpty()) {
                saveMemoryItems(l3Items)
                repository.archiveMemoryItemsByLevel("operator", operatorId, MemoryLevel.L2, System.currentTimeMillis())
                repository.saveMemoryBatch(MemoryBatch(
                    ownerType = "operator",
                    ownerId = operatorId,
                    sourceKind = MemorySourceKind.PRIVATE_CHAT,
                    targetLevel = MemoryLevel.L3,
                    inputCount = l2Items.size,
                    outputCount = l3Items.size,
                    windowStart = l2Items.minOfOrNull { it.createdAt } ?: System.currentTimeMillis(),
                    windowEnd = l2Items.maxOfOrNull { it.createdAt } ?: System.currentTimeMillis(),
                    promptVersion = "memory_v2_l3_v1",
                    createdAt = System.currentTimeMillis()
                ))
            }
        }
    }

    suspend fun buildPrivateMemoryContext(operatorId: String, limitL1: Int, limitL2: Int, limitL3: Int): String {
        val now = System.currentTimeMillis()
        val l1 = repository.getActiveMemoryItemsByLevel("operator", operatorId, MemoryLevel.L1, now).take(limitL1)
        val l2 = repository.getActiveMemoryItemsByLevel("operator", operatorId, MemoryLevel.L2, now).take(limitL2)
        val l3 = repository.getActiveMemoryItemsByLevel("operator", operatorId, MemoryLevel.L3, now).take(limitL3)
        return buildString {
            if (l3.isNotEmpty()) {
                append("【长期记忆】\n")
                l3.forEach { append("- ").append(it.content).append('\n') }
            }
            if (l2.isNotEmpty()) {
                append("【中期记忆】\n")
                l2.forEach { append("- ").append(it.content).append('\n') }
            }
            if (l1.isNotEmpty()) {
                append("【近期记忆】\n")
                l1.forEach { append("- ").append(it.content).append('\n') }
            }
        }.trim()
    }

    private fun formatPrivateMessage(msg: ChatMessage): String {
        val time = if (msg.timestamp > 0) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
            sdf.format(java.util.Date(msg.timestamp))
        } else "unknown"
        val speaker = if (msg.isMe) "用户" else msg.senderName.ifBlank { "AI" }
        return "[$time] $speaker：${msg.content.take(240)}"
    }

    private suspend fun extractL1(
        sourceKind: MemorySourceKind,
        text: String,
        ownerType: String,
        ownerId: String,
        sourceRefId: String,
        sessionId: String,
        operatorName: String
    ): List<MemoryItem> {
        val prompt = MemoryV2PromptTemplates.getL1(sourceKind.name) + "\n系统提供的当前昵称：${userNicknameProvider()}\n干员：$operatorName\n内容：\n$text\n"
        val raw = withTimeout(15_000) {
            aiService.chat(
                settings.apiKey,
                listOf(AiMessage("system", prompt)),
                settings.provider,
                settings.modelName,
                settings.customUrl,
                temperature = settings.aiTemperature
            ).content
        }
        return parseMemoryItems(raw, ownerType, ownerId, MemoryLevel.L1, sourceKind, sourceRefId, sessionId)
    }

    private suspend fun extractGroupL1(groupId: String, groupName: String, text: String): List<MemoryItem> {
        val prompt = MemoryV2PromptTemplates.getL1("GROUP_CHAT") + "\n系统提供的当前昵称：${userNicknameProvider()}\n群聊：$groupName\n内容：\n$text\n"
        val raw = withTimeout(15_000) {
            aiService.chat(
                settings.apiKey,
                listOf(AiMessage("system", prompt)),
                settings.provider,
                settings.modelName,
                settings.customUrl,
                temperature = settings.aiTemperature
            ).content
        }
        return parseMemoryItems(raw, "group", groupId, MemoryLevel.L1, MemorySourceKind.GROUP_CHAT, groupId, groupId)
    }

    private suspend fun extractEventL1(sourceKind: MemorySourceKind, ownerType: String, ownerId: String, sourceRefId: String, sessionId: String, text: String): List<MemoryItem> {
        val prompt = MemoryV2PromptTemplates.getL1("MOMENT") + "\n系统提供的当前昵称：${userNicknameProvider()}\n内容：\n$text\n"
        val raw = withTimeout(15_000) {
            aiService.chat(
                settings.apiKey,
                listOf(AiMessage("system", prompt)),
                settings.provider,
                settings.modelName,
                settings.customUrl,
                temperature = settings.aiTemperature
            ).content
        }
        return parseMemoryItems(raw, ownerType, ownerId, MemoryLevel.L1, sourceKind, sourceRefId, sessionId)
    }

    private fun formatGroupMessage(groupName: String, msg: ChatMessage): String {
        val time = if (msg.timestamp > 0) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
            sdf.format(java.util.Date(msg.timestamp))
        } else "unknown"
        val speaker = if (msg.isMe) "用户" else msg.senderName.ifBlank { "成员" }
        return "[$time] 群聊「$groupName」中，$speaker：${msg.content.take(240)}"
    }

    private fun formatMoment(moment: Moment): String {
        val time = if (moment.createdAt > 0) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
            sdf.format(java.util.Date(moment.createdAt))
        } else "unknown"
        return "[$time] ${moment.operatorName}发布动态：${moment.content.take(240)}"
    }

    private fun formatMomentComment(comment: MomentComment, momentId: Long): String {
        val time = if (comment.createdAt > 0) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
            sdf.format(java.util.Date(comment.createdAt))
        } else "unknown"
        return "[$time] ${comment.operatorName}评论动态#$momentId：${comment.content.take(240)}"
    }

    private fun buildLevelPrompt(base: String, items: List<MemoryItem>): String {
        return "$base\n输入内容：\n${json.encodeToString(ListSerializer(MemoryItem.serializer()), items)}"
    }

    private fun parseMemoryItems(raw: String, ownerType: String, ownerId: String, level: MemoryLevel, sourceKind: MemorySourceKind, sourceRefId: String = "", sessionId: String = ""): List<MemoryItem> {
        return try {
            val clean = aiService.cleanJson(raw)
            val arr = json.decodeFromString(ListSerializer(JsonObject.serializer()), clean)
            arr.mapNotNull { obj ->
                val type = textValue(obj["type"])?.takeIf { it in allowedTypes } ?: return@mapNotNull null
                val content = sanitizeContent(textValue(obj["content"]).orEmpty()).takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val nickname = textValue(obj["nickname"])?.takeIf { it.isNotBlank() } ?: userNicknameProvider()
                val importance = intValue(obj["importance"]).coerceIn(0, 100)
                val privacy = textValue(obj["privacy"])?.takeIf { it in allowedPrivacy } ?: defaultPrivacy(sourceKind)
                val unmetNeed = booleanValue(obj["unmet_need"])
                val location = textValue(obj["location"])?.takeIf { it.isNotBlank() }
                val emotionValence = textValue(obj["emotion_valence"])?.takeIf { it in allowedEmotionValence } ?: "neutral"
                val eventTime = textValue(obj["event_time"])?.takeIf { it.isNotBlank() }
                val scheduledTime = textValue(obj["scheduled_time"])?.takeIf { it.isNotBlank() }
                val action = textValue(obj["action"]) ?: ""
                val careType = textValue(obj["care_type"])?.takeIf { it in allowedCareTypes } ?: "none"
                MemoryItem(
                    ownerType = ownerType,
                    ownerId = ownerId,
                    memoryLevel = level,
                    memoryType = type,
                    sourceKind = sourceKind,
                    sourceRefId = sourceRefId,
                    sessionId = sessionId,
                    content = content,
                    nickname = nickname,
                    importance = importance,
                    privacy = privacy,
                    unmetNeed = unmetNeed,
                    location = location,
                    emotionValence = emotionValence,
                    eventTime = eventTime,
                    scheduledTime = scheduledTime,
                    action = action,
                    careType = careType,
                    rawJson = obj.toString(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }.distinctBy { normalizeForDedup(it.memoryType, it.content) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun saveMemoryItems(items: List<MemoryItem>) {
        items.forEach { item ->
            val existing = repository.getActiveMemoryItemByContent(
                ownerType = item.ownerType,
                ownerId = item.ownerId,
                level = item.memoryLevel,
                type = item.memoryType,
                content = item.content
            )
            if (existing != null) return@forEach
            val id = repository.insertMemoryItem(item)
            if (id > 0) saveMemoryItemToVector(id, item)
        }
    }

    private suspend fun saveMemoryItemToVector(id: Long, item: MemoryItem) {
        val service = memoryVectorService ?: return
        if (item.content.isBlank()) return
        val vectorId = "memory_v2_${item.ownerType}_${item.ownerId}_${item.memoryLevel.name.lowercase()}_$id"
        try {
            service.saveMemory(
                VectorMemory(
                    id = vectorId,
                    ownerType = item.ownerType,
                    ownerId = item.ownerId,
                    sourceType = "memory_v2_${item.memoryLevel.name.lowercase()}",
                    sourceId = item.sourceRefId.ifBlank { item.sessionId },
                    content = item.content,
                    importance = item.importance.coerceIn(0, 100) / 100.0,
                    tags = listOf(item.memoryLevel.name, item.memoryType, item.sourceKind.name).joinToString(","),
                    visibility = item.privacy ?: defaultPrivacy(item.sourceKind),
                    createdAt = item.createdAt,
                    expiresAt = item.expiresAt,
                )
            )
            repository.updateMemoryItemVectorId(id, vectorId, System.currentTimeMillis())
        } catch (_: Exception) {
        }
    }

    private fun textValue(element: JsonElement?): String? = element?.jsonPrimitive?.contentOrNull?.trim()

    private fun intValue(element: JsonElement?): Int {
        val primitive = element?.jsonPrimitive ?: return 0
        return primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull() ?: 0
    }

    private fun booleanValue(element: JsonElement?): Boolean {
        val primitive = element?.jsonPrimitive ?: return false
        return primitive.booleanOrNull ?: primitive.contentOrNull?.equals("true", true) == true
    }

    private fun sanitizeContent(value: String): String {
        return value
            .replace("系统记录", "")
            .replace("记忆锚点", "")
            .replace("锚点", "")
            .replace("摘要", "")
            .replace("好感度", "")
            .replace("affection", "", ignoreCase = true)
            .trim(' ', '，', '。', ',', ';', '；')
            .take(120)
    }

    private fun normalizeForDedup(type: String, content: String): String {
        return type + ":" + content.lowercase().filterNot { it.isWhitespace() || it in "，。！？；,.!?;：:" }
    }

    private fun defaultPrivacy(sourceKind: MemorySourceKind): String = when (sourceKind) {
        MemorySourceKind.PRIVATE_CHAT -> "private"
        MemorySourceKind.GROUP_CHAT, MemorySourceKind.MOMENT, MemorySourceKind.MOMENT_COMMENT, MemorySourceKind.WORLD_EVENT -> "public"
        else -> "shared"
    }

    private companion object {
        val allowedTypes = setOf(
            "emotion_state", "behavior_state", "physiological_state", "event", "agreement_commitment",
            "intent_wish", "preference_expression", "evaluation_opinion", "self_cognition_statement",
            "external_knowledge", "care_reminder"
        )
        val allowedPrivacy = setOf("public", "private", "shared")
        val allowedEmotionValence = setOf("positive", "neutral", "negative", "mixed")
        val allowedCareTypes = setOf("comfort", "remind", "celebrate", "accompany", "none")
    }
}
