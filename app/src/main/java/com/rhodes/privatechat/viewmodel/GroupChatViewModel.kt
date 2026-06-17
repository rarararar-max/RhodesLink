package com.rhodes.privatechat.viewmodel

import android.util.Log
import com.rhodes.privatechat.shared.model.AnchorType
import com.rhodes.privatechat.shared.model.ChatMessage
import com.rhodes.privatechat.shared.model.ChatSession
import com.rhodes.privatechat.shared.model.MemoryAnchor
import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.util.DebugLogger
import com.rhodes.privatechat.shared.model.RelationshipType
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.network.AIService
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.shared.model.GroupMsgResult
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.rhodes.privatechat.viewmodel.shared.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.withTimeout

private val json = Json { ignoreUnknownKeys = true }

class GroupChatViewModel(
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    private val sharedUtils: SharedUtils,
    private val appState: AppStateHolder,
    private val markSessionRead: (String) -> Unit,
    private val unhideSession: suspend (String) -> Unit,
    private val getUserProfile: () -> UserProfile,
    private val getPromptTemplate: (String, String) -> String,
    private val generateShortTermSummary: suspend (ChatSession, List<ChatMessage>?) -> Unit,
    private val sessionMessageCounter: MutableMap<String, Int>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    companion object {
        const val DEBUG = true
        /** 静态共享，避免不同 ViewModel 实例干扰 */
        private val globalAutoChatGenerations = ConcurrentHashMap<String, Long>()
        private val globalAutoGroupChatJobs = ConcurrentHashMap<String, Job>()
    }

    private val autoChatGenerations get() = globalAutoChatGenerations
    private val autoGroupChatJobs get() = globalAutoGroupChatJobs

    private val groupActivityCache = ConcurrentHashMap<String, String>()
    private val _groupMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val groupMessages: StateFlow<List<ChatMessage>> = _groupMessages.asStateFlow()

    private val _groupLoading = MutableStateFlow(false)
    val groupLoading: StateFlow<Boolean> = _groupLoading.asStateFlow()

    private val _currentGroupId = MutableStateFlow("")
    val currentGroupId: StateFlow<String> = _currentGroupId.asStateFlow()

    private val _lastSendError = MutableStateFlow("")
    val lastSendError: StateFlow<String> = _lastSendError.asStateFlow()

    private var groupMessagesJob: Job? = null
    private val groupMessageMutexes = ConcurrentHashMap<String, Mutex>()
    private fun mutexFor(groupId: String): Mutex = groupMessageMutexes.computeIfAbsent(groupId) { Mutex() }

    // 自动群聊
    private val lastUserMsgTime = mutableMapOf<String, Long>()
    private val groupAiJobs = ConcurrentHashMap<String, Job>()

    fun setCurrentGroup(groupSessionId: String) {
        DebugLogger.log("GroupChat", "设置当前群聊: $groupSessionId")
        _currentGroupId.value = groupSessionId
        markSessionRead(groupSessionId)
        groupMessagesJob?.cancel()
        groupMessagesJob = scope.launch {
            repository.getMessages(groupSessionId).collect { _groupMessages.value = it; DebugLogger.log("GroupChat/DB", "群消息刷新, count=${it.size}") }
        }
    }

    fun clearCurrentGroup() {
        _currentGroupId.value = ""
        groupMessagesJob?.cancel()
        _groupMessages.value = emptyList()
        groupActivityCache.clear()
    }

    fun removeMessage(msgId: Long) {
        _groupMessages.value = _groupMessages.value.filter { it.id != msgId }
    }

    /** 撤回群聊消息：删除整条（不分段） */
    fun recallMessageSegment(msgId: Long, segmentIndex: Int) {
        removeMessage(msgId)
        scope.launch { repository.deleteMessage(msgId) }
    }

    private fun removeSegmentFromArray(content: String, segmentIndex: Int): String? {
        return try {
            val arr = json.parseToJsonElement(content) as? kotlinx.serialization.json.JsonArray ?: return null
            val list = arr.toMutableList()
            if (segmentIndex in list.indices) list.removeAt(segmentIndex)
            if (list.isEmpty()) null else list.joinToString(",", "[", "]") { it.toString() }
        } catch (_: Exception) {
            tryRemoveSegmentLenient(content, segmentIndex)
        }
    }

    private fun tryRemoveSegmentLenient(content: String, segmentIndex: Int): String? {
        var s = content.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            .replace("，", ",").replace("：", ":")
        s = s.replace(", ]", "]").replace(",]", "]")
        if (!s.startsWith("[")) { val start = s.indexOf('['); if (start >= 0) s = s.substring(start) }
        if (!s.endsWith("]")) { val end = s.lastIndexOf(']'); if (end >= 0) s = s.substring(0, end + 1) }
        return try {
            val arr = json.parseToJsonElement(s) as? kotlinx.serialization.json.JsonArray ?: return null
            val list = arr.toMutableList()
            if (segmentIndex in list.indices) list.removeAt(segmentIndex)
            if (list.isEmpty()) null else list.joinToString(",", "[", "]") { it.toString() }
        } catch (_: Exception) { null }
    }

    fun clearGroupMessages(groupId: String) {
        scope.launch { repository.deleteSessionMessages(groupId) }
    }

    fun deleteGroup(groupSessionId: String, onComplete: () -> Unit = {}) {
        stopAutoGroupChat(groupSessionId)
        settings.remove("group_auto_$groupSessionId")
        scope.launch {
            repository.deleteSessionMessages(groupSessionId)
            repository.deleteSession(groupSessionId)
            onComplete()
        }
    }

    fun isAutoGroupChatEnabled(groupId: String): Boolean =
        settings.getGroupAuto(groupId)

    fun setAutoGroupChatEnabled(groupId: String, enabled: Boolean) {
        settings.putGroupAuto(groupId, enabled)
        if (enabled) {
            if (autoGroupChatJobs[groupId]?.isActive == true) {
                DebugLogger.log("GroupChat/Auto", "跳过启动: id=$groupId, 已有活跃协程")
                return
            }
            scope.launch {
                val session = repository.getSession(groupId)
                if (session != null) startAutoGroupChat(groupId, session.operatorName)
            }
        } else {
            stopAutoGroupChat(groupId)
        }
    }

    private fun startAutoGroupChat(groupId: String, groupName: String) {
        val existing = autoGroupChatJobs[groupId]
        if (existing?.isActive == true) {
            DebugLogger.log("GroupChat/Auto", "跳过启动: id=$groupId, 已有活跃协程")
            return
        }
        autoGroupChatJobs[groupId]?.cancel()
        autoGroupChatJobs.remove(groupId)
        val generation = (autoChatGenerations[groupId] ?: 0L) + 1L
        autoChatGenerations[groupId] = generation
        val minMs = settings.groupChatMinInterval * 1000L
        val maxMs = settings.groupChatMaxInterval * 1000L
        DebugLogger.log("GroupChat/Auto", "启动: id=$groupId, gen=$generation, min=${minMs/1000}秒, max=${maxMs/1000}秒")
        autoGroupChatJobs[groupId] = scope.launch {
            var loopCount = 0
            while (isAutoGroupChatEnabled(groupId)) {
                loopCount++
                if (loopCount > 50) break
                if (autoChatGenerations[groupId] != generation) {
                    DebugLogger.log("GroupChat/Auto", "gen变化退出: id=$groupId")
                    break
                }
                // 先等待完整间隔，再发消息（首次也一样）
                val interval = minMs + (Math.random() * (maxMs - minMs).coerceAtLeast(0L)).toLong()
                DebugLogger.log("GroupChat/Auto", "第${loopCount}轮: id=$groupId, 等待${interval/1000}秒, gen=$generation")
                val tickMs = 1000L
                var remaining = interval
                while (remaining > 0 && isAutoGroupChatEnabled(groupId)) {
                    if (autoChatGenerations[groupId] != generation) break
                    delay(minOf(remaining, tickMs))
                    remaining -= tickMs
                }
                if (autoChatGenerations[groupId] != generation) break
                // 等够间隔，发消息
                DebugLogger.log("GroupChat/Auto", "发消息: id=$groupId, loop=$loopCount")
                val session = repository.getSession(groupId) ?: break
                val mode = getGroupChatMode(groupId)
                sendGroupMessage(groupId, groupName, "", mode, isAuto = true)
            }
        }
    }

    fun resetAutoGroupChatTimer(groupId: String) {
        DebugLogger.log("GroupChat/Auto", "重置计时器: id=$groupId")
        lastUserMsgTime[groupId] = System.currentTimeMillis()
    }

    private fun getGroupChatMode(groupId: String): String =
        settings.getGroupMode(groupId)

    fun stopAutoGroupChat(groupId: String) {
        autoChatGenerations[groupId] = (autoChatGenerations[groupId] ?: 0L) + 1L
        autoGroupChatJobs[groupId]?.cancel()
        autoGroupChatJobs.remove(groupId)
    }

    fun stopAllAutoGroupChats() {
        autoChatGenerations.clear()
        autoGroupChatJobs.values.forEach { it.cancel() }
        autoGroupChatJobs.clear()
    }

    fun refreshAutoGroupChats() {
        appState.sessions.value.filter { it.operatorId.startsWith("group_") || it.operatorId.startsWith("group") }.forEach { group ->
            if (settings.getGroupAuto(group.id)) {
                startAutoGroupChat(group.id, group.operatorName)
            } else {
                stopAutoGroupChat(group.id)
            }
        }
    }

    fun sendGroupMessage(groupSessionId: String, groupName: String, text: String, mode: String = "online", autoSpeak: Boolean = false, isAuto: Boolean = false, onMessageSent: () -> Unit = {}) {
        groupAiJobs[groupSessionId]?.cancel()
        val job = scope.launch {
            // 步骤1: 用户消息立即插入（不持锁），消息即时显示
            if (!isAuto && text.isNotBlank()) {
                val userMsgId = repository.getNextMessageId()
                repository.sendMessage(groupSessionId, ChatMessage(
                    id = userMsgId, sessionId = groupSessionId,
                    senderName = "我", content = text, type = "text", mode = mode, isMe = true
                ))
                DebugLogger.log("GroupChat/DB", "群用户消息已写入, session=$groupSessionId, id=$userMsgId, text=${text.take(50)}")
                onMessageSent()
                resetAutoGroupChatTimer(groupSessionId)
                unhideSession(groupSessionId)
                val today = sharedUtils.beijingSdf("yyyyMMdd").format(java.util.Date())
                val rewardDate = settings.rewardDate
                if (rewardDate != today) { settings.rewardDate = today; settings.dailyLmbCount = 0 }
                val dailyCount = settings.dailyLmbCount
                if (dailyCount < 5000) { val balance = settings.lmb; settings.lmb = balance + 10; settings.dailyLmbCount = dailyCount + 1 }
            }

            // 步骤2: AI 处理 — 串行化（持锁）
            var mutexLocked = false
            try {
                mutexFor(groupSessionId).lock()
                mutexLocked = true
                _groupLoading.value = true
                groupAiJobs[groupSessionId] = coroutineContext[Job]!!
                val session = repository.getSession(groupSessionId) ?: run {
                    DebugLogger.log("GroupChat", "⚠️ 群session不存在: $groupSessionId")
                    _groupLoading.value = false; if (mutexLocked) { mutexFor(groupSessionId).unlock(); mutexLocked = false }; return@launch
                }
                DebugLogger.log("GroupChat", "群session加载成功: ${session.operatorName}, members=${session.members}")
                val memberIds = session.members.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val allOps = appState.operators.value
                val opsById = allOps.associateBy { it.id }
                val opsByName = allOps.associateBy { it.name }
                val members = memberIds.mapNotNull { id -> opsById[id] ?: opsByName[id] }
                val mutedIds = session.mutedMembers.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                val activeMembers = members.filter { it.id !in mutedIds && it.name !in mutedIds }

                // 全员禁言时直接返回，不调用 AI
                if (activeMembers.isEmpty()) {
                    repository.sendMessage(groupSessionId, ChatMessage(
                        id = repository.getNextMessageId(), sessionId = groupSessionId,
                        senderName = "系统", content = "所有成员已被禁言，无法回复",
                        type = "system", mode = mode, isMe = false
                    ))
                    _groupLoading.value = false
                    if (mutexLocked) { mutexFor(groupSessionId).unlock(); mutexLocked = false }
                    return@launch
                }

                val profile = getUserProfile()
                val relContext = getGroupRelationshipContext(activeMembers)
                val relationHints = if (relContext.isNotBlank()) relContext else "无"
                val memberPrivateContext = if (!isAuto) {
                    buildString {
                        for (m in activeMembers) {
                            val ctx = repository.getPrivateChatContext(m.id)
                            if (ctx != null) append("- ${m.name}：${ctx}\n")
                            else append("- ${m.name}：暂无特别的互动\n")
                        }
                    }.toString()
                } else ""
                val groupSummary = repository.getShortTermMemory(groupSessionId)?.content ?: ""
                val longTermImpression = if (!isAuto && activeMembers.isNotEmpty()) {
                    activeMembers.take(5).mapNotNull { m ->
                        repository.getLongTermImpression(m.id)?.content?.let { "- ${m.name}对${profile.nickname}的印象：${it.take(100)}" }
                    }.joinToString("\n").ifBlank { "成员们对${profile.nickname}尚无深入了解。" }
                } else ""
                val memberProfiles = buildString {
                    for (m in activeMembers.shuffled()) {
                        val key = "${groupSessionId}_${m.id}"
                        val act = groupActivityCache.computeIfAbsent(key) { "活跃${"%.1f".format(0.5 + Math.random() * 0.5)}" }
                        val titleStr = if (m.title.isBlank()) "" else "，${m.title}"
                        val genderStr = if (m.gender.isNotBlank()) "，${m.gender}" else ""
                        append("${m.name}（${act}${genderStr}${titleStr}）：${m.groupPrompt.ifBlank { m.description }}\n")
                    }
                }
                val userMessage = if (isAuto) "" else if (autoSpeak) "（群聊已空闲一段时间，干员们自然地闲聊起来，无需等待用户发言。）" else text
                val grpTpl = getPromptTemplate("group", if (isAuto) "auto" else mode)
                val userObserving = if (isAuto) when (mode) {
                    "offline" -> "用户坐在一旁，安静地听着大家的对话，没有插话。"
                    "director" -> "用户作为导演正在观察大家的表演，没有给出新指令。"
                    else -> "群内用户正在安静地观察，没有发言。"
                } else ""
                val grpModeFormat = if (isAuto) when (mode) {
                    "offline", "director" -> "\n允许旁白条目（speaker为\"旁白\"，type为\"narration\"），对话条目type为\"dialogue\"。"
                    else -> ""
                } else ""
                val now = sharedUtils.beijingSdf("yyyy-MM-dd HH:mm").format(java.util.Date())
                val grpReplacements = mapOf(
                    "CURRENT_TIME" to now, "GROUP_NAME" to groupName,
                    "GROUP_RULES" to (session.rules.ifBlank { "无" }),
                    "USER_NAME" to profile.nickname, "USER_GENDER" to profile.gender.ifBlank { "未知" },
                    "USER_BIO" to profile.bio.ifBlank { "无" }, "RELATION_HINTS" to relationHints,
                    "MEMBER_PRIVATE_CONTEXT" to memberPrivateContext,
                    "SHORT_TERM_SUMMARY" to groupSummary, "GROUP_SUMMARY" to groupSummary,
                    "DAILY_SUMMARY" to ((repository.getLatestDailyBySession(groupSessionId) ?: repository.getLatestDaily())?.content ?: "无"),
                    "LONG_TERM_IMPRESSION" to longTermImpression,
                    "MEMBER_PROFILES" to memberProfiles.toString(),
                    "GROUP_NAR_SEG_MIN" to settings.groupNarSegMin.toString(),
                    "GROUP_NAR_SEG_MAX" to settings.groupNarSegMax.toString(),
                    "GROUP_NAR_MIN" to settings.groupNarMin.toString(),
                    "GROUP_NAR_MAX" to settings.groupNarMax.toString(),
                    "GROUP_MSG_MIN" to settings.groupMsgMin.toString(),
                    "GROUP_MSG_MAX" to settings.groupMsgMax.toString(),
                    "GROUP_SPEECH_MIN" to settings.groupSpeechMin.toString(),
                    "GROUP_SPEECH_MAX" to settings.groupSpeechMax.toString(),
                    "USER_MESSAGE" to userMessage, "USER_OBSERVING" to userObserving,
                    "GROUP_MODE_FORMAT" to grpModeFormat,
                    "MEMBER_NAMES" to activeMembers.joinToString("、") { it.name }
                )
                val systemPrompt = sharedUtils.compactTemplate(sharedUtils.applyTemplate(grpTpl, grpReplacements))
                val apiMessages = mutableListOf(AiMessage("system", systemPrompt))
                val historyLimit = settings.historyMessages
                val activeNames = activeMembers.map { it.name }.toSet() + "我" + "系统"
                val allHistory = repository.getMessagesSync(groupSessionId).let { msgs ->
                    val limited = if (historyLimit > 0) msgs.takeLast(historyLimit) else msgs
                    limited.filter { msg -> msg.isMe || msg.type == "system" || msg.type == "ai_json" || msg.senderName in activeNames }
                }.toMutableList()
                // 去掉最后一条用户消息，避免与下文重复
                if (!isAuto && allHistory.lastOrNull()?.isMe == true) {
                    allHistory.removeAt(allHistory.lastIndex)
                }
                for (msg in allHistory) {
                    val role = if (msg.isMe) "user" else "assistant"
                    val content = if (msg.isMe) "用户：${msg.content}" else msg.content
                    apiMessages.add(AiMessage(role, content))
                }
                if (!isAuto) {
                    val userMsg = if (autoSpeak) "（群聊已空闲一段时间，干员们自然地闲聊起来，无需等待用户发言。）" else text
                    apiMessages.add(AiMessage("user", "用户：$userMsg"))
                }
                // 估算总 token，超限则丢弃最早的历史消息
                val maxPromptTokens = settings.maxContextTokens - 2000
                var totalTokens = apiMessages.sumOf { (it.content.length * 1.3).toInt() + 10 }
                com.rhodes.privatechat.util.DebugLogger.log("GroupChat/Token", "估算token=$totalTokens, 上限=$maxPromptTokens, 消息数=${apiMessages.size}")
                if (totalTokens > maxPromptTokens) {
                    var dropIdx = 1
                    while (apiMessages.size > 2 && totalTokens > maxPromptTokens) {
                        if (dropIdx >= apiMessages.size - 1) break
                        apiMessages.removeAt(dropIdx)
                        totalTokens = apiMessages.sumOf { (it.content.length * 1.3).toInt() + 10 }
                        dropIdx++
                    }
                    com.rhodes.privatechat.util.DebugLogger.log("GroupChat/Token", "截断后: 消息数=${apiMessages.size}, 估算token=$totalTokens")
                }
                val promptText = apiMessages.firstOrNull()?.content ?: ""
                if (DEBUG) sharedUtils.logAiCall("GroupChat", promptText, "(requesting...)", apiMessages)
                val rawBase = withTimeout(90_000) { sharedUtils.chat(apiMessages, "GroupChat") }.trim()
                    .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                sharedUtils.trackTokens("group", apiMessages, rawBase)
                if (DEBUG) sharedUtils.logAiCall("GroupChat", promptText, rawBase, apiMessages)
                var results: List<GroupMsgResult> = extractGroupResults(rawBase)
                if (results.isEmpty() && rawBase.isNotBlank()) {
                    results = listOf(GroupMsgResult(speaker = "系统", message = rawBase.take(500), type = "narration"))
                }
                val validSpeakers = members.map { it.name }.toSet() + "旁白" + "系统"
                val filtered = results.filter { it.message.isNotBlank() && it.speaker in validSpeakers }
                if (filtered.isNotEmpty()) {
                    val aiMsgId = repository.getNextMessageId()
                    val storedContent = if (filtered.isNotEmpty()) {
                        try {
                            json.encodeToString(filtered)
                        } catch (_: Exception) { rawBase }
                    } else rawBase
                    repository.sendMessage(groupSessionId, ChatMessage(
                        id = aiMsgId, sessionId = groupSessionId,
                        senderName = groupName, content = storedContent,
                        type = "ai_json", mode = mode, isMe = false
                    ))
                    for (r in filtered) {
                        val anchorOp = if (r.speaker == "旁白" || r.speaker == "系统") null
                        else opsByName[r.speaker]
                        if (anchorOp != null) {
                            repository.saveAnchor(MemoryAnchor(
                                sessionId = "anchor_${System.currentTimeMillis()}",
                                operatorId = anchorOp.id, type = AnchorType.EVENT,
                                content = "在群聊「${groupName}」中${r.speaker}说：${r.message.take(40)}",
                                isPrivate = false,
                                createdAt = System.currentTimeMillis(),
                                expiresAt = System.currentTimeMillis() + settings.cleanDays * 86_400_000L
                            ))
                        }
                    }
                    val last = filtered.last()
                    repository.updateLastMessage(groupSessionId, "${last.speaker}：${last.message.take(50)}", System.currentTimeMillis())
                }
                if (_currentGroupId.value != groupSessionId && filtered.isNotEmpty()) {
                    val sess = repository.getSession(groupSessionId)
                    if (sess != null) repository.insertSession(sess.copy(unreadCount = sess.unreadCount + 1))
                    unhideSession(groupSessionId)
                }
                val gc = sessionMessageCounter.getOrDefault(groupSessionId, 0) + 1
                sessionMessageCounter[groupSessionId] = gc
                if (gc >= settings.summaryThreshold && groupSessionId.isNotBlank()) {
                    val gs = repository.getSession(groupSessionId)
                    if (gs != null) {
                        val freshMsgs = repository.getMessagesSync(gs.id)
                        generateShortTermSummary(gs, freshMsgs)
                        sessionMessageCounter[groupSessionId] = 0
                        // 生成群聊每日摘要（昨日消息 >1 条时）
                        generateGroupDailySummary(groupSessionId, gs.operatorName)
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e("GroupChat", "Timeout: ${e.message}")
                repository.sendMessage(groupSessionId, ChatMessage(id = repository.getNextMessageId(), sessionId = groupSessionId, senderName = "系统", content = "响应超时，请重试", type = "system", mode = mode, isMe = false))
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 被新消息取消，不做任何事
            } catch (e: Exception) {
                val errMsg = classifyGroupError(e)
                Log.e("GroupChat", "Error: ${e.message}", e)
                DebugLogger.log("GroupChat/Error", "发送失败: $errMsg")
                repository.sendMessage(groupSessionId, ChatMessage(id = repository.getNextMessageId(), sessionId = groupSessionId, senderName = "系统", content = errMsg, type = "system", mode = mode, isMe = false))
                _lastSendError.value = errMsg
            } finally {
                _groupLoading.value = false
                if (mutexLocked) mutexFor(groupSessionId).unlock()
            }
        }
        groupAiJobs[groupSessionId] = job
    }

    fun clearSendError() { _lastSendError.value = "" }

    private fun classifyGroupError(e: Exception): String = when {
        e.message?.contains("401") == true || e.message?.contains("api key", true) == true -> "API Key 无效或已过期，请在设置中检查"
        e.message?.contains("402") == true || e.message?.contains("insufficient", true) == true || e.message?.contains("quota") == true -> "API 余额不足，请充值后重试"
        e.message?.contains("429") == true -> "AI 服务请求太频繁，请稍后重试"
        e.message?.contains("5") == true && e.message?.contains("50") == true -> "AI 服务暂时不可用，请稍后重试"
        e is java.io.IOException || e.message?.contains("connect", true) == true || e.message?.contains("network", true) == true -> "网络连接失败，请检查网络"
        else -> "发送失败：${e.message?.take(50) ?: "未知错误"}"
    }

    private fun extractGroupResults(raw: String): List<GroupMsgResult> {
        try {
            val cleaned = sharedUtils.aiService.cleanJson(raw)
            val arr = json.decodeFromString<List<GroupMsgResult>>(cleaned)
            if (arr.isNotEmpty()) return arr
        } catch (_: Exception) {}

        try {
            val objRegex = Regex("""\{[^}]*\}""")
            val results = objRegex.findAll(raw).mapNotNull { match ->
                try { json.decodeFromString<GroupMsgResult>(match.value) } catch (_: Exception) { null }
            }.filter { it.message.isNotBlank() }.toList()
            if (results.isNotEmpty()) return results
        } catch (_: Exception) {}

        try {
            val objPattern = Regex("""\{[^}]*\}""")
            val results = objPattern.findAll(raw).mapNotNull { match ->
                val obj = match.value
                val speaker = Regex(""""speaker"\s*:\s*"([^"]*)"""").find(obj)?.groupValues?.getOrNull(1) ?: return@mapNotNull null
                val message = Regex(""""message"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(obj)?.groupValues?.getOrNull(1) ?: return@mapNotNull null
                val type = Regex(""""type"\s*:\s*"([^"]*)"""").find(obj)?.groupValues?.getOrNull(1) ?: "dialogue"
                GroupMsgResult(speaker = speaker, message = message, type = type)
            }.filter { it.message.isNotBlank() }.toList()
            if (results.isNotEmpty()) return results
        } catch (_: Exception) {}

        return emptyList()
    }

    private suspend fun generateGroupDailySummary(groupSessionId: String, groupName: String) {
        try {
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val dayBegin = cal.time
            val dayEnd = java.util.Date(dayBegin.time + 86_400_000)
            val msgs = repository.getMessagesInRange(dayBegin.time, dayEnd.time)
                .filter { it.sessionId == groupSessionId }
            if (msgs.size <= 1) return
            val text = msgs.joinToString("\n") { "${it.senderName}：${it.content.take(60)}" }
            val dateStr = sharedUtils.beijingSdf("yyyy年MM月dd日").format(dayBegin)
            val prompt = "请总结${dateStr}「${groupName}」的聊天记录，生成50-150字的每日摘要。直接输出纯文本。\n${text}"
            val content = withTimeout(15_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Memory") }.trim()
            if (content.isNotBlank()) {
                repository.saveMemory(com.rhodes.privatechat.shared.model.Memory(
                    sessionId = groupSessionId, operatorId = groupSessionId,
                    type = com.rhodes.privatechat.shared.model.MemoryType.DAILY, content = content,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = System.currentTimeMillis() + settings.cleanDays * 86_400_000L
                ))
                DebugLogger.log("GroupChat", "群聊每日摘要已生成: $groupSessionId")
            }
        } catch (_: Exception) {}
    }

    private suspend fun getGroupRelationshipContext(members: List<Operator>): String {
        val lines = mutableListOf<String>()
        for (i in members.indices) {
            for (j in i + 1 until members.size) {
                val a = members[i]; val b = members[j]
                val rel = repository.getRelationship(a.id, b.id)
                if (rel != null) {
                    val desc = sharedUtils.relationshipGroupDesc(a.name, b.name, rel.type)
                    lines.add("- $desc（亲密${rel.intimacy}）")
                }
            }
        }
        return lines.joinToString("\n")
    }
}
