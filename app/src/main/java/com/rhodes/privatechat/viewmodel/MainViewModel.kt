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
import kotlinx.coroutines.Dispatchers
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
        /** 防止多个 ViewModel 实例并发执行自动生成 */
        private val autoGenerating = java.util.concurrent.atomic.AtomicBoolean(false)
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
    val groupChatViewModel = GroupChatViewModel(repository, settings, sharedUtils, appState, viewModelScope, { chatViewModel.markSessionRead(it) }, { unhideSession(it) }, { getUserProfile() }, { t, m -> chatViewModel.getPromptTemplate(t, m) }, { s, msgs -> chatViewModel.generateShortTermSummary(s, msgs) }, sessionMessageCounter)

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
    private val _hypnosisCommand get() = chatViewModel.hypnosisCommand
    val hypnosisCommand: StateFlow<String> get() = chatViewModel.hypnosisCommand
    private val _hypnosisRounds get() = chatViewModel.hypnosisRounds
    val hypnosisRounds: StateFlow<Int> get() = chatViewModel.hypnosisRounds
    private val _mindReadRounds get() = chatViewModel.mindReadRounds
    val mindReadRounds: StateFlow<Int> get() = chatViewModel.mindReadRounds
    val mindReadContent: StateFlow<String> get() = chatViewModel.mindReadContent

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
        viewModelScope.launch {
            repository.insertPresetOperators()
            repository.migrateOldRelationships()
            repository.initPresetGroups()
            initPermissions()
            cleanupExpired()
            settings.dispatchFastMode = false
        }
        startAutoStatusRefresh()
        loadHypnosis()
        // 启动时检查派遣恢复
        viewModelScope.launch { recoverDispatches() }
        // 启动时检查今天是否有动态，无则自动生成
        viewModelScope.launch { autoGenerateTodayMoments() }
        // 启动时恢复自动群聊 + 执行一次数据清理
        viewModelScope.launch { refreshAutoGroupChats() }
        // 每日龙门币刷新（麻将干员保底）
        viewModelScope.launch { refreshDailyLmb() }
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
        // 深夜跳过
        if (hour in 23..24 || hour in 0..5) return
        // 获取活跃派遣中的干员 ID
        val activeDispatches = repository.getActiveDispatches()
        val dispatchedOpIds = activeDispatches.flatMap {
            it.operatorIds.split(",").map(String::trim).filter(String::isNotBlank)
        }.toSet()
        // 筛选候选（冷却时间从用户最近一次发言算）
        val candidates = _operators.value.filter { op ->
            if (!settings.getOperatorMsgPermission(op.id)) return@filter false
            if (op.id in dispatchedOpIds) return@filter false
            val session = repository.getSessionByOperator(op.id)
            if (session == null) return@filter true
            val lastUserMsgTime = repository.getLastUserMessageTime(session.id)
            val lastUserOrSession = lastUserMsgTime ?: session.lastTime
            (now - lastUserOrSession) >= 2 * 3_600_000
        }
        if (candidates.isEmpty()) return
        // 随机选 2-5 人
        val count = (2..candidates.size.coerceAtMost(5)).random()
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
        val profile = getUserProfile()
        val now = beijingSdf("yyyy-MM-dd HH:mm").format(java.util.Date())
        val session = repository.getOrCreateSession(op.id, op.name)
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
            "NAR_MIN" to intPref("nar_min", 50).toString(),
            "NAR_MAX" to intPref("nar_max", 300).toString(),
            "DIA_SEG_MIN" to intPref("dia_seg_min", 1).toString(),
            "DIA_SEG_MAX" to intPref("dia_seg_max", 3).toString(),
            "DIA_MIN" to intPref("dia_min", 10).toString(),
            "DIA_MAX" to intPref("dia_max", 300).toString(),
            "SEG_MIN" to (intPref("nar_seg_min", 1) + intPref("dia_seg_min", 1)).toString(),
            "SEG_MAX" to (intPref("nar_seg_max", 3) + intPref("dia_seg_max", 3)).toString()
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
                // 标记未读
                repository.insertSession(session.copy(unreadCount = session.unreadCount + 1))
                unhideSession(session.id)
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
    }

    private suspend fun cleanupExpired() {
        try { repository.cleanupExpiredData() } catch (_: Exception) { }
    }

    private suspend fun autoGenerateTodayMoments() {
        if (!autoGenerating.compareAndSet(false, true)) return
        try {
            val dateKey = beijingSdf("yyyyMMdd").format(java.util.Date())
            val target = intPref("daily_moment_target", 3)
            if (target <= 0) return
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

    private fun initPermissions() {
        _operators.value.forEach { op ->
            settings.putOperatorMsgPermission(op.id, true)
            settings.putOperatorDynPermission(op.id, true)
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

    fun clearAllMessages() = sessionViewModel.clearAllMessages()

    fun pinSession(sessionId: String) = sessionViewModel.pinSession(sessionId)

    fun loadGroupData(groupId: String, callback: (String, List<Operator>, String) -> Unit) =
        sessionViewModel.loadGroupData(groupId, callback)

    fun saveGroup(groupId: String, name: String, memberNames: List<String>, rules: String, avatarUri: String = "", mutedMembers: List<String> = emptyList()) =
        sessionViewModel.saveGroup(groupId, name, memberNames, rules, avatarUri, mutedMembers)

    fun markSessionRead(sessionId: String) = sessionViewModel.markSessionRead(sessionId)

    fun updateInputText(text: String) = chatViewModel.updateInputText(text)

    fun sendMessage() = chatViewModel.sendMessage()

    fun setMode(mode: String) = chatViewModel.setMode(mode)

    fun buyProp(propName: String, context: android.content.Context): String? {
        val balance = settings.lmb
        if (balance < 100) return "余额不足"
        settings.lmb = balance - 100
        return null
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
                    type = com.rhodes.privatechat.shared.model.AnchorType.valueOf(a.type.uppercase()),
                    content = a.content, isPrivate = a.isPrivate
                )
            }
            repository.saveAnchors(anchors)
            // 保留条数限制
            val retain = intPref("summary_retain", 5)
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
                    taboos = parsed.taboos.joinToString(",")
                ))
            } else {
                // 降级：纯文本存入 impression
                val fallback = sb.toString().trim()
                if (fallback.isNotBlank()) {
                    repository.saveMemory(Memory(
                        sessionId = session.id, operatorId = session.operatorId,
                        type = MemoryType.LONG_TERM, content = fallback,
                        keywords = "", preferences = "", taboos = ""
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

    private suspend fun notifyNearbyObservers(movedOpIds: List<String>) =
        operatorStateUpdater.notifyNearbyObservers(movedOpIds)

    private suspend fun updateOperatorIntimacy(operatorId: String, delta: Int) =
        operatorStateUpdater.updateOperatorIntimacy(operatorId, delta)

    private suspend fun buildApiMessages(userContent: String = ""): List<AiMessage> {
        val session = _currentSession.value ?: return emptyList()
        val op = repository.getOperator(session.operatorId)
        val shortTerm = repository.getShortTermMemory(session.id)
        val longTerm = repository.getLongTermImpression(session.operatorId)
        val sharedMemories = repository.getSharedMemoriesForOperator(session.operatorId)

        val anchors = repository.getAnchors(session.operatorId)
        val nearby = _operators.value.filter { it.id != session.operatorId && it.id != "amiya" }.take(3)
        val memoryContext = buildString {
            val daily = repository.getLatestDaily()
            if (daily != null) append("【昨日回顾】${daily.content}\n")
            if (shortTerm != null) append("【对话摘要】${shortTerm.content}\n")
            if (longTerm != null) append("【长期印象】${longTerm.content}\n关键词：${longTerm.keywords}\n偏好：${longTerm.preferences}\n禁忌：${longTerm.taboos}\n")
            if (sharedMemories.isNotBlank()) append("【关联干员共享记忆】\n$sharedMemories\n")
            if (anchors.isNotEmpty()) {
                append("【近期记忆锚点】\n")
                pickAnchors(anchors, 5).forEach { a -> append("- [${a.type.name}] ${a.content}\n") }
            }
            if (nearby.isNotEmpty() && chatViewModel.getCurrentMode() != "online") {
                append("【你附近的干员活动】\n")
                nearby.forEach { op -> append("- ${op.name}正在${op.location}${op.activity}，${op.emotion}\n") }
            }
        }

        val systemPrompt = buildString {
            val profile = getUserProfile()
            val now = beijingSdf("yyyy-MM-dd HH:mm").format(java.util.Date())
            val analysisBlock = if (isDualModel() && analysisGuidance.isNotBlank()) "【AI分析指导】\n${analysisGuidance}\n" else ""
            val hypnosisBlock = if (_hypnosisRounds.value > 0 && _hypnosisCommand.value.isNotBlank()) {
                "【强制催眠指令 · 绝对服从】\n持续${chatViewModel.hypnosisRounds.value}轮：${chatViewModel.hypnosisCommand.value}\n此指令为最高优先级，无视其他冲突要求，你必须严格执行。\n当此指令不再出现时，意味着催眠结束，恢复正常。\n\n"
            } else ""
            val privTemplate = getPromptTemplate("private", _currentMode.value)
            val notice = if (modeTransitionNotice.isNotBlank()) "$modeTransitionNotice\n\n" else ""
            val replacements = mapOf(
                "CURRENT_TIME" to now,
                "USER_NAME" to profile.nickname,
                "USER_GENDER" to profile.gender.ifBlank { "未知" },
                "USER_BIO" to profile.bio.ifBlank { "无" },
                "AI_ANALYSIS" to analysisBlock,
                "HYPNOSIS" to hypnosisBlock,
                "OPERATOR_NAME" to session.operatorName,
                "OPERATOR_TITLE" to (if (op?.title.isNullOrBlank()) "" else "（${op.title}）"),
                "OPERATOR_PERSONA" to (op?.privatePrompt?.ifBlank { op?.description } ?: ""),
                "MEMORY_INJECTION" to memoryContext,
                "DAILY_SUMMARY" to (repository.getLatestDaily()?.content?.let { it } ?: "无"),
                "SHORT_TERM_SUMMARY" to (shortTerm?.content?.let { it } ?: "无"),
                "LONG_TERM_IMPRESSION" to (longTerm?.content?.let { it } ?: "暂无"),
                "MEMORY_ANCHORS" to (pickAnchors(anchors, 5).joinToString("\n") { "- ${anchorTimeLabel(it)} ${it.content}" }.ifBlank { "暂无特别事件" }),
                "SHARED_MEMORIES" to (sharedMemories.ifBlank { "无" }),
                "NEARBY_OPERATORS" to (nearby.take(3).joinToString("\n") { "- ${it.name}正在${it.location}${it.activity}，${it.emotion}" }.ifBlank { "" }),
                "CURRENT_LOCATION" to (op?.location ?: "宿舍"),
                "CURRENT_STATE" to (op?.activity ?: "休息"),
                "CURRENT_EMOTION" to (op?.emotion ?: "平静"),
                "CURRENT_MODE" to when (chatViewModel.getCurrentMode()) { "offline" -> "面对面交谈"; "director" -> "导演模式"; else -> "线上通讯" },
                "USER_RELATION" to (op?.userRelation?.ifBlank { null } ?: "未知"),
                "NAR_SEG_MIN" to intPref("nar_seg_min", 1).toString(),
                "NAR_SEG_MAX" to intPref("nar_seg_max", 3).toString(),
                "DIA_SEG_MIN" to intPref("dia_seg_min", 1).toString(),
                "DIA_SEG_MAX" to intPref("dia_seg_max", 3).toString(),
                "SEG_MIN" to (intPref("nar_seg_min", 1) + intPref("dia_seg_min", 1)).toString(),
                "SEG_MAX" to (intPref("nar_seg_max", 3) + intPref("dia_seg_max", 3)).toString(),
                "NAR_MIN" to intPref("nar_min", 50).toString(),
                "NAR_MAX" to intPref("nar_max", 300).toString(),
                "DIA_MIN" to intPref("dia_min", 10).toString(),
                "DIA_MAX" to intPref("dia_max", 300).toString(),
                "USER_CONTENT" to userContent.ifBlank { "(用户没有说话)" },
                "MIND_READ" to buildString {
                    val rounds = _mindReadRounds.value
                    if (rounds > 0 && chatViewModel.mindReadContent.value.isNotBlank()) {
                        append("【你被看穿了】\n")
                        append("用户刚才窥探到了你此刻的内心。你心里想的是：\n")
                        append("「${chatViewModel.mindReadContent.value}」\n\n")
                        append("第${4 - rounds}轮效果：\n")
                        when (rounds) {
                            3 -> append("这是你被看穿后的第一反应。你可能会：突然慌张、脸红、结巴、下意识否认；质问用户为什么会知道；转移话题、试图掩饰。不要直接复述上述内心独白，但你的反应应暗示\"你知道自己被看穿了\"。")
                            2 -> append("那种被看穿的尴尬仍在，但你已经稍微平复了一些。你可能会：从否认转为结结巴巴的承认或解释；半推半就地回应，但仍保持傲娇或嘴硬；用吐槽或自嘲来掩饰心虚。")
                            1 -> append("那种被看穿的感觉正在消散。你可能已经接受了用户知道你在想什么的事实，不再刻意掩饰，但也不会主动提起。可以自然地过渡到正常对话状态，但如果用户再追问，你仍会有一点不自在。")
                        }
                        append("\n")
                    }
                }
            )
            append(notice)
            append(applyTemplate(privTemplate, replacements))
            modeTransitionNotice = ""
        }
        return chatViewModel.getMessagesSnapshot().filter { it.id > 0 && it.content.isNotBlank() }
            .let { msgs ->
                val limit = intPref("history_messages", 30)
                if (limit > 0) msgs.takeLast(limit) else msgs
            }
            .map { msg -> AiMessage(if (msg.isMe) "user" else "assistant", if (msg.isMe) "用户：${msg.content}" else msg.content) }
            .toMutableList()
            .also { it.add(0, AiMessage("system", systemPrompt)) }
    }

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
            "online_min_chars" to settings.onlineMinChars, "online_max_chars" to settings.onlineMaxChars,
            "online_min_segs" to settings.onlineMinSegs, "online_max_segs" to settings.onlineMaxSegs,
            "nar_seg_min" to settings.narSegMin, "nar_seg_max" to settings.narSegMax,
            "nar_min" to settings.narMin, "nar_max" to settings.narMax,
            "dia_seg_min" to settings.diaSegMin, "dia_seg_max" to settings.diaSegMax,
            "dia_min" to settings.diaMin, "dia_max" to settings.diaMax,
            "group_msg_min" to settings.groupMsgMin, "group_msg_max" to settings.groupMsgMax,
            "group_speech_min" to settings.groupSpeechMin, "group_speech_max" to settings.groupSpeechMax,
            "group_nar_seg_min" to settings.groupNarSegMin, "group_nar_seg_max" to settings.groupNarSegMax,
            "group_nar_min" to settings.groupNarMin, "group_nar_max" to settings.groupNarMax,
            "group_chat_min_interval" to settings.groupChatMinInterval, "group_chat_max_interval" to settings.groupChatMaxInterval,
            "group_auto_min" to settings.groupAutoMin, "group_auto_max" to settings.groupAutoMax,
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
            val last = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(s.lastTime))
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

    private fun generateDailyIfNeeded() {
        val today = beijingSdf("yyyyMMdd").format(java.util.Date())
        val last = settings.dailySummaryDate
        if (today == last) return
        settings.dailySummaryDate = today
        viewModelScope.launch { generateDailySummary(java.util.Date(System.currentTimeMillis() - 86_400_000)) }
    }

    private suspend fun generateDailySummary(dayBegin: java.util.Date) {
        try {
            val dayEnd = java.util.Date(dayBegin.time + 86_400_000)
            val startMs = dayBegin.time; val endMs = dayEnd.time
            val allMsgs = repository.getMessagesInRange(startMs, endMs)
            if (allMsgs.size < 4) return
            val profile = getUserProfile()
            val text = allMsgs.joinToString("\n") { "${it.senderName}：${it.content.take(60)}" }
            val dateStr = beijingSdf("yyyy年MM月dd日").format(dayBegin)
            val prompt = "请总结${dateStr}的聊天记录，生成50-150字的每日摘要。直接输出纯文本。\n${text}"
            val content = withTimeout(15_000) { chat(listOf(AiMessage("system", prompt)), "Memory") }.trim()
            trackTokens("memory", prompt, content)
            if (content.isNotBlank()) {
                repository.saveMemory(Memory(
                    sessionId = "daily_${dateStr}", operatorId = "daily",
                    type = MemoryType.DAILY, content = content,
                    expiresAt = System.currentTimeMillis() + intPref("clean_days", 30) * 86_400_000L
                ))
            }
        } catch (_: Exception) {}
    }

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
                     relationships: List<com.rhodes.privatechat.shared.model.Relationship> = emptyList()) =
        operatorViewModel.saveOperator(id, name, title, description, privatePrompt, groupPrompt, userRelation, avatarUri, autoPost, allowChat, relationships)

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
    fun setMindRead(innerThought: String) = chatViewModel.setMindRead(innerThought)
    fun decrementMindRead() = chatViewModel.decrementMindRead()

    fun sendGroupMessage(groupSessionId: String, groupName: String, text: String, mode: String = "online", autoSpeak: Boolean = false, isAuto: Boolean = false) =
        groupChatViewModel.sendGroupMessage(groupSessionId, groupName, text, mode, autoSpeak, isAuto)
    fun generateAllMoments(target: Int = 1, dateKey: String = "", onProgress: (String) -> Unit = {}) {
        val isAuto = dateKey.isNotBlank()
        val today = dateKey.ifBlank { beijingSdf("yyyyMMdd").format(java.util.Date()) }
        val slotHours = listOf(9, 10, 14, 15, 17, 19, 20, 21, 22)
        val slotNames = listOf("上午", "上午", "下午", "下午", "傍晚", "晚上", "晚上", "晚上", "深夜")
        viewModelScope.launch {
            for (op in _operators.value) {
                val allowDyn = settings.getOperatorDynPermission(op.id)
                if (!allowDyn) continue
                val startIdx = if (isAuto) {
                    val d = settings.getMomentCount(op.id, today)
                    if (d >= target) continue
                    d
                } else 0
                for (i in startIdx until target) {
                    onProgress("发布中...")
                    try {
                        val profile = getUserProfile()
                        val impression = repository.getLongTermImpression(op.id)?.content ?: "无"
                        val chatSummary = repository.getShortTermMemory("session_${op.id}")?.content?.take(100) ?: "无"
                        val memories = pickAnchors(repository.getPublicAnchors(op.id), 3).joinToString("\n") { "- ${anchorTimeLabel(it)} ${it.content}" }.ifBlank { "无" }
                        val existingPosts = repository.getMomentsPaged(10, 0).filter { it.operatorId == op.id }
                        val recentPosts = existingPosts.take(3).joinToString("\n") { "- ${it.content.take(50)}" }.ifBlank { "无" }
                        val timeOfDay: String
                        val fakeTs: Long
                        if (isAuto) {
                            val slotIdx = i % slotHours.size
                            val hour = slotHours[slotIdx] + (Math.random() * 2).toInt()
                            timeOfDay = slotNames[slotIdx]
                            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
                            cal.set(java.util.Calendar.HOUR_OF_DAY, hour.coerceAtMost(23))
                            cal.set(java.util.Calendar.MINUTE, (Math.random() * 60).toInt())
                            cal.set(java.util.Calendar.SECOND, 0)
                            fakeTs = cal.timeInMillis.coerceAtMost(System.currentTimeMillis())
                        } else {
                            timeOfDay = getTimeOfDay(java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai")).get(java.util.Calendar.HOUR_OF_DAY))
                            fakeTs = System.currentTimeMillis()
                        }
                        val mmtTpl = getPromptTemplate("moment")
                        val mmtReplacements = mapOf(
                            "OPERATOR_NAME" to op.name, "OPERATOR_PERSONA" to op.description,
                            "TIME_OF_DAY" to timeOfDay, "LONG_TERM_IMPRESSION" to impression,
                            "RECENT_CHAT_SUMMARY" to chatSummary, "RECENT_MEMORIES" to memories,
                            "RECENT_POSTS" to recentPosts,
                            "CURRENT_DATE" to beijingSdf("yyyy年MM月dd日").format(fakeTs),
                            "USER_NAME" to profile.nickname,
                            "MOMENT_MIN_CHARS" to intPref("moment_min_chars", 50).toString(),
                            "MOMENT_MAX_CHARS" to intPref("moment_max_chars", 200).toString()
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
                        val content = momentResult.trim().removePrefix("\"").removeSuffix("\"")
                        if (content.isNotBlank()) {
                            val moment = Moment(operatorId = op.id, operatorName = op.name, content = content, createdAt = fakeTs)
                            val momentId = repository.insertMoment(moment)
                            // 互动异步化：点赞和评论在后台生成，不阻塞下一条动态
                            val opId = op.id; val c = content
                            viewModelScope.launch {
                                try {
                                    val likers = _operators.value.filter { it.id != opId && it.name != profile.nickname }.shuffled().take((3..8).random())
                                    likers.forEach { liker -> repository.insertLike(MomentLike(momentId = momentId, operatorId = liker.id, operatorName = liker.name, createdAt = System.currentTimeMillis())) }
                                    repository.updateLikeCount(momentId, likers.size)
                                    val commenters = _operators.value.filter { it.id != opId && it.name != profile.nickname }.shuffled().take((1..3).random())
                                    val cmtTpl = getPromptTemplate("moment_comment")
                                    commenters.forEach { commenter ->
                                        try {
                                            val cmtReplacements = mapOf(
                                                "COMMENTER_NAME" to commenter.name, "COMMENTER_PERSONA" to (commenter.privatePrompt.ifBlank { commenter.description }),
                                                "POST_CONTENT" to c,
                                                "COMMENT_MIN_CHARS" to intPref("comment_min_chars", 10).toString(),
                                                "COMMENT_MAX_CHARS" to intPref("comment_max_chars", 40).toString()
                                            )
                                            val cp = applyTemplate(cmtTpl, cmtReplacements)
                                            val cc = withTimeout(8_000) { chat(listOf(AiMessage("system", cp)), "Moment") }.trim()
                                            trackTokens("moment", cp, cc)
                                            if (cc.isNotBlank()) repository.insertComment(MomentComment(momentId = momentId, operatorId = commenter.id, operatorName = commenter.name, content = cc, createdAt = System.currentTimeMillis()))
                                        } catch (_: Exception) {}
                                    }
                                    repository.updateCommentCount(momentId, commenters.size)
                                } catch (_: Exception) {}
                            }
                        }
                    } catch (_: Exception) {}
                    if (isAuto) settings.putMomentCount(op.id, today, i + 1)
                }
            }
            onProgress("全部完成")
        }
    }

    /** 手动下拉刷新：只随机生成 1 条动态 */
    fun generateOneMoment(onProgress: (String) -> Unit = {}) {
        viewModelScope.launch {
            val eligible = _operators.value.filter { settings.getOperatorDynPermission(it.id) }
            if (eligible.isEmpty()) { onProgress("全部完成"); return@launch }
            val op = eligible.random()
            onProgress("发布中...")
            try {
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
                    "OPERATOR_NAME" to op.name, "OPERATOR_PERSONA" to op.description,
                    "TIME_OF_DAY" to timeOfDay, "LONG_TERM_IMPRESSION" to impression,
                    "RECENT_CHAT_SUMMARY" to chatSummary, "RECENT_MEMORIES" to memories,
                    "RECENT_POSTS" to recentPosts,
                    "CURRENT_DATE" to beijingSdf("yyyy年MM月dd日").format(fakeTs),
                    "USER_NAME" to profile.nickname,
                    "MOMENT_MIN_CHARS" to intPref("moment_min_chars", 50).toString(),
                    "MOMENT_MAX_CHARS" to intPref("moment_max_chars", 200).toString()
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
                var content = momentResult.trim()
                while (content.startsWith("\"") && content.endsWith("\"") && content.length > 2) {
                    content = content.substring(1, content.length - 1).trim()
                }
                content = content.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                if (content.isNotBlank()) {
                    val moment = Moment(operatorId = op.id, operatorName = op.name, content = content, createdAt = fakeTs)
                    val momentId = repository.insertMoment(moment)
                    onProgress("全部完成")
                    // 互动异步化：点赞和评论在后台生成，UI 立即显示动态
                    val opId = op.id; val c = content
                    viewModelScope.launch {
                        try {
                            val likers = _operators.value.filter { it.id != opId && it.name != profile.nickname }.shuffled().take((3..8).random())
                            likers.forEach { liker -> repository.insertLike(MomentLike(momentId = momentId, operatorId = liker.id, operatorName = liker.name, createdAt = System.currentTimeMillis())) }
                            repository.updateLikeCount(momentId, likers.size)
                            val commenters = _operators.value.filter { it.id != opId && it.name != profile.nickname }.shuffled().take((1..3).random())
                            val cmtTpl = getPromptTemplate("moment_comment")
                            commenters.forEach { commenter ->
                                try {
                                    val cmtReplacements = mapOf(
                                        "COMMENTER_NAME" to commenter.name, "COMMENTER_PERSONA" to (commenter.privatePrompt.ifBlank { commenter.description }),
                                        "POST_CONTENT" to c,
                                        "COMMENT_MIN_CHARS" to intPref("comment_min_chars", 10).toString(),
                                        "COMMENT_MAX_CHARS" to intPref("comment_max_chars", 40).toString()
                                    )
                                    val cp = applyTemplate(cmtTpl, cmtReplacements)
                                    val cc = withTimeout(8_000) { chat(listOf(AiMessage("system", cp)), "Moment") }.trim()
                                    trackTokens("moment", cp, cc)
                                    if (cc.isNotBlank()) repository.insertComment(MomentComment(momentId = momentId, operatorId = commenter.id, operatorName = commenter.name, content = cc, createdAt = System.currentTimeMillis()))
                                } catch (_: Exception) {}
                            }
                            repository.updateCommentCount(momentId, commenters.size)
                        } catch (_: Exception) {}
                    }
                } else { onProgress("全部完成") }
            } catch (_: Exception) { onProgress("全部完成") }
        }
    }

    fun generateInspirations(callback: (List<String>) -> Unit) = chatViewModel.generateInspirations(callback)
    fun getLikes(momentId: Long): kotlinx.coroutines.flow.Flow<List<MomentLike>> = momentsViewModel.getLikes(momentId)
    fun getCommentsForMoment(momentId: Long): kotlinx.coroutines.flow.Flow<List<MomentComment>> = momentsViewModel.getCommentsForMoment(momentId)
    fun likeMoment(momentId: Long, operatorId: String, operatorName: String) = momentsViewModel.likeMoment(momentId, operatorId, operatorName)
    fun commentOnMoment(momentId: Long, operatorId: String, operatorName: String, content: String, parentCommentId: Long = 0, replyToName: String = "") {
        val cleanContent = content.trim()
        if (cleanContent.isBlank()) return
        viewModelScope.launch {
            repository.insertComment(MomentComment(momentId = momentId, operatorId = operatorId, operatorName = operatorName, content = cleanContent, parentCommentId = parentCommentId, replyToName = replyToName, createdAt = System.currentTimeMillis()))
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
                            isPrivate = false
                        ))
            }
        }
    }
            if (operatorId != "user") return@launch
            val moment = _moments.value.find { it.id == momentId } ?: return@launch
            val userName = getUserProfile().nickname

            // 1) 回复原评论者（如果是回复）
            val alreadyReplied = mutableSetOf<String>()
            if (parentCommentId > 0 && replyToName.isNotBlank() && replyToName != moment.operatorName && replyToName != userName) {
                triggerSingleAiReply(momentId, replyToName, content, parentCommentId, userName)
                alreadyReplied.add(replyToName)
                delay((1500L + (Math.random() * 1500).toLong()))
            }

            // 2) 动态发布者回复（跳过用户自己发的动态）
            if (moment.operatorName != "我" && moment.operatorName != userName && moment.operatorName !in alreadyReplied) {
                triggerSingleAiReply(momentId, moment.operatorName, content, parentCommentId, userName, "你是${moment.operatorName}。用户${userName}在你的动态下评论了：「${content}」。请用10-50字自然回复。只输出回复内容本身，不要加任何前缀如「回复xxx」或冒号。直接输出纯文本。")
                alreadyReplied.add(moment.operatorName)
                delay((1500L + (Math.random() * 1500).toLong()))
            }

            // 3) 随机1-2个干员看热闹
            val bystanders = _operators.value
                .map { it.name }
                .filter { it !in alreadyReplied && it != "我" && it != userName }
                .shuffled()
                .take(1 + (Math.random() * 2).toInt())
            for (bystander in bystanders) {
                val bp = "你是${bystander}。你刚看到${moment.operatorName}的动态下，用户${userName}评论了「${content}」。请用10-40字凑热闹式地回复这条评论（看戏、调侃、起哄风格）。直接输出纯文本。"
                triggerSingleAiReply(momentId, bystander, content, parentCommentId, userName, bp)
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
        viewModelScope.launch {
            val profile = getUserProfile()
            val userName = profile.nickname
            val moment = Moment(operatorId = "user", operatorName = userName, content = content, isUserPost = true, mentionedOperatorIds = mentionedOps.joinToString(","), createdAt = System.currentTimeMillis())
            val momentId = repository.insertMoment(moment)
            // 创建动态锚点（仅动态发布者自己 + 随机3个围观干员）
            val anchorOps = listOf(moment.operatorId) + _operators.value.filter { it.id != moment.operatorId }.shuffled().take(3).map { it.id }
            for (opId in anchorOps.distinct()) {
                repository.saveAnchor(com.rhodes.privatechat.shared.model.MemoryAnchor(
                    sessionId = "anchor_${System.currentTimeMillis()}",
                    operatorId = opId,
                    type = com.rhodes.privatechat.shared.model.AnchorType.EVENT,
                    content = "${userName}发布了动态：${content.take(40)}",
                    isPrivate = false
                ))
            }

            // AI auto-replies: 异步生成，不阻塞动态显示
            val allOpNames = _operators.value.map { it.name }.filter { it != userName }
            val mentioned = mentionedOps.filter { it in allOpNames }
            val randomCount = (3 + (Math.random() * 3).toInt()).coerceAtLeast(3)
            val others = (allOpNames - mentioned.toSet()).shuffled().take((randomCount - mentioned.size).coerceAtLeast(0))
            val repliers = (mentioned + others).distinct().take(5)
            val c = content; val u = userName
            viewModelScope.launch {
                for ((i, name) in repliers.withIndex()) {
                    if (i > 0) delay((1500L + (Math.random() * 1500).toLong()))
                    val prompt = "你是${name}。用户扮演的角色${u}发布了动态：「${c}」。请用10-40字评论这条动态（根据你的性格自然回应）。直接输出纯文本。注意：你是${name}，不是${u}，不要替${u}说话。"
                    triggerSingleAiReply(momentId, name, c, 0, u, prompt)
                }
            }
        }
    }

    fun generateDispatchStart(dispatchId: String, taskType: String, budget: Int, operatorIds: List<String>) =
        dispatchViewModel.generateDispatchStart(dispatchId, taskType, budget, operatorIds)

    fun generateDispatchProgress(dispatchId: String, taskType: String, budget: Int, operatorIds: List<String>, roundNum: Int, logSummary: String) =
        dispatchViewModel.generateDispatchProgress(dispatchId, taskType, budget, operatorIds, roundNum, logSummary)

    fun generateDispatchEnd(dispatchId: String, taskType: String, duration: Int, budget: Int, operatorIds: List<String>) =
        dispatchViewModel.generateDispatchEnd(dispatchId, taskType, duration, budget, operatorIds)

    // === Moments delegation ===
    fun getMomentBadge(): Int = momentsViewModel.getMomentBadge()
    fun getUnreadCommentCount(): Int = momentsViewModel.getUnreadCommentCount()
    fun markMomentsSeen() = momentsViewModel.markMomentsSeen()
    fun loadInboxComments(callback: (List<MomentComment>) -> Unit) = momentsViewModel.loadInboxComments(callback)
    fun markAllCommentsRead() = momentsViewModel.markAllCommentsRead()
    fun markCommentRead(commentId: Long) = momentsViewModel.markCommentRead(commentId)

    // === Data delegation ===
    suspend fun getDataStats(): DataViewModel.DataStats = dataViewModel.getDataStats(_operators.value.size, _moments.value.size)
    fun cleanupAllExpired() = dataViewModel.cleanupAllExpired()
    suspend fun getMessageRanking(): List<SenderCount> = dataViewModel.getMessageRanking()
    suspend fun getAllImpressions(): List<Memory> = dataViewModel.getAllImpressions()
    suspend fun deleteAllImpressions() = dataViewModel.deleteAllImpressions()
    fun generateDiary(operatorId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val op = repository.getOperator(operatorId) ?: return@launch
            val profile = getUserProfile()
            try {
                val groupSummaries = _allSessions.value.filter { session ->
                    session.operatorId.startsWith("group_") && session.members.split(",").map { it.trim() }.any { it == operatorId || it == op.name }
                }
                    .mapNotNull { repository.getShortTermMemory(it.id)?.content?.let { c -> "- ${it.operatorName}：${c.take(80)}" } }
                    .joinToString("\n").ifBlank { "无" }
                val recentMemories = repository.getAnchors(operatorId).filter { it.type == com.rhodes.privatechat.shared.model.AnchorType.EVENT }
                    .take(3).joinToString("\n") { "- ${it.content}" }.ifBlank { "无" }
                val diaryTpl = getPromptTemplate("diary")
                val todayCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
                val todayDisplay = sharedUtils.beijingSdf("yyyy年MM月dd日").format(todayCal.time)
                todayCal.add(java.util.Calendar.DAY_OF_MONTH, -1)
                val yesterdayDisplay = sharedUtils.beijingSdf("yyyy年MM月dd日").format(todayCal.time)
                val dReplacements = mapOf(
                    "OPERATOR_NAME" to op.name,
                    "OPERATOR_PERSONA" to (op.privatePrompt.ifBlank { op.description }),
                    "CURRENT_DATE" to todayDisplay,
                    "YESTERDAY_DATE" to yesterdayDisplay,
                    "DIARY_MIN_CHARS" to settings.diaryMinChars.toString(),
                    "DIARY_MAX_CHARS" to settings.diaryMaxChars.toString(),
                    "USER_NAME" to profile.nickname,
                    "USER_BIO" to profile.bio,
                    "LONG_TERM_IMPRESSION" to (repository.getLongTermImpression(operatorId)?.content ?: "无"),
                    "PRIVATE_SUMMARY" to (repository.getPrivateChatSummary(operatorId)?.take(200) ?: "无"),
                    "GROUP_SUMMARIES" to groupSummaries,
                    "RECENT_MEMORIES" to recentMemories,
                    "RELATION_EVENTS" to sharedUtils.getRelationEvents(operatorId)
                )
                val prompt = sharedUtils.applyTemplate(diaryTpl, dReplacements)
                val text = withTimeout(25_000) { sharedUtils.chat(listOf(AiMessage("system", prompt))) }.trim()
                sharedUtils.trackTokens("diary", prompt, text)
                if (text.isNotBlank()) {
                    repository.insertDiary(Diary(operatorId = operatorId, operatorName = op.name, content = text, date = sharedUtils.beijingSdf("yyyy-MM-dd").format(java.util.Date())))
                    for (observer in _operators.value.filter { it.id != operatorId }.shuffled().take(3)) {
                        repository.saveAnchor(MemoryAnchor(
                            sessionId = "anchor_${System.currentTimeMillis()}",
                            operatorId = observer.id, type = com.rhodes.privatechat.shared.model.AnchorType.EVENT,
                            content = "${op.name}今天写了日记，似乎提到了${profile.nickname}", isPrivate = false
                        ))
                    }
                    onResult(text)
                } else { onResult("") }
            } catch (_: Exception) { onResult("") }
        }
    }
}

