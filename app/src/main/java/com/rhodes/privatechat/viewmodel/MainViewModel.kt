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
import com.rhodes.privatechat.shared.network.AIService
import com.rhodes.privatechat.shared.model.ChatMessage
import com.rhodes.privatechat.shared.model.ChatSession
import com.rhodes.privatechat.shared.model.DispatchRecord
import com.rhodes.privatechat.shared.model.MemoryAnchor
import com.rhodes.privatechat.shared.model.Memory
import com.rhodes.privatechat.shared.model.MemoryType
import com.rhodes.privatechat.shared.model.ImpressionResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import com.rhodes.privatechat.shared.model.Diary
import com.rhodes.privatechat.shared.model.MomentComment
import com.rhodes.privatechat.shared.model.Moment
import com.rhodes.privatechat.shared.model.MomentLike
import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.util.DebugLogger
import com.rhodes.privatechat.shared.data.SenderCount
import com.rhodes.privatechat.shared.data.BfsNode
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.viewmodel.shared.OperatorStateUpdater
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.shared.PromptTemplates
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.rhodes.privatechat.viewmodel.shared.UserProfile
import com.rhodes.privatechat.shared.model.AnalysisResult
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.shared.model.OfflineModeResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

private val json = Json { ignoreUnknownKeys = true }

