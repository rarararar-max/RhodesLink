package com.rhodes.privatechat.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rhodes.privatechat.data.ExportPayload
import com.rhodes.privatechat.data.ExportHelper
import com.rhodes.privatechat.data.MessageExport
import com.rhodes.privatechat.data.OperatorExport
import com.rhodes.privatechat.data.RelationshipExport
import com.rhodes.privatechat.data.SessionExport
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.memory.AnchorSourcePolicy
import com.rhodes.privatechat.shared.network.AIService
import com.rhodes.privatechat.shared.model.ChatMessage
import com.rhodes.privatechat.shared.model.ChatSession
import com.rhodes.privatechat.shared.model.DispatchRecord
import com.rhodes.privatechat.shared.model.MemoryAnchor
import com.rhodes.privatechat.shared.model.Memory
import com.rhodes.privatechat.shared.model.MemoryType
import com.rhodes.privatechat.shared.model.ImpressionResponse
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import com.rhodes.privatechat.shared.model.Diary
import com.rhodes.privatechat.shared.model.MomentComment
import com.rhodes.privatechat.shared.model.Moment
import com.rhodes.privatechat.shared.model.MomentLike
import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.shared.model.WorldEvent
import com.rhodes.privatechat.shared.model.WorldEventType
import com.rhodes.privatechat.util.DebugLogger
import com.rhodes.privatechat.shared.data.SenderCount
import com.rhodes.privatechat.shared.data.BfsNode
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.viewmodel.shared.OperatorStateUpdater
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.shared.PromptTemplates
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.rhodes.privatechat.viewmodel.shared.UserProfile
import com.rhodes.privatechat.viewmodel.shared.MemoryPolicy
import com.rhodes.privatechat.viewmodel.shared.MemorySurface
import com.rhodes.privatechat.shared.model.AnchorType
import com.rhodes.privatechat.shared.model.AnalysisResult
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.shared.model.OfflineModeResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

private val json = Json { ignoreUnknownKeys = true }

data class MomentGenerateStatus(val running: Boolean = false, val msg: String = "")
@Serializable
data class WorldLogEntry(val time: Long, val title: String, val detail: String = "", val type: String = "world")

private enum class MomentTriggerType { MANUAL, AUTO, EVENT }
private const val MAX_WORLD_CHAIN_DEPTH = 3
private const val MOMENT_PAGE_SIZE = 20

