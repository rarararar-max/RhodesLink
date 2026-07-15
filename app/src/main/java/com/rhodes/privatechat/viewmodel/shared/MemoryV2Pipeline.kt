package com.rhodes.privatechat.viewmodel.shared

import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.shared.model.ChatMessage
import com.rhodes.privatechat.shared.model.MomentComment
import com.rhodes.privatechat.shared.model.Moment
import com.rhodes.privatechat.shared.model.MemoryBatch
import com.rhodes.privatechat.shared.model.MemoryItem
import com.rhodes.privatechat.shared.model.MemoryLevel
import com.rhodes.privatechat.shared.model.MemoryLink
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
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
        maybePromotePrivateMemory(operatorId, thresholdL1 = settings.memoryV2PromoteL1Threshold, thresholdL2 = settings.memoryV2PromoteL2Threshold)
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
        maybePromoteOwnerMemory("group", groupId, MemorySourceKind.GROUP_CHAT, settings.memoryV2PromoteL1Threshold, settings.memoryV2PromoteL2Threshold)
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
        maybePromoteOwnerMemory("operator", moment.operatorId, MemorySourceKind.MOMENT, settings.memoryV2PromoteL1Threshold, settings.memoryV2PromoteL2Threshold)
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
        maybePromoteOwnerMemory("operator", comment.operatorId, MemorySourceKind.MOMENT_COMMENT, settings.memoryV2PromoteL1Threshold, settings.memoryV2PromoteL2Threshold)
    }

    suspend fun maybePromotePrivateMemory(operatorId: String, thresholdL1: Int, thresholdL2: Int) {
        maybePromoteOwnerMemory("operator", operatorId, MemorySourceKind.PRIVATE_CHAT, thresholdL1, thresholdL2)
    }

    private suspend fun maybePromoteOwnerMemory(ownerType: String, ownerId: String, sourceKind: MemorySourceKind, thresholdL1: Int, thresholdL2: Int) {
        val now = System.currentTimeMillis()
        val l1Items = repository.getActiveMemoryItemsByLevel(ownerType, ownerId, MemoryLevel.L1, now)
        val l1Topic = pickPromotionTopic(l1Items, thresholdL1)
        if (l1Topic != null) {
            val selected = l1Topic.items
            val prompt = buildLevelPrompt(MemoryV2PromptTemplates.L2, selected)
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
            val l2Items = parsePromotionItems(raw, ownerType, ownerId, MemoryLevel.L2, sourceKind, l1Topic.topicKey, selected)
            if (l2Items.isNotEmpty()) {
                val saved = saveMemoryItems(l2Items)
                if (saved.isNotEmpty()) linkAndArchiveParents(saved, selected, "merge_l1_to_l2")
                repository.saveMemoryBatch(MemoryBatch(
                    ownerType = ownerType,
                    ownerId = ownerId,
                    sourceKind = sourceKind,
                    targetLevel = MemoryLevel.L2,
                    inputCount = selected.size,
                    outputCount = saved.size,
                    windowStart = selected.minOfOrNull { it.createdAt } ?: System.currentTimeMillis(),
                    windowEnd = selected.maxOfOrNull { it.createdAt } ?: System.currentTimeMillis(),
                    promptVersion = "memory_v2_l2_v1",
                    createdAt = System.currentTimeMillis()
                ))
            }
        }

        val l2Items = repository.getActiveMemoryItemsByLevel(ownerType, ownerId, MemoryLevel.L2, now)
        val l2Topic = pickPromotionTopic(l2Items, thresholdL2, requireStable = true)
        if (l2Topic != null) {
            val selected = l2Topic.items
            val prompt = buildLevelPrompt(MemoryV2PromptTemplates.L3, selected)
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
            val l3Items = parsePromotionItems(raw, ownerType, ownerId, MemoryLevel.L3, sourceKind, l2Topic.topicKey, selected)
            if (l3Items.isNotEmpty()) {
                val saved = saveMemoryItems(l3Items)
                if (saved.isNotEmpty()) linkAndArchiveParents(saved, selected, "merge_l2_to_l3")
                repository.saveMemoryBatch(MemoryBatch(
                    ownerType = ownerType,
                    ownerId = ownerId,
                    sourceKind = sourceKind,
                    targetLevel = MemoryLevel.L3,
                    inputCount = selected.size,
                    outputCount = saved.size,
                    windowStart = selected.minOfOrNull { it.createdAt } ?: System.currentTimeMillis(),
                    windowEnd = selected.maxOfOrNull { it.createdAt } ?: System.currentTimeMillis(),
                    promptVersion = "memory_v2_l3_v1",
                    createdAt = System.currentTimeMillis()
                ))
            }
        }
    }

    suspend fun buildPrivateMemoryContext(operatorId: String, limitL1: Int, limitL2: Int, limitL3: Int): String {
        val now = System.currentTimeMillis()
        val l1 = repository.getActiveMemoryItemsByLevel("operator", operatorId, MemoryLevel.L1, now).rankForPrompt().take(limitL1)
        val l2 = repository.getActiveMemoryItemsByLevel("operator", operatorId, MemoryLevel.L2, now).rankForPrompt().take(limitL2)
        val l3 = repository.getActiveMemoryItemsByLevel("operator", operatorId, MemoryLevel.L3, now).rankForPrompt().take(limitL3)
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

    private fun List<MemoryItem>.rankForPrompt(): List<MemoryItem> {
        val now = System.currentTimeMillis()
        return filter { it.content.isNotBlank() && it.expiresAt > now && it.status == "active" }
            .distinctBy { normalizeForDedup(it.memoryType, it.content) }
            .sortedWith(
                compareByDescending<MemoryItem> { levelPromptWeight(it.memoryLevel) }
                    .thenByDescending { it.importance }
                    .thenByDescending { it.confidence }
                    .thenBy { it.lastUsedAt.coerceAtLeast(0L) }
                    .thenBy { it.usedCount }
                    .thenByDescending { it.createdAt }
            )
    }

    private fun levelPromptWeight(level: MemoryLevel): Int = when (level) {
        MemoryLevel.L3 -> 300
        MemoryLevel.L2 -> 200
        MemoryLevel.L1 -> 100
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
                val requestedPrivacy = textValue(obj["privacy"])?.takeIf { it in allowedPrivacy }
                val policy = MemoryPrivacyPolicy.forSource(sourceKind, ownerType, ownerId, content)
                // Source policy is authoritative: private chats never become public by model choice,
                // while public posts/comments cannot be downgraded into a private hidden record.
                val privacy = when (sourceKind) {
                    MemorySourceKind.PRIVATE_CHAT -> "private"
                    MemorySourceKind.MOMENT, MemorySourceKind.MOMENT_COMMENT, MemorySourceKind.GROUP_CHAT, MemorySourceKind.WORLD_EVENT -> "public"
                    else -> requestedPrivacy ?: policy.privacy
                }
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
                    topicKey = if (level == MemoryLevel.L1) topicKey(type, content) else "",
                    sourceActor = nickname,
                    sourceTarget = ownerId,
                    lastUsedAt = 0L,
                    usedCount = 0,
                    confidence = if (level == MemoryLevel.L1) 0.8 else 0.85,
                    rawJson = obj.toString(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    expiresAt = expiresAtFor(type, level)
                )
            }.distinctBy { normalizeForDedup(it.memoryType, it.content) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun saveMemoryItems(items: List<MemoryItem>): List<MemoryItem> {
        val saved = mutableListOf<MemoryItem>()
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
            if (id > 0) {
                val withId = item.copy(id = id)
                saveMemoryItemToVector(id, withId)
                saved += withId
            }
        }
        return saved
    }

    private suspend fun linkAndArchiveParents(children: List<MemoryItem>, parents: List<MemoryItem>, linkType: String) {
        val now = System.currentTimeMillis()
        val parentById = parents.filter { it.id > 0 }.associateBy { it.id }
        val linkedParentIds = mutableSetOf<Long>()
        children.filter { it.id > 0 }.forEach { child ->
            val evidenceIds = evidenceIdsFrom(child.rawJson).filter { it in parentById }
            evidenceIds.forEach { evidenceId ->
                val parent = parentById.getValue(evidenceId)
                repository.insertMemoryLink(MemoryLink(parentMemoryId = parent.id, childMemoryId = child.id, linkType = linkType, createdAt = now))
                linkedParentIds += parent.id
            }
        }
        linkedParentIds.mapNotNull(parentById::get).forEach { parent ->
            repository.archiveMemoryItem(parent.id, now)
            parent.vectorId.takeIf { it.isNotBlank() }?.let { memoryVectorService?.deleteMemory(it) }
        }
    }

    private fun parsePromotionItems(
        raw: String,
        ownerType: String,
        ownerId: String,
        level: MemoryLevel,
        sourceKind: MemorySourceKind,
        topicKey: String,
        parents: List<MemoryItem>
    ): List<MemoryItem> {
        val parsed = parseMemoryItems(raw, ownerType, ownerId, level, sourceKind)
        val objects = runCatching { json.decodeFromString(ListSerializer(JsonObject.serializer()), aiService.cleanJson(raw)) }.getOrDefault(emptyList())
        val allowedIds = parents.map { it.id }.filter { it > 0 }.toSet()
        return parsed.mapIndexedNotNull { index, item ->
            val evidenceIds = objects.getOrNull(index)?.let { obj: JsonObject -> evidenceIdsFrom(obj) }
                ?.filter { it in allowedIds }
                ?.distinct()
                .orEmpty()
            if (evidenceIds.isEmpty()) null else withPromotionMeta(item, topicKey, parents.filter { it.id in evidenceIds }, level)
        }
    }

    private fun evidenceIdsFrom(jsonText: String): List<Long> = runCatching<List<Long>> {
        val obj = json.parseToJsonElement(jsonText).jsonObject
        evidenceIdsFrom(obj)
    }.getOrDefault(emptyList())

    private fun evidenceIdsFrom(obj: JsonObject): List<Long> {
        val value = obj["evidence_ids"] ?: return emptyList()
        return runCatching {
            value.jsonArray.mapNotNull { it.jsonPrimitive.longOrNull ?: it.jsonPrimitive.contentOrNull?.toLongOrNull() }
        }.getOrElse {
            value.jsonPrimitive.contentOrNull.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }
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
        } catch (e: Exception) {
            com.rhodes.privatechat.util.DebugLogger.log("Vector/Save", "记忆向量写入失败 owner=${item.ownerId} level=${item.memoryLevel} err=${e.message?.take(80)}")
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

    private fun expiresAtFor(type: String, level: MemoryLevel): Long {
        if (level == MemoryLevel.L3 || type in setOf("preference_expression", "agreement_commitment")) return Long.MAX_VALUE
        val days = when (type) {
            "emotion_state", "behavior_state", "physiological_state" -> 21L
            "intent_wish", "care_reminder" -> 30L
            else -> 30L
        }
        return System.currentTimeMillis() + days * 86_400_000L
    }

    private data class PromotionTopic(val topicKey: String, val items: List<MemoryItem>)

    private fun pickPromotionTopic(items: List<MemoryItem>, threshold: Int, requireStable: Boolean = false): PromotionTopic? {
        if (items.size < threshold) return null
        val grouped = items
            .filter { it.content.isNotBlank() && it.importance >= 20 }
            .groupBy { topicKey(it.memoryType, it.content) }
            .mapValues { (_, values) -> values.sortedByDescending { promotionItemScore(it) } }
            .filterValues { values -> values.size >= topicThreshold(threshold, values) }
        if (grouped.isEmpty()) return null
        val picked = grouped.maxByOrNull { (_, values) -> values.sumOf { it.importance } + values.size * 10 } ?: return null
        val selected = picked.value.take(12)
        if (requireStable && !isStableEnoughForL3(selected)) return null
        return PromotionTopic(picked.key, selected)
    }

    private fun topicThreshold(baseThreshold: Int, values: List<MemoryItem>): Int {
        val strong = values.any { it.importance >= 80 || it.unmetNeed || it.memoryType == "agreement_commitment" || it.memoryType == "care_reminder" }
        return if (strong) 2 else (baseThreshold / 3).coerceIn(3, baseThreshold)
    }

    private fun promotionItemScore(item: MemoryItem): Long {
        val ageDays = ((System.currentTimeMillis() - item.createdAt).coerceAtLeast(0L) / 86_400_000L).coerceAtMost(30L)
        val recency = 30L - ageDays
        return item.importance * 100L + recency
    }

    private fun isStableEnoughForL3(items: List<MemoryItem>): Boolean {
        if (items.size < 3 && items.none { it.importance >= 85 }) return false
        val times = items.map { it.createdAt }.filter { it > 0 }
        val span = if (times.size >= 2) (times.maxOrNull() ?: 0L) - (times.minOrNull() ?: 0L) else 0L
        val stableType = items.any { it.memoryType in stableMemoryTypes }
        return stableType && (span >= 3L * 86_400_000L || items.size >= 4 || items.any { it.importance >= 90 })
    }

    private fun topicKey(memoryType: String, content: String): String {
        val typeGroup = when (memoryType) {
            "preference_expression" -> "preference"
            "agreement_commitment", "care_reminder", "intent_wish" -> "plan"
            "emotion_state", "behavior_state", "physiological_state" -> "state"
            "evaluation_opinion", "self_cognition_statement" -> "relation"
            "external_knowledge" -> "knowledge"
            else -> "event"
        }
        val tokens = content
            .replace(Regex("""[\s，。！？；,.!?;：:\"'“”‘’【】\[\]（）()]"""), "")
            .windowed(2, 1, partialWindows = false)
            .filterNot { token -> commonTopicNoise.any { token.contains(it) } }
            .take(8)
            .joinToString("")
            .take(24)
        return "$typeGroup:${tokens.ifBlank { memoryType }}"
    }

    private fun withPromotionMeta(item: MemoryItem, topicKey: String, parents: List<MemoryItem>, targetLevel: MemoryLevel): MemoryItem {
        val meta = buildJsonObject {
            put("topic_key", topicKey)
            put("evidence_count", parents.size)
            put("evidence_ids", parents.map { it.id }.filter { it > 0 }.joinToString(","))
            put("promoted_to", targetLevel.name)
            put("source_levels", parents.map { it.memoryLevel.name }.distinct().joinToString(","))
            put("last_evidence_at", parents.maxOfOrNull { it.createdAt } ?: item.createdAt)
            put("source_kinds", parents.map { it.sourceKind.name }.distinct().joinToString(","))
        }.toString()
        val maxImportance = maxOf(item.importance, parents.maxOfOrNull { it.importance } ?: item.importance)
        val privacy = if (parents.any { it.privacy == "private" }) "private" else item.privacy
        return item.copy(
            importance = maxImportance.coerceIn(0, 100),
            privacy = privacy,
            topicKey = topicKey,
            sourceActor = parents.firstOrNull()?.sourceActor ?: item.sourceActor,
            sourceTarget = parents.firstOrNull()?.sourceTarget ?: item.sourceTarget,
            lastUsedAt = 0L,
            usedCount = 0,
            confidence = (0.8 + parents.size * 0.02).coerceAtMost(0.95),
            rawJson = meta,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
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
        val stableMemoryTypes = setOf("preference_expression", "agreement_commitment", "care_reminder", "evaluation_opinion", "self_cognition_statement")
        val commonTopicNoise = setOf("用户", "今天", "昨天", "最近", "表示", "觉得", "希望", "提到", "自己", "没有", "可以")
    }
}