data class MomentGenerateStatus(val running: Boolean = false, val msg: String = "")

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
        onRefreshOperatorStatus = { refreshAllOperatorStatus() }
    )
    private val sessionMessageCounter = mutableMapOf<String, Int>()
    val momentsViewModel = MomentsViewModel(repository, settings, appState, viewModelScope) { getUserProfile() }
    val dispatchViewModel = DispatchViewModel(repository, settings, sharedUtils, operatorStateUpdater, appState, viewModelScope, { refreshAllOperatorStatus() }) { getUserProfile() }
    val groupChatViewModel = GroupChatViewModel(repository, settings, sharedUtils, appState, { chatViewModel.markSessionRead(it) }, { unhideSession(it) }, { getUserProfile() }, { t, m -> chatViewModel.getPromptTemplate(t, m) }, { s, msgs -> chatViewModel.generateShortTermSummary(s, msgs) }, sessionMessageCounter)

    private val _momentGenerateStatus: MutableStateFlow<MomentGenerateStatus> get() = _globalMomentStatus
    val momentGenerateStatus: StateFlow<MomentGenerateStatus> = _momentGenerateStatus.asStateFlow()

    // Chat state delegates to ChatViewModel
    private val _selectedOperator get() = chatViewModel.selectedOperator
    val selectedOperator: StateFlow<Operator?> get() = chatViewModel.selectedOperator
    private val _currentSession get() = chatViewModel.currentSession
    val currentSession: StateFlow<ChatSession?> get() = chatViewModel.currentSession
    private val _messages get() = chatViewModel.messages
    val messages: StateFlow<List<ChatMessage>> get() = chatViewModel.messages
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
    private val lastProactiveMsgTime = mutableMapOf<String, Long>()
    private var modeTransitionNotice = ""

    private var messagesJob: kotlinx.coroutines.Job? = null

    // Group chat state delegates to GroupChatViewModel
    val groupMessages: StateFlow<List<ChatMessage>> get() = groupChatViewModel.groupMessages
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
        sessionMessageCounter.keys.removeAll { it !in activeIds }
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
                val groupIds = listOf("group_elite", "group_penguin", "group_lungmen", "group_rhine", "group_sui")
                val hidden = settings.hiddenIds.toMutableSet()
                hidden.addAll(groupIds)
                settings.hiddenIds = hidden
                settings.putBoolean("initial_hidden_done", true)
            }
            cleanupExpired()
            settings.dispatchFastMode = false
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

    fun createMahjongAnchor(content: String) = mahjongViewModel.createMahjongAnchor(content)

    fun postMahjongMoment(content: String) = mahjongViewModel.postMahjongMoment(content)

    private fun startAutoStatusRefresh() {
        viewModelScope.launch {
            refreshAllOperatorStatus()
            while (true) {
                kotlinx.coroutines.delay(3_600_000) // 每小时
                refreshAllOperatorStatus()
                checkAndTriggerProactiveMessages()
            }
        }
    }

    /** 干员主动私聊：筛选候选 → 随机选 0-2 人 → 错峰发送 */
    private suspend fun checkAndTriggerProactiveMessages() {
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
            val lastSent = lastProactiveMsgTime[op.id] ?: 0L
            (now - maxOf(lastUserOrSession, lastSent)) >= 2 * 3_600_000
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
                sendProactiveMessage(op)
            }
        }
    }

    private suspend fun sendProactiveMessage(op: Operator) {
        if (getApiKey().isBlank()) return
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
        val replacements = mapOf(
            "CURRENT_TIME" to now,
            "USER_NAME" to profile.nickname,
            "USER_GENDER" to profile.gender.ifBlank { "未知" },
            "USER_BIO" to profile.bio.ifBlank { "无" },
            "USER_CONTENT" to "(用户没有说话)",
            "AI_ANALYSIS" to analysisBlock,
            "HYPNOSIS" to "",
            "MIND_READ" to "",
            "OPERATOR_NAME" to op.name,
            "OPERATOR_TITLE" to (if (op.title.isNullOrBlank()) "" else "（${op.title}）"),
            "OPERATOR_PERSONA" to (op.privatePrompt.ifBlank { op.description }),
            "CURRENT_LOCATION" to op.location,
            "CURRENT_STATE" to op.activity,
            "CURRENT_EMOTION" to op.emotion,
            "LONG_TERM_IMPRESSION" to (longTerm?.content ?: "暂无"),
            "MEMORY_ANCHORS" to pickAnchors(anchors, 5).joinToString("\n") { "- ${anchorTimeLabel(it)} ${it.content}" }.ifBlank { "暂无特别事件" },
            "SHARED_MEMORIES" to sharedMemories.ifBlank { "无" },
            "DAILY_SUMMARY" to (repository.getLatestDaily()?.content ?: "无"),
            "SHORT_TERM_SUMMARY" to (shortTerm?.content ?: "无"),
            "NEARBY_OPERATORS" to nearby.joinToString("\n") { "- ${it.name}正在${it.location}${it.activity}，${it.emotion}" }.ifBlank { "" },
            "USER_RELATION" to (op.userRelation.ifBlank { "未知" }),
            "NAR_SEG_MIN" to intPref("nar_seg_min", 1).toString(),
            "NAR_SEG_MAX" to intPref("nar_seg_max", 3).toString(),
            "NAR_MIN" to settings.narMin.toString(),
            "NAR_MAX" to settings.narMax.toString(),
            "DIA_SEG_MIN" to intPref("dia_seg_min", 1).toString(),
            "DIA_SEG_MAX" to intPref("dia_seg_max", 3).toString(),
            "DIA_MIN" to intPref("dia_min", 10).toString(),
            "DIA_MAX" to settings.diaMax.toString(),
            "SEG_MIN" to (intPref("nar_seg_min", 1) + intPref("dia_seg_min", 1)).toString(),
            "SEG_MAX" to (intPref("nar_seg_max", 3) + intPref("dia_seg_max", 3)).toString(),
            "TRANSITION_NOTICE" to "",
            "GROUP_CONTEXT" to ""
        )
        val prompt = applyTemplate(getPromptTemplate("private", "online"), replacements)
        try {
            val sb = StringBuilder()
            withTimeout(60_000) { sb.append(chat(listOf(AiMessage("system", prompt)))) }
            val raw = sharedUtils.aiService.cleanJson(sb.toString().trim())
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
                lastProactiveMsgTime[op.id] = System.currentTimeMillis()
                val parsed = sharedUtils.aiService.parseOfflineResponse(raw)
                if (parsed.emotion.isNotBlank() || parsed.location.isNotBlank() || parsed.state.isNotBlank()) {
                    updateOperatorStatus(op.id, parsed.location, parsed.state, parsed.emotion)
                }
            }
        } catch (_: Exception) { }
    }

    private suspend fun refreshAllOperatorStatus() {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val allOps = _operators.value.filter { it.id != "amiya" || true } // 阿米娅同其他干员一致处理

        // 深夜强制
        if (hour in 22..23 || hour in 0..4) {
            for (op in allOps) { repository.updateOperator(op.copy(location = "宿舍", activity = "睡觉", emotion = "安静")) }
            return
        }

        // 6时段权重
        val periodWeights: Map<String, Int> = when {
            hour in 5..7 -> mapOf("训练场" to 40, "宿舍" to 30, "食堂" to 15, "舰桥" to 5, "机库" to 5, "医疗部" to 5)
            hour in 8..11 -> mapOf("训练场" to 40, "舰桥" to 20, "机库" to 15, "医疗部" to 10, "食堂" to 10, "宿舍" to 5)
            hour in 12..13 -> mapOf("食堂" to 40, "宿舍" to 20, "活动室" to 10, "医疗部" to 10, "舰桥" to 10, "训练场" to 10)
            hour in 14..17 -> mapOf("训练场" to 35, "舰桥" to 20, "机库" to 15, "医疗部" to 15, "食堂" to 10, "宿舍" to 5)
            else -> mapOf("活动室" to 30, "食堂" to 25, "宿舍" to 15, "舰桥" to 10, "训练场" to 10, "医疗部" to 10)
        }

        // 活动池
        val activities = mapOf(
            "宿舍" to listOf("整理装备", "写日记", "发呆", "做俯卧撑", "听广播", "保养武器", "午睡"),
            "训练场" to listOf("负重跑", "格斗练习", "靶场射击", "战术推演", "指导新人", "体能测试", "模拟对战"),
            "医疗部" to listOf("例行体检", "配药", "照顾病患", "研究病例", "打扫诊室", "整理档案"),
            "食堂" to listOf("吃饭", "帮厨", "清洗餐具", "研究新菜谱", "搬运食材", "泡咖啡"),
            "舰桥" to listOf("监测航线", "值班瞭望", "写报告", "调试通讯", "护送访客", "开会"),
            "机库" to listOf("检修车辆", "改装装备", "清点物资", "搬运货物", "焊接练习", "保养无人机"),
            "活动室" to listOf("下棋", "打牌", "弹吉他", "看录像", "聊天", "做手工", "打台球")
        )

        // 情绪权重
        val emotionWeights = listOf(
            "平静" to 40, "疲惫" to 15, "专注" to 10, "愉快" to 10,
            "有些低落" to 10, "小兴奋" to 5, "生气" to 5, "焦虑" to 5
        )

        var locCount = mutableMapOf<String, Int>()

        for (op in allOps) {
            // 加权随机选位置
            val totalW = periodWeights.values.sum()
            var r = (Math.random() * totalW).toInt()
            var loc = "宿舍"
            for ((l, w) in periodWeights) { r -= w; if (r < 0) { loc = l; break } }
            val cnt = locCount.getOrDefault(loc, 0)
            if (cnt >= 10) loc = "宿舍"
            locCount[loc] = cnt + 1

            val acts = activities[loc] ?: listOf("休息")
            val activity = acts.random()

            // 加权随机选情绪（30%概率刷新）
            val emotion = if (Math.random() < 0.3) {
                val etw = emotionWeights.sumOf { it.second }
                var er = (Math.random() * etw).toInt()
                var emo = "平静"
                for ((e, w) in emotionWeights) { er -= w; if (er < 0) { emo = e; break } }
                emo
            } else op.emotion

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
        android.util.Log.d("MomentGen", "** [DBG] ** 自动生成开始")
        // 清理旧版残留的过量动态（保留 7 天内的）
        val allNow = repository.getAllMomentsSync()
        android.util.Log.d("MomentGen", "** [DBG] ** 当前动态总数=${allNow.size}")
        val weekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val oldMoments = allNow.filter { it.createdAt < weekAgo }
        if (oldMoments.size > 10) {
            repository.deleteOldMoments(weekAgo)
            android.util.Log.d("MomentGen", "** [DBG] ** 清理了 ${oldMoments.size} 条 7 天前的动态")
        }
        if (!autoGenerating.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                val dateKey = beijingSdf("yyyyMMdd").format(java.util.Date())
                val target = intPref("daily_moment_target", 2)
                val permCount = _operators.value.count { settings.getOperatorDynPermission(it.id) }
                android.util.Log.d("MomentGen", "** [DBG] ** target=$target 有权限=$permCount 总干员=${_operators.value.size}")
                if (target <= 0) return@launch
                generateAllMoments(target, dateKey) { /* silent */ }
                // 清理 7 天前的计数
                val weekAgo = beijingSdf("yyyyMMdd").format(java.util.Date(System.currentTimeMillis() - 7 * 86400000L))
                for (op in _operators.value) {
                    settings.removeMomentCount(op.id, weekAgo)
                }
            } finally {
                autoGenerating.set(false)
            }
        }
    }

    fun findOperatorByName(name: String): com.rhodes.privatechat.shared.model.Operator? =
        sessionViewModel.findOperatorByName(name)

    fun selectOperator(operator: Operator) = chatViewModel.selectOperator(operator)

    fun clearSelection() = chatViewModel.clearSelection()

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
        appState.clearChatListOnly()
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
        val balance = settings.lmb
        if (balance < PROP_PRICE) return "余额不足"
        settings.lmb = balance - PROP_PRICE
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
  - type：锚点类型。event=事件，preference=偏好，plan=约定，emotion=情感，taboo=禁忌，relation=干员间互动
  - content：具体内容，30字内
  - isPrivate：涉及用户负面情绪、私密情感、自我怀疑时设为true；正面评价、公开约定、普通事件设为false