class MainViewModel(
    application: Application,
    val repository: ChatRepository,
    val settings: SettingsRepository,
    val appState: AppStateHolder,
    val sharedUtils: SharedUtils,
    val operatorStateUpdater: OperatorStateUpdater
) : AndroidViewModel(application) {
    data class DataStats(
        val chatSessions: Int, val groups: Int, val diaries: Int, val anchors: Int,
        val messages: Int, val operators: Int, val moments: Int = 0, val dispatches: Int = 0
    )
    companion object {
        /** 全局调试开关，上线前改为 false */
        const val DEBUG = true
        /** 道具价格 */
        const val PROP_PRICE = 100
        /** 防止多个 ViewModel 实例并发执行自动生成 */
        private val autoGenerating = java.util.concurrent.atomic.AtomicBoolean(false)
        /** 防止手动催发与自动生成互相阻塞 */
        private val forceGenerating = java.util.concurrent.atomic.AtomicBoolean(false)
        /** 催发专用 scope，不依赖 viewModelScope，切页面不中断 */
        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val _globalMomentStatus = MutableStateFlow(MomentGenerateStatus())
    }
    val dataViewModel = DataViewModel(repository, settings, viewModelScope)
    val mahjongViewModel = MahjongViewModel(repository, settings, sharedUtils, viewModelScope) { appState.operators.value }
    val sessionViewModel = SessionViewModel(repository, settings, appState, viewModelScope)
    val operatorViewModel = OperatorViewModel(repository, settings, appState, viewModelScope) { op ->
        if (op != null && op.id == chatViewModel.selectedOperator.value?.id) {
            chatViewModel.updateSelectedOperator(op)
        }
    }
    val chatViewModel = ChatViewModel(application, repository, settings, sharedUtils, operatorStateUpdater, appState,
        onShowToast = { msg -> android.widget.Toast.makeText(application, msg, android.widget.Toast.LENGTH_SHORT).show() },
        onUnhideSession = { unhideSession(it) },
        onRefreshOperatorStatus = { refreshAllOperatorStatus(force = true) }
    )
    private val sessionMessageCounter = ConcurrentHashMap<String, Int>()
    val momentsViewModel = MomentsViewModel(repository, settings, appState, viewModelScope) { getUserProfile() }
    val dispatchViewModel = DispatchViewModel(repository, settings, sharedUtils, operatorStateUpdater, appState, viewModelScope, { refreshAllOperatorStatus(force = true) }) { getUserProfile() }
    val groupChatViewModel = GroupChatViewModel(
        repository,
        settings,
        sharedUtils,
        appState,
        { chatViewModel.markSessionRead(it) },
        { unhideSession(it) },
        { getUserProfile() },
        { t, m -> chatViewModel.getPromptTemplate(t, m) },
        sessionMessageCounter,
        { reason -> tryConsumeAutoAiBudget(reason) },
        { event, parent -> derivedWorldEvent(event, parent) }
    )
    private val worldScheduler = WorldScheduler(
        repository,
        settings,
        appState,
        viewModelScope,
        { parent -> generateOneMomentForWorld(parent) },
        { parent -> triggerEventGroupChats(parent) },
        { opId -> generateDiarySync(opId, auto = true) },
        { event -> triggerProactivePrivateFromEvent(event) },
        { reason -> canUseWorldTrigger(reason) },
        { reason -> consumeWorldTrigger(reason) }
    )

    private val _momentGenerateStatus: MutableStateFlow<MomentGenerateStatus> get() = _globalMomentStatus
    val momentGenerateStatus: StateFlow<MomentGenerateStatus> = _momentGenerateStatus.asStateFlow()

    // Chat state delegates to ChatViewModel
    private val _selectedOperator get() = chatViewModel.selectedOperator
    val selectedOperator: StateFlow<Operator?> get() = chatViewModel.selectedOperator
    private val _currentSession get() = chatViewModel.currentSession
    val currentSession: StateFlow<ChatSession?> get() = chatViewModel.currentSession
    private val _messages get() = chatViewModel.messages
    val messages: StateFlow<List<ChatMessage>> get() = chatViewModel.messages
    val isLoadingOlderMessages: StateFlow<Boolean> get() = chatViewModel.isLoadingOlderMessages
    val hasMoreMessages: StateFlow<Boolean> get() = chatViewModel.hasMoreMessages
    private val _currentMode get() = chatViewModel.currentMode
    val currentMode: StateFlow<String> get() = chatViewModel.currentMode
    val inputText: StateFlow<String> get() = chatViewModel.inputText
    val isLoading: StateFlow<Boolean> get() = chatViewModel.isLoading
    val hypnosisCommand: StateFlow<String> get() = chatViewModel.hypnosisCommand
    val hypnosisRounds: StateFlow<Int> get() = chatViewModel.hypnosisRounds

    // Shared state delegates to AppStateHolder
    private val _operators get() = appState.operators
    val operators: StateFlow<List<Operator>> get() = appState.operators
    private val _sessions get() = appState.sessions
    val sessions: StateFlow<List<ChatSession>> get() = appState.sessions
    private val _allSessions get() = appState.allSessions
    val allSessions: StateFlow<List<ChatSession>> get() = appState.allSessions
    private val _userProfile get() = appState.userProfile
    val userProfile: StateFlow<UserProfile> get() = appState.userProfile
    private val _moments get() = appState.moments
    val moments: StateFlow<List<Moment>> get() = appState.moments
    private val _isLoadingMoments = MutableStateFlow(false)
    val isLoadingMoments: StateFlow<Boolean> = _isLoadingMoments.asStateFlow()
    private val _hasMoreMoments = MutableStateFlow(true)
    val hasMoreMoments: StateFlow<Boolean> = _hasMoreMoments.asStateFlow()

    fun isDualModel(): Boolean = settings.dualModel

    fun setDualModel(enabled: Boolean) { settings.dualModel = enabled }

    private val _comments = MutableStateFlow<List<MomentComment>>(emptyList())
    val comments: StateFlow<List<MomentComment>> = _comments.asStateFlow()

    private val _diaries = MutableStateFlow<List<Diary>>(emptyList())
    val diaries: StateFlow<List<Diary>> = _diaries.asStateFlow()

    private var messageCounter: Int
        get() = settings.messageCounter
        set(v) { settings.messageCounter = v }
    private var impressionMsgCounter: Int
        get() = settings.impressionMsgCounter
        set(v) { settings.impressionMsgCounter = v }
    private val shortTermThreshold: Int get() = settings.summaryThreshold
    private val updateMutex = Mutex()
    private var lastDbUpdate = 0L
    private var analysisGuidance = ""
    private var modeTransitionNotice = ""
    private var currentAutoAiTickCount = 0

    private var messagesJob: kotlinx.coroutines.Job? = null

    // Group chat state delegates to GroupChatViewModel
    val groupMessages: StateFlow<List<ChatMessage>> get() = groupChatViewModel.groupMessages
    val isLoadingOlderGroupMessages: StateFlow<Boolean> get() = groupChatViewModel.isLoadingOlderGroupMessages
    val hasMoreGroupMessages: StateFlow<Boolean> get() = groupChatViewModel.hasMoreGroupMessages
    val groupLoading: StateFlow<Boolean> get() = groupChatViewModel.groupLoading
    private val _currentGroupId get() = groupChatViewModel.currentGroupId

    fun getPromptTemplate(type: String, mode: String = ""): String {
        val key = if (mode.isNotBlank()) "prompt_${type}_${mode}" else "prompt_$type"
        return settings.getString(key, "")?.ifBlank { null } ?: defaultTemplate(type, mode)
    }

    fun savePromptTemplate(type: String, mode: String, template: String) {
        val key = if (mode.isNotBlank()) "prompt_${type}_${mode}" else "prompt_$type"
        settings.putString(key, template)
    }

    fun resetPromptTemplate(type: String, mode: String = "") {
        val key = if (mode.isNotBlank()) "prompt_${type}_${mode}" else "prompt_$type"
        settings.remove(key)
    }

    fun applyTemplate(template: String, replacements: Map<String, String>): String =
        sharedUtils.applyTemplate(template, replacements)

    private fun defaultTemplate(type: String, mode: String = ""): String =
        PromptTemplates.get(type, mode)

    fun setCurrentGroup(groupSessionId: String) = groupChatViewModel.setCurrentGroup(groupSessionId)

    fun clearCurrentGroup() = groupChatViewModel.clearCurrentGroup()
    
    fun cleanupExpiredSessionCounters() {
        val activeIds = _sessions.value.map { it.id }.toSet()
        sessionMessageCounter.keys.removeIf { it !in activeIds }
    }

    override fun onCleared() {
        groupChatViewModel.clear()
        super.onCleared()
    }

    init {
        android.util.Log.d("MainVM", "** [DBG] ** init 实例: ${System.identityHashCode(this)}")
        viewModelScope.launch {
            repository.insertPresetOperators()
            // 只在首次安装时设置默认权限，不覆盖用户手动修改
            val permissionsDone = settings.getBoolean("permissions_initialized", false)
            if (!permissionsDone) {
                val allOps = repository.getAllOperatorsSync()
                for (op in allOps) {
                    settings.putOperatorDynPermission(op.id, false)
                    settings.putOperatorMsgPermission(op.id, false)
                }
                val enabledOps = setOf("amiya", "kaltsit", "suzuran", "shu", "muelsyse", "exusiai", "priestess", "goldenglow", "zhuang_fangyi", "loxy")
                for (op in allOps) {
                    if (op.id in enabledOps) {
                        settings.putOperatorDynPermission(op.id, true)
                        settings.putOperatorMsgPermission(op.id, true)
                    }
                }
                settings.putBoolean("permissions_initialized", true)
            }
            repository.migrateOldRelationships()
            repository.initPresetGroups()
            // 新群组默认从主页隐藏，仅在通讯录显示（只执行一次）
            val initialHiddenDone = settings.getBoolean("initial_hidden_done", false)
            if (!initialHiddenDone) {
                val groupIds = listOf("group_elite")
                val hidden = settings.hiddenIds.toMutableSet()
                hidden.addAll(groupIds)
                settings.hiddenIds = hidden
                settings.putBoolean("initial_hidden_done", true)
            }
            cleanupExpired()
            autoGenerateTodayMoments()
        }
        startAutoStatusRefresh()
        loadHypnosis()
        // 启动时检查派遣恢复
        viewModelScope.launch { recoverDispatches() }
        // 启动时恢复自动群聊 + 执行一次数据清理
        viewModelScope.launch { refreshAutoGroupChats() }
        // 每日龙门币刷新（麻将干员保底）
        viewModelScope.launch { refreshDailyLmb() }
        // 定期清理过期记忆、锚点等（每6小时）
        viewModelScope.launch {
            while (true) {
                dataViewModel.cleanupAllExpired()
                try { repository.cleanupExpiredData() } catch (_: Exception) { }
                // 每个干员保留最多 200 条锚点
                for (op in _operators.value) {
                    try { repository.enforceAnchorRetain(op.id, 200) } catch (_: Exception) { }
                }
                delay(6 * 60 * 60 * 1000L)
            }
        }
        // 派遣后台监控（每分钟检查，推进段落或结算超时派遣）
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                dispatchViewModel.checkActiveDispatches()
            }
        }
        dataViewModel.cleanupAllExpired()
    }

    private suspend fun refreshDailyLmb() = mahjongViewModel.refreshDailyLmb()

    fun saveMahjongGame(json: String, ruleType: String) = mahjongViewModel.saveMahjongGame(json, ruleType)

    fun loadMahjongSave(callback: (com.rhodes.privatechat.shared.model.MahjongSave?) -> Unit) = mahjongViewModel.loadMahjongSave(callback)

    fun deleteMahjongSave() = mahjongViewModel.deleteMahjongSave()

    fun generateMahjongTableTalk(
        player: com.rhodes.privatechat.game.mahjong.PlayerState,
        event: String,
        tile: com.rhodes.privatechat.game.mahjong.Tile?,
        roundLabel: String,
        wallLeft: Int,
        shanten: Int,
        fallback: String,
        participants: List<String> = emptyList(),
        recentChat: List<String> = emptyList(),
        callback: (String) -> Unit
    ) = mahjongViewModel.generateMahjongTableTalk(player, event, tile, roundLabel, wallLeft, shanten, fallback, participants, recentChat, callback)

    fun generateMahjongSettlementLine(
        player: com.rhodes.privatechat.game.mahjong.PlayerState?,
        name: String,
        isWinner: Boolean,
        isDraw: Boolean,
        rank: Int,
        netGain: Int,
        summary: String,
        fallback: String,
        callback: (String) -> Unit
    ) = mahjongViewModel.generateMahjongSettlementLine(player, name, isWinner, isDraw, rank, netGain, summary, fallback, callback)

    fun createMahjongAnchor(content: String) = mahjongViewModel.createMahjongAnchor(content)

    fun postMahjongMoment(content: String) = mahjongViewModel.postMahjongMoment(content)

    fun settleMahjongGame(
        participantNames: List<String>,
        winnerName: String,
        loserName: String,
        winType: String,
        summary: String,
        userNetGain: Int,
        assistantName: String
    ) = mahjongViewModel.settleMahjongGame(participantNames, winnerName, loserName, winType, summary, userNetGain, assistantName)

    fun generatePokerTalk(
        speaker: String,
        gameName: String,
        event: String,
        tableInfo: String,
        recentTalk: List<String>,
        fallback: String,
        callback: (String) -> Unit
    ) {
        viewModelScope.launch {
            val prompt = """
你正在扮演$speaker，和博士以及其他干员在游戏室打$gameName。
你就是牌桌上的本人，不是旁白。

【当前事件】$event
【桌面情况】$tableInfo
【最近发言】
${recentTalk.takeLast(6).joinToString("\n").ifBlank { "暂无" }}

只输出一句你说出口的话，不要姓名前缀，不要JSON，不要解释。
要像牌桌上随口吐槽、得意、嘴硬或提醒。博士就在场，不要说博士不在。
12到36个中文字符。
""".trimIndent()
            val text = try {
                if (settings.apiKey.isBlank()) fallback else sharedUtils.chat(listOf(AiMessage("system", prompt)), "Poker", "poker").trim().lines().firstOrNull { it.isNotBlank() }?.trim(' ', '"', '“', '”', '：', ':')?.take(48).orEmpty().ifBlank { fallback }
            } catch (_: Exception) { fallback }
            callback(text)
        }
    }

    private fun startAutoStatusRefresh() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(15 * 60 * 1000L) // 每15分钟
                currentAutoAiTickCount = 0
                settings.putInt("world_trigger_tick_count", 0)
                if (settings.autoAiEnabled) {
                    if (settings.autoStatusRefresh) refreshAllOperatorStatus()
                    autoGenerateTodayMoments()
                    checkAndTriggerProactiveMessages()
                    worldScheduler.tick()
                }
            }
        }
    }

    /** 干员主动私聊：筛选候选 → 随机选 0-2 人 → 错峰发送 */
    private suspend fun checkAndTriggerProactiveMessages() {
        if (!settings.autoAiEnabled) return
        if (!settings.idleProactiveChatEnabled) return
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        // 深夜跳过（23:00~05:59 不发送主动消息）
        if (hour == 23 || hour in 0..5) return
        // 获取活跃派遣中的干员 ID
        val activeDispatches = repository.getActiveDispatches()
        val dispatchedOpIds = activeDispatches.flatMap {
            it.operatorIds.split(",").map(String::trim).filter(String::isNotBlank)
        }.toSet()
        // 筛选候选（冷却时间从用户最近一次发言算，同时考虑上次主动消息时间）
        val candidates = _operators.value.filter { op ->
            if (!settings.getOperatorMsgPermission(op.id)) return@filter false
            if (op.id in dispatchedOpIds) return@filter false
            val session = repository.getSessionByOperator(op.id)
            if (session == null) return@filter false
            val lastUserMsgTime = repository.getLastUserMessageTime(session.id)
            val lastUserOrSession = lastUserMsgTime ?: session.lastTime
            val lastSent = getLastProactiveSentAt(op.id)
            (now - maxOf(lastUserOrSession, lastSent)) >= 30 * 60 * 1000L
        }
        if (candidates.size < 1) return
        // 随机选 1-2 人
        val count = (1..candidates.size.coerceAtMost(2)).random()
        val selected = candidates.shuffled().take(count)
        // 错峰：每人独立协程，随机延迟 5-10 分钟
        for (op in selected) {
            viewModelScope.launch {
                val delayMs = 5*60*1000 + (Math.random() * 5*60*1000).toLong()
                delay(delayMs)
                // 延迟到期后检查用户最近是否发了消息（5分钟内），是则取消
                val session = repository.getSessionByOperator(op.id)
                if (session != null) {
                    val lastUserMsgTime = repository.getLastUserMessageTime(session.id)
                    if (lastUserMsgTime != null && System.currentTimeMillis() - lastUserMsgTime < 5 * 60 * 1000) return@launch
                }
                if (!settings.autoAiEnabled || !settings.idleProactiveChatEnabled) return@launch
                sendProactiveMessage(op)
            }
        }
    }

    private suspend fun triggerProactivePrivateFromEvent(event: WorldEvent): Boolean {
        if (!settings.autoAiEnabled) return false
        if (!settings.worldProactiveChatEnabled) return false
        val op = _operators.value.find { it.id == event.actorId || it.name == event.actorName } ?: return false
        if (!settings.getOperatorMsgPermission(op.id)) return false
        val now = System.currentTimeMillis()
        val lastSent = getLastProactiveSentAt(op.id)
        if (now - lastSent < 30 * 60 * 1000L) return false
        if (!tryConsumeAutoAiBudget("proactive_private")) return false
        return sendProactiveMessage(op, event.content)
    }

    private suspend fun sendProactiveMessage(op: Operator, eventContext: String = ""): Boolean {
        if (!settings.autoAiEnabled) return false
        if (eventContext.isBlank() && !settings.idleProactiveChatEnabled) return false
        if (eventContext.isNotBlank() && !settings.worldProactiveChatEnabled) return false
        if (getApiKey().isBlank()) return false
        val profile = getUserProfile()
        val now = beijingSdf("yyyy-MM-dd HH:mm").format(java.util.Date())
        val session = repository.getOrCreateSession(op.id, op.name, op.avatarUri)
        // 构建替换映射
        val shortTerm = repository.getShortTermMemory(session.id)
        val longTerm = repository.getLongTermImpression(op.id)
        val sharedMemories = repository.getSharedMemoriesForOperator(op.id)
        val anchors = repository.getAnchors(op.id)
        val analysisBlock = if (isDualModel() && analysisGuidance.isNotBlank()) "【AI分析指导】\n${analysisGuidance}\n" else ""
        val nearby = _operators.value.filter { it.id != op.id }.take(3)
        val pickedAnchors = sharedUtils.pickAnchorsForSurface(anchors, settings.privateAnchorCount, MemorySurface.PRIVATE_CHAT)
        DebugLogger.log(
            "Memory/Inject",
            "主动消息记忆注入: op=${op.id}, short=${shortTerm != null}, long=${longTerm != null}, anchors=${pickedAnchors.size}, sharedLines=${sharedMemories.lines().filter { it.isNotBlank() }.size}, daily=${repository.getLatestDaily() != null}"
        )
        val replacements = mapOf(
            "CURRENT_TIME" to now,
            "USER_NAME" to profile.nickname,
            "USER_GENDER" to profile.gender.ifBlank { "未知" },
            "USER_BIO" to profile.bio.ifBlank { "无" },
            "USER_CONTENT" to "(用户没有说话)",
            "PROACTIVE_TRIGGER_TYPE" to (if (eventContext.isBlank()) "idle" else "event"),
            "PROACTIVE_TRIGGER_CONTEXT" to eventContext,
            "AI_ANALYSIS" to analysisBlock,
            "HYPNOSIS" to "",
            "MIND_READ" to "",
            "OPERATOR_NAME" to op.name,
            "OPERATOR_TITLE" to (if (op.title.isNullOrBlank()) "" else "（${op.title}）"),
            "OPERATOR_PERSONA" to (op.privatePrompt.ifBlank { op.description }),
            "OPERATOR_GENDER" to (op.gender.ifBlank { "" }),
            "CURRENT_LOCATION" to op.location,
            "CURRENT_STATE" to op.activity,
            "CURRENT_EMOTION" to op.emotion,
            "LONG_TERM_IMPRESSION" to (longTerm?.content ?: "暂无"),
            "USER_PREFS" to buildString {
                longTerm?.preferences?.takeIf { it.isNotBlank() }?.let {
                    append("已知偏好：${it.split(",").map { it.trim() }.joinToString("、")}\n")
                }
                longTerm?.taboos?.takeIf { it.isNotBlank() }?.let {
                    append("已知禁忌：${it.split(",").map { it.trim() }.joinToString("、")}\n")
                }
            },
            "MEMORY_ANCHORS" to pickedAnchors.joinToString("\n") { "- ${anchorTimeLabel(it)} ${it.content}" }.ifBlank { "暂无特别事件" },
            "UNCONSUMED_EVENTS" to eventContext.ifBlank { "无" },
            "RECENT_SOCIAL_EVENTS" to eventContext.ifBlank { "无" },
            "EVENT_TRIGGERED_PRIVATE_CONTEXT" to eventContext.ifBlank { "无" },
            "SHARED_MEMORIES" to sharedMemories.ifBlank { "无" },
            "DAILY_SUMMARY" to (repository.getLatestDaily()?.content ?: "无"),
            "SHORT_TERM_SUMMARY" to (shortTerm?.content ?: "无"),
            "NEARBY_OPERATORS" to nearby.joinToString("\n") { "- ${it.name}正在${it.location}${it.activity}，${it.emotion}" }.ifBlank { "" },
            "USER_RELATION" to (op.userRelation.ifBlank { "未知" }),
            // 主动消息只发一句话（1段台词，无旁白）
            "NAR_SEG_MIN" to "0",
            "NAR_SEG_MAX" to "0",
            "NAR_MIN" to "0",
            "NAR_MAX" to "0",
            "DIA_SEG_MIN" to "1",
            "DIA_SEG_MAX" to "1",
            "DIA_MIN" to intPref("dia_min", 10).toString(),
            "DIA_MAX" to settings.diaMax.toString(),
            "SEG_MIN" to "1",
            "SEG_MAX" to "1",
            "TRANSITION_NOTICE" to "",
            "GROUP_CONTEXT" to ""
        )
        val prompt = applyTemplate(getPromptTemplate("private", "online"), replacements)
        try {
            val parsed = withTimeout(60_000) { sharedUtils.chatWithRetry(listOf(AiMessage("system", prompt)), "ProactivePrivate") }
            val raw = json.encodeToString(com.rhodes.privatechat.shared.model.OfflineModeResponse.serializer(), parsed)
            if (raw.isNotBlank()) {
                val msgId = repository.getNextMessageId()
                repository.sendMessage(session.id, ChatMessage(
                    id = msgId, sessionId = session.id,
                    senderName = op.name, content = raw,
                    type = "ai_json", mode = "online", isMe = false
                ))
                // 重新读取会话，仅更新未读计数（lastMessage 由 sendMessage 自动更新）
                val freshSession = repository.getSession(session.id) ?: session
                repository.insertSession(freshSession.copy(
                    unreadCount = freshSession.unreadCount + 1,
                    lastTime = System.currentTimeMillis()
                ))
                unhideSession(session.id)
                setLastProactiveSentAt(op.id, System.currentTimeMillis())
                if (parsed.emotion.isNotBlank() || parsed.location.isNotBlank() || parsed.state.isNotBlank()) {
                    updateOperatorStatus(op.id, parsed.location, parsed.state, parsed.emotion)
                }
                return true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) { }
        return false
    }

    private fun getLastProactiveSentAt(operatorId: String): Long =
        settings.getLong("proactive_last_sent_$operatorId", 0L)

    private fun setLastProactiveSentAt(operatorId: String, time: Long) {
        settings.putLong("proactive_last_sent_$operatorId", time)
    }

    private suspend fun refreshAllOperatorStatus(force: Boolean = false) {
        if (!force && !settings.autoAiEnabled) return
        if (!force && !settings.autoStatusRefresh) return
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val currentChatOpId = chatViewModel.selectedOperator.value?.id
        val dispatchedOpIds = repository.getActiveDispatches().flatMap {
            it.operatorIds.split(",").map(String::trim).filter(String::isNotBlank)
        }.toSet()
        val allOps = _operators.value.filter { it.id != currentChatOpId && it.id !in dispatchedOpIds }

        // 深夜强制
        if (hour in 22..23 || hour in 0..4) {
            for (op in allOps) { repository.updateOperator(op.copy(location = "宿舍", activity = "睡觉", emotion = "安静")) }
            return
        }

        val locations = settings.parseStatusPool(settings.statusLocationPool, settings.defaultStatusLocations)
        val activities = settings.parseStatusPool(settings.statusActivityPool, settings.defaultStatusActivities)
        val emotions = settings.parseStatusPool(settings.statusEmotionPool, settings.defaultStatusEmotions)

        var locCount = mutableMapOf<String, Int>()

        for (op in allOps) {
            var loc = locations.random()
            val cnt = locCount.getOrDefault(loc, 0)
            if (cnt >= 10) loc = locations.firstOrNull() ?: "宿舍"
            locCount[loc] = cnt + 1

            val activity = activities.random()

            val emotion = if (Math.random() < 0.3) emotions.random() else op.emotion

            repository.updateOperator(op.copy(location = loc, activity = activity, emotion = emotion))
        }
        // 同步聊天页顶栏的干员状态
        val selected = chatViewModel.selectedOperator.value
        if (selected != null) {
            val fresh = repository.getOperator(selected.id)
            if (fresh != null) chatViewModel.updateSelectedOperator(fresh)
        }
    }

    private suspend fun cleanupExpired() {
        try { repository.cleanupExpiredData() } catch (_: Exception) { }
    }

    private suspend fun autoGenerateTodayMoments() {
        if (!settings.autoAiEnabled) return
        if (!settings.dailyAutoMomentEnabled) return
        android.util.Log.d("MomentGen", "** [DBG] ** 自动生成开始")
        val weekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        repository.deleteOldMoments(weekAgo)
        if (!autoGenerating.compareAndSet(false, true)) return
        DebugLogger.log("MomentGen", "autoGenerating=true")
        viewModelScope.launch {
            try {
                val dateKey = beijingSdf("yyyyMMdd").format(java.util.Date())
                val target = intPref("daily_moment_target", 2)
                val permCount = _operators.value.count { settings.getOperatorDynPermission(it.id) }
                android.util.Log.d("MomentGen", "** [DBG] ** target=$target 有权限=$permCount 总干员=${_operators.value.size}")
                DebugLogger.log("MomentGen", "调用 generateAllMoments target=$target dateKey=$dateKey")
                if (target <= 0) return@launch
                generateAllMoments(target, dateKey) { /* silent */ }
                // 清理 7 天前的计数
                val weekAgo = beijingSdf("yyyyMMdd").format(java.util.Date(System.currentTimeMillis() - 7 * 86400000L))
                for (op in _operators.value) {
                    settings.removeMomentCount(op.id, weekAgo)
                }
            } finally {
                autoGenerating.set(false)
                DebugLogger.log("MomentGen", "autoGenerating=false")
            }
        }
    }

    fun findOperatorByName(name: String): com.rhodes.privatechat.shared.model.Operator? =
        sessionViewModel.findOperatorByName(name)

    fun selectOperator(operator: Operator) = chatViewModel.selectOperator(operator)

    fun clearSelection() = chatViewModel.clearSelection()

    fun loadOlderMessages() = chatViewModel.loadOlderMessages()

    fun loadOlderGroupMessages() = groupChatViewModel.loadOlderGroupMessages()

    fun clearMessages() = chatViewModel.clearMessages()
    fun clearGroupMessages(groupId: String) = groupChatViewModel.clearGroupMessages(groupId)
    private suspend fun unhideSession(sessionId: String) {
        val hidden = settings.hiddenIds.toMutableSet()
        if (hidden.remove(sessionId)) {
            settings.hiddenIds = hidden
            // 更新会话模式，触发会话列表刷新
            repository.updateSessionMode(sessionId, "")
        }
    }

    fun markAllRead() = sessionViewModel.markAllRead()

    fun deleteSession(sessionId: String) = sessionViewModel.deleteSession(sessionId)

    fun hideSession(sessionId: String) {
        val hidden = settings.hiddenIds.toMutableSet()
        hidden.add(sessionId)
        settings.hiddenIds = hidden
        appState.refreshAllSessions(appState.allSessions.value, hidden)
    }

    fun clearAllMessages() = sessionViewModel.clearAllMessages()

    fun pinSession(sessionId: String) = sessionViewModel.pinSession(sessionId)

    fun loadGroupData(groupId: String, callback: (String, List<Operator>, String, Set<String>) -> Unit) =
        sessionViewModel.loadGroupData(groupId, callback)

    fun saveGroup(groupId: String, name: String, memberNames: List<String>, rules: String, avatarUri: String = "", mutedMembers: List<String> = emptyList(), onComplete: () -> Unit = {}) =
        sessionViewModel.saveGroup(groupId, name, memberNames, rules, avatarUri, mutedMembers, onComplete)

    fun markSessionRead(sessionId: String) = sessionViewModel.markSessionRead(sessionId)

    fun updateInputText(text: String) = chatViewModel.updateInputText(text)

    fun sendMessage() = chatViewModel.sendMessage()

    fun setMode(mode: String) = chatViewModel.setMode(mode)

    fun buyProp(propName: String, context: android.content.Context): String? {
        if (!settings.trySpendLmb(PROP_PRICE)) return "余额不足"
        return null
    }

    fun insertMessage(sessionId: String, senderName: String, content: String) {
        viewModelScope.launch {
            val msgId = repository.getNextMessageId()
            repository.sendMessage(sessionId, com.rhodes.privatechat.shared.model.ChatMessage(
                id = msgId, sessionId = sessionId,
                senderName = senderName, content = content,
                type = "text", mode = "online", isMe = false
            ))
        }
    }

    private suspend fun generateShortTermSummary(session: ChatSession, messageSource: List<ChatMessage>? = null) {
        val source = messageSource ?: chatViewModel.getMessagesSnapshot()
        val recentMsgs = source.takeLast(40)
        if (recentMsgs.size < 4) return
        val profile = getUserProfile()
        val isGroup = session.id.startsWith("group_")
        val conversationText = recentMsgs.joinToString("\n") { msg ->
            val name = when {
                msg.isMe -> profile.nickname
                isGroup && msg.senderName.isNotBlank() -> msg.senderName
                else -> session.operatorName
            }
            val content = if (msg.type == "ai_json") {
                try {
                    val tree = Json.parseToJsonElement(msg.content)
                    if (tree is JsonArray) {
                        tree.mapNotNull { el ->
                            val obj = el.jsonObject
                            "${obj["speaker"]?.jsonPrimitive?.content ?: "?"}：${obj["message"]?.jsonPrimitive?.content?.take(60) ?: ""}"
                        }.joinToString(" | ")
                    } else {
                        val obj = tree.jsonObject
                        val segments = obj["segments"] as? JsonArray
                        if (segments != null) {
                            segments.mapNotNull { seg ->
                                val s = seg.jsonObject
                                "${s["type"]?.jsonPrimitive?.content ?: "?"}：${s["content"]?.jsonPrimitive?.content?.take(60) ?: ""}"
                            }.joinToString(" | ")
                        } else {
                            obj["dialogue"]?.jsonPrimitive?.content?.take(80) ?: msg.content.take(80)
                        }
                    }
                } catch (_: Exception) { msg.content.take(80) }
            } else msg.content.take(80)
            "$name：$content"
        }
        val prompt = """
你是罗德岛的记录员。将对话压缩为摘要并提取记忆锚点。

总结以下对话，生成摘要和记忆锚点。

输出JSON：{"summary":"50~200字摘要","anchors":[{"type":"event|preference|plan|emotion|taboo|relation","content":"具体内容","isPrivate":false}]}

字段说明：
- summary：重点关注用户喜好、习惯、重要事件、决定、承诺，以及对话情感氛围
- anchors：3~5个关键信息锚点
  - type：锚点类型。event=事件，preference=用户偏好，plan=用户约定，emotion=用户情绪或重要互动情绪，taboo=用户禁忌，relation=关系变化
  - content：具体内容，30字内
  - isPrivate：涉及用户负面情绪、私密情感、自我怀疑时设为true；正面评价、公开约定、普通事件设为false

提取边界：
- preference/taboo 只能记录用户的偏好和禁忌，不能记录干员自己的习惯、职业偏好或性格。
- 干员自身状态、工作偏好、被打断后的反应，应归为 event/emotion，不要归为 preference/taboo。
- content 中禁止出现“好感度提升/下降”“affection”“系统数值”等系统机制词。
- 用户只是输入短测试字符、数字、拼音或乱码时，不要推断为稳定人格，只能作为普通事件或直接忽略。

隐私标记规则：
- 必须设为true：用户负面情绪、个人隐私、"别告诉别人"的内容
- 可设为false：正面评价、公开约定、一般偏好、干员间公开互动、干员普通情绪反应

对话内容：
""".trimIndent()
        val messages = listOf(
            AiMessage("system", prompt),
            AiMessage("user", conversationText)
        )
        try {
            DebugLogger.log("Memory/Summary", "开始短期摘要(Main): session=${session.id}, operator=${session.operatorName}, textLen=${conversationText.length}")
            var result = ""
            result = chat(messages, "Memory")
            trackTokens("memory", prompt, result)
            val parsed = sharedUtils.aiService.parseSummaryResponse(result)
            repository.saveMemory(Memory(
                sessionId = session.id, operatorId = session.operatorId,
                type = MemoryType.SHORT_TERM, content = parsed.summary,
                keywords = parsed.keywords.joinToString(","),
                createdAt = System.currentTimeMillis(),
                expiresAt = MemoryPolicy.memoryExpiresAt(settings)
            ))
            DebugLogger.log("Memory/Summary", "短期摘要已保存(Main): session=${session.id}, summaryLen=${parsed.summary.length}, anchors=${parsed.anchors.size}")
            val anchors = parsed.anchors.mapNotNull { a ->
                val type = try { com.rhodes.privatechat.shared.model.AnchorType.valueOf(a.type.uppercase()) } catch (_: Exception) { com.rhodes.privatechat.shared.model.AnchorType.EVENT }
                val cleanedContent = sanitizeAnchorContent(a.content)
                if (cleanedContent.isBlank()) return@mapNotNull null
                val finalType = normalizeAnchorType(type, cleanedContent)
                AnchorSourcePolicy.buildAnchor(
                    source = AnchorSourcePolicy.PRIVATE_CHAT,
                    sourceName = "与${session.operatorName}的私聊",
                    sourceActor = profile.nickname,
                    sourceTarget = session.operatorName,
                    operatorId = session.operatorId,
                    type = finalType,
                    content = cleanedContent,
                    importance = if (a.isPrivate) AnchorSourcePolicy.STRONG else AnchorSourcePolicy.MEDIUM,
                    sessionId = session.id,
                    isPrivate = a.isPrivate,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = MemoryPolicy.anchorExpiresAt(settings, finalType)
                )
            }
            repository.saveAnchors(anchors)
            anchors.forEach { a -> DebugLogger.log("Memory/Anchor", "摘要锚点(Main): op=${a.operatorId}, type=${a.type}, private=${a.isPrivate}, content=${a.content.take(40)}") }
            // 保留条数限制
            val retain = settings.summaryRetain
            repository.enforceMemoryRetain(session.id, retain)
        } catch (_: Exception) { }
    }

    private suspend fun generateLongTermImpression(session: ChatSession) {
        try {
            val op = repository.getOperator(session.operatorId) ?: return
            var summaries = repository.getShortTermMemory(session.id)?.content
            if (summaries == null) {
                // 无可滚动摘要时，用最近对话代替
                val recentMsgs = _messages.value.takeLast(10)
                if (recentMsgs.size < 3) return
                val profile = getUserProfile()
                summaries = recentMsgs.joinToString("\n") { "${if (it.isMe) profile.nickname else session.operatorName}: ${it.content.take(80)}" }
            }
            val oldImpression = repository.getLongTermImpression(session.operatorId)
            val profile = getUserProfile()
            val oldImpressionText = oldImpression?.content ?: "无"
            DebugLogger.log("Memory/Impression", "开始更新印象(Main): op=${session.operatorId}, old=${oldImpression != null}, summaryLen=${summaries.length}")
            val prompt = """
你是罗德岛的心理档案员。基于多次对话摘要总结干员对用户的长期印象。每次更新时融合旧印象和新摘要。

总结用户${profile.nickname}在${op.name}眼中的整体印象。

输出JSON：{"impression":"50~200字印象描述，使用'用户'指代对方","keywords":["关键词1","关键词2","关键词3"],"preferences":["偏好1","偏好2"],"taboos":["禁忌1"]}

字段说明：
- impression：完整人像描述，包含性格特质、偏好、情感模式、互动风格
- keywords：3~5个关键词，最突出特点
- preferences：2~4个持续偏好标签
- taboos：0~2个持续禁忌标签，无则空数组

质量要求：
- impression要有整体感，不是零散信息堆砌
- 旧印象与新信息冲突时以新信息为准
- 标签从所有摘要中综合提取
- 如果用户主要输入短句、数字、拼音、测试字符或乱码，不要过度心理分析；最多描述为“近期表达较简短/测试性输入较多”
- 长期特征必须来自多次明确表达或反复行为，不能从一两句含糊输入中编造人格标签
- 不要使用“符号化回应”“高强度思考”“最低限度联系”等过度诊断式标签，除非对话中有明确证据

宁缺毋滥：
- 如果对话内容不足以支撑足够标签，可返回少于标准数量
- 不要为了凑数而编造不存在的标签

之前的印象（在此基础上融合更新）：
${oldImpressionText}

新的对话摘要：
${summaries}

直接输出JSON对象。
""".trimIndent()
            val sb = StringBuilder()
            sb.append(withTimeout(15_000) { chat(listOf(AiMessage("system", prompt)), "Memory") })
            trackTokens("memory", prompt, sb.toString())
            val cleaned = sharedUtils.aiService.cleanJson(sb.toString().trim())
            val parsed = try { json.decodeFromString<ImpressionResponse>(cleaned) } catch (_: Exception) { null }
            if (parsed != null && parsed.impression.isNotBlank()) {
                DebugLogger.log("Memory/Impression", "印象已保存(Main): op=${session.operatorId}, len=${parsed.impression.length}, keywords=${parsed.keywords.joinToString(",").take(40)}")
                repository.saveMemory(Memory(
                    sessionId = session.id, operatorId = session.operatorId,
                    type = MemoryType.LONG_TERM, content = parsed.impression,
                    keywords = parsed.keywords.joinToString(","),
                    preferences = parsed.preferences.joinToString(","),
                    taboos = parsed.taboos.joinToString(","),
                    createdAt = System.currentTimeMillis()
                ))
            } else {
                // 降级：纯文本存入 impression
                val fallback = sb.toString().trim()
                if (fallback.isNotBlank()) {
                    repository.saveMemory(Memory(
                        sessionId = session.id, operatorId = session.operatorId,
                        type = MemoryType.LONG_TERM, content = fallback,
                        keywords = "", preferences = "", taboos = "",
                        createdAt = System.currentTimeMillis()
                    ))
                }
            }
        } catch (_: Exception) { }
    }

    private fun updateAiMessage(msgId: Long, content: String) {
        viewModelScope.launch {
            val session = chatViewModel.currentSession.value ?: return@launch
            chatViewModel.updateMessageInList(msgId, content)
            // 防抖：最多每 300ms 写一次 DB
            updateMutex.withLock {
                val now = System.currentTimeMillis()
                if (now - lastDbUpdate > 300) {
                    repository.sendMessage(session.id, ChatMessage(
                        id = msgId, sessionId = session.id,
                        senderName = session.operatorName, content = content,
                        type = "text", mode = chatViewModel.getCurrentMode(), isMe = false
                    ))
                    lastDbUpdate = now
                }
            }
        }
    }

    private suspend fun updateOperatorStatus(operatorId: String, location: String, activity: String, emotion: String) {
        operatorStateUpdater.updateOperatorStatus(operatorId, location, activity, emotion) { opId, newLoc, newAct, newEmo ->
            if (opId == chatViewModel.selectedOperator.value?.id) {
                chatViewModel.updateSelectedOperatorCopy(newLoc, newAct, newEmo)
            }
        }
    }

    private suspend fun updateOperatorIntimacy(operatorId: String, delta: Int) =
        operatorStateUpdater.updateOperatorIntimacy(operatorId, delta)

    private fun parseOnlineEmotion(text: String): Pair<String, String> = sharedUtils.parseOnlineEmotion(text)

    private fun beijingSdf(pattern: String) = sharedUtils.beijingSdf(pattern)
    private fun getApiKey(): String = sharedUtils.getApiKey()

    fun setApiKey(key: String) {
        viewModelScope.launch {
            settings.apiKey = key
        }
    }

    fun getSavedApiKey(): String = sharedUtils.getApiKey()

    fun getProvider(): String = sharedUtils.getProvider()
    fun getModelName(): String = sharedUtils.getModelName()
    fun getCustomUrl(): String = sharedUtils.getCustomUrl()

    private fun getTimeOfDay(hour: Int): String = sharedUtils.getTimeOfDay(hour)

    private suspend fun getRecentPosts(operatorId: String, limit: Int = 3): String =
        sharedUtils.getRecentPosts(operatorId, limit)

    private suspend fun getRelationEvents(operatorId: String): String =
        sharedUtils.getRelationEvents(operatorId)

    private fun logAiCall(tag: String, prompt: String, response: String, allMessages: List<AiMessage>? = null) =
        sharedUtils.logAiCall(tag, prompt, response, allMessages)

    /** 转储全部调试状态到 logcat */
    fun dumpDebugState() {
        if (!DEBUG) return
        val aiTag = "AI调试输出"
        val sb = StringBuilder()
        sb.appendLine("╔══ 调试状态转储 ═════════════════════════════")
        sb.appendLine("║ 干员数: ${_operators.value.size}")
        sb.appendLine("║ 会话数: ${_sessions.value.size}")
        sb.appendLine("║ 群聊数: ${_allSessions.value.count { it.operatorId.startsWith("group") }}")
        // 设置参数
        sb.appendLine("╠══ 参数设置 ════════════════════════════════")
        val keys = listOf(
            "summary_threshold" to settings.summaryThreshold, "summary_retain" to settings.summaryRetain,
            "impression_threshold" to settings.impressionThreshold, "history_messages" to settings.historyMessages,

            "nar_seg_min" to settings.narSegMin, "nar_seg_max" to settings.narSegMax,
            "nar_min" to settings.narMin, "nar_max" to settings.narMax,
            "dia_seg_min" to settings.diaSegMin, "dia_seg_max" to settings.diaSegMax,
            "dia_min" to settings.diaMin, "dia_max" to settings.diaMax,
            "group_msg_min" to settings.groupMsgMin, "group_msg_max" to settings.groupMsgMax,
            "group_speech_min" to settings.groupSpeechMin, "group_speech_max" to settings.groupSpeechMax,
            "group_nar_seg_min" to settings.groupNarSegMin, "group_nar_seg_max" to settings.groupNarSegMax,
            "group_nar_min" to settings.groupNarMin, "group_nar_max" to settings.groupNarMax,
            "group_chat_min_interval" to settings.groupChatMinInterval, "group_chat_max_interval" to settings.groupChatMaxInterval,

            "moment_min_chars" to settings.momentMinChars, "moment_max_chars" to settings.momentMaxChars,
            "diary_min_chars" to settings.diaryMinChars, "diary_max_chars" to settings.diaryMaxChars,
            "dispatch_min_chars" to settings.dispatchMinChars, "dispatch_max_chars" to settings.dispatchMaxChars,
            "daily_moment_target" to settings.dailyMomentTarget, "clean_days" to settings.cleanDays,
            "daily_intimacy_cap" to settings.dailyIntimacyCap, "ai_temperature" to (settings.aiTemperature * 100).toInt()
        )
        for ((k, v) in keys) {
            sb.appendLine("║ $k = $v")
        }
        sb.appendLine("║ dual_model = ${settings.dualModel}")
        sb.appendLine("║ messageCounter = $messageCounter")
        sb.appendLine("║ impressionMsgCounter = $impressionMsgCounter")
        sb.appendLine("║ shortTermThreshold = $shortTermThreshold")
        // 关系网
        sb.appendLine("╠══ 关系网 ══════════════════════════════════")
        runBlockingCatching {
            val allOps = _operators.value
            for (op in allOps.take(5)) {
                val rels = repository.getRelationships(op.id)
                if (rels.isNotEmpty()) {
                    val desc = rels.take(4).joinToString { rel -> "→${rel.relatedOperatorName}【${relationshipDebugLabel(rel.type)}】" }
                    sb.appendLine("║ ${op.name}$desc")
                }
            }
        }

        // 会话摘要统计
        sb.appendLine("╠══ 会话状态 ════════════════════════════════")
        for (s in _sessions.value.take(10)) {
            val mode = s.mode.take(10)
            val last = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai") }.format(java.util.Date(s.lastTime))
            sb.appendLine("║ ${s.operatorName.take(8)} | $mode | 最后:$last | 未读:${s.unreadCount}")
        }
        sb.appendLine("╠══ 权限开关 ════════════════════════════════")
        for (op in _operators.value.take(10)) {
            val msg = settings.getOperatorMsgPermission(op.id)
            val dyn = settings.getOperatorDynPermission(op.id)
            sb.appendLine("║ ${op.name.take(8)} | 主动:$msg | 动态:$dyn")
        }
        sb.appendLine("╚══════════════════════════════════════════════")
        Log.d(aiTag, sb.toString())
    }

    private fun runBlockingCatching(block: suspend () -> Unit) = sharedUtils.runBlockingCatching(block)

    private fun intPref(key: String, default: Int): Int = settings.getInt(key, default)

    private fun tryConsumeAutoAiBudget(reason: String): Boolean {
        if (!settings.autoAiEnabled) {
            DebugLogger.log("World/Budget", "后台自动AI总开关已关闭: $reason")
            return false
        }
        val dailyLimit = settings.dailyAutoAiLimit
        if (dailyLimit <= 0) {
            DebugLogger.log("World/Budget", "自动AI已关闭: $reason")
            return false
        }
        val today = beijingSdf("yyyyMMdd").format(java.util.Date())
        val key = "auto_ai_count_$today"
        val usedToday = settings.getInt(key, 0)
        if (usedToday >= dailyLimit) {
            DebugLogger.log("World/Budget", "达到每日自动AI上限: $usedToday/$dailyLimit reason=$reason")
            return false
        }
        val tickLimit = settings.tickAutoAiLimit
        if (tickLimit > 0 && currentAutoAiTickCount >= tickLimit) {
            DebugLogger.log("World/Budget", "达到单次调度自动AI上限: $currentAutoAiTickCount/$tickLimit reason=$reason")
            return false
        }
        settings.putInt(key, usedToday + 1)
        currentAutoAiTickCount += 1
        return true
    }

    private fun canUseWorldTrigger(reason: String): Boolean {
        if (!settings.autoAiEnabled || !settings.worldSchedulerEnabled) return false
        val today = beijingSdf("yyyyMMdd").format(java.util.Date())
        val dailyKey = "world_trigger_count_$today"
        val usedToday = settings.getInt(dailyKey, 0)
        val dailyLimit = settings.dailyWorldTriggerLimit
        if (dailyLimit <= 0 || usedToday >= dailyLimit) {
            DebugLogger.log("World/Budget", "达到每日世界触发上限: $usedToday/$dailyLimit reason=$reason")
            return false
        }
        val tickKey = "world_trigger_tick_count"
        val usedTick = settings.getInt(tickKey, 0)
        val tickLimit = settings.tickWorldTriggerLimit
        if (tickLimit > 0 && usedTick >= tickLimit) {
            DebugLogger.log("World/Budget", "达到单轮世界触发上限: $usedTick/$tickLimit reason=$reason")
            return false
        }
        return true
    }

    private fun consumeWorldTrigger(reason: String) {
        if (!settings.autoAiEnabled || !settings.worldSchedulerEnabled) return
        val today = beijingSdf("yyyyMMdd").format(java.util.Date())
        val dailyKey = "world_trigger_count_$today"
        val usedToday = settings.getInt(dailyKey, 0)
        val tickKey = "world_trigger_tick_count"
        val usedTick = settings.getInt(tickKey, 0)
        settings.putInt(dailyKey, usedToday + 1)
        settings.putInt(tickKey, usedTick + 1)
        DebugLogger.log("World/Budget", "世界触发计数+1: reason=$reason daily=${usedToday + 1} tick=${usedTick + 1}")
    }

    private fun tryConsumeWorldTrigger(reason: String): Boolean {
        if (!canUseWorldTrigger(reason)) return false
        consumeWorldTrigger(reason)
        return true
    }

    private fun canContinueWorldChain(parent: WorldEvent?): Boolean =
        parent == null || parent.chainDepth < MAX_WORLD_CHAIN_DEPTH

    private fun derivedWorldEvent(event: WorldEvent, parent: WorldEvent?): WorldEvent {
        if (parent == null) return event.copy(originType = event.originType.ifBlank { event.type })
        val root = if (parent.rootEventId > 0L) parent.rootEventId else parent.id
        return event.copy(
            originType = parent.originType.ifBlank { parent.type },
            chainDepth = parent.chainDepth + 1,
            rootEventId = root
        )
    }

    private fun trackTokens(category: String, prompt: String, response: String) =
        sharedUtils.trackTokens(category, prompt, response)

    private fun relationshipDebugLabel(type: com.rhodes.privatechat.shared.model.RelationshipType): String =
        sharedUtils.relationshipDebugLabel(type)

    private fun anchorTimeLabel(anchor: com.rhodes.privatechat.shared.model.MemoryAnchor): String =
        sharedUtils.anchorTimeLabel(anchor)

    private fun pickAnchors(anchors: List<com.rhodes.privatechat.shared.model.MemoryAnchor>, maxCount: Int = 5): List<com.rhodes.privatechat.shared.model.MemoryAnchor> =
        sharedUtils.pickAnchors(anchors, maxCount)

    private fun sanitizeAnchorContent(content: String): String {
        return content
            .replace("好感度提升", "")
            .replace("好感度下降", "")
            .replace("好感提升", "")
            .replace("好感下降", "")
            .replace("affection", "", ignoreCase = true)
            .replace("系统数值", "")
            .trim(' ', '，', '。', ',', ';', '；')
    }

    private fun normalizeAnchorType(type: AnchorType, content: String): AnchorType {
        if (type != AnchorType.PREFERENCE && type != AnchorType.TABOO) return type
        val userSignals = listOf("用户", getUserProfile().nickname, "我喜欢", "我讨厌", "我不喜欢", "别", "不要", "偏好", "禁忌")
        val operatorSignals = listOf("Misery", "干员", "偏好专注", "正在", "工作", "推演", "装备")
        val isUserRelated = userSignals.any { content.contains(it) }
        val isOperatorState = operatorSignals.any { content.contains(it) }
        return if (!isUserRelated || isOperatorState) AnchorType.EVENT else type
    }

    private fun relationshipGroupDesc(aName: String, bName: String, type: com.rhodes.privatechat.shared.model.RelationshipType): String =
        sharedUtils.relationshipGroupDesc(aName, bName, type)

    private suspend fun chat(messages: List<AiMessage>, logTag: String = "Chat"): String =
        sharedUtils.chat(messages, logTag)

    fun getUserProfile(): UserProfile = appState.userProfile.value

    fun saveUserProfile(nickname: String, gender: String, bio: String, avatarUri: String = "") {
        settings.userName = nickname
        settings.userGender = gender
        settings.userSignature = bio
        settings.userAvatarUri = avatarUri
        appState.refreshUserProfile()
    }

    fun saveOperator(id: String, name: String, title: String = "", description: String,
                     privatePrompt: String = "", groupPrompt: String = "",
                     userRelation: String = "", avatarUri: String = "",
                     autoPost: Boolean = true, allowChat: Boolean = true,
                     relationships: List<com.rhodes.privatechat.shared.model.Relationship> = emptyList(),
                     activityLevel: Float = 0.5f,
                     gender: String = "",
                     onComplete: () -> Unit = {}) =
        operatorViewModel.saveOperator(id, name, title, description, privatePrompt, groupPrompt, userRelation, avatarUri, autoPost, allowChat, relationships, activityLevel, gender, onComplete)

    fun loadRelationships(operatorId: String, callback: (List<com.rhodes.privatechat.shared.model.Relationship>) -> Unit) =
        operatorViewModel.loadRelationships(operatorId, callback)

    fun saveRelationship(rel: com.rhodes.privatechat.shared.model.Relationship, reciprocal: com.rhodes.privatechat.shared.model.Relationship? = null, onComplete: () -> Unit = {}) =
        operatorViewModel.saveRelationship(rel, reciprocal, onComplete)

    fun loadRelationGraph(operatorId: String, callback: (List<BfsNode>) -> Unit) =
        operatorViewModel.loadRelationGraph(operatorId, callback)

    fun loadSharedMemories(operatorId: String, callback: (String) -> Unit) =
        operatorViewModel.loadSharedMemories(operatorId, callback)

    /** 按 Levenshtein 距离模糊匹配干员名 */
    private fun matchOperatorName(input: String): String? {
        if (input.isBlank()) return null
        val names = _operators.value.map { it.name }
        // 精确匹配
        names.find { it == input }?.let { return it }
        // 前缀匹配
        names.find { it.startsWith(input) || input.startsWith(it) }?.let { return it }
        // Levenshtein 距离 ≤ 1
        names.find { n -> n.length == input.length && n.zip(input).count { (a, b) -> a != b } <= 1 }?.let { return it }
        return null
    }

    fun startDispatch(id: String, task: String, duration: Int, budget: Int, operatorIds: List<String>, onSuccess: () -> Unit = {}) =
        dispatchViewModel.startDispatch(id, task, duration, budget, operatorIds, onSuccess)
    fun finishDispatch(dispatchId: String) = dispatchViewModel.finishDispatch(dispatchId)

    fun deleteGroup(groupSessionId: String, onComplete: () -> Unit = {}) = groupChatViewModel.deleteGroup(groupSessionId, onComplete)

    fun isAutoGroupChatEnabled(groupId: String): Boolean = groupChatViewModel.isAutoGroupChatEnabled(groupId)
    fun setAutoGroupChatEnabled(groupId: String, enabled: Boolean) = groupChatViewModel.setAutoGroupChatEnabled(groupId, enabled)
    fun resetAutoGroupChatTimer(groupId: String) = groupChatViewModel.resetAutoGroupChatTimer(groupId)
    fun stopAutoGroupChat(groupId: String) = groupChatViewModel.stopAutoGroupChat(groupId)
    fun stopAllAutoGroupChats() = groupChatViewModel.stopAllAutoGroupChats()
    fun refreshAutoGroupChats() = groupChatViewModel.refreshAutoGroupChats()
    fun triggerEventGroupChats(parentEvent: WorldEvent? = null): Boolean {
        if (!canContinueWorldChain(parentEvent)) return false
        return groupChatViewModel.triggerEventGroupChats(parentEvent)
    }

    fun cancelDispatch(dispatchId: String) = dispatchViewModel.cancelDispatch(dispatchId)

    private suspend fun recoverDispatches() = dispatchViewModel.recoverDispatches()

    fun deleteOperator(operatorId: String) = operatorViewModel.deleteOperator(operatorId)

    suspend fun exportAllOperators(context: android.content.Context): java.io.File =
        dataViewModel.exportAllOperators(context, _operators.value)

    fun importOperators(payload: ExportPayload, mode: String, targetOpId: String = "") =
        dataViewModel.importOperators(payload, mode, targetOpId)

    suspend fun exportChatHistory(context: android.content.Context, sessionId: String): java.io.File =
        dataViewModel.exportChatHistory(context, sessionId)

    fun loadComments(momentId: Long) {
        viewModelScope.launch {
            repository.getComments(momentId).collect { _comments.value = it }
        }
    }

    fun recallMessage(msgId: Long) {
        val isGroup = _currentGroupId.value.isNotBlank()
        if (isGroup) {
            groupChatViewModel.removeMessage(msgId)
            viewModelScope.launch { repository.deleteMessage(msgId) }
        } else {
            chatViewModel.recallMessage(msgId)
        }
    }

    fun recallMessageSegment(msgId: Long, segmentIndex: Int) {
        val isGroup = _currentGroupId.value.isNotBlank()
        if (isGroup) {
            groupChatViewModel.recallMessageSegment(msgId, segmentIndex)
        } else {
            chatViewModel.recallMessageSegment(msgId, segmentIndex)
        }
    }

    fun regenerateAiMessage(msgId: Long) = chatViewModel.regenerateAiMessage(msgId)

    fun continueAiMessage(msgId: Long) = chatViewModel.continueAiMessage(msgId)

    fun setHypnosis(command: String) = chatViewModel.setHypnosis(command)
    fun decrementHypnosis() = chatViewModel.decrementHypnosis()
    fun loadHypnosis() = chatViewModel.loadHypnosis()

    fun sendGroupMessage(groupSessionId: String, groupName: String, text: String, mode: String = "online", autoSpeak: Boolean = false, isAuto: Boolean = false, onMessageSent: () -> Unit = {}) =
        groupChatViewModel.sendGroupMessage(groupSessionId, groupName, text, mode, autoSpeak = autoSpeak, isAuto = isAuto, onMessageSent = onMessageSent)
    suspend fun generateAllMoments(target: Int = 1, dateKey: String = "", force: Boolean = false, onProgress: (String) -> Unit = {}) {
        val isAuto = dateKey.isNotBlank()
        val today = dateKey.ifBlank { beijingSdf("yyyyMMdd").format(java.util.Date()) }
        // 全天 9 个时段，从清晨到深夜
        val allSlots = listOf(
            6 to "清晨", 8 to "上午", 10 to "上午", 12 to "中午",
            14 to "下午", 16 to "下午", 18 to "傍晚", 20 to "晚上", 22 to "深夜"
        )
        val currentHour = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
            .get(java.util.Calendar.HOUR_OF_DAY)
        coroutineScope {
            DebugLogger.log("MomentGen", "generateAllMoments 内部协程启动 isAuto=$isAuto target=$target force=$force operators=${_operators.value.size}")
            var totalGenerated = 0
            for (op in _operators.value) {
                val allowDyn = settings.getOperatorDynPermission(op.id)
                if (!allowDyn && !force) continue
                val startIdx = if (isAuto) {
                    val d = getTodayMomentCount(op.id, today)
                    if (d >= target) continue
                    d
                } else 0
                // 每个干员基于名称哈希偏移，让不同干员落到不同时段
                val offset = (op.name.hashCode() and Int.MAX_VALUE) % allSlots.size
                var generated = startIdx
                for (i in startIdx until target) {
                    val slotIdx = (offset + i) % allSlots.size
                    val baseHour = allSlots[slotIdx].first
                    val hour = baseHour + (Math.random() * 2).toInt()
                    val timeOfDay = allSlots[slotIdx].second
                    // 自动模式：只生成当前时间之前（含当前小时）的时段
                    if (isAuto && hour > currentHour) continue
                    if (isAuto && !tryConsumeAutoAiBudget("auto_moment_item")) break
                    onProgress("发布中...")
                    try {
                        val profile = getUserProfile()
                        val impression = repository.getLongTermImpression(op.id)?.content ?: "无"
                        val chatSummary = repository.getShortTermMemory("session_${op.id}")?.content?.take(100) ?: "无"
                        val memories = sharedUtils.trimContextBlock(sharedUtils.pickAnchorsForSurface(repository.getPublicAnchors(op.id), settings.momentAnchorCount, MemorySurface.MOMENT).joinToString("\n") { "- ${anchorTimeLabel(it)} ${it.content}" }.ifBlank { "无" }, sharedUtils.contextBlockLimit())
                        val sourceAwareMemories = sharedUtils.trimContextBlock(sharedUtils.buildSourceAwareMemoryContext(repository.getPublicAnchors(op.id), settings.momentAnchorCount, MemorySurface.MOMENT), sharedUtils.contextBlockLimit())
                        val worldEvents = sharedUtils.trimContextBlock(sharedUtils.buildWorldEventContext(op.id, op.name, 5), sharedUtils.contextBlockLimit())
                        val unconsumedEvents = sharedUtils.trimContextBlock(sharedUtils.buildUnconsumedEventContextForOperator(op.id, op.name, "moment:${op.id}", settings.eventContextCount, markConsumed = false), sharedUtils.contextBlockLimit())
                        val existingPosts = repository.getMomentsPaged(10, 0).filter { it.operatorId == op.id }
                        val recentPosts = existingPosts.take(settings.momentRecentPostCount).joinToString("\n") { "- ${it.content.take(50)}" }.ifBlank { "无" }
                        // 构造伪造的时间戳
                        val fakeTs: Long = if (isAuto) {
                            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
                            cal.set(java.util.Calendar.HOUR_OF_DAY, hour.coerceAtMost(23))
                            cal.set(java.util.Calendar.MINUTE, (Math.random() * 60).toInt())
                            cal.set(java.util.Calendar.SECOND, 0)
                            cal.timeInMillis.coerceAtMost(System.currentTimeMillis())
                        } else {
                            System.currentTimeMillis()
                        }
                        val mmtTpl = getPromptTemplate("moment")
                        val userMentionRoll = (Math.random() * 100).toInt()
                        val mentionUser = userMentionRoll < settings.momentUserRelatedRate
                        val mmtReplacements = mapOf(
                            "OPERATOR_NAME" to op.name, "OPERATOR_PERSONA" to op.privatePrompt.ifBlank { op.description },
                            "TIME_OF_DAY" to timeOfDay, "LONG_TERM_IMPRESSION" to if (mentionUser) impression else "无",
                            "RECENT_CHAT_SUMMARY" to if (mentionUser) chatSummary else "无",
                            "RECENT_MEMORIES" to memories,
                            "SOURCE_AWARE_MEMORIES" to sourceAwareMemories,
                            "RECENT_WORLD_EVENTS" to worldEvents,
                            "UNCONSUMED_EVENTS" to unconsumedEvents,
                            "MOMENT_EVENT_SEED" to unconsumedEvents,
                            "MOMENT_TRIGGER_TYPE" to (if (isAuto) "daily" else "manual"),
                            "WORLD_TODAY_STATE" to "${op.name}现在在${op.location}，正在${op.activity}，情绪${op.emotion}",
                            "MOMENT_TRIGGER_REASON" to worldEvents.lines().firstOrNull { it.isNotBlank() }?.removePrefix("- ").orEmpty().ifBlank { "普通日常分享" },
                            "KNOWN_FROM_CONTEXT" to sourceAwareMemories,
                            "SOURCE_AWARE_RULES" to sharedUtils.sourceAwareUsageRule(MemorySurface.MOMENT),
                            "RECENT_POSTS" to recentPosts,
                            "RECENT_DAILY_SUMMARY" to if (mentionUser) (repository.getLatestPrivateDaily(op.id)?.content ?: "无") else "无",
                            "CURRENT_DATE" to beijingSdf("yyyy年MM月dd日").format(fakeTs),
                            "USER_NAME" to if (mentionUser) profile.nickname else "博士",
                            "USER_GENDER" to if (mentionUser) profile.gender.ifBlank { "" } else "",
                            "MOMENT_MIN_CHARS" to settings.momentMinChars.toString(),
                            "MOMENT_MAX_CHARS" to settings.momentMaxChars.toString()
                        )
                        sharedUtils.logMemoryContext(
                            surface = "moment",
                            title = "${op.name}/${op.id}",
                            placeholders = mapOf(
                                "LONG_TERM_IMPRESSION" to mmtReplacements["LONG_TERM_IMPRESSION"].orEmpty(),
                                "RECENT_CHAT_SUMMARY" to mmtReplacements["RECENT_CHAT_SUMMARY"].orEmpty(),
                                "RECENT_MEMORIES" to mmtReplacements["RECENT_MEMORIES"].orEmpty(),
                                "SOURCE_AWARE_MEMORIES" to mmtReplacements["SOURCE_AWARE_MEMORIES"].orEmpty(),
                                "RECENT_DAILY_SUMMARY" to mmtReplacements["RECENT_DAILY_SUMMARY"].orEmpty(),
                                "RECENT_POSTS" to mmtReplacements["RECENT_POSTS"].orEmpty()
                            ),
                            extra = mapOf(
                                "auto" to isAuto.toString(),
                                "timeOfDay" to timeOfDay,
                                "momentAnchorCount" to settings.momentAnchorCount.toString(),
                                "momentRecentPostCount" to settings.momentRecentPostCount.toString()
                            )
                        )
                        val prompt = applyTemplate(mmtTpl, mmtReplacements)
                        val temp = intPref("ai_temperature", 95).toDouble() / 100.0
                        var momentResult = ""
                        repeat(3) { attempt ->
                            try {
                                momentResult = ""
                                momentResult = withTimeout(15_000) { chat(listOf(AiMessage("system", prompt)), "Moment") }
                                if (momentResult.isNotBlank()) return@repeat
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) { if (attempt < 2) delay((1000L * (attempt + 1))) }
                        }
                        trackTokens("moment", prompt, momentResult)
                        val content = cleanAiOutput(momentResult)
                        if (content.isNotBlank()) {
                            if (isAuto) settings.putMomentCount(op.id, today, generated + 1)
                            val moment = Moment(operatorId = op.id, operatorName = op.name, content = content, createdAt = fakeTs)
                            val momentId = repository.insertMoment(moment)
                            addWorldLog("${op.name}发布了一条动态", content.take(80), "moment")
                            repository.insertWorldEvent(WorldEvent(
                                type = WorldEventType.MOMENT_POSTED,
                                actorId = op.id,
                                actorName = op.name,
                                source = "moment",
                                sourceId = momentId.toString(),
                                content = "${op.name}发布动态：${content.take(120)}",
                                createdAt = fakeTs,
                                expiresAt = MemoryPolicy.memoryExpiresAt(settings)
                            ))
                            repository.saveAnchor(AnchorSourcePolicy.buildAnchor(
                                source = AnchorSourcePolicy.MOMENT,
                                sourceName = "自己的动态",
                                sourceActor = op.name,
                                sourceTarget = op.name,
                                operatorId = op.id,
                                type = AnchorType.EVENT,
                                content = "发布动态：${content.take(60)}",
                                importance = AnchorSourcePolicy.WEAK,
                                sessionId = "moment_${momentId}",
                                createdAt = fakeTs,
                                expiresAt = MemoryPolicy.anchorExpiresAt(settings, AnchorType.EVENT)
                            ))
                            totalGenerated++
                            DebugLogger.log("MomentGen", "插入动态: operator=${op.name}, id=$momentId, total=$totalGenerated")
                            refreshMomentsNow()
                            if (unconsumedEvents != "无") {
                                sharedUtils.buildUnconsumedEventContextForOperator(op.id, op.name, "moment:${op.id}", settings.eventContextCount, markConsumed = true)
                            }
                            generated++
                            // 互动异步化：点赞和评论在后台生成，不阻塞下一条动态
                            val opId = op.id; val c = content
                            viewModelScope.launch {
                                try {
                                    val likers = _operators.value.filter { it.id != opId && it.name != profile.nickname }.shuffled().take((3..8).random())
                                    likers.forEach { liker -> repository.insertLike(MomentLike(momentId = momentId, operatorId = liker.id, operatorName = liker.name, createdAt = System.currentTimeMillis())) }
                                    repository.updateLikeCount(momentId, likers.size)
                                    val commenters = _operators.value.filter { it.id != opId && it.name != profile.nickname }.shuffled().take((1..3).random())
                                     val cmtTpl = getPromptTemplate("moment_comment")
                                     var actualComments = 0
                                     commenters.forEach { commenter ->
                                        if (!tryConsumeAutoAiBudget("auto_moment_comment")) return@forEach
                                         try {
                                            val cmtReplacements = mapOf(
                                                "COMMENTER_NAME" to commenter.name, "COMMENTER_PERSONA" to (commenter.groupPrompt.ifBlank { commenter.description }),
                                                "POST_CONTENT" to c,
                                                "COMMENT_MIN_CHARS" to intPref("comment_min_chars", 10).toString(),
                                                "COMMENT_MAX_CHARS" to intPref("comment_max_chars", 40).toString()
                                            )
                                            val cp = applyTemplate(cmtTpl, cmtReplacements)
                                            val cc = withTimeout(8_000) { chat(listOf(AiMessage("system", cp)), "Moment") }.trim()
                                            trackTokens("moment", cp, cc)
                                            if (cc.isNotBlank()) {
                                                repository.insertComment(MomentComment(momentId = momentId, operatorId = commenter.id, operatorName = commenter.name, content = cc, createdAt = System.currentTimeMillis()))
                                                repository.insertWorldEvent(WorldEvent(
                                                    type = WorldEventType.COMMENT_POSTED,
                                                    actorId = commenter.id,
                                                    actorName = commenter.name,
                                                    targetId = momentId.toString(),
                                                    targetName = op.name,
                                                    source = "comment",
                                                    sourceId = momentId.toString(),
                                                    content = "${commenter.name}评论${op.name}的动态：${cc.take(120)}",
                                                    createdAt = System.currentTimeMillis(),
                                                    expiresAt = MemoryPolicy.memoryExpiresAt(settings)
                                                ))
                                                actualComments++
                                            }
                                        } catch (_: Exception) {}
                                    }
                                    refreshMomentCommentCount(momentId)
                                } catch (_: Exception) {}
                            }
                        }
                    } catch (e: Exception) {
                        val errMsg = "${op.name}失败: ${e.message?.take(30) ?: "未知错误"}"
                        onProgress(errMsg)
                        _momentGenerateStatus.value = MomentGenerateStatus(running = false, msg = errMsg)
                    }
                }
            }
            onProgress("全部完成")
            refreshMomentsNow()
        }
    }

    /** 手动下拉刷新：只随机生成 1 条动态 */
    fun generateOneMoment(onProgress: (String, Boolean) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            try {
                val eligible = _operators.value.filter { settings.getOperatorDynPermission(it.id) }
                if (eligible.isEmpty()) { onProgress("", true); return@launch }
                val op = eligible.random()
                onProgress("发布中...", false)
                val momentId = generateOneForOpSync(op, MomentTriggerType.MANUAL)
                if (momentId != null) {
                    generateLikesAndComments(momentId, op, consumeBudget = false)
                    refreshMomentsNow()
                }
            } finally {
                onProgress("", true)
            }
        }
    }

    private suspend fun generateOneMomentForWorld(parentEvent: WorldEvent?): Boolean {
        if (!canContinueWorldChain(parentEvent)) return false
        if (!settings.autoAiEnabled || !settings.autoMomentEnabled) return false
        if (!tryConsumeAutoAiBudget("event_moment")) return false
        val today = beijingSdf("yyyyMMdd").format(java.util.Date())
        val eligible = _operators.value.filter { settings.getOperatorDynPermission(it.id) && getTodayMomentCount(it.id, today) < settings.dailyMomentTarget }
        if (eligible.isEmpty()) return false
        val op = eligible.random()
        val momentId = generateOneForOpSync(op, MomentTriggerType.EVENT, parentEvent)
        if (momentId != null) {
            val childEvent = parentEvent?.let {
                WorldEvent(id = 0, type = WorldEventType.MOMENT_POSTED, content = "", originType = it.originType.ifBlank { it.type }, chainDepth = it.chainDepth + 1, rootEventId = if (it.rootEventId > 0L) it.rootEventId else it.id)
            }
            generateLikesAndComments(momentId, op, consumeBudget = true, parentEvent = childEvent)
            refreshMomentsNow()
            return true
        }
        return false
    }

    private fun cleanAiOutput(raw: String): String {
        var content = raw.trim()
        while (content.startsWith("\"") && content.endsWith("\"") && content.length > 1) {
            content = content.removePrefix("\"").removeSuffix("\"").trim()
        }
        content = content.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return content
    }

    /** 为指定干员同步生成 1 条动态（不含点赞评论），返回 momentId */
    private suspend fun generateOneForOpSync(op: Operator, triggerType: MomentTriggerType = MomentTriggerType.MANUAL, parentEvent: WorldEvent? = null): Long? {
        Log.d("RHODES_MOMENT", "generateOneForOpSync: 开始 op=${op.name} triggerType=$triggerType")
        return try {
            val profile = getUserProfile()
            val impression = repository.getLongTermImpression(op.id)?.content ?: "无"
            val chatSummary = repository.getShortTermMemory("session_${op.id}")?.content?.take(100) ?: "无"
            val memories = sharedUtils.pickAnchorsForSurface(repository.getPublicAnchors(op.id), settings.momentAnchorCount, MemorySurface.MOMENT).joinToString("\n") { "- ${anchorTimeLabel(it)} ${it.content}" }.ifBlank { "无" }
            val sourceAwareMemories = sharedUtils.buildSourceAwareMemoryContext(repository.getPublicAnchors(op.id), settings.momentAnchorCount, MemorySurface.MOMENT)
            val worldEvents = sharedUtils.buildWorldEventContext(op.id, op.name, 5)
            val unconsumedEvents = sharedUtils.buildUnconsumedEventContextForOperator(op.id, op.name, "moment:${op.id}", settings.eventContextCount, markConsumed = false)
            val existingPosts = repository.getMomentsPaged(10, 0).filter { it.operatorId == op.id }
            val recentPosts = existingPosts.take(settings.momentRecentPostCount).joinToString("\n") { "- ${it.content.take(50)}" }.ifBlank { "无" }
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
            val timeOfDay = getTimeOfDay(cal.get(java.util.Calendar.HOUR_OF_DAY))
            val fakeTs = System.currentTimeMillis()
            val mmtTpl = getPromptTemplate("moment")
            val userMentionRoll = (Math.random() * 100).toInt()
            val mentionUser = userMentionRoll < settings.momentUserRelatedRate
            DebugLogger.log("Moment", "用户提及决策: rate=${settings.momentUserRelatedRate}, roll=$userMentionRoll, mention=$mentionUser")
            val mmtReplacements = mapOf(
                "OPERATOR_NAME" to op.name, "OPERATOR_PERSONA" to op.privatePrompt.ifBlank { op.description },
                "TIME_OF_DAY" to timeOfDay, "LONG_TERM_IMPRESSION" to if (mentionUser) impression else "无",
                "RECENT_CHAT_SUMMARY" to if (mentionUser) chatSummary else "无",
                "RECENT_MEMORIES" to memories,
                "SOURCE_AWARE_MEMORIES" to sourceAwareMemories,
                "RECENT_WORLD_EVENTS" to worldEvents,
                "UNCONSUMED_EVENTS" to unconsumedEvents,
                "MOMENT_EVENT_SEED" to unconsumedEvents,
                "MOMENT_TRIGGER_TYPE" to triggerType.name.lowercase(),
                "WORLD_TODAY_STATE" to "${op.name}现在在${op.location}，正在${op.activity}，情绪${op.emotion}",
                "MOMENT_TRIGGER_REASON" to worldEvents.lines().firstOrNull { it.isNotBlank() }?.removePrefix("- ").orEmpty().ifBlank { "普通日常分享" },
                "KNOWN_FROM_CONTEXT" to sourceAwareMemories,
                "SOURCE_AWARE_RULES" to sharedUtils.sourceAwareUsageRule(MemorySurface.MOMENT),
                "RECENT_POSTS" to recentPosts,
                "RECENT_DAILY_SUMMARY" to if (mentionUser) (repository.getLatestPrivateDaily(op.id)?.content ?: "无") else "无",
                "CURRENT_DATE" to beijingSdf("yyyy年MM月dd日").format(fakeTs),
                "USER_NAME" to if (mentionUser) profile.nickname else "博士",
                "USER_GENDER" to if (mentionUser) profile.gender.ifBlank { "" } else "",
                "MOMENT_MIN_CHARS" to settings.momentMinChars.toString(),
                "MOMENT_MAX_CHARS" to settings.momentMaxChars.toString()
            )
            sharedUtils.logMemoryContext(
                surface = "moment",
                title = "${op.name}/${op.id}",
                placeholders = mapOf(
                    "LONG_TERM_IMPRESSION" to mmtReplacements["LONG_TERM_IMPRESSION"].orEmpty(),
                    "RECENT_CHAT_SUMMARY" to mmtReplacements["RECENT_CHAT_SUMMARY"].orEmpty(),
                    "RECENT_MEMORIES" to mmtReplacements["RECENT_MEMORIES"].orEmpty(),
                    "SOURCE_AWARE_MEMORIES" to mmtReplacements["SOURCE_AWARE_MEMORIES"].orEmpty(),
                    "RECENT_DAILY_SUMMARY" to mmtReplacements["RECENT_DAILY_SUMMARY"].orEmpty(),
                    "RECENT_POSTS" to mmtReplacements["RECENT_POSTS"].orEmpty()
                ),
                extra = mapOf(
                    "auto" to "false",
                    "timeOfDay" to timeOfDay,
                    "momentAnchorCount" to settings.momentAnchorCount.toString(),
                    "momentRecentPostCount" to settings.momentRecentPostCount.toString()
                )
            )
            val prompt = applyTemplate(mmtTpl, mmtReplacements)
            var momentResult = ""
            repeat(3) { attempt ->
                try {
                    momentResult = ""
                    momentResult = withTimeout(15_000) { chat(listOf(AiMessage("system", prompt)), "Moment") }
                    Log.d("RHODES_MOMENT", "generateOneForOpSync: AI调用 attempt=$attempt 长度=${momentResult.length} op=${op.name}")
                    if (momentResult.isNotBlank()) return@repeat
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) { if (attempt < 2) delay((1000L * (attempt + 1))) }
            }
            trackTokens("moment", prompt, momentResult)
            var content = cleanAiOutput(momentResult)
            if (content.isNotBlank()) {
                val moment = Moment(operatorId = op.id, operatorName = op.name, content = content, createdAt = fakeTs)
                val momentId = repository.insertMoment(moment)
                Log.d("RHODES_MOMENT", "generateOneForOpSync: 写入成功 id=$momentId op=${op.name}")
                addWorldLog("${op.name}发布了一条动态", content.take(80), "moment")
                if (triggerType != MomentTriggerType.MANUAL) {
                    val today = beijingSdf("yyyyMMdd").format(java.util.Date(fakeTs))
                    settings.putMomentCount(op.id, today, settings.getMomentCount(op.id, today) + 1)
                }
                repository.insertWorldEvent(derivedWorldEvent(WorldEvent(
                    type = WorldEventType.MOMENT_POSTED,
                    actorId = op.id,
                    actorName = op.name,
                    source = "moment",
                    sourceId = momentId.toString(),
                    content = "${op.name}发布动态：${content.take(120)}",
                    createdAt = fakeTs,
                    expiresAt = MemoryPolicy.memoryExpiresAt(settings)
                ), parentEvent))
                repository.saveAnchor(AnchorSourcePolicy.buildAnchor(
                    source = AnchorSourcePolicy.MOMENT,
                    sourceName = "自己的动态",
                    sourceActor = op.name,
                    sourceTarget = op.name,
                    operatorId = op.id,
                    type = AnchorType.EVENT,
                    content = "发布动态：${content.take(60)}",
                    importance = AnchorSourcePolicy.WEAK,
                    sessionId = "moment_${momentId}",
                    createdAt = fakeTs,
                    expiresAt = MemoryPolicy.anchorExpiresAt(settings, AnchorType.EVENT)
                ))
                if (unconsumedEvents != "无") {
                    sharedUtils.buildUnconsumedEventContextForOperator(op.id, op.name, "moment:${op.id}", settings.eventContextCount, markConsumed = true)
                }
                momentId
            } else {
                Log.w("RHODES_MOMENT", "generateOneForOpSync: AI结果为空 op=${op.name}")
                null
            }
        } catch (e: CancellationException) {
            Log.e("RHODES_MOMENT", "generateOneForOpSync: 取消 op=${op.name}")
            throw e
        } catch (e: Exception) {
            Log.e("RHODES_MOMENT", "generateOneForOpSync: 异常 op=${op.name} ${e.message}", e)
            null
        }
    }

    /** 异步生成点赞和评论（不阻塞调用方） */
    private fun generateLikesAndComments(momentId: Long, op: Operator, consumeBudget: Boolean = false, parentEvent: WorldEvent? = null) {
        Log.d("RHODES_MOMENT", "generateLikesAndComments: 开始 momentId=$momentId op=${op.name} consumeBudget=$consumeBudget")
        appScope.launch {
            try {
                val profile = getUserProfile()
                val opId = op.id
                val moment = repository.getMoment(momentId)
                val postContent = moment?.content ?: ""
                val likers = _operators.value.filter { it.id != opId && it.name != profile.nickname }.shuffled().take((3..8).random())
                likers.forEach { liker -> repository.insertLike(MomentLike(momentId = momentId, operatorId = liker.id, operatorName = liker.name, createdAt = System.currentTimeMillis())) }
                repository.updateLikeCount(momentId, likers.size)
                Log.d("RHODES_MOMENT", "generateLikesAndComments: 点赞 ${likers.size}人 momentId=$momentId")
                val commenters = _operators.value.filter { it.id != opId && it.name != profile.nickname }.shuffled().take((1..3).random())
                val cmtTpl = getPromptTemplate("moment_comment")
                var actualComments = 0
                commenters.forEach { commenter ->
                    if (consumeBudget && !tryConsumeAutoAiBudget("moment_comment")) {
                        Log.w("RHODES_MOMENT", "generateLikesAndComments: 评论跳过(预算不足) commenter=${commenter.name}")
                        return@forEach
                    }
                    try {
                        val cmtReplacements = mapOf(
                            "COMMENTER_NAME" to commenter.name, "COMMENTER_PERSONA" to (commenter.groupPrompt.ifBlank { commenter.description }),
                            "POST_CONTENT" to postContent, "COMMENT_MIN_CHARS" to intPref("comment_min_chars", 10).toString(),
                            "COMMENT_MAX_CHARS" to intPref("comment_max_chars", 40).toString()
                        )
                        val cp = applyTemplate(cmtTpl, cmtReplacements)
                        val cc = withTimeout(8_000) { chat(listOf(AiMessage("system", cp)), "Moment") }.trim()
                        trackTokens("moment", cp, cc)
                        if (cc.isNotBlank()) {
                            repository.insertComment(MomentComment(momentId = momentId, operatorId = commenter.id, operatorName = commenter.name, content = cc, createdAt = System.currentTimeMillis()))
                            repository.insertWorldEvent(derivedWorldEvent(WorldEvent(
                                type = WorldEventType.COMMENT_POSTED,
                                actorId = commenter.id,
                                actorName = commenter.name,
                                targetId = momentId.toString(),
                                targetName = op.name,
                                source = "comment",
                                sourceId = momentId.toString(),
                                content = "${commenter.name}评论${op.name}的动态：${cc.take(120)}",
                                createdAt = System.currentTimeMillis(),
                                expiresAt = MemoryPolicy.memoryExpiresAt(settings)
                            ), parentEvent))
                            actualComments++
                            Log.d("RHODES_MOMENT", "generateLikesAndComments: 评论成功 i=${commenters.indexOf(commenter)} name=${commenter.name}")
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("RHODES_MOMENT", "generateLikesAndComments: 评论生成失败 ${commenter.name} ${e.message?.take(100)}")
                    }
                }
                refreshMomentCommentCount(momentId)
                Log.d("RHODES_MOMENT", "generateLikesAndComments: 完成 momentId=$momentId 评论=$actualComments")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("RHODES_MOMENT", "generateLikesAndComments: 异常 ${e.message}", e)
            }
        }
    }

    fun generateInspirations(callback: (List<String>) -> Unit) = chatViewModel.generateInspirations(callback)
    fun getLikes(momentId: Long): kotlinx.coroutines.flow.Flow<List<MomentLike>> = momentsViewModel.getLikes(momentId)
    fun getCommentsForMoment(momentId: Long): kotlinx.coroutines.flow.Flow<List<MomentComment>> = momentsViewModel.getCommentsForMoment(momentId)
    fun likeMoment(momentId: Long, operatorId: String, operatorName: String) = momentsViewModel.likeMoment(momentId, operatorId, operatorName)
    fun commentOnMoment(momentId: Long, operatorId: String, operatorName: String, content: String, parentCommentId: Long = 0, replyToName: String = "") {
        val cleanContent = content.trim()
        if (cleanContent.isBlank()) return
        Log.d("RHODES_MOMENT", "commentOnMoment: operatorId=$operatorId momentId=$momentId content=${cleanContent.take(50)} parentId=$parentCommentId replyTo=$replyToName")
        DebugLogger.log("Moment", "用户发评论: momentId=$momentId, content=${cleanContent.take(50)}, parentId=$parentCommentId, replyTo=$replyToName")
        viewModelScope.launch {
            // 微信模式：向上追溯到根一级评论，所有回复挂在一级下面
            val rootParentId = if (parentCommentId > 0) {
                val parentComment = repository.getCommentById(parentCommentId)
                if (parentComment?.parentCommentId != null && parentComment.parentCommentId > 0)
                    parentComment.parentCommentId
                else
                    parentCommentId
            } else 0L
            repository.insertComment(MomentComment(momentId = momentId, operatorId = operatorId, operatorName = operatorName, content = cleanContent, parentCommentId = rootParentId, replyToName = replyToName, createdAt = System.currentTimeMillis(), isRead = operatorId == "user"))
            Log.d("RHODES_MOMENT", "commentOnMoment: 评论已写入")
            addWorldLog("${operatorName}评论了一条动态", cleanContent.take(80), "comment")
            refreshMomentCommentCount(momentId)
            val commentEvent = WorldEvent(
                type = WorldEventType.COMMENT_POSTED,
                actorId = operatorId,
                actorName = operatorName,
                targetId = momentId.toString(),
                targetName = replyToName,
                source = "comment",
                sourceId = momentId.toString(),
                content = "${operatorName}评论：${cleanContent.take(120)}",
                createdAt = System.currentTimeMillis(),
                expiresAt = MemoryPolicy.memoryExpiresAt(settings)
            )
            val commentEventId = repository.insertWorldEvent(derivedWorldEvent(commentEvent, null))
            val insertedCommentEvent = commentEvent.copy(id = commentEventId, originType = commentEvent.type, rootEventId = commentEventId)
            DebugLogger.log("Moment/DB", "评论已写入DB, momentId=$momentId")
            // 创建评论锚点（仅动态发布者 + 被回复者，不扩散到全干员）
            if (operatorId == "user") {
                val moment = _moments.value.find { it.id == momentId }
                val targetName = if (parentCommentId > 0 && replyToName.isNotBlank()) "回复了${replyToName}" else "评论了${moment?.operatorName ?: ""}的动态"
                val anchorTargets = mutableSetOf<String>()
                if (moment != null) anchorTargets.add(moment.operatorId)
                if (parentCommentId > 0 && replyToName.isNotBlank()) anchorTargets.add(replyToName)
                for (anchorOpId in anchorTargets) {
                    val realOp = _operators.value.find { it.name == anchorOpId || it.id == anchorOpId }
                    if (realOp != null) {
                        repository.saveAnchor(AnchorSourcePolicy.buildAnchor(
                            source = AnchorSourcePolicy.COMMENT,
                            sourceName = "${moment?.operatorName ?: "动态"}的动态",
                            sourceActor = getUserProfile().nickname,
                            sourceTarget = realOp.name,
                            operatorId = realOp.id,
                            type = AnchorType.EVENT,
                            content = "$targetName：${content.take(40)}",
                            importance = AnchorSourcePolicy.STRONG,
                            sessionId = "moment_${momentId}",
                            createdAt = System.currentTimeMillis(),
                            expiresAt = MemoryPolicy.anchorExpiresAt(settings, AnchorType.EVENT)
                        ))
                    }
                }
                if (moment != null && settings.autoAiEnabled && settings.worldSchedulerEnabled && settings.worldProactiveChatEnabled && (0..99).random() < settings.commentToPrivateTriggerRate) {
                    repository.insertWorldEvent(derivedWorldEvent(WorldEvent(
                        type = WorldEventType.PRIVATE_TRIGGER,
                        actorId = moment.operatorId,
                        actorName = moment.operatorName,
                        targetId = "user",
                        targetName = getUserProfile().nickname,
                        source = "comment",
                        sourceId = momentId.toString(),
                        content = "${moment.operatorName}注意到${getUserProfile().nickname}在动态下评论：${cleanContent.take(80)}",
                        createdAt = System.currentTimeMillis(),
                        expiresAt = MemoryPolicy.memoryExpiresAt(settings)
                    ), insertedCommentEvent))
                }
                if (settings.autoAiEnabled && settings.worldSchedulerEnabled && settings.worldAutoGroupEnabled && (0..99).random() < settings.momentToGroupTriggerRate) {
                    if (canUseWorldTrigger("comment_event_group") && triggerEventGroupChats(insertedCommentEvent)) {
                        consumeWorldTrigger("comment_event_group")
                    }
                }
            }

            if (operatorId != "user") return@launch
            val moment = _moments.value.find { it.id == momentId } ?: return@launch
            val userName = getUserProfile().nickname

            val alreadyReplied = mutableSetOf<String>()
            if (parentCommentId > 0 && replyToName.isNotBlank() && replyToName.trim() != moment.operatorName.trim() && replyToName.trim() != userName.trim()) {
                Log.d("RHODES_MOMENT", "commentOnMoment: 回复目标人=$replyToName")
                triggerSingleAiReply(momentId, replyToName, content, rootParentId, userName)
                alreadyReplied.add(replyToName)
                delay((1500L + (Math.random() * 1500).toLong()))
            }

            if (moment.operatorName != "我" && moment.operatorName.trim() != userName.trim() && moment.operatorName !in alreadyReplied) {
                Log.d("RHODES_MOMENT", "commentOnMoment: 动态主人回复 start=${moment.operatorName}")
                triggerSingleAiReply(momentId, moment.operatorName, content, rootParentId, userName, "你是${moment.operatorName}。用户${userName}在你的动态下评论了：「${content}」。请用10-50字自然回复。只输出回复内容本身，不要加任何前缀如「回复xxx」或冒号。直接输出纯文本。")
                alreadyReplied.add(moment.operatorName)
                delay((1500L + (Math.random() * 1500).toLong()))
            }

            val bystanderCount = pickBystanderReplyCount()
            Log.d("RHODES_MOMENT", "commentOnMoment: 旁观者回复 count=$bystanderCount")
            val bystanders = _operators.value
                .filter { settings.getOperatorDynPermission(it.id) }
                .map { it.name }
                .filter { it !in alreadyReplied && it != "我" && it != userName }
                .shuffled()
                .take(bystanderCount)
            for ((i, bystander) in bystanders.withIndex()) {
                Log.d("RHODES_MOMENT", "commentOnMoment: 旁观者回复 i=$i name=$bystander")
                val bp = "你是${bystander}。你刚看到${moment.operatorName}的动态下，用户${userName}评论了「${content}」。请用10-40字凑热闹式地回复这条评论（看戏、调侃、起哄风格）。直接输出纯文本。"
                triggerSingleAiReply(momentId, bystander, content, rootParentId, userName, bp)
                delay((1500L + (Math.random() * 1500).toLong()))
            }
        }
    }

    private fun pickBystanderReplyCount(): Int {
        val min = settings.commentBystanderMin.coerceAtMost(settings.commentBystanderMax)
        val max = settings.commentBystanderMax.coerceAtLeast(min)
        if (max <= 0) return 0
        return (min..max).random()
    }

    private suspend fun refreshMomentCommentCount(momentId: Long) {
        val count = repository.getCommentCount(momentId)
        repository.updateCommentCount(momentId, count)
    }

    private fun todayStartMillis(): Long {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private suspend fun getTodayMomentCount(operatorId: String, dateKey: String = beijingSdf("yyyyMMdd").format(java.util.Date())): Int {
        return maxOf(settings.getMomentCount(operatorId, dateKey), repository.countMomentsByOperatorSince(operatorId, todayStartMillis()))
    }

    private fun triggerSingleAiReply(momentId: Long, speakerName: String, userContent: String, parentCommentId: Long, userName: String, customPrompt: String? = null) {
        viewModelScope.launch {
            try {
                val realOp = _operators.value.find { it.name == speakerName || it.id == speakerName }
                if (realOp != null && !settings.getOperatorDynPermission(realOp.id)) return@launch
                val recentComments = try {
                    withTimeout(500) { repository.getComments(momentId).first() }
                        .takeLast(settings.commentContextCount)
                        .joinToString("\n") { "${it.operatorName}：${it.content.take(60)}" }
                } catch (_: Exception) { "" }
                val memory = if (realOp != null) {
                    sharedUtils.pickAnchorsForSurface(repository.getPublicAnchors(realOp.id), settings.commentMemoryCount, MemorySurface.COMMENT, userContent)
                        .joinToString("\n") { "- ${it.content}" }
                } else ""
                val sourceAwareMemory = if (realOp != null) {
                    sharedUtils.buildSourceAwareMemoryContext(repository.getPublicAnchors(realOp.id), settings.commentMemoryCount, MemorySurface.COMMENT, userContent)
                } else ""
                val contextBlock = listOfNotNull(
                    recentComments.takeIf { it.isNotBlank() }?.let { "【评论上下文】\n$it" },
                    memory.takeIf { it.isNotBlank() }?.let { "【你的相关记忆】\n$it" },
                    sourceAwareMemory.takeIf { it.isNotBlank() && it != "无" }?.let { "【你知道这些事的来源】\n$it\n${sharedUtils.sourceAwareUsageRule(MemorySurface.COMMENT)}" }
                ).joinToString("\n")
                sharedUtils.logMemoryContext(
                    surface = "comment",
                    title = "$speakerName/moment_$momentId",
                    placeholders = mapOf(
                        "COMMENT_CONTEXT" to recentComments,
                        "COMMENTER_MEMORY" to memory,
                        "SOURCE_AWARE_MEMORIES" to sourceAwareMemory,
                        "USER_CONTENT" to userContent
                    ),
                    extra = mapOf(
                        "customPrompt" to (customPrompt != null).toString(),
                        "user" to userName,
                        "commentContextCount" to settings.commentContextCount.toString(),
                        "commentMemoryCount" to settings.commentMemoryCount.toString()
                    )
                )
                val prompt = if (customPrompt != null) "$customPrompt\n$contextBlock" else "你是${speakerName}。用户扮演的角色${userName}刚刚回复了你的评论，说：「${userContent}」。\n$contextBlock\n请用10-50字自然回复。只输出回复内容本身，不要加任何前缀如「回复xxx」或冒号。直接输出纯文本。注意：你是${speakerName}，不是${userName}，不要替${userName}说话。"
                val reply = withTimeout(10_000) { chat(listOf(AiMessage("system", prompt))) }.trim()
                if (reply.isNotBlank()) {
                    val realId = realOp?.id ?: speakerName
                    repository.insertComment(MomentComment(momentId = momentId, operatorId = realId, operatorName = speakerName, content = reply, parentCommentId = parentCommentId, replyToName = userName, createdAt = System.currentTimeMillis()))
                    refreshMomentCommentCount(momentId)
                    repository.insertWorldEvent(WorldEvent(
                        type = WorldEventType.COMMENT_POSTED,
                        actorId = realId,
                        actorName = speakerName,
                        targetId = momentId.toString(),
                        targetName = userName,
                        source = "comment",
                        sourceId = momentId.toString(),
                        content = "${speakerName}回复评论：${reply.take(120)}",
                        createdAt = System.currentTimeMillis(),
                        expiresAt = MemoryPolicy.memoryExpiresAt(settings)
                    ))
                    Log.d("RHODES_MOMENT", "triggerSingleAiReply: $speakerName 回复成功")
                } else {
                    Log.w("RHODES_MOMENT", "triggerSingleAiReply: $speakerName 回复内容为空")
                }
            } catch (e: Exception) {
                Log.e("RHODES_MOMENT", "triggerSingleAiReply: $speakerName 异常: ${e.message}", e)
            }
        }
    }



    fun postUserMoment(content: String, mentionedOps: List<String>) {
        DebugLogger.log("Moment", "用户发动态: content=${content.take(50)}, mentioned=$mentionedOps")
        viewModelScope.launch {
            val profile = getUserProfile()
            val userName = profile.nickname
            val moment = Moment(operatorId = "user", operatorName = userName, content = content, isUserPost = true, mentionedOperatorIds = mentionedOps.joinToString(","), createdAt = System.currentTimeMillis())
            val momentId = repository.insertMoment(moment)
            addWorldLog("${userName}发布了一条动态", content.take(80), "moment")
            repository.insertWorldEvent(WorldEvent(
                type = WorldEventType.MOMENT_POSTED,
                actorId = "user",
                actorName = userName,
                source = "moment",
                sourceId = momentId.toString(),
                content = "${userName}发布动态：${content.take(120)}",
                createdAt = moment.createdAt,
                expiresAt = MemoryPolicy.memoryExpiresAt(settings)
            ))
            DebugLogger.log("Moment/DB", "动态已写入DB, id=$momentId")
            refreshMomentsNow()
            // 创建动态锚点：被@的干员必定记住，再补随机围观干员。
            val mentionedIds = mentionedOps.mapNotNull { name -> _operators.value.find { it.name == name || it.id == name }?.id }
            val randomObservers = _operators.value.filter { it.id != "user" && it.id !in mentionedIds }.shuffled().take(settings.momentUserPostObserverCount).map { it.id }
            val anchorOps = mentionedIds + randomObservers
            for (opId in anchorOps.distinct()) {
                val opName = _operators.value.find { it.id == opId }?.name ?: opId
                repository.saveAnchor(AnchorSourcePolicy.buildAnchor(
                    source = AnchorSourcePolicy.MOMENT,
                    sourceName = "${userName}的动态",
                    sourceActor = userName,
                    sourceTarget = opName,
                    operatorId = opId,
                    type = AnchorType.EVENT,
                    content = if (opId in mentionedIds) "在动态中提到了${opName}：${content.take(60)}" else "发布动态：${content.take(60)}",
                    importance = if (opId in mentionedIds) AnchorSourcePolicy.STRONG else AnchorSourcePolicy.WEAK,
                    sessionId = "moment_${momentId}",
                    createdAt = System.currentTimeMillis(),
                    expiresAt = MemoryPolicy.anchorExpiresAt(settings, AnchorType.EVENT)
                ))
            }

            // 用户主动发布动态引起的 AI 评论不计入自动预算，但仍尊重总自动 AI 开关。
            if (settings.autoAiEnabled) {
                val allOpNames = _operators.value.map { it.name }.filter { it != userName }
                val mentioned = mentionedOps.filter { it in allOpNames }
                val randomCount = (3 + (Math.random() * 3).toInt()).coerceAtLeast(3)
                val others = (allOpNames - mentioned.toSet()).shuffled().take((randomCount - mentioned.size).coerceAtLeast(0))
                val repliers = (mentioned + others).distinct().take(5)
                val c = content; val u = userName
                for ((i, name) in repliers.withIndex()) {
                    if (i > 0) delay((1500L + (Math.random() * 1500).toLong()))
                    val prompt = "你是${name}。用户扮演的角色${u}发布了动态：「${c}」。请用10-40字评论这条动态（根据你的性格自然回应）。直接输出纯文本。注意：你是${name}，不是${u}，不要替${u}说话。"
                    triggerSingleAiReply(momentId, name, c, 0, u, prompt)
                }
            }
        }
    }

    // === Moments delegation ===
    fun getMomentBadge(): Int = momentsViewModel.getMomentBadge()
    suspend fun getMomentBadgeSuspend(): Int = momentsViewModel.getMomentBadgeSuspend()
    suspend fun getUnreadCommentCountSuspend(): Int = momentsViewModel.getUnreadCommentCountSuspend()
    fun markMomentsSeen() = momentsViewModel.markMomentsSeen()
    fun loadInboxComments(callback: (List<MomentComment>) -> Unit) = momentsViewModel.loadInboxComments(callback)
    fun markAllCommentsRead() = momentsViewModel.markAllCommentsRead()
    fun markCommentRead(commentId: Long) = momentsViewModel.markCommentRead(commentId)
    fun markMomentCommentsRead(momentId: Long) = momentsViewModel.markMomentCommentsRead(momentId)

    // === Data delegation ===
    suspend fun getDataStats(): DataViewModel.DataStats = dataViewModel.getDataStats(_operators.value.size, _moments.value.size)
    fun cleanupAllExpired() = dataViewModel.cleanupAllExpired()
    fun deleteAllWorldEvents() = dataViewModel.deleteAllWorldEvents()
    suspend fun getMessageRanking(): List<SenderCount> = dataViewModel.getMessageRanking()
    suspend fun getDailyRanking(): List<SenderCount> = dataViewModel.getDailyRanking()
    fun forceGenerateMoments() {
        if (!forceGenerating.compareAndSet(false, true)) {
            Log.w("RHODES_MOMENT", "forceGenerateMoments: 正在生成中跳过")
            return
        }
        Log.d("RHODES_MOMENT", "forceGenerateMoments: 开始 operators.size=${_operators.value.size}")
        _momentGenerateStatus.value = MomentGenerateStatus(running = true, msg = "开始生成...")
        appScope.launch {
            try {
                var generated = 0
                val candidates = _operators.value.filter {
                    settings.getOperatorDynPermission(it.id)
                }.shuffled()
                Log.d("RHODES_MOMENT", "forceGenerateMoments: 候选干员 ${candidates.size}人")
                for (op in candidates) {
                    _momentGenerateStatus.value = MomentGenerateStatus(running = true, msg = "${op.name}发布中...")
                    Log.d("RHODES_MOMENT", "forceGenerateMoments: 正在生成 op=${op.name}")
                    try {
                        val momentId = generateOneForOpSync(op, MomentTriggerType.MANUAL)
                        if (momentId != null) {
                            generated++
                            Log.d("RHODES_MOMENT", "forceGenerateMoments: 生成成功 op=${op.name} id=${momentId}")
                            generateLikesAndComments(momentId, op, consumeBudget = false)
                            refreshMomentsNow()
                        } else {
                            Log.w("RHODES_MOMENT", "forceGenerateMoments: 生成失败 op=${op.name} (返回null)")
                        }
                    } catch (e: Exception) {
                        Log.e("RHODES_MOMENT", "forceGenerateMoments: 生成异常 op=${op.name} ${e.message}", e)
                    }
                }
                Log.d("RHODES_MOMENT", "forceGenerateMoments: 完成 generated=$generated")
                _momentGenerateStatus.value = MomentGenerateStatus(
                    running = false,
                    msg = if (generated > 0) "生成完成（${generated}条）" else "无可用干员"
                )
                refreshMomentsNow()
            } catch (e: Exception) {
                Log.e("RHODES_MOMENT", "forceGenerateMoments: 整体异常 ${e.message}", e)
                _momentGenerateStatus.value = MomentGenerateStatus(running = false, msg = "生成失败")
            } finally {
                forceGenerating.set(false)
            }
        }
    }

    fun refreshMomentsNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val limit = appState.moments.value.size.coerceAtLeast(MOMENT_PAGE_SIZE)
            val fresh = repository.getMomentsPaged(limit, 0)
            _hasMoreMoments.value = fresh.size >= limit
            appState.refreshMoments(fresh)
        }
    }

    fun loadInitialMoments() {
        if (_isLoadingMoments.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMoments.value = true
            try {
                val firstPage = repository.getMomentsPaged(MOMENT_PAGE_SIZE, 0)
                _hasMoreMoments.value = firstPage.size >= MOMENT_PAGE_SIZE
                appState.refreshMoments(firstPage)
            } finally {
                _isLoadingMoments.value = false
            }
        }
    }

    fun loadMoreMoments() {
        if (_isLoadingMoments.value || !_hasMoreMoments.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMoments.value = true
            try {
                val current = appState.moments.value
                val more = repository.getMomentsPaged(MOMENT_PAGE_SIZE, current.size)
                _hasMoreMoments.value = more.size >= MOMENT_PAGE_SIZE
                if (more.isNotEmpty()) {
                    appState.refreshMoments((current + more).distinctBy { it.id }.sortedByDescending { it.createdAt })
                }
            } finally {
                _isLoadingMoments.value = false
            }
        }
    }
    suspend fun getAllImpressions(): List<Memory> = dataViewModel.getAllImpressions()
    suspend fun deleteAllImpressions() = dataViewModel.deleteAllImpressions()
    fun generateDiary(operatorId: String, auto: Boolean = false, onResult: (String) -> Unit) {
        DebugLogger.log("Diary", "偷看日记: operatorId=$operatorId")
        viewModelScope.launch {
            val text = generateDiaryText(operatorId, auto)
            if (!auto && text.isNotBlank()) settings.putDiaryReadAt(operatorId, System.currentTimeMillis())
            onResult(text)
        }
    }

    suspend fun getUnreadDiaryCount(): Int {
        return repository.getAllOperatorsSync().count { op ->
            val latest = repository.getAllDiaryEntries(op.id).maxOfOrNull { it.createdAt } ?: 0L
            latest > settings.getDiaryReadAt(op.id)
        }
    }

    suspend fun getUnreadDiaryOperatorIds(): Set<String> {
        return repository.getAllOperatorsSync().mapNotNull { op ->
            val latest = repository.getAllDiaryEntries(op.id).maxOfOrNull { it.createdAt } ?: 0L
            if (latest > settings.getDiaryReadAt(op.id)) op.id else null
        }.toSet()
    }

    fun markDiaryRead(operatorId: String, latestCreatedAt: Long = System.currentTimeMillis()) {
        settings.putDiaryReadAt(operatorId, latestCreatedAt)
    }

    fun getWorldLogs(): List<WorldLogEntry> {
        return try {
            Json.decodeFromString<List<WorldLogEntry>>(settings.worldLogJson).sortedByDescending { it.time }
        } catch (_: Exception) { emptyList() }
    }

    fun addWorldLog(title: String, detail: String = "", type: String = "world") {
        val logs = getWorldLogs().toMutableList()
        logs.add(0, WorldLogEntry(System.currentTimeMillis(), title, detail, type))
        settings.worldLogJson = Json.encodeToString(logs.sortedByDescending { it.time }.take(200))
    }

    private suspend fun generateDiarySync(operatorId: String, auto: Boolean = false): Boolean {
        return generateDiaryText(operatorId, auto).isNotBlank()
    }

    private suspend fun generateDiaryText(operatorId: String, auto: Boolean = false): String {
        if (auto && !tryConsumeAutoAiBudget("diary")) return ""
        val op = repository.getOperator(operatorId) ?: run {
            DebugLogger.log("Diary", "干员不存在: $operatorId")
            return ""
        }
            val profile = getUserProfile()
            try {
                DebugLogger.log("Diary", "开始生成日记: ${op.name}")
                // 用北京时间计算昨天和今天的日期
                val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
                cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
                val yesterdayStr = sharedUtils.beijingSdf("yyyy-MM-dd").format(cal.time)
                val yesterdayDisplay = sharedUtils.beijingSdf("yyyy年MM月dd日").format(cal.time)
                cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
                val todayDisplay = sharedUtils.beijingSdf("yyyy年MM月dd日").format(cal.time)

                val groupSummaries = _allSessions.value.filter { session ->
                    session.operatorId.startsWith("group_") && session.members.split(",").map { it.trim() }.any { it == operatorId || it == op.name }
                }
                    .mapNotNull { repository.getShortTermMemory(it.id)?.content?.let { c -> "- ${it.operatorName}：${c.take(80)}" } }
                    .take(settings.diaryGroupSummaryCount)
                    .joinToString("\n").ifBlank { "无" }
                val recentMemories = sharedUtils.pickAnchorsForSurface(repository.getAnchors(operatorId), settings.diaryAnchorCount, MemorySurface.DIARY)
                    .joinToString("\n") { "- ${it.content}" }.ifBlank { "无" }
                val sourceAwareMemories = sharedUtils.buildSourceAwareMemoryContext(repository.getAnchors(operatorId), settings.diaryAnchorCount, MemorySurface.DIARY)
                val worldEvents = sharedUtils.buildWorldEventContext(operatorId, op.name, 8)
                val unconsumedEvents = sharedUtils.buildUnconsumedEventContextForOperator(operatorId, op.name, "diary:$operatorId", settings.eventContextCount, markConsumed = false)
                val diaryTpl = getPromptTemplate("diary")
                val dReplacements = mapOf(
                    "OPERATOR_NAME" to op.name,
                    "OPERATOR_PERSONA" to (op.privatePrompt.ifBlank { op.description }),
                    "OPERATOR_GENDER" to (op.gender.ifBlank { "" }),
                    "CURRENT_DATE" to todayDisplay,
                    "YESTERDAY_DATE" to yesterdayDisplay,
                    "DIARY_MIN_CHARS" to settings.diaryMinChars.toString(),
                    "DIARY_MAX_CHARS" to settings.diaryMaxChars.toString(),
                    "USER_NAME" to profile.nickname,
                    "USER_GENDER" to profile.gender.ifBlank { "" },
                    "USER_BIO" to profile.bio,
                    "USER_RELATION" to (op.userRelation.ifBlank { "未知" }),
                    "LONG_TERM_IMPRESSION" to (repository.getLongTermImpression(operatorId)?.content ?: "无"),
                    "DAILY_SUMMARY" to (repository.getLatestDaily()?.content ?: "无"),
                    "PRIVATE_DAILY_SUMMARY" to (repository.getLatestPrivateDaily(operatorId)?.content ?: "无"),
                    "PRIVATE_SUMMARY" to (repository.getPrivateChatSummary(operatorId)?.take(200) ?: "无"),
                    "GROUP_SUMMARIES" to groupSummaries,
                    "RECENT_MEMORIES" to recentMemories,
                    "SOURCE_AWARE_MEMORIES" to sourceAwareMemories,
                    "WORLD_DAY_EVENTS" to worldEvents,
                    "DIARY_EVENT_DIGEST" to unconsumedEvents,
                    "UNRESOLVED_THOUGHTS" to unconsumedEvents,
                    "SELF_STATUS_CHANGES" to "${op.name}最近在${op.location}，正在${op.activity}，情绪${op.emotion}",
                    "SOCIAL_INTERACTIONS" to worldEvents,
                    "KNOWN_FROM_CONTEXT" to sourceAwareMemories,
                    "SOURCE_AWARE_RULES" to sharedUtils.sourceAwareUsageRule(MemorySurface.DIARY),
                    "RELATION_EVENTS" to sharedUtils.getRelationEvents(operatorId).lines().filter { it.isNotBlank() }.take(settings.diaryRelationEventCount).joinToString("\n").ifBlank { "无" }
                )
                sharedUtils.logMemoryContext(
                    surface = "diary",
                    title = "${op.name}/$operatorId",
                    placeholders = mapOf(
                        "LONG_TERM_IMPRESSION" to dReplacements["LONG_TERM_IMPRESSION"].orEmpty(),
                        "DAILY_SUMMARY" to dReplacements["DAILY_SUMMARY"].orEmpty(),
                        "PRIVATE_DAILY_SUMMARY" to dReplacements["PRIVATE_DAILY_SUMMARY"].orEmpty(),
                        "PRIVATE_SUMMARY" to dReplacements["PRIVATE_SUMMARY"].orEmpty(),
                        "GROUP_SUMMARIES" to dReplacements["GROUP_SUMMARIES"].orEmpty(),
                        "RECENT_MEMORIES" to dReplacements["RECENT_MEMORIES"].orEmpty(),
                        "SOURCE_AWARE_MEMORIES" to dReplacements["SOURCE_AWARE_MEMORIES"].orEmpty(),
                        "RELATION_EVENTS" to dReplacements["RELATION_EVENTS"].orEmpty()
                    ),
                    extra = mapOf(
                        "user" to profile.nickname,
                        "date" to yesterdayStr,
                        "diaryAnchorCount" to settings.diaryAnchorCount.toString(),
                        "diaryGroupSummaryCount" to settings.diaryGroupSummaryCount.toString(),
                        "diaryRelationEventCount" to settings.diaryRelationEventCount.toString()
                    )
                )
                val prompt = sharedUtils.applyTemplate(diaryTpl, dReplacements)
                val text = withTimeout(25_000) { sharedUtils.chat(listOf(AiMessage("system", prompt))) }.trim()
                sharedUtils.trackTokens("diary", prompt, text)
                if (text.isNotBlank()) {
                    val now = System.currentTimeMillis()
                    repository.insertDiary(Diary(operatorId = operatorId, operatorName = op.name, content = text, date = yesterdayStr, createdAt = now))
                    addWorldLog("${op.name}写下了昨日记事", "日记已生成，进入干员日记可查看。", "diary")
                    repository.insertWorldEvent(WorldEvent(
                        type = WorldEventType.DIARY_WRITTEN,
                        actorId = operatorId,
                        actorName = op.name,
                        source = "diary",
                        sourceId = yesterdayStr,
                        content = "${op.name}写下了${yesterdayStr}的日记：${text.take(120)}",
                        createdAt = now,
                        expiresAt = MemoryPolicy.memoryExpiresAt(settings)
                    ))
                    repository.saveAnchor(AnchorSourcePolicy.buildAnchor(
                        source = AnchorSourcePolicy.DIARY,
                        sourceName = "自己的日记",
                        sourceActor = op.name,
                        sourceTarget = op.name,
                        operatorId = operatorId,
                        type = AnchorType.EVENT,
                        content = "日记记录：${text.take(70)}",
                        importance = AnchorSourcePolicy.STRONG,
                        sessionId = "diary_${System.currentTimeMillis()}",
                        createdAt = System.currentTimeMillis(),
                        expiresAt = MemoryPolicy.anchorExpiresAt(settings, AnchorType.EVENT)
                    ))
                    if (unconsumedEvents != "无") {
                        sharedUtils.buildUnconsumedEventContextForOperator(operatorId, op.name, "diary:$operatorId", settings.eventContextCount, markConsumed = true)
                    }
                    DebugLogger.log("Diary", "日记生成成功: ${text.take(50)}")
                    return text
                } else { DebugLogger.log("Diary", "日记生成为空"); return "" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLogger.log("Diary", "日记生成异常: ${e.message?.take(100)}")
                return ""
            }
    }

    // === AI 人设生成 ===
    data class OperatorPromptResult(
        val title: String = "",
        val gender: String = "",
        val description: String = "",
        val privatePrompt: String = "",
        val groupPrompt: String = ""
    )

    private fun parseOperatorPromptResult(raw: String): OperatorPromptResult {
        val cleaned = sharedUtils.aiService.cleanJson(raw)
        try {
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(cleaned).jsonObject
            fun j(n: String) = obj[n]?.jsonPrimitive?.content?.trim() ?: ""
            return OperatorPromptResult(title = j("title"), gender = j("gender"),
                description = j("description"), privatePrompt = j("privatePrompt"), groupPrompt = j("groupPrompt"))
        } catch (_: Exception) {}
        fun extr(n: String) = Regex("\"$n\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(cleaned)?.groupValues?.getOrNull(1)?.replace("\\\"", "\"")?.trim() ?: ""
        val r = OperatorPromptResult(title = extr("title"), gender = extr("gender"),
            description = extr("description"), privatePrompt = extr("privatePrompt"), groupPrompt = extr("groupPrompt"))
        if (r.privatePrompt.isBlank() && raw.isNotBlank()) return r.copy(privatePrompt = raw.trim())
        return r
    }

    fun generateOperatorPrompt(requirement: String, existingPrompt: String, onResult: (OperatorPromptResult) -> Unit) {
        viewModelScope.launch {
            try {
                val existingBlock = if (existingPrompt.isNotBlank()) "\n【现有的人设文本（请在此基础上升级优化，保留核心设定）】\n$existingPrompt\n" else ""
                val prompt = """
【任务】
你是一位角色设定专家。根据用户的需求为角色设计完整的人设信息。
生成的内容将用于 AI 角色扮演对话系统，直接影响角色在私聊和群聊中的表现质量。

【用户需求】
$requirement
$existingBlock

【输出要求 · 五个字段】

1. title（身份/称号）：
   简短有力，一句话点明角色定位。例如："退役骑士"、"天才研究员"、"街头艺人"

2. gender（性别）：
   男 / 女 / 其他

3. description（一句话简介）：
   20~50字，概括角色核心特点，用做角色列表中的摘要展示

4. privatePrompt（私聊人设—核心字段）：
   至少500汉字，直接影响私聊时 AI 的表现质量。不达到此字数请继续补充。
   必须包含以下维度：
   · 角色身份与背景：职业、来历、当前状态
   · 性格特质：用具体的行为描述替代抽象标签。不说"性格温柔"，说"说话轻声细语，从不打断别人"
   · 说话风格：语速快慢、常用语气词、语气倾向（直率/含蓄/幽默/冷峻）
   · 行为习惯：标志性的小动作（如思考时敲桌子、紧张时摸耳垂）
   · 与用户的关系基调：亲近/疏离/尊敬/调侃
   · 情绪倾向：容易紧张/永远淡定/情绪外露/喜怒不形于色
   用第二人称「你」来写，描述用户扮演该角色时需要注意什么。

5. groupPrompt（群聊人设）：
   不超过300汉字，侧重该角色在群聊中的社交风格：活跃还是旁观、容易成为话题中心还是存在感薄弱、对其他成员的态度。超出此字数请精简。

【质量要求】
- 人设要有"可演性"——读完后能想象出这个人说话的样子
- 避免通用模板：不要写"性格开朗活泼"、"乐于助人"
- 用具体、可感知的特征代替抽象形容词

【JSON格式】
{"title":"","gender":"","description":"","privatePrompt":"","groupPrompt":""}
直接输出JSON对象，不加额外文字。
""".trimIndent()
                val text = withTimeout(30_000) { sharedUtils.chat(listOf(AiMessage("system", prompt))) }.trim()
                val result = parseOperatorPromptResult(text)
                if (result.privatePrompt.isNotBlank()) {
                    DebugLogger.log("GenPrompt", "人设生成成功: title=${result.title}")
                    onResult(result)
                } else {
                    DebugLogger.log("GenPrompt", "人设为空，降级返回原文: ${text.take(100)}")
                    onResult(OperatorPromptResult(privatePrompt = text))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLogger.log("GenPrompt/ERROR", "生成失败: ${e.message}")
                onResult(OperatorPromptResult())
            }
        }
    }
}

