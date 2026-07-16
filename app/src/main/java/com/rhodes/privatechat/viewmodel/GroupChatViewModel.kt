package com.rhodes.privatechat.viewmodel

import android.util.Log
import com.rhodes.privatechat.shared.model.AnchorType
import com.rhodes.privatechat.shared.model.ChatMessage
import com.rhodes.privatechat.shared.model.ChatSession
import com.rhodes.privatechat.shared.model.MemoryAnchor
import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.util.DebugLogger
import com.rhodes.privatechat.util.ChatTrace
import com.rhodes.privatechat.shared.model.RelationshipType
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.memory.AnchorSourcePolicy
import com.rhodes.privatechat.shared.network.AIService
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.shared.model.GroupMsgResult
import com.rhodes.privatechat.shared.modelgateway.VisionAnalyzeRequest
import com.rhodes.privatechat.shared.modelgateway.VisionGateway
import com.rhodes.privatechat.shared.modelgateway.createVisionGateway
import com.rhodes.privatechat.shared.vector.MemoryVectorService
import com.rhodes.privatechat.shared.vector.VectorMemory
import com.rhodes.privatechat.notification.RhodesAppVisibility
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.rhodes.privatechat.viewmodel.shared.UserProfile
import com.rhodes.privatechat.viewmodel.shared.MemoryPolicy
import com.rhodes.privatechat.viewmodel.shared.MemorySurface
import com.rhodes.privatechat.viewmodel.shared.MemoryV2Pipeline
import com.rhodes.privatechat.viewmodel.shared.MemoryContextBuilder
import com.rhodes.privatechat.viewmodel.shared.UnifiedMemoryContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    private val sessionMessageCounter: ConcurrentHashMap<String, Int>,
    private val memoryVectorService: MemoryVectorService? = null,
    private val visionGateway: VisionGateway? = null,
    private val showNotification: (String, String, String?) -> Unit = { _, _, _ -> },
    private val consumeAutoAiBudget: (String) -> Boolean = { true }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    companion object {
        const val DEBUG = false
        private const val CHAT_PAGE_SIZE = 50L
    }

    private val autoChatGenerations = ConcurrentHashMap<String, Long>()
    private val autoGroupChatJobs = ConcurrentHashMap<String, Job>()

    private val groupActivityCache = ConcurrentHashMap<String, String>()
    private val _groupMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val groupMessages: StateFlow<List<ChatMessage>> = _groupMessages.asStateFlow()

    private val _isLoadingOlderGroupMessages = MutableStateFlow(false)
    val isLoadingOlderGroupMessages: StateFlow<Boolean> = _isLoadingOlderGroupMessages.asStateFlow()

    private val _hasMoreGroupMessages = MutableStateFlow(true)
    val hasMoreGroupMessages: StateFlow<Boolean> = _hasMoreGroupMessages.asStateFlow()

    private val _groupRestartAt = MutableStateFlow(0L)
    val groupRestartAt: StateFlow<Long> = _groupRestartAt.asStateFlow()

    private val _groupLoading = MutableStateFlow(false)
    val groupLoading: StateFlow<Boolean> = _groupLoading.asStateFlow()
    private val groupLoadingStates = ConcurrentHashMap<String, Boolean>()

    private val _currentGroupId = MutableStateFlow("")
    val currentGroupId: StateFlow<String> = _currentGroupId.asStateFlow()

    private val _lastSendError = MutableStateFlow("")
    val lastSendError: StateFlow<String> = _lastSendError.asStateFlow()

    private var groupMessagesJob: Job? = null
    private val pageSize: Long get() = CHAT_PAGE_SIZE
    private val groupMessageMutexes = ConcurrentHashMap<String, Mutex>()
    private fun mutexFor(groupId: String): Mutex = groupMessageMutexes.computeIfAbsent(groupId) { Mutex() }
    private val memoryV2Pipeline = MemoryV2Pipeline(repository, settings, sharedUtils.aiService, memoryVectorService) { getUserProfile().nickname }
    private val memoryContextBuilder = MemoryContextBuilder(settings, memoryVectorService)

    // 自动群聊
    private val lastUserMsgTime = ConcurrentHashMap<String, Long>()
    private val autoRoundCounts = ConcurrentHashMap<String, Int>()
    private val groupAiJobs = ConcurrentHashMap<String, Job>()
    private val groupJobLock = Any()

    private fun setGroupLoading(groupId: String, loading: Boolean) {
        if (loading) groupLoadingStates[groupId] = true else groupLoadingStates.remove(groupId)
        if (_currentGroupId.value == groupId) _groupLoading.value = loading
    }

    fun setCurrentGroup(groupSessionId: String) {
        val sameGroup = _currentGroupId.value == groupSessionId && groupMessagesJob?.isActive == true
        ChatTrace.d("GroupVM", "setCurrent group=$groupSessionId sameGroup=$sameGroup")
        if (sameGroup) {
            markSessionRead(groupSessionId)
            _groupLoading.value = groupLoadingStates[groupSessionId] == true
            return
        }
        _currentGroupId.value = groupSessionId
        _groupRestartAt.value = settings.getSessionRestartAt(groupSessionId)
        _groupLoading.value = groupLoadingStates[groupSessionId] == true
        markSessionRead(groupSessionId)
        groupMessagesJob?.cancel()
        _groupMessages.value = emptyList()
        _hasMoreGroupMessages.value = true
        groupMessagesJob = scope.launch {
            try {
                repository.getRecentMessages(groupSessionId, pageSize).collect { msgs ->
                    ChatTrace.d("GroupVM", "flow group=$groupSessionId count=${msgs.size}")
                    mergeGroupMessagesFromFlow(msgs)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                ChatTrace.d("GroupVM", "flow.CANCEL group=$groupSessionId")
            } catch (e: Exception) {
                ChatTrace.e("GroupVM", "flow.ERROR group=$groupSessionId err=${e.message}", e)
            }
        }
    }

    fun clearCurrentGroup() {
        _currentGroupId.value = ""
        _groupRestartAt.value = 0L
        groupMessagesJob?.cancel()
        _groupMessages.value = emptyList()
        _hasMoreGroupMessages.value = true
        _isLoadingOlderGroupMessages.value = false
        groupActivityCache.clear()
    }

    fun loadOlderGroupMessages() {
        val groupId = _currentGroupId.value
        val first = _groupMessages.value.firstOrNull() ?: return
        if (groupId.isBlank() || _isLoadingOlderGroupMessages.value || !_hasMoreGroupMessages.value) return
        scope.launch {
            _isLoadingOlderGroupMessages.value = true
            try {
                val older = repository.getMessagesBefore(groupId, first.timestamp, first.id, pageSize)
                ChatTrace.d("GroupVM", "loadOlder group=$groupId before=${first.id}/${first.timestamp} result=${older.size} ids=${ChatTrace.ids(older.map { it.id })}")
                if (older.isEmpty() || older.size < pageSize) _hasMoreGroupMessages.value = false
                if (older.isNotEmpty()) {
                    _groupMessages.value = (older + _groupMessages.value).distinctBy { it.id }.sortedWith(compareBy<ChatMessage> { it.timestamp }.thenBy { it.id })
                }
            } catch (e: Exception) {
                DebugLogger.log("GroupChat/Paging", "加载群历史失败: ${e.message}")
            } finally {
                _isLoadingOlderGroupMessages.value = false
            }
        }
    }

    private fun mergeGroupMessagesFromFlow(messages: List<ChatMessage>) {
        val sortedIncoming = messages.distinctBy { it.id }.sortedWith(compareBy<ChatMessage> { it.timestamp }.thenBy { it.id })
        val olderLoaded = sortedIncoming.firstOrNull()?.let { firstRecent ->
            _groupMessages.value.filter { it.timestamp < firstRecent.timestamp || (it.timestamp == firstRecent.timestamp && it.id < firstRecent.id) }
        } ?: emptyList()
        val merged = (olderLoaded + sortedIncoming)
            .distinctBy { it.id }
            .sortedWith(compareBy<ChatMessage> { it.timestamp }.thenBy { it.id })
        _groupMessages.value = merged
        if (merged.size == sortedIncoming.size) {
            _hasMoreGroupMessages.value = sortedIncoming.size >= pageSize
        }
        ChatTrace.d("GroupVM", "merge incoming=${sortedIncoming.size} total=${merged.size} hasMore=${_hasMoreGroupMessages.value}")
    }

    fun clear() {
        groupMessagesJob?.cancel()
        groupMessagesJob = null
        groupAiJobs.values.forEach { it.cancel() }
        groupAiJobs.clear()
        stopAllAutoGroupChats()
        scope.cancel()
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
        restartGroupSession(groupId)
    }

    fun restartGroupSession(groupId: String) {
        if (groupId.isBlank()) return
        scope.launch {
            val now = System.currentTimeMillis()
            settings.putSessionRestartAt(groupId, now)
            settings.putSummaryCursor(groupId, 0L)
            if (_currentGroupId.value == groupId) _groupRestartAt.value = now
            repository.sendMessage(groupId, ChatMessage(
                id = repository.getNextMessageId(),
                sessionId = groupId,
                senderName = "系统",
                content = "已从这里开始新的群聊。上方旧群聊会保留为浅灰色，后续回复默认只参考新群聊。",
                type = "system",
                timestamp = now,
                isMe = false
            ))
        }
    }

    fun deleteGroup(groupSessionId: String, onComplete: () -> Unit = {}) {
        stopAutoGroupChat(groupSessionId)
        settings.remove("group_auto_$groupSessionId")
        settings.remove("group_event_auto_$groupSessionId")
        scope.launch {
            repository.purgeSessionData(groupSessionId)
            repository.deleteSession(groupSessionId)
            onComplete()
        }
    }

    fun isAutoGroupChatEnabled(groupId: String): Boolean =
        settings.autoAiEnabled && settings.getGroupAuto(groupId)

    fun setAutoGroupChatEnabled(groupId: String, enabled: Boolean) {
        settings.putGroupAuto(groupId, enabled)
        if (enabled && settings.autoAiEnabled) {
            autoRoundCounts[groupId] = 0
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
            try {
                while (isAutoGroupChatEnabled(groupId)) {
                    val nextRound = (autoRoundCounts[groupId] ?: 0) + 1
                    val maxRounds = settings.groupAutoMaxRounds
                    if (nextRound > maxRounds) {
                        DebugLogger.log("GroupChat/Auto", "达到连续轮数上限暂停: id=$groupId, max=$maxRounds")
                        break
                    }
                    if (autoChatGenerations[groupId] != generation) {
                        DebugLogger.log("GroupChat/Auto", "gen变化退出: id=$groupId")
                        break
                    }
                    // 先等待完整间隔，再发消息（首次也一样）
                    val interval = minMs + (Math.random() * (maxMs - minMs).coerceAtLeast(0L)).toLong()
                    DebugLogger.log("GroupChat/Auto", "第${nextRound}轮: id=$groupId, 等待${interval/1000}秒, gen=$generation")
                    val tickMs = 1000L
                    var remaining = interval
                    while (remaining > 0 && isAutoGroupChatEnabled(groupId)) {
                        if (autoChatGenerations[groupId] != generation) break
                        delay(minOf(remaining, tickMs))
                        remaining -= tickMs
                    }
                    if (autoChatGenerations[groupId] != generation) break
                    // 等够间隔，发消息
                    autoRoundCounts[groupId] = nextRound
                    DebugLogger.log("GroupChat/Auto", "发消息: id=$groupId, round=$nextRound")
                    val session = repository.getSession(groupId) ?: break
                    val mode = getGroupChatMode(groupId)
                    sendGroupMessage(groupId, groupName, "", mode, isAuto = true)
                }
            } finally {
                if (autoChatGenerations[groupId] == generation) {
                    autoGroupChatJobs.remove(groupId)
                }
            }
        }
    }

    fun resetAutoGroupChatTimer(groupId: String) {
        DebugLogger.log("GroupChat/Auto", "重置计时器: id=$groupId")
        lastUserMsgTime[groupId] = System.currentTimeMillis()
        autoRoundCounts[groupId] = 0
        if (isAutoGroupChatEnabled(groupId)) {
            autoChatGenerations[groupId] = (autoChatGenerations[groupId] ?: 0L) + 1L
            autoGroupChatJobs[groupId]?.cancel()
            autoGroupChatJobs.remove(groupId)
            scope.launch {
                val session = repository.getSession(groupId)
                if (session != null) startAutoGroupChat(groupId, session.operatorName)
            }
        }
    }

    private fun getGroupChatMode(groupId: String): String =
        settings.getGroupMode(groupId)

    fun stopAutoGroupChat(groupId: String) {
        autoChatGenerations[groupId] = (autoChatGenerations[groupId] ?: 0L) + 1L
        autoGroupChatJobs[groupId]?.cancel()
        autoGroupChatJobs.remove(groupId)
        autoRoundCounts.remove(groupId)
    }

    fun stopAllAutoGroupChats() {
        autoChatGenerations.clear()
        autoGroupChatJobs.values.forEach { it.cancel() }
        autoGroupChatJobs.clear()
    }

    fun refreshAutoGroupChats() {
        if (!settings.autoAiEnabled) {
            stopAllAutoGroupChats()
            return
        }
        appState.sessions.value.filter { it.operatorId.startsWith("group_") || it.operatorId.startsWith("group") }.forEach { group ->
            if (settings.getGroupAuto(group.id)) {
                startAutoGroupChat(group.id, group.operatorName)
            } else {
                stopAutoGroupChat(group.id)
            }
        }
    }

    fun sendGroupMessage(groupSessionId: String, groupName: String, text: String, mode: String = "online", autoSpeak: Boolean = false, isAuto: Boolean = false, userMessageAlreadyStored: Boolean = false, onMessageSent: () -> Unit = {}) {
        if (isAuto && !settings.autoAiEnabled) return
        if (isAuto && !consumeAutoAiBudget("idle_group_chat")) return
        synchronized(groupJobLock) {
            groupAiJobs[groupSessionId]?.cancel()
            groupAiJobs[groupSessionId] = scope.launch {
            // 步骤1: 用户消息立即插入（不持锁），消息即时显示
            if (!isAuto && !userMessageAlreadyStored && text.isNotBlank()) {
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
                settings.grantDailyLmb(today, 10)
            }

            // 步骤2: AI 处理 — 串行化（持锁）
            var mutexLocked = false
            try {
                mutexFor(groupSessionId).lock()
                mutexLocked = true
                setGroupLoading(groupSessionId, true)
                val session = repository.getSession(groupSessionId) ?: run {
                    DebugLogger.log("GroupChat", "⚠️ 群session不存在: $groupSessionId")
                    setGroupLoading(groupSessionId, false); if (mutexLocked) { mutexFor(groupSessionId).unlock(); mutexLocked = false }; return@launch
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
                    setGroupLoading(groupSessionId, false)
                    if (mutexLocked) { mutexFor(groupSessionId).unlock(); mutexLocked = false }
                    return@launch
                }

                val profile = getUserProfile()
                val relContext = getGroupRelationshipContext(activeMembers)
                val relationHints = if (relContext.isNotBlank()) relContext else "无"
                val memberPrivateContext = buildString {
                    appendLine("【共同经历引用风格】${when (settings.personalMemoryReferenceStyle) { "restrained" -> "仅在高度相关时提及"; "proactive" -> "可主动自然关联共同经历"; else -> "话题相关时自然提及，不要无故翻旧账" }}")
                    activeMembers.forEach { member ->
                        val knowledge = memoryV2Pipeline.buildPrivateMemoryContext(member.id, limitL1 = 1, limitL2 = 2, limitL3 = 1, query = text)
                        if (knowledge.isNotBlank()) {
                            appendLine("【${member.name}的个人知识，仅${member.name}发言时可使用】")
                            appendLine(knowledge)
                        }
                    }
                }.ifBlank { "无" }
                val groupSummary = ""
                val memberMemoryContext = ""
                val sourceAwareMemories = "无"
                val groupUnconsumedEvents = sharedUtils.trimContextBlock(sharedUtils.buildUnconsumedEventContextForGroup(groupSessionId, activeMembers.map { it.id }, activeMembers.map { it.name }, settings.eventContextCount, markConsumed = false), sharedUtils.contextBlockLimit())
                val groupVectorMemories = memoryV2Pipeline.buildOwnerMemoryContext(
                    ownerType = "group",
                    ownerId = groupSessionId,
                    limitL1 = 3,
                    limitL2 = 3,
                    limitL3 = 1,
                    query = text,
                ).ifBlank { "无" }
                val groupDailySummary = if (UnifiedMemoryContext.shouldIncludeTimeSummary(text)) {
                    (repository.getLatestDailyBySession(groupSessionId) ?: repository.getLatestDaily())?.content ?: "无"
                } else "无"
                val legacyMemberMemory = ""
                val unifiedGroupMemory = UnifiedMemoryContext.mergeBlocks(
                    maxChars = sharedUtils.contextBlockLimit(2),
                    legacyMemberMemory,
                    groupVectorMemories,
                    groupUnconsumedEvents
                )
                val unifiedKnownFrom = UnifiedMemoryContext.mergeBlocks(
                    maxChars = sharedUtils.contextBlockLimit(),
                    if (legacyMemberMemory.isNotBlank()) sourceAwareMemories else "",
                    if (unifiedGroupMemory != "无") "关系网和公开场合可自然听说的内容已合并在群聊背景中。" else ""
                )
                DebugLogger.log(
                    "Memory/Inject",
                    "群聊记忆注入: group=$groupName, mode=${if (isAuto) "auto/$mode" else mode}, members=${activeMembers.size}, relationHints=${relationHints != "无"}, privateCtxLines=${memberPrivateContext.lines().filter { it.isNotBlank() }.size}, unified=${unifiedGroupMemory != "无"}, summary=${groupSummary.isNotBlank()}, daily=${groupDailySummary != "无"}"
                )
                val memberProfiles = buildString {
                    for (m in activeMembers.sortedBy { it.id }) {
                        val key = "${groupSessionId}_${m.id}"
                        val act = groupActivityCache.computeIfAbsent(key) { "活跃${"%.1f".format(0.5 + Math.random() * 0.5)}" }
                        val titleStr = if (m.title.isBlank()) "" else "，${m.title}"
                        val genderStr = if (m.gender.isNotBlank()) "，${m.gender}" else ""
                        append("${m.name}（${act}${genderStr}${titleStr}）：${m.groupPrompt.ifBlank { m.privatePrompt.ifBlank { m.description } }}\n")
                    }
                }
                val userMessage = if (isAuto) "（用户没有新发言。请只根据最近群聊自然延续话题，不要替用户发言。）" else if (autoSpeak) "（群聊已空闲一段时间，干员们自然地闲聊起来，无需等待用户发言。）" else text
                val grpTpl = getPromptTemplate("group", mode)
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
                    "AUTO_REASON" to (if (isAuto) "idle" else "manual"),
                    "AUTO_REASON_TEXT" to (if (isAuto) "群聊空闲自然闲聊。" else "用户主动发言。"),
                    "GROUP_RULES" to (session.rules.ifBlank { "无" }),
                    "USER_NAME" to profile.nickname, "USER_GENDER" to profile.gender.ifBlank { "未知" }, "USER_PREFS" to "仅使用公开场合已知的用户偏好；无则不特别提及。",
                    "USER_BIO" to profile.bio.ifBlank { "无" }, "RELATION_HINTS" to sharedUtils.trimContextBlock(relationHints, sharedUtils.contextBlockLimit()),
                    "MEMBER_PRIVATE_CONTEXT" to sharedUtils.trimContextBlock(memberPrivateContext, sharedUtils.contextBlockLimit()),
                    "SHORT_TERM_SUMMARY" to groupSummary, "GROUP_SUMMARY" to groupSummary,
                    "DAILY_SUMMARY" to groupDailySummary,
                    "LONG_TERM_IMPRESSION" to "无",
                    "SOURCE_AWARE_MEMORIES" to unifiedKnownFrom,
                    "MEMORY_ANCHORS" to unifiedGroupMemory,
                    "MEMORY_V2_CONTEXT" to unifiedGroupMemory,
                    "GROUP_TRIGGER_EVENT" to "群聊自然延续",
                    "GROUP_RECENT_WORLD_EVENTS" to "无",
                    "GROUP_TOPIC_SEED" to "无",
                    "GROUP_UNCONSUMED_EVENTS" to groupUnconsumedEvents,
                    "KNOWN_FROM_CONTEXT" to sourceAwareMemories,
                    "SOURCE_AWARE_RULES" to sharedUtils.sourceAwareUsageRule(MemorySurface.GROUP_CHAT),
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
                sharedUtils.logMemoryContext(
                    surface = "group_chat",
                    title = "$groupName/$groupSessionId",
                    placeholders = mapOf(
                        "RELATION_HINTS" to relationHints,
                        "MEMBER_PRIVATE_CONTEXT" to memberPrivateContext,
                        "SHORT_TERM_SUMMARY" to groupSummary,
                        "GROUP_SUMMARY" to groupSummary,
                        "DAILY_SUMMARY" to grpReplacements["DAILY_SUMMARY"].orEmpty(),
                        "LONG_TERM_IMPRESSION" to grpReplacements["LONG_TERM_IMPRESSION"].orEmpty(),
                        "SOURCE_AWARE_MEMORIES" to grpReplacements["SOURCE_AWARE_MEMORIES"].orEmpty(),
                        "GROUP_UNCONSUMED_EVENTS" to grpReplacements["GROUP_UNCONSUMED_EVENTS"].orEmpty(),
                        "MEMBER_PROFILES" to memberProfiles.toString(),
                        "USER_MESSAGE" to userMessage,
                        "MEMBER_NAMES" to activeMembers.joinToString("、") { it.name }
                    ),
                    extra = mapOf(
                        "mode" to mode,
                        "isAuto" to isAuto.toString(),
                        "activeMembers" to activeMembers.size.toString(),
                        "groupMemberMemoryCount" to settings.groupMemberMemoryCount.toString(),
                        "groupRelationshipHintCount" to settings.groupRelationshipHintCount.toString()
                    )
                )
                val finalSystemPrompt = sharedUtils.compactTemplate(sharedUtils.applyTemplate(grpTpl, grpReplacements))
                val historyLimit = settings.historyMessages
                val activeNames = activeMembers.map { it.name }.toSet() + "我" + "系统"
                val allHistory = repository.getMessagesSync(groupSessionId).let { msgs ->
                    val restartAt = settings.getSessionRestartAt(groupSessionId)
                    val currentConversation = if (restartAt > 0L) msgs.filter { it.timestamp >= restartAt } else msgs
                    val limited = if (historyLimit > 0) currentConversation.takeLast(historyLimit) else currentConversation
                    limited.filter { msg -> msg.isMe || msg.type == "system" || msg.type == "ai_json" || msg.senderName in activeNames }
                }.toMutableList()
                // 去掉最后一条用户消息，避免与下文重复
                if (!isAuto && allHistory.lastOrNull()?.isMe == true) {
                    allHistory.removeAt(allHistory.lastIndex)
                }
                val apiMessages = mutableListOf(AiMessage("system", finalSystemPrompt))
                allHistory.forEach { msg ->
                    val formatted = formatGroupHistoryForPrompt(msg).take(1200)
                    if (formatted.isNotBlank()) {
                        apiMessages.add(AiMessage(if (msg.isMe) "user" else "assistant", formatted))
                    }
                }
                if (!isAuto) {
                    val userMsg = if (autoSpeak) "（群聊已空闲一段时间，干员们自然地闲聊起来，无需等待用户发言。）" else text
                    apiMessages.add(AiMessage("user", "用户：$userMsg"))
                }
                // 估算总 token，超限则丢弃最早的历史消息
                val maxPromptTokens = settings.maxContextTokens - 2000
                var totalTokens = apiMessages.sumOf { estimateTokens(it.content) + 10 }
                com.rhodes.privatechat.util.DebugLogger.log("GroupChat/Token", "估算token=$totalTokens, 上限=$maxPromptTokens, 消息数=${apiMessages.size}")
                if (totalTokens > maxPromptTokens) {
                    while (apiMessages.size > 2 && totalTokens > maxPromptTokens) {
                        apiMessages.removeAt(1)
                        totalTokens = apiMessages.sumOf { estimateTokens(it.content) + 10 }
                    }
                    com.rhodes.privatechat.util.DebugLogger.log("GroupChat/Token", "截断后: 消息数=${apiMessages.size}, 估算token=$totalTokens")
                }
                val promptText = apiMessages.firstOrNull()?.content ?: ""
                if (DEBUG) sharedUtils.logAiCall("GroupChat", promptText, "(requesting...)", apiMessages)
                val validSpeakers = activeMembers.map { it.name }.toSet() + "旁白"
                var rawBase = ""
                var filtered: List<GroupMsgResult> = emptyList()
                var firstPass: List<GroupMsgResult> = emptyList()
                var requestMessages = apiMessages.toList()
                repeat(2) { attempt ->
                    if (filtered.isNotEmpty()) return@repeat
                    rawBase = withTimeout(90_000) { sharedUtils.chat(requestMessages, "GroupChat") }.trim()
                        .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    sharedUtils.trackTokens("group", requestMessages, rawBase)
                    if (DEBUG) sharedUtils.logAiCall("GroupChat", promptText, rawBase, requestMessages)
                    val results = extractGroupResults(rawBase)
                    val normalized = normalizeGroupResults(results, validSpeakers, mode)
                    if (attempt == 0) firstPass = normalized
                    filtered = if (attempt == 0) normalized else firstPass + normalized
                    if (!hasRequiredGroupTurn(filtered, activeMembers.map { it.name }, mode)) filtered = emptyList()
                    if (filtered.isEmpty() && attempt == 0) {
                        requestMessages = apiMessages + AiMessage(
                            "user",
                            if (mode == "online") {
                                "请只补充以下尚未发言成员的非空 dialogue：${missingMemberNames(firstPass, activeMembers.map { it.name }).joinToString("、")}。严格输出 JSON 数组；speaker 只能从名单中选择，message 必须是纯文字台词。不要重复已有发言、不要旁白、动作、神态、场景描写或解释。"
                            } else {
                                "请只补充以下尚未发言成员的台词：${missingMemberNames(firstPass, activeMembers.map { it.name }).joinToString("、")}。可为这些成员补充贴近其台词的旁白；严格输出 JSON 数组，不要重复已有成员发言、不要输出名单外角色或解释。"
                            }
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    repository.sendMessage(groupSessionId, ChatMessage(
                        id = repository.getNextMessageId(), sessionId = groupSessionId,
                        senderName = "系统", content = "群聊回复格式错误或发言者不在当前群成员中，请重试。",
                        type = "system", mode = mode, isMe = false
                    ))
                    markGroupUnreadIfNotCurrent(groupSessionId)
                }
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
                    notifyIfBackground(groupName, filtered.firstOrNull()?.let { "${it.speaker}：${it.message}" } ?: "群聊有新消息", groupSessionId)
                    val last = filtered.last()
                    repository.updateLastMessage(groupSessionId, "${last.speaker}：${last.message.take(50)}", System.currentTimeMillis())
                    if (groupUnconsumedEvents != "无") {
                        sharedUtils.buildUnconsumedEventContextForGroup(groupSessionId, activeMembers.map { it.id }, activeMembers.map { it.name }, settings.eventContextCount, markConsumed = true)
                    }
                }
                if (filtered.isNotEmpty()) markGroupUnreadIfNotCurrent(groupSessionId, visibleGroupMessageCount(filtered, mode))
                val gc = sessionMessageCounter.merge(groupSessionId, 1) { old, inc -> old + inc } ?: 1
                if (gc >= settings.summaryThreshold && groupSessionId.isNotBlank()) {
                    val gs = repository.getSession(groupSessionId)
                    if (gs != null) {
                        sessionMessageCounter[groupSessionId] = 0
                        generateGroupShortTermSummary(groupSessionId, gs.operatorName)
                        // 生成群聊每日摘要（昨日消息 >1 条时）
                        generateGroupDailySummary(groupSessionId, gs.operatorName)
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e("GroupChat", "Timeout: ${e.message}")
                repository.sendMessage(groupSessionId, ChatMessage(id = repository.getNextMessageId(), sessionId = groupSessionId, senderName = "系统", content = "响应超时，请重试", type = "system", mode = mode, isMe = false))
                markGroupUnreadIfNotCurrent(groupSessionId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 被新消息取消，不做任何事
            } catch (e: Exception) {
                val errMsg = classifyGroupError(e)
                Log.e("GroupChat", "Error: ${e.message}", e)
                DebugLogger.log("GroupChat/Error", "发送失败: $errMsg")
                repository.sendMessage(groupSessionId, ChatMessage(id = repository.getNextMessageId(), sessionId = groupSessionId, senderName = "系统", content = errMsg, type = "system", mode = mode, isMe = false))
                markGroupUnreadIfNotCurrent(groupSessionId)
                _lastSendError.value = errMsg
            } finally {
                setGroupLoading(groupSessionId, false)
                if (groupAiJobs[groupSessionId] == coroutineContext[Job]) groupAiJobs.remove(groupSessionId)
                if (mutexLocked) mutexFor(groupSessionId).unlock()
            }
            }
        }
    }

    private fun formatGroupHistoryForPrompt(msg: ChatMessage): String {
        if (msg.type == "image" && msg.isMe) return formatGroupImageForPrompt(msg)
        if (msg.isMe) return "用户：${msg.content.take(500)}"
        if (msg.type == "system") return "系统：${msg.content.take(300)}"
        if (msg.type != "ai_json") return "${msg.senderName}：${msg.content.take(500)}"
        return try {
            val items = extractGroupResults(msg.content).take(8)
            if (items.isNotEmpty()) {
                items.joinToString("\n") { r -> if (r.type == "narration" || r.speaker == "旁白") "旁白：${r.message.take(300)}" else "${r.speaker}：${r.message.take(300)}" }
            } else "群聊回复：[上一条消息格式异常]"
        } catch (_: Exception) {
            "群聊回复：[上一条消息格式异常]"
        }
    }

    private fun formatGroupMessageForMemory(msg: ChatMessage, limit: Int): String {
        if (msg.type == "system") return ""
        if (msg.type == "image" && msg.isMe) return formatGroupImageForPrompt(msg).take(limit)
        if (!msg.isMe && msg.type != "ai_json") return ""
        if (msg.isMe) return "用户：${msg.content.take(limit)}"
        if (msg.type == "system") return "系统：${msg.content.take(limit)}"
        if (msg.type != "ai_json") return "${msg.senderName}：${msg.content.take(limit)}"
        return try {
            val items = extractGroupResults(msg.content).take(8)
            if (items.isNotEmpty()) {
                items.joinToString("\n") { r ->
                    if (r.type == "narration" || r.speaker == "旁白") "旁白：${r.message.take(limit)}" else "${r.speaker}：${r.message.take(limit)}"
                }
            } else "群聊回复：[格式异常]"
        } catch (_: Exception) {
            "群聊回复：[格式异常]"
        }
    }

    private fun normalizeGroupResults(results: List<GroupMsgResult>, validSpeakers: Set<String>, mode: String): List<GroupMsgResult> {
        return results.mapNotNull { raw ->
            val stripped = stripSpeakerPrefix(raw.message)
            var speaker = raw.speaker.trim().ifBlank { stripped.first.ifBlank { "旁白" } }
            var message = stripped.second.ifBlank { raw.message }.trim()
            var type = if (raw.type.equals("narration", true) || raw.type == "旁白") "narration" else "dialogue"
            if (stripped.first.isNotBlank() && stripped.first in validSpeakers) speaker = stripped.first
            if (speaker == "旁白") type = "narration"
            if (type == "narration") speaker = "旁白"
            if (type == "narration" && containsFirstPersonNarration(message)) return@mapNotNull null
            if (type == "dialogue" && looksNarrationLike(message)) {
                if (containsFirstPersonNarration(message)) return@mapNotNull null
                speaker = "旁白"
                type = "narration"
            }
            if (mode == "online" && type == "narration") return@mapNotNull null
            if (message.isBlank() || speaker !in validSpeakers) return@mapNotNull null
            GroupMsgResult(speaker = speaker, message = message, type = type)
        }
    }

    private fun stripSpeakerPrefix(content: String): Pair<String, String> {
        val idx = listOf(content.indexOf('：'), content.indexOf(':')).filter { it in 1..12 }.minOrNull() ?: return "" to content
        return content.substring(0, idx).trim(' ', '“', '”', '"') to content.substring(idx + 1).trim()
    }

    private fun looksNarrationLike(content: String): Boolean {
        val text = content.take(80)
        return listOf("牌桌上", "气氛", "众人", "看向", "走到", "坐在", "站在", "抬手", "转身", "垂眸", "低头", "环顾", "风", "灯光").any { text.contains(it) }
    }

    private fun containsFirstPersonNarration(content: String): Boolean {
        val text = content.take(120)
        return listOf("我", "我们", "咱们", "俺", "咱").any { text.contains(it) }
    }

    private fun estimateTokens(content: String): Int {
        var total = 0
        for (ch in content) total += if (ch.code <= 0x7F) 1 else 2
        return (total * 1.2).toInt()
    }

    fun sendGroupImageMessage(groupSessionId: String, groupName: String, imageUri: String, imageForModel: String?, caption: String, mode: String = "online", onMessageSent: () -> Unit = {}, onResult: (Boolean) -> Unit = {}) {
        if (groupSessionId.isBlank()) { onResult(false); return }
        if (!isVisionConfigured()) {
            _lastSendError.value = "图片聊天需要先设置识图模型，请在模型设置中填写识图地址、模型名和密钥。"
            onResult(false)
            return
        }
        if (imageForModel.isNullOrBlank()) {
            _lastSendError.value = "无法读取这张图片，请重新选择后再试。"
            onResult(false)
            return
        }
        scope.launch {
            try {
                val id = repository.getNextMessageId()
                val placeholderJson = json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), kotlinx.serialization.json.JsonObject(mapOf(
                    "imageUri" to kotlinx.serialization.json.JsonPrimitive(imageUri),
                    "caption" to kotlinx.serialization.json.JsonPrimitive(caption.trim()),
                    "visionSummary" to kotlinx.serialization.json.JsonPrimitive("")
                )))
                repository.sendMessage(groupSessionId, ChatMessage(
                    id = id,
                    sessionId = groupSessionId,
                    senderName = "我",
                    content = placeholderJson,
                    type = "image",
                    mode = mode,
                    isMe = true
                ))
                // The message is safely persisted now. The composer must never wait for vision/AI work.
                onMessageSent()
                onResult(true)
                val visionText = try {
                    currentVisionGateway().analyzeImage(VisionAnalyzeRequest(
                        imageUrlOrBase64 = imageForModel,
                        prompt = """请分析这张图片，用以下 JSON 格式回答（只输出 JSON，不要 Markdown 标记）：
{
  "visibleSummary": "一句话描述画面中最重要的可见内容",
  "userStateGuess": "基于画面的谨慎推测，不确定写 unknown",
  "notableObjects": ["物体1", "物体2"],
  "sceneQuality": "clear | dim | blurry | blocked | unknown",
  "confidence": 0.0~1.0
}
要求：只描述确定看到的内容，不确定的字段填 unknown，不要编造看不见的内容，输出中文。"""
                    ))?.text?.take(2000).orEmpty().ifBlank { throw IllegalStateException("没有识别到图片内容") }
                } catch (e: Exception) {
                    DebugLogger.log("Vision", "群聊识图失败: ${e.message?.take(100)}")
                    val failedJson = json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), kotlinx.serialization.json.JsonObject(mapOf(
                        "imageUri" to kotlinx.serialization.json.JsonPrimitive(imageUri),
                        "caption" to kotlinx.serialization.json.JsonPrimitive(caption.trim()),
                        "visionSummary" to kotlinx.serialization.json.JsonPrimitive(""),
                        "status" to kotlinx.serialization.json.JsonPrimitive("failed")
                    )))
                    repository.updateMessageContent(id, failedJson)
                    _lastSendError.value = "图片识别失败，请检查识图模型设置后重试。"
                    onResult(false)
                    return@launch
                }
                val cleanText = visionText.trim().removePrefix("```json").removePrefix("```").trim().removeSuffix("```").trim()
                val visionSummary = runCatching {
                    json.parseToJsonElement(cleanText).jsonObject["visibleSummary"]?.jsonPrimitive?.content
                }.getOrNull()?.take(500)
                val imageJson = json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), kotlinx.serialization.json.JsonObject(mapOf(
                    "imageUri" to kotlinx.serialization.json.JsonPrimitive(imageUri),
                    "caption" to kotlinx.serialization.json.JsonPrimitive(caption.trim()),
                    "visionSummary" to kotlinx.serialization.json.JsonPrimitive(visionText.take(800))
                )))
                repository.updateMessageContent(id, imageJson)
                val promptText = buildString {
                    append("用户在群聊中发送了一张图片。")
                    if (caption.isNotBlank()) append("\n用户附带文字：${caption.trim()}")
                    append("\n【用户发送的图片分析】")
                    append("\n画面内容：${visionSummary ?: visionText.take(500)}")
                    append("\n请所有在场成员根据自己的性格、关系和当前群聊氛围自然回应这张图片。")
                }
                saveVisionVectorMemory(groupSessionId, caption, visionSummary ?: visionText)
                // The image message above is the only user-visible record. The vision result is AI-only context.
                sendGroupMessage(groupSessionId, groupName, promptText, mode, userMessageAlreadyStored = true)
            } catch (e: Exception) {
                _lastSendError.value = classifyGroupError(e)
                onResult(false)
            }
        }
    }

    private fun hasRequiredGroupTurn(results: List<GroupMsgResult>, activeNames: List<String>, mode: String): Boolean {
        // Narration placement is a style preference. Only real dialogue from every active member is blocking.
        return activeNames.none { name -> results.none { it.speaker == name && it.type == "dialogue" && it.message.isNotBlank() } }
    }

    private fun missingMemberNames(results: List<GroupMsgResult>, activeNames: List<String>): List<String> =
        activeNames.filter { name -> results.none { it.speaker == name && it.type == "dialogue" && it.message.isNotBlank() } }

    private fun formatGroupImageForPrompt(msg: ChatMessage): String = try {
        val obj = json.parseToJsonElement(msg.content).jsonObject
        val caption = obj["caption"]?.jsonPrimitive?.content.orEmpty()
        val rawSummary = obj["visionSummary"]?.jsonPrimitive?.content.orEmpty()
        val cleanSummary = rawSummary.trim().removePrefix("```json").removePrefix("```").trim().removeSuffix("```").trim()
        val summary = runCatching {
            json.parseToJsonElement(cleanSummary).jsonObject["visibleSummary"]?.jsonPrimitive?.content
        }.getOrNull() ?: rawSummary.take(200)
        buildString {
            append("用户发送图片")
            if (summary.isNotBlank()) append("：$summary")
            if (caption.isNotBlank()) append("；附言：$caption")
        }
    } catch (_: Exception) { "用户发送了一张图片" }

    private fun isVisionConfigured(): Boolean =
        settings.visionBaseUrl.isNotBlank() &&
            settings.visionModelName.isNotBlank() &&
            settings.visionApiKey.ifBlank { settings.apiKey }.isNotBlank()

    private fun currentVisionGateway(): VisionGateway = createVisionGateway(settings)

    private suspend fun saveGroupAnchorToVector(anchor: MemoryAnchor, groupSessionId: String, groupName: String) {
        repository.saveAnchor(anchor)
        val service = memoryVectorService ?: return
        val now = anchor.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
        val importance = if (anchor.importance == AnchorSourcePolicy.WEAK) 0.25 else 0.6
        val content = "在群聊「$groupName」中，${anchor.sourceActor.ifBlank { "成员" }}提到：${anchor.content}".take(180)
        try {
            service.saveMemory(VectorMemory(
                id = "anchor_${anchor.operatorId}_${now}_${anchor.content.hashCode()}",
                ownerType = "operator",
                ownerId = anchor.operatorId,
                sourceType = "anchor_${anchor.source.ifBlank { anchor.type.name.lowercase() }}",
                sourceId = anchor.sessionId,
                content = content,
                importance = importance,
                tags = anchor.type.name,
                visibility = "public",
                createdAt = now,
                expiresAt = anchor.expiresAt
            ))
            service.saveMemory(VectorMemory(
                id = "group_anchor_${groupSessionId}_${anchor.operatorId}_${now}_${anchor.content.hashCode()}",
                ownerType = "group",
                ownerId = groupSessionId,
                sourceType = "anchor_${anchor.source.ifBlank { anchor.type.name.lowercase() }}",
                sourceId = anchor.sessionId,
                content = content,
                importance = importance,
                tags = anchor.type.name,
                visibility = "public",
                createdAt = now,
                expiresAt = anchor.expiresAt
            ))
        } catch (e: Exception) {
            DebugLogger.log("Vector/Save", "群聊锚点向量写入失败: ${e.message?.take(80)}")
        }
    }

    private suspend fun saveVisionVectorMemory(groupSessionId: String, caption: String, visionText: String) {
        val service = memoryVectorService ?: return
        if (visionText.isBlank() || visionText.startsWith("[")) return
        try {
            val now = System.currentTimeMillis()
            service.saveMemory(VectorMemory(
                id = "vision_group_${groupSessionId}_${now}",
                ownerType = "group",
                ownerId = groupSessionId,
                sourceType = "vision",
                sourceId = groupSessionId,
                content = buildString {
                    append("群聊图片识图内容：${visionText.take(500)}")
                    if (caption.isNotBlank()) append("；用户附带文字：${caption.take(120)}")
                },
                importance = 0.5,
                tags = "VISION",
                visibility = "public",
                createdAt = now,
                expiresAt = MemoryPolicy.memoryExpiresAt(settings)
            ))
        } catch (e: Exception) {
            DebugLogger.log("Vector/Save", "群聊图片向量写入失败: ${e.message?.take(80)}")
        }
    }

    private suspend fun recallGroupVectorMemories(groupSessionId: String, query: String): String {
        return memoryContextBuilder.groupVectorContext(groupSessionId, query)
    }

    private fun notifyIfBackground(title: String, content: String, sessionId: String?) {
        if (!RhodesAppVisibility.isForeground) {
            showNotification(title, content.take(120), sessionId)
        }
    }

    fun clearSendError() { _lastSendError.value = "" }

    private suspend fun markGroupUnreadIfNotCurrent(groupSessionId: String, count: Int = 1) {
        if (_currentGroupId.value != groupSessionId) {
            repository.incrementUnread(groupSessionId, count)
            unhideSession(groupSessionId)
        }
    }

    private fun visibleGroupMessageCount(items: List<GroupMsgResult>, mode: String): Int =
        items.count { mode != "online" || (it.type != "narration" && it.speaker != "旁白") }.coerceAtLeast(1)

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
            if (!consumeAutoAiBudget("group_daily_summary")) return
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
            val text = msgs.mapNotNull { formatGroupMessageForMemory(it, 60).takeIf { line -> line.isNotBlank() } }.joinToString("\n")
            if (text.isBlank()) return
            val dateStr = sharedUtils.beijingSdf("yyyy年MM月dd日").format(dayBegin)
            val prompt = "请总结${dateStr}「${groupName}」的聊天记录，生成50-150字的每日摘要。直接输出纯文本。\n${text}"
            val content = withTimeout(15_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Memory") }.trim()
            if (content.isNotBlank()) {
                repository.saveMemory(com.rhodes.privatechat.shared.model.Memory(
                    sessionId = groupSessionId, operatorId = groupSessionId,
                    type = com.rhodes.privatechat.shared.model.MemoryType.DAILY, content = content,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = MemoryPolicy.memoryExpiresAt(settings)
                ))
                DebugLogger.log("GroupChat", "群聊每日摘要已生成: $groupSessionId")
            }
        } catch (e: Exception) {
            DebugLogger.log("GroupChat", "群聊每日摘要失败: ${e.message?.take(80)}")
        }
    }

    private suspend fun generateGroupShortTermSummary(groupSessionId: String, groupName: String) {
        try {
            if (!consumeAutoAiBudget("group_short_summary")) return
            val retain = settings.summaryRetain.coerceAtLeast(5)
            val window = (settings.summaryThreshold + retain).coerceAtLeast(retain + 3)
            val allMessages = repository.getMessagesSync(groupSessionId)
            val cursor = if (settings.summaryCursorEnabled) settings.getSummaryCursor(groupSessionId) else 0L
            val source = (if (cursor > 0L) allMessages.filter { it.id > cursor } else allMessages).takeLast(window)
            val msgs = if (source.size > retain) source.dropLast(retain) else source
            if (msgs.size <= 2) return
            val text = msgs.mapNotNull { formatGroupMessageForMemory(it, 120).takeIf { line -> line.isNotBlank() } }.joinToString("\n")
            if (text.isBlank()) return
            val oldSummary = repository.getShortTermMemory(groupSessionId)?.content?.takeIf { it.isNotBlank() } ?: "无"
            val prompt = """请融合群聊「${groupName}」的已有摘要和新增对话，生成一份连续短期摘要。输出80-180字纯文本，不要编造。

要求：
- 保留主要话题、参与者态度、关系变化、未解决事项和下次可接的话茬。
- 已有摘要中已经稳定成立的内容可以压缩保留，不要重复流水账。
- 如果新增对话与已有摘要冲突，以新增对话为准。
- 不要出现“摘要”“系统记录”等机制词。

已有摘要：
$oldSummary

新增对话：
$text"""
            val content = withTimeout(20_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "GroupMemory") }.trim()
            if (content.isNotBlank()) {
                repository.replaceShortTermMemory(com.rhodes.privatechat.shared.model.Memory(
                    sessionId = groupSessionId,
                    operatorId = groupSessionId,
                    type = com.rhodes.privatechat.shared.model.MemoryType.SHORT_TERM,
                    content = content,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = MemoryPolicy.memoryExpiresAt(settings)
                ))
                ingestGroupMemoryV2(groupSessionId, groupName, msgs)
                if (settings.summaryCursorEnabled) msgs.maxOfOrNull { it.id }?.let { settings.putSummaryCursor(groupSessionId, it) }
                DebugLogger.log("GroupChat", "群聊短期摘要已生成: $groupSessionId")
            }
        } catch (e: Exception) {
            DebugLogger.log("GroupChat", "群聊短期摘要失败: ${e.message?.take(80)}")
        }
    }

    // === 聊天记录 ===
    suspend fun getGroupMessageDates(groupId: String): List<String> {
        return repository.getMessageDatesBySession(groupId)
    }

    suspend fun getGroupMessagesByDate(groupId: String, date: String): List<ChatMessage> {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
        }
        val start = runCatching { sdf.parse(date)?.time ?: 0L }.getOrDefault(0L)
        if (start <= 0L) return emptyList()
        return repository.getMessagesBySessionInRange(groupId, start, start + 86_400_000L - 1)
    }

    suspend fun searchGroupMessages(groupId: String, keyword: String, limit: Long = 200): List<ChatMessage> {
        val q = keyword.trim()
        if (q.isBlank()) return emptyList()
        return repository.searchMessagesInSession(groupId, q, limit)
    }

    private suspend fun ingestGroupMemoryV2(groupSessionId: String, groupName: String, messages: List<ChatMessage>) {
        if (!settings.memoryV2Enabled) return
        if (messages.isEmpty()) return
        try {
            val memberIds = repository.getSession(groupSessionId)?.members
                ?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
            memoryV2Pipeline.ingestGroupChat(groupSessionId, groupName, messages, memberIds)
        } catch (e: Exception) {
            DebugLogger.log("MemoryV2", "群聊L1写入失败: ${e.message?.take(80)}")
        }
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
        return lines.take(settings.groupRelationshipHintCount).joinToString("\n")
    }
}