隐私标记规则：
- 必须设为true：用户负面情绪、个人隐私、"别告诉别人"的内容
- 可设为false：正面评价、公开约定、一般偏好、干员间公开互动

对话内容：
""".trimIndent()
        val messages = listOf(
            AiMessage("system", prompt),
            AiMessage("user", conversationText)
        )
        try {
            var result = ""
            result = chat(messages, "Memory")
            trackTokens("memory", prompt, result)
            val parsed = sharedUtils.aiService.parseSummaryResponse(result)
            repository.saveMemory(Memory(
                sessionId = session.id, operatorId = session.operatorId,
                type = MemoryType.SHORT_TERM, content = parsed.summary,
                keywords = parsed.keywords.joinToString(","),
                expiresAt = System.currentTimeMillis() + intPref("clean_days", 30) * 86_400_000L
            ))
            val anchors = parsed.anchors.map { a ->
                MemoryAnchor(
                    sessionId = session.id, operatorId = session.operatorId,
                    type = try { com.rhodes.privatechat.shared.model.AnchorType.valueOf(a.type.uppercase()) } catch (_: Exception) { com.rhodes.privatechat.shared.model.AnchorType.EVENT },
                    content = a.content, isPrivate = a.isPrivate,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = System.currentTimeMillis() + intPref("clean_days", 30) * 86_400_000L
                )
            }
            repository.saveAnchors(anchors)
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

    private fun trackTokens(category: String, prompt: String, response: String) =
        sharedUtils.trackTokens(category, prompt, response)

    private fun relationshipDebugLabel(type: com.rhodes.privatechat.shared.model.RelationshipType): String =
        sharedUtils.relationshipDebugLabel(type)

    private fun anchorTimeLabel(anchor: com.rhodes.privatechat.shared.model.MemoryAnchor): String =
        sharedUtils.anchorTimeLabel(anchor)

    private fun pickAnchors(anchors: List<com.rhodes.privatechat.shared.model.MemoryAnchor>, maxCount: Int = 5): List<com.rhodes.privatechat.shared.model.MemoryAnchor> =
        sharedUtils.pickAnchors(anchors, maxCount)

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
                     onComplete: () -> Unit = {}) =
        operatorViewModel.saveOperator(id, name, title, description, privatePrompt, groupPrompt, userRelation, avatarUri, autoPost, allowChat, relationships, activityLevel, onComplete)

    fun loadRelationships(operatorId: String, callback: (List<com.rhodes.privatechat.shared.model.Relationship>) -> Unit) =
        operatorViewModel.loadRelationships(operatorId, callback)

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

    fun deleteGroup(groupSessionId: String) = groupChatViewModel.deleteGroup(groupSessionId)

    fun isAutoGroupChatEnabled(groupId: String): Boolean = groupChatViewModel.isAutoGroupChatEnabled(groupId)
    fun setAutoGroupChatEnabled(groupId: String, enabled: Boolean) = groupChatViewModel.setAutoGroupChatEnabled(groupId, enabled)
    fun resetAutoGroupChatTimer(groupId: String) = groupChatViewModel.resetAutoGroupChatTimer(groupId)
    fun stopAutoGroupChat(groupId: String) = groupChatViewModel.stopAutoGroupChat(groupId)
    fun stopAllAutoGroupChats() = groupChatViewModel.stopAllAutoGroupChats()
    fun refreshAutoGroupChats() = groupChatViewModel.refreshAutoGroupChats()

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

    fun sendGroupMessage(groupSessionId: String, groupName: String, text: String, mode: String = "online", autoSpeak: Boolean = false, isAuto: Boolean = false) =
        groupChatViewModel.sendGroupMessage(groupSessionId, groupName, text, mode, autoSpeak, isAuto)
    fun generateAllMoments(target: Int = 1, dateKey: String = "", force: Boolean = false, onProgress: (String) -> Unit = {}) {
        val isAuto = dateKey.isNotBlank()
        val today = dateKey.ifBlank { beijingSdf("yyyyMMdd").format(java.util.Date()) }
        // 全天 9 个时段，从清晨到深夜
        val allSlots = listOf(
            6 to "清晨", 8 to "上午", 10 to "上午", 12 to "中午",
            14 to "下午", 16 to "下午", 18 to "傍晚", 20 to "晚上", 22 to "深夜"
        )
        val currentHour = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
            .get(java.util.Calendar.HOUR_OF_DAY)
        viewModelScope.launch {
            for (op in _operators.value) {
                val allowDyn = settings.getOperatorDynPermission(op.id)
                if (!allowDyn && !force) continue
                val startIdx = if (isAuto) {
                    val d = settings.getMomentCount(op.id, today)
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
                    onProgress("发布中...")
                    try {
                        val profile = getUserProfile()
                        val impression = repository.getLongTermImpression(op.id)?.content ?: "无"
                        val chatSummary = repository.getShortTermMemory("session_${op.id}")?.content?.take(100) ?: "无"
                        val memories = pickAnchors(repository.getPublicAnchors(op.id), 3).joinToString("\n") { "- ${anchorTimeLabel(it)} ${it.content}" }.ifBlank { "无" }
                        val existingPosts = repository.getMomentsPaged(10, 0).filter { it.operatorId == op.id }
                        val recentPosts = existingPosts.take(3).joinToString("\n") { "- ${it.content.take(50)}" }.ifBlank { "无" }
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
                        val mmtReplacements = mapOf(
                            "OPERATOR_NAME" to op.name, "OPERATOR_PERSONA" to op.privatePrompt.ifBlank { op.description },
                            "TIME_OF_DAY" to timeOfDay, "LONG_TERM_IMPRESSION" to impression,
                            "RECENT_CHAT_SUMMARY" to chatSummary, "RECENT_MEMORIES" to memories,
                            "RECENT_POSTS" to recentPosts,
                            "RECENT_DAILY_SUMMARY" to (repository.getLatestPrivateDaily(op.id)?.content ?: "无"),
                            "CURRENT_DATE" to beijingSdf("yyyy年MM月dd日").format(fakeTs),
                            "USER_NAME" to profile.nickname,
"MOMENT_MIN_CHARS" to settings.momentMinChars.toString(),
                    "MOMENT_MAX_CHARS" to settings.momentMaxChars.toString()
                        )
                        val prompt = applyTemplate(mmtTpl, mmtReplacements)
                        val temp = intPref("ai_temperature", 95).toDouble() / 100.0
                        var momentResult = ""
                        repeat(3) { attempt ->
                            try {
                                momentResult = ""
                                momentResult = withTimeout(15_000) { chat(listOf(AiMessage("system", prompt)), "Moment") }
                                if (momentResult.isNotBlank()) return@repeat
                            } catch (_: Exception) { if (attempt < 2) delay((1000L * (attempt + 1))) }
                        }
                        trackTokens("moment", prompt, momentResult)
                        val content = cleanAiOutput(momentResult)
                        if (content.isNotBlank()) {
                            if (isAuto) settings.putMomentCount(op.id, today, generated + 1)
                            val moment = Moment(operatorId = op.id, operatorName = op.name, content = content, createdAt = fakeTs)
                            val momentId = repository.insertMoment(moment)
                            refreshMomentsNow()
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
                                            if (cc.isNotBlank()) { repository.insertComment(MomentComment(momentId = momentId, operatorId = commenter.id, operatorName = commenter.name, content = cc, createdAt = System.currentTimeMillis())); actualComments++ }
                                        } catch (_: Exception) {}
                                    }
                                    repository.updateCommentCount(momentId, actualComments)
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
                val momentId = generateOneForOpSync(op)
                if (momentId != null) {
                    generateLikesAndComments(momentId, op)
                    refreshMomentsNow()
                }
            } finally {
                onProgress("", true)
            }
        }
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
    private suspend fun generateOneForOpSync(op: Operator): Long? {
        return try {
            val profile = getUserProfile()
            val impression = repository.getLongTermImpression(op.id)?.content ?: "无"
            val chatSummary = repository.getShortTermMemory("session_${op.id}")?.content?.take(100) ?: "无"
            val memories = pickAnchors(repository.getPublicAnchors(op.id), 3).joinToString("\n") { "- ${anchorTimeLabel(it)} ${it.content}" }.ifBlank { "无" }
            val existingPosts = repository.getMomentsPaged(10, 0).filter { it.operatorId == op.id }
            val recentPosts = existingPosts.take(3).joinToString("\n") { "- ${it.content.take(50)}" }.ifBlank { "无" }
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
            val timeOfDay = getTimeOfDay(cal.get(java.util.Calendar.HOUR_OF_DAY))
            val fakeTs = System.currentTimeMillis()
            val mmtTpl = getPromptTemplate("moment")
            val mmtReplacements = mapOf(
                "OPERATOR_NAME" to op.name, "OPERATOR_PERSONA" to op.privatePrompt.ifBlank { op.description },
                "TIME_OF_DAY" to timeOfDay, "LONG_TERM_IMPRESSION" to impression,
                "RECENT_CHAT_SUMMARY" to chatSummary, "RECENT_MEMORIES" to memories,
                "RECENT_POSTS" to recentPosts,
                "RECENT_DAILY_SUMMARY" to (repository.getLatestPrivateDaily(op.id)?.content ?: "无"),
                "CURRENT_DATE" to beijingSdf("yyyy年MM月dd日").format(fakeTs),
                "USER_NAME" to profile.nickname,
                "MOMENT_MIN_CHARS" to settings.momentMinChars.toString(),
                "MOMENT_MAX_CHARS" to settings.momentMaxChars.toString()
            )
            val prompt = applyTemplate(mmtTpl, mmtReplacements)
            var momentResult = ""
            repeat(3) { attempt ->
                try {
                    momentResult = ""
                    momentResult = withTimeout(15_000) { chat(listOf(AiMessage("system", prompt)), "Moment") }
                    if (momentResult.isNotBlank()) return@repeat
                } catch (_: Exception) { if (attempt < 2) delay((1000L * (attempt + 1))) }
            }
            trackTokens("moment", prompt, momentResult)
            var content = cleanAiOutput(momentResult)
            if (content.isNotBlank()) {
                val moment = Moment(operatorId = op.id, operatorName = op.name, content = content, createdAt = fakeTs)
                val momentId = repository.insertMoment(moment)
                momentId
            } else null
        } catch (_: Exception) { null }
    }

    /** 异步生成点赞和评论（不阻塞调用方） */
    private fun generateLikesAndComments(momentId: Long, op: Operator) {
        appScope.launch {
            try {
                val profile = getUserProfile()
                val opId = op.id
                val moment = repository.getMoment(momentId)
                val postContent = moment?.content ?: ""
                val likers = _operators.value.filter { it.id != opId && it.name != profile.nickname }.shuffled().take((3..8).random())
                likers.forEach { liker -> repository.insertLike(MomentLike(momentId = momentId, operatorId = liker.id, operatorName = liker.name, createdAt = System.currentTimeMillis())) }
                repository.updateLikeCount(momentId, likers.size)
                DebugLogger.log("Moment", "生成点赞完成: momentId=$momentId, count=${likers.size}")
                val commenters = _operators.value.filter { it.id != opId && it.name != profile.nickname }.shuffled().take((1..3).random())
                val cmtTpl = getPromptTemplate("moment_comment")
                var actualComments = 0
                commenters.forEach { commenter ->
                    try {
                        val cmtReplacements = mapOf(
                            "COMMENTER_NAME" to commenter.name, "COMMENTER_PERSONA" to (commenter.groupPrompt.ifBlank { commenter.description }),
                            "POST_CONTENT" to postContent, "COMMENT_MIN_CHARS" to intPref("comment_min_chars", 10).toString(),
                            "COMMENT_MAX_CHARS" to intPref("comment_max_chars", 40).toString()
                        )
                        val cp = applyTemplate(cmtTpl, cmtReplacements)
                        val cc = withTimeout(8_000) { chat(listOf(AiMessage("system", cp)), "Moment") }.trim()
                        trackTokens("moment", cp, cc)
                        if (cc.isNotBlank()) { repository.insertComment(MomentComment(momentId = momentId, operatorId = commenter.id, operatorName = commenter.name, content = cc, createdAt = System.currentTimeMillis())); actualComments++ }
                    } catch (e: Exception) {
                        DebugLogger.log("Moment", "评论生成失败: ${e.message?.take(100)}")
                    }
                }
                repository.updateCommentCount(momentId, actualComments)
                DebugLogger.log("Moment", "生成评论完成: momentId=$momentId, count=$actualComments")
            } catch (e: Exception) {
                DebugLogger.log("Moment", "生成点赞评论异常: ${e.message?.take(100)}")
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
            repository.insertComment(MomentComment(momentId = momentId, operatorId = operatorId, operatorName = operatorName, content = cleanContent, parentCommentId = rootParentId, replyToName = replyToName, createdAt = System.currentTimeMillis()))
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
                        repository.saveAnchor(com.rhodes.privatechat.shared.model.MemoryAnchor(
                            sessionId = "anchor_${System.currentTimeMillis()}",
                            operatorId = realOp.id,
                            type = com.rhodes.privatechat.shared.model.AnchorType.EVENT,
                            content = "${getUserProfile().nickname}${targetName}：${content.take(30)}",
                            isPrivate = false,
                            createdAt = System.currentTimeMillis(),
                            expiresAt = System.currentTimeMillis() + 86_400_000L
                        ))
                    }
                }
            }

            if (operatorId != "user") return@launch
            val moment = _moments.value.find { it.id == momentId } ?: return@launch
            val userName = getUserProfile().nickname

            val alreadyReplied = mutableSetOf<String>()
            if (parentCommentId > 0 && replyToName.isNotBlank() && replyToName.trim() != moment.operatorName.trim() && replyToName.trim() != userName.trim()) {
                triggerSingleAiReply(momentId, replyToName, content, rootParentId, userName)
                alreadyReplied.add(replyToName)
                delay((1500L + (Math.random() * 1500).toLong()))
            }

            if (moment.operatorName != "我" && moment.operatorName.trim() != userName.trim() && moment.operatorName !in alreadyReplied) {
                triggerSingleAiReply(momentId, moment.operatorName, content, rootParentId, userName, "你是${moment.operatorName}。用户${userName}在你的动态下评论了：「${content}」。请用10-50字自然回复。只输出回复内容本身，不要加任何前缀如「回复xxx」或冒号。直接输出纯文本。")
                alreadyReplied.add(moment.operatorName)
                delay((1500L + (Math.random() * 1500).toLong()))
            }

            val bystanders = _operators.value
                .map { it.name }
                .filter { it !in alreadyReplied && it != "我" && it != userName }
                .shuffled()
                .take(1 + (Math.random() * 2).toInt())
            for (bystander in bystanders) {
                val bp = "你是${bystander}。你刚看到${moment.operatorName}的动态下，用户${userName}评论了「${content}」。请用10-40字凑热闹式地回复这条评论（看戏、调侃、起哄风格）。直接输出纯文本。"
                triggerSingleAiReply(momentId, bystander, content, rootParentId, userName, bp)
                delay((1500L + (Math.random() * 1500).toLong()))
            }
        }
    }

    private fun triggerSingleAiReply(momentId: Long, speakerName: String, userContent: String, parentCommentId: Long, userName: String, customPrompt: String? = null) {
        viewModelScope.launch {
            try {
                val prompt = customPrompt ?: "你是${speakerName}。用户扮演的角色${userName}刚刚回复了你的评论，说：「${userContent}」。请用10-50字自然回复。只输出回复内容本身，不要加任何前缀如「回复xxx」或冒号。直接输出纯文本。注意：你是${speakerName}，不是${userName}，不要替${userName}说话。"
                val reply = withTimeout(10_000) { chat(listOf(AiMessage("system", prompt))) }.trim()
                if (reply.isNotBlank()) {
                    val realOp = _operators.value.find { it.name == speakerName || it.id == speakerName }
                    val realId = realOp?.id ?: speakerName
                    repository.insertComment(MomentComment(momentId = momentId, operatorId = realId, operatorName = speakerName, content = reply, parentCommentId = parentCommentId, replyToName = userName, createdAt = System.currentTimeMillis()))
                }
            } catch (_: Exception) {}
        }
    }



    fun postUserMoment(content: String, mentionedOps: List<String>) {
        DebugLogger.log("Moment", "用户发动态: content=${content.take(50)}, mentioned=$mentionedOps")
        viewModelScope.launch {
            val profile = getUserProfile()
            val userName = profile.nickname
            val moment = Moment(operatorId = "user", operatorName = userName, content = content, isUserPost = true, mentionedOperatorIds = mentionedOps.joinToString(","), createdAt = System.currentTimeMillis())
            val momentId = repository.insertMoment(moment)
            DebugLogger.log("Moment/DB", "动态已写入DB, id=$momentId")
            refreshMomentsNow()
            // 创建动态锚点（仅动态发布者自己 + 随机3个围观干员）
            val anchorOps = _operators.value.filter { it.id != "user" }.shuffled().take(3).map { it.id }
            for (opId in anchorOps.distinct()) {
                repository.saveAnchor(com.rhodes.privatechat.shared.model.MemoryAnchor(
                    sessionId = "anchor_${System.currentTimeMillis()}",
                    operatorId = opId,
                    type = com.rhodes.privatechat.shared.model.AnchorType.EVENT,
                    content = "${userName}发布了动态：${content.take(40)}",
                    isPrivate = false,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = System.currentTimeMillis() + intPref("clean_days", 30) * 86_400_000L
                ))
            }

            // AI auto-replies: 异步生成，不阻塞动态显示
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

    // === Moments delegation ===
    fun getMomentBadge(): Int = momentsViewModel.getMomentBadge()
    fun getUnreadCommentCount(): Int = momentsViewModel.getUnreadCommentCount()
    suspend fun getUnreadCommentCountSuspend(): Int = momentsViewModel.getUnreadCommentCountSuspend()
    fun markMomentsSeen() = momentsViewModel.markMomentsSeen()
    fun loadInboxComments(callback: (List<MomentComment>) -> Unit) = momentsViewModel.loadInboxComments(callback)
    fun markAllCommentsRead() = momentsViewModel.markAllCommentsRead()
    fun markCommentRead(commentId: Long) = momentsViewModel.markCommentRead(commentId)

    // === Data delegation ===
    suspend fun getDataStats(): DataViewModel.DataStats = dataViewModel.getDataStats(_operators.value.size, _moments.value.size)
    fun cleanupAllExpired() = dataViewModel.cleanupAllExpired()
    suspend fun getMessageRanking(): List<SenderCount> = dataViewModel.getMessageRanking()
    suspend fun getDailyRanking(): List<SenderCount> = dataViewModel.getDailyRanking()
    fun forceGenerateMoments() {
        if (!forceGenerating.compareAndSet(false, true)) {
            android.util.Log.w("MainVM", "正在生成中，跳过强制生成")
            return
        }
        DebugLogger.log("Moment", "手动催发开始")
        _momentGenerateStatus.value = MomentGenerateStatus(running = true, msg = "开始生成...")
        appScope.launch {
            try {
                var generated = 0
                val candidates = _operators.value.filter {
                    settings.getOperatorDynPermission(it.id)
                }.shuffled().take(5)
                DebugLogger.log("Moment", "催发候选干员: ${candidates.size}人")
                android.util.Log.d("MainVM", "** [DBG] ** forceGenerateMoments: candidates=${candidates.size} VM=${System.identityHashCode(this)}")
                for (op in candidates) {
                    _momentGenerateStatus.value = MomentGenerateStatus(running = true, msg = "${op.name}发布中...")
                    val momentId = generateOneForOpSync(op)
                    if (momentId != null) {
                        generated++
                        generateLikesAndComments(momentId, op)
                        refreshMomentsNow()
                    }
                }
                _momentGenerateStatus.value = MomentGenerateStatus(
                    running = false,
                    msg = if (generated > 0) "生成完成（${generated}条）" else "无可用干员"
                )
                refreshMomentsNow()
            } finally {
                forceGenerating.set(false)
            }
        }
    }

    fun refreshMomentsNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val fresh = repository.getAllMomentsSync()
            appState.refreshMoments(fresh)
        }
    }
    suspend fun getAllImpressions(): List<Memory> = dataViewModel.getAllImpressions()
    suspend fun deleteAllImpressions() = dataViewModel.deleteAllImpressions()
    fun generateDiary(operatorId: String, onResult: (String) -> Unit) {
        DebugLogger.log("Diary", "偷看日记: operatorId=$operatorId")
        viewModelScope.launch {
            val op = repository.getOperator(operatorId) ?: run { DebugLogger.log("Diary", "干员不存在: $operatorId"); onResult(""); return@launch }
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

                // 防重复：昨天已生成则直接返回已有内容
                val existing = repository.getDiary(operatorId, yesterdayStr)
                if (existing != null) {
                    onResult(existing.content)
                    return@launch
                }

                val groupSummaries = _allSessions.value.filter { session ->
                    session.operatorId.startsWith("group_") && session.members.split(",").map { it.trim() }.any { it == operatorId || it == op.name }
                }
                    .mapNotNull { repository.getShortTermMemory(it.id)?.content?.let { c -> "- ${it.operatorName}：${c.take(80)}" } }
                    .joinToString("\n").ifBlank { "无" }
                val recentMemories = repository.getAnchors(operatorId).filter { it.type == com.rhodes.privatechat.shared.model.AnchorType.EVENT }
                    .take(3).joinToString("\n") { "- ${it.content}" }.ifBlank { "无" }
                val diaryTpl = getPromptTemplate("diary")
                val dReplacements = mapOf(
                    "OPERATOR_NAME" to op.name,
                    "OPERATOR_PERSONA" to (op.privatePrompt.ifBlank { op.description }),
                    "CURRENT_DATE" to todayDisplay,
                    "YESTERDAY_DATE" to yesterdayDisplay,
                    "DIARY_MIN_CHARS" to settings.diaryMinChars.toString(),
                    "DIARY_MAX_CHARS" to settings.diaryMaxChars.toString(),
                    "USER_NAME" to profile.nickname,
                    "USER_BIO" to profile.bio,
                    "USER_RELATION" to (op.userRelation.ifBlank { "未知" }),
                    "LONG_TERM_IMPRESSION" to (repository.getLongTermImpression(operatorId)?.content ?: "无"),
                    "DAILY_SUMMARY" to (repository.getLatestDaily()?.content ?: "无"),
                    "PRIVATE_DAILY_SUMMARY" to (repository.getLatestPrivateDaily(operatorId)?.content ?: "无"),
                    "PRIVATE_SUMMARY" to (repository.getPrivateChatSummary(operatorId)?.take(200) ?: "无"),
                    "GROUP_SUMMARIES" to groupSummaries,
                    "RECENT_MEMORIES" to recentMemories,
                    "RELATION_EVENTS" to sharedUtils.getRelationEvents(operatorId)
                )
                val prompt = sharedUtils.applyTemplate(diaryTpl, dReplacements)
                val text = withTimeout(25_000) { sharedUtils.chat(listOf(AiMessage("system", prompt))) }.trim()
                sharedUtils.trackTokens("diary", prompt, text)
                if (text.isNotBlank()) {
                    repository.insertDiary(Diary(operatorId = operatorId, operatorName = op.name, content = text, date = yesterdayStr))
                    for (observer in _operators.value.filter { it.id != operatorId }.shuffled().take(3)) {
                        repository.saveAnchor(MemoryAnchor(
                            sessionId = "anchor_${System.currentTimeMillis()}",
                            operatorId = observer.id, type = com.rhodes.privatechat.shared.model.AnchorType.EVENT,
                            content = "${op.name}今天写了日记，似乎提到了${profile.nickname}", isPrivate = false,
                            createdAt = System.currentTimeMillis(),
                            expiresAt = System.currentTimeMillis() + intPref("clean_days", 30) * 86_400_000L
                        ))
                    }
                    DebugLogger.log("Diary", "日记生成成功: ${text.take(50)}")
                    onResult(text)
                } else { DebugLogger.log("Diary", "日记生成为空"); onResult("") }
            } catch (e: Exception) {
                DebugLogger.log("Diary", "日记生成异常: ${e.message?.take(100)}")
                onResult("")
            }
        }
    }
}

