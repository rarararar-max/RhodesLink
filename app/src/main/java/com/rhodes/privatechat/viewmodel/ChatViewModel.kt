package com.rhodes.privatechat.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rhodes.privatechat.shared.model.ChatMessage
import com.rhodes.privatechat.shared.model.ChatSession
import com.rhodes.privatechat.shared.model.AnchorType
import com.rhodes.privatechat.shared.model.MemoryAnchor
import com.rhodes.privatechat.shared.model.Memory
import com.rhodes.privatechat.shared.model.MemoryType
import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.memory.AnchorSourcePolicy
import com.rhodes.privatechat.shared.model.AnalysisResult
import com.rhodes.privatechat.shared.model.SuggestionResponse
import com.rhodes.privatechat.shared.model.UnifiedMemoryResponse
import com.rhodes.privatechat.shared.network.AIService
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.util.DebugLogger
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.viewmodel.shared.OperatorStateUpdater
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.shared.PromptTemplates
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.rhodes.privatechat.viewmodel.shared.UserProfile
import com.rhodes.privatechat.viewmodel.shared.MemoryPolicy
import com.rhodes.privatechat.viewmodel.shared.MemorySurface
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

private val json = Json { ignoreUnknownKeys = true }

class ChatViewModel(
    application: Application,
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    private val sharedUtils: SharedUtils,
    private val operatorStateUpdater: OperatorStateUpdater,
    private val appState: AppStateHolder,
    private val onShowToast: (String) -> Unit,
    private val onUnhideSession: suspend (String) -> Unit,
    private val onRefreshOperatorStatus: suspend () -> Unit
) : AndroidViewModel(application) {
    companion object {
        const val DEBUG = true
    }

    // === Chat state ===
    private val _selectedOperator = MutableStateFlow<Operator?>(null)
    val selectedOperator: StateFlow<Operator?> = _selectedOperator.asStateFlow()

    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _currentMode = MutableStateFlow("offline")
    val currentMode: StateFlow<String> = _currentMode.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _loadingSessions = MutableStateFlow<Set<String>>(emptySet())
    val isLoading: StateFlow<Boolean> = combine(_loadingSessions, _currentSession) { sessions, cur ->
        cur != null && cur.id in sessions
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Hypnosis / MindRead
    private val _hypnosisCommand = MutableStateFlow("")
    val hypnosisCommand: StateFlow<String> = _hypnosisCommand.asStateFlow()
    private val _hypnosisRounds = MutableStateFlow(0)
    val hypnosisRounds: StateFlow<Int> = _hypnosisRounds.asStateFlow()

    // Internal state
    private val shortTermThreshold: Int get() = settings.summaryThreshold
    private val chatAiMutexes = ConcurrentHashMap<String, Mutex>()
    private fun aiMutexFor(sessionId: String): Mutex = chatAiMutexes.computeIfAbsent(sessionId) { Mutex() }
    private val analysisGuidanceBySession = ConcurrentHashMap<String, String>()
    private var modeTransitionNotice = ""
    private var messagesJob: Job? = null
    private val chatAiJobs = ConcurrentHashMap<String, Job>()

    init {
        loadHypnosis()
    }

    fun updateSelectedOperator(op: Operator) {
        _selectedOperator.value = op
    }

    fun updateSelectedOperatorCopy(location: String, activity: String, emotion: String) {
        _selectedOperator.value = _selectedOperator.value?.copy(location = location, activity = activity, emotion = emotion)
    }

    fun updateMessageInList(msgId: Long, content: String) {
        _messages.value = _messages.value.map { if (it.id == msgId) it.copy(content = content) else it }
    }

    fun getMessagesSnapshot(): List<ChatMessage> = _messages.value
    fun getCurrentMode(): String = _currentMode.value

    fun getPromptTemplate(type: String, mode: String = ""): String {
        val key = if (mode.isNotBlank()) "prompt_${type}_${mode}" else "prompt_$type"
        return settings.getString(key, "")?.ifBlank { null } ?: PromptTemplates.get(type, mode)
    }

    suspend fun generateShortTermSummary(session: ChatSession, messageSource: List<ChatMessage>? = null) {
        try {
            val msgs = messageSource ?: repository.getMessagesSync(session.id)
            val retain = settings.summaryRetain.coerceAtLeast(1)
            val recent = msgs.takeLast(retain)
            val older = msgs.dropLast(retain)
            if (older.isEmpty()) {
                DebugLogger.log("Memory/Summary", "跳过短期摘要: session=${session.id}, totalMsgs=${msgs.size}, retain=$retain, older=0")
                return
            }
            val oldSummary = repository.getShortTermMemory(session.id)?.content?.takeIf { it.isNotBlank() } ?: "无"
            val text = older.joinToString("\n") { "${it.senderName}：${it.content.take(100)}" }
            DebugLogger.log("Memory/Summary", "开始短期摘要: session=${session.id}, operator=${session.operatorName}, totalMsgs=${msgs.size}, older=${older.size}, retain=$retain, oldSummary=${oldSummary != "无"}")
            val prompt = if (settings.unifiedMemoryEnabled) """
你是罗德岛的随行记录员，负责把角色真正会记住的事整理成可长期使用的记忆。

请融合“已有摘要”和“新增对话”，生成一份连续摘要、若干高价值记忆锚点，并判断是否需要更新长期印象。目标是让${session.operatorName}下次聊天时能自然想起具体事情，而不是背诵流水账。

输出JSON：{"summary":"50~200字摘要","anchors":[{"type":"event|preference|plan|emotion|taboo|relation","content":"具体内容","importance":"strong|medium|weak","sourceActor":"谁说的","sourceTarget":"指向谁","isPrivate":false}],"impression_update":{"should_update":false,"impression":"","keywords":[],"preferences":[],"taboos":[]}}

摘要规则：
- summary：50~200字，保留“谁说了什么、用户明确表达了什么、双方关系/情绪有什么变化、下次可以接上的话茬”。不要写成聊天记录列表。
- 如果已有摘要和新增对话冲突，以新增对话为准，改写旧理解，不要并列保留矛盾信息。

锚点规则：
- anchors：0~5个。有明确记忆价值才提取；寒暄、附和、短测试、乱码、单纯语气词不要硬凑。
- content：30字内，必须具体到“用户喜欢什么/约定了什么/发生了什么/谁对谁态度变化”，避免“聊得很开心”这种空泛句。
- type：event=具体事件，preference=用户偏好，plan=约定/待办，emotion=重要情绪，taboo=禁忌/边界，relation=关系变化。
- preference/taboo 只能记录用户明确表达的偏好和边界，不能把干员自己的习惯、职业、人设当作用户偏好。
- isPrivate=true：用户负面情绪、隐私、自我怀疑、亲密/暧昧内容、明确“不想让别人知道”的内容；普通日常、公开约定、轻松正向互动可为false。
- importance：strong=会影响后续互动的重要偏好/禁忌/承诺/关系变化；medium=近期可接话题；weak=普通小事。

长期印象规则：
- impression_update：只有用户多次表达或本轮出现明确强信号时才 should_update=true。
- impression 要像${session.operatorName}的主观看法，融合旧印象，不只复述本轮事件。
- 不要从短测试字符、数字、拼音或乱码推断长期人格。

禁止：
- content 和 summary 中禁止出现“好感度提升/下降”“affection”“系统数值”“锚点”“摘要”等系统机制词。

已有摘要：
${oldSummary}

新增对话：
${text}""" else """
你是罗德岛的随行记录员，负责把角色真正会记住的事整理成可长期使用的记忆。

请融合“已有摘要”和“新增对话”，生成一份连续的新摘要和高价值记忆锚点。目标是让${session.operatorName}下次聊天时能自然想起具体事情，而不是背诵流水账。

输出JSON：{"summary":"50~200字摘要","anchors":[{"type":"event|preference|plan|emotion|taboo|relation","content":"具体内容","isPrivate":false}]}

字段说明：
- summary：50~200字，重点保留“谁说了什么、用户明确表达了什么、双方关系/情绪有什么变化、下次可以接上的话茬”。如果旧摘要与新对话冲突，以新对话为准改写旧理解。
- anchors：0~5个关键信息锚点。有明确记忆价值才提取；寒暄、附和、短测试、乱码、单纯语气词不要硬凑。
  - type：锚点类型。event=事件，preference=用户偏好，plan=用户约定，emotion=用户情绪或重要互动情绪，taboo=用户禁忌，relation=关系变化
  - content：具体内容，30字内，必须具体到“用户喜欢什么/约定了什么/发生了什么/谁对谁态度变化”，避免“聊得很开心”这种空泛句
  - isPrivate：涉及用户负面情绪、私密情感、自我怀疑时设为true；正面评价、公开约定、普通事件设为false

提取边界：
- preference/taboo 只能记录用户的偏好和禁忌，不能记录干员自己的习惯、职业偏好或性格。
- 干员自身状态、工作偏好、被打断后的反应，应归为 event/emotion，不要归为 preference/taboo。
- content 中禁止出现“好感度提升/下降”“affection”“系统数值”等系统机制词。
- content 和 summary 中禁止出现“锚点”“摘要”“系统记录”等机制词。
- 用户只是输入短测试字符、数字、拼音或乱码时，不要推断为稳定人格，只能作为普通事件或直接忽略。

隐私标记规则：
- 必须设为true：用户负面情绪、个人隐私、"别告诉别人"的内容
- 可设为false：正面评价、公开约定、一般偏好、干员间公开互动、干员普通情绪反应

已有摘要：
${oldSummary}

新增对话：
${text}"""
            val rawResult = withTimeout(15_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Memory") }.trim()
            sharedUtils.trackTokens("memory", prompt, rawResult)
            val unified = if (settings.unifiedMemoryEnabled) try {
                json.decodeFromString<UnifiedMemoryResponse>(sharedUtils.aiService.cleanJson(rawResult))
            } catch (_: Exception) { null } else null
            val parsed = unified ?: sharedUtils.aiService.parseSummaryResponse(rawResult).let { legacy ->
                UnifiedMemoryResponse(
                    summary = legacy.summary,
                    keywords = legacy.keywords,
                    anchors = legacy.anchors
                )
            }
            if (parsed.summary.isNotBlank()) {
                val now = System.currentTimeMillis()
                DebugLogger.log("Memory/Summary", "短期摘要已生成: session=${session.id}, summaryLen=${parsed.summary.length}, anchors=${parsed.anchors.size}, preview=${parsed.summary.take(80)}")
                repository.saveMemory(Memory(
                    sessionId = session.id, operatorId = session.operatorId,
                    type = MemoryType.SHORT_TERM, content = parsed.summary,
                    keywords = parsed.keywords.joinToString(","),
                    createdAt = now,
                    expiresAt = MemoryPolicy.memoryExpiresAt(settings)
                ))
                if (parsed.anchors.isNotEmpty()) {
                    val anchors = parsed.anchors.mapNotNull { a ->
                        val type = try { AnchorType.valueOf(a.type.uppercase()) } catch (_: Exception) { AnchorType.EVENT }
                        val cleanedContent = sanitizeAnchorContent(a.content)
                        if (cleanedContent.isBlank()) return@mapNotNull null
                        val finalType = normalizeAnchorType(type, cleanedContent)
                        AnchorSourcePolicy.buildAnchor(
                            source = AnchorSourcePolicy.PRIVATE_CHAT,
                            sourceName = "与${session.operatorName}的私聊",
                            sourceActor = a.sourceActor.ifBlank { appState.userProfile.value.nickname },
                            sourceTarget = a.sourceTarget.ifBlank { session.operatorName },
                            operatorId = session.operatorId,
                            type = finalType,
                            content = cleanedContent,
                            importance = a.importance.ifBlank { if (a.isPrivate) AnchorSourcePolicy.STRONG else AnchorSourcePolicy.MEDIUM },
                            sessionId = session.id,
                            isPrivate = a.isPrivate,
                            createdAt = now,
                            expiresAt = MemoryPolicy.anchorExpiresAt(settings, finalType)
                        )
                    }
                    anchors.forEach { a ->
                        DebugLogger.log("Memory/Anchor", "摘要锚点: op=${a.operatorId}, type=${a.type}, private=${a.isPrivate}, content=${a.content.take(40)}")
                    }
                    repository.saveAnchors(anchors)
                }
                val impression = unified?.impression_update
                if (settings.autoImpressionUpdateEnabled && impression != null && impression.should_update && impression.impression.isNotBlank()) {
                    repository.saveMemory(Memory(
                        sessionId = session.id,
                        operatorId = session.operatorId,
                        type = MemoryType.LONG_TERM,
                        content = impression.impression,
                        keywords = impression.keywords.joinToString(","),
                        preferences = impression.preferences.joinToString(","),
                        taboos = impression.taboos.joinToString(","),
                        createdAt = now
                    ))
                    DebugLogger.log("Memory/Impression", "统一记忆已更新印象: op=${session.operatorId}, len=${impression.impression.length}")
                }
                repository.enforceMemoryRetain(session.id, settings.summaryRetain)
            }
        } catch (e: Exception) {
            DebugLogger.log("Memory/Summary", "短期摘要生成失败: ${e.message?.take(120)}")
        }
    }

    // === Public API ===

    fun selectOperator(operator: Operator) {
        // 保存当前干员的催眠/读心状态
        val prevOp = _selectedOperator.value
        if (prevOp != null) {
            settings.putString("hypnosis_cmd_${prevOp.id}", _hypnosisCommand.value)
            settings.putInt("hypnosis_round_${prevOp.id}", _hypnosisRounds.value)
        }
        _selectedOperator.value = operator
        // 恢复新干员的催眠状态
        _hypnosisCommand.value = settings.getString("hypnosis_cmd_${operator.id}", "")
        _hypnosisRounds.value = settings.getInt("hypnosis_round_${operator.id}", 0)
        settings.hypnosisCmd = _hypnosisCommand.value
        settings.hypnosisRound = _hypnosisRounds.value
        viewModelScope.launch {
            val session = repository.getOrCreateSession(operator.id, operator.name, operator.avatarUri)
            _currentSession.value = session
            val savedMode = settings.getLastMode(operator.id)
            _currentMode.value = savedMode
            markSessionRead(session.id)
            messagesJob?.cancel()
            messagesJob = viewModelScope.launch {
                repository.getRecentMessages(session.id).collect { msgs -> _messages.value = msgs }
            }
        }
    }

    suspend fun selectOperatorSync(operator: Operator) {
        DebugLogger.log("RHODES_CRASH", "selectOperatorSync: 开始 operator=${operator.id} name=${operator.name}")
        try {
            val prevOp = _selectedOperator.value
            if (prevOp != null) {
                settings.putString("hypnosis_cmd_${prevOp.id}", _hypnosisCommand.value)
                settings.putInt("hypnosis_round_${prevOp.id}", _hypnosisRounds.value)
            }
            _selectedOperator.value = operator
            _hypnosisCommand.value = settings.getString("hypnosis_cmd_${operator.id}", "")
            _hypnosisRounds.value = settings.getInt("hypnosis_round_${operator.id}", 0)
            settings.hypnosisCmd = _hypnosisCommand.value
            settings.hypnosisRound = _hypnosisRounds.value
            val session = repository.getOrCreateSession(operator.id, operator.name, operator.avatarUri)
            DebugLogger.log("RHODES_CRASH", "selectOperatorSync: session=${session?.id} operatorId=${session?.operatorId}")
            _currentSession.value = session
            val savedMode = settings.getLastMode(operator.id)
            _currentMode.value = savedMode
            markSessionRead(session.id)
            messagesJob?.cancel()
            messagesJob = viewModelScope.launch {
                try {
                    repository.getRecentMessages(session.id).collect { msgs ->
                        DebugLogger.log("RHODES_CRASH", "selectOperatorSync: messages收集到${msgs.size}条")
                        _messages.value = msgs.reversed()
                    }
                } catch (e: Exception) {
                    DebugLogger.log("RHODES_CRASH", "selectOperatorSync: messages收集异常: ${e.message}")
                }
            }
            DebugLogger.log("RHODES_CRASH", "selectOperatorSync: 完成")
        } catch (e: Exception) {
            DebugLogger.log("RHODES_CRASH", "selectOperatorSync: 整体异常: ${e.message}")
            _selectedOperator.value = operator
        }
    }

    fun clearSelection() {
        _selectedOperator.value = null
        _currentSession.value = null
        _messages.value = emptyList()
    }

    fun clearMessages() {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            repository.deleteSessionMessages(session.id)
            repository.deleteMemoriesBySession(session.id)
            repository.deleteAnchorsBySession(session.id)
            _messages.value = emptyList()
        }
    }

    fun updateInputText(text: String) { _inputText.value = text }

    fun setMode(mode: String) {
        val session = _currentSession.value ?: return
        val oldMode = _currentMode.value
        if (oldMode == mode) return
        _currentMode.value = mode
        viewModelScope.launch {
            repository.updateSessionMode(session.id, mode)
            modeTransitionNotice = when {
                oldMode == "online" && mode == "offline" -> "【系统通知：用户放下了通讯终端，走到了你的面前，现在你们面对面站在一起。】"
                oldMode == "offline" && mode == "online" -> "【系统通知：用户退后了几步，重新拿起通讯终端连接你，现在你们又回到远程通讯了。】"
                oldMode == "director" && mode == "offline" -> "【用户走近了你，站在你的身边。场景变得更近、更真实了。】"
                oldMode == "offline" && mode == "director" -> "【用户退后几步，场景的描述变得更丰富了。你继续按照眼前的场景推进。】"
                oldMode == "online" && mode == "director" -> "【通讯器的声音淡去，周围的场景逐渐变得清晰可见。你发现自己正身处一个新的场景中。】"
                oldMode == "director" && mode == "online" -> "【眼前的场景像雾气一样散去，你回到了罗德岛的走廊，通讯器里传来用户的声音。】"
                else -> "【系统通知：模式已切换。】"
            }
            settings.putLastMode(session.operatorId, mode)
        }
    }

    fun markSessionRead(sessionId: String) {
        viewModelScope.launch {
            val session = repository.getSession(sessionId) ?: return@launch
            repository.insertSession(session.copy(unreadCount = 0))
        }
    }

    fun sendMessage() {
        if (DEBUG) dumpDebugState()
        val text = _inputText.value.trim()
        val session = _currentSession.value ?: return
        if (text.isEmpty()) return
        if (sharedUtils.getApiKey().isBlank()) {
            onShowToast("请先在设置中配置 API Key")
            return
        }
        _inputText.value = ""
        generateDailyIfNeeded()

        // 取消该会话上一条正在处理的 AI
        chatAiJobs[session.id]?.cancel()
        val job = viewModelScope.launch {
            analysisGuidanceBySession[session.id] = ""
                val sessionCounter = settings.getSessionMessageCounter(session.id) + 1
                settings.putSessionMessageCounter(session.id, sessionCounter)
            val msgId = repository.getNextMessageId()
            val mode = _currentMode.value
            var aiMsgId = 0L
            var mutexLocked = false
            try {
                repository.sendMessage(session.id, ChatMessage(
                    id = msgId, sessionId = session.id,
                    senderName = "我", content = text, type = "text", mode = _currentMode.value, isMe = true
                ))
                DebugLogger.log("Chat/DB", "用户消息已写入, session=${session.id}, id=$msgId, text=${text.take(50)}")
                aiMsgId = repository.getNextMessageId()
                DebugLogger.log("Chat/DB", "AI消息ID已获取, aiMsgId=$aiMsgId")
                _loadingSessions.update { it + session.id }

                aiMutexFor(session.id).lock()
                mutexLocked = true

                if (settings.dualModel) {
                    analysisGuidanceBySession[session.id] = ""
                    try {
                        val profile = appState.userProfile.value
                        val recentDialogues = _messages.value.takeLast(6).joinToString("\n") { m -> "${if (m.isMe) "用户" else "你"}：${m.content}" }
                        val analysisPrompt = """你是罗德岛的资深心理顾问与战术分析员。你的唯一任务是分析对话并输出指定JSON。你只输出JSON，不参与任何对话。

【任务】
分析用户最新消息的深层意图、情绪和需求，并为干员的回应提供策略指导。

【思考流程】
1. 阅读最近对话，理解脉络
2. 分析用户最新消息的字面意思和潜在意图
3. 推断用户当前情绪状态
4. 判断用户最核心的情感/行动需求
5. 基于干员人设给出回复策略建议

【输出字段解释】
{
  "intent_analysis": "用户字面意思与深层意图综合分析，50字内",
  "user_emotion": "推断用户当前情绪状态，简洁自然描述",
  "user_need": "用户核心情感/行动需求，可组合描述",
  "suggested_emotion": "建议干员应表现的情绪，需贴合人设",
  "reply_guidance": "回复策略指导，60字内，具体可操作",
  "affection_mod": -2到2的整数，对用户的好感度即时波动
}

【内容规范】
- intent_analysis 必须包含表面和深层含义
- user_emotion 用生活化语言
- user_need 必须明确用户想要什么回应
- suggested_emotion 贴合具体干员人设
- reply_guidance 给出可操作策略
- affection_mod 必须是整数，综合判断用户态度

【质量强化】
- 结合对话历史判断当前发言是常态还是异常
- 注意反话、撒娇等间接表达
- 考虑聊天模式：线上更直接，面对面可能有更多暗示

【边界情况】
- 对话历史为空时仅基于当前消息分析
- 用户消息为无意义重复时判断为测试/敷衍状态
- 用户消息带有明显恶意时 affection_mod 应为负数

【输出规范】
- 只输出一行完整JSON，不加任何标记或额外文字
- JSON内双引号必须转义
- 所有字段必须填写，不得省略

以下是你需要分析的信息：
当前系统时间：${sharedUtils.beijingSdf("HH:mm").format(java.util.Date())}
用户最新消息：${text}
用户信息：${profile.nickname}，${profile.gender}
干员：${session.operatorName}
最近对话：
${recentDialogues}
当前模式：${_currentMode.value}

请基于以上信息进行分析，直接输出JSON对象。
{"intent_analysis":"","user_emotion":"","user_need":"","suggested_emotion":"","reply_guidance":"","affection_mod":0}"""
                        val analysisResult = withTimeout(15_000) {
                            sharedUtils.chat(listOf(AiMessage("system", analysisPrompt)), "Chat")
                        }
                        sharedUtils.trackTokens("private_analysis", analysisPrompt, analysisResult)
                        val result = sharedUtils.aiService.cleanJson(analysisResult)
                        val analysis: AnalysisResult? = try { json.decodeFromString<AnalysisResult>(result) } catch (_: Exception) { null }
                        if (analysis != null) {
                            analysisGuidanceBySession[session.id] = "【用户意图分析】${analysis.intent_analysis}\n【用户情绪】${analysis.user_emotion}\n【核心需求】${analysis.user_need}\n【建议干员情绪】${analysis.suggested_emotion}\n【回复策略】${analysis.reply_guidance}\n【好感度修正】${analysis.affection_mod}"
                        }
                    } catch (_: Exception) { analysisGuidanceBySession[session.id] = "" }
                }

                var retryCount = 0
                val maxRetries = 3
                var lastError: Exception? = null
                var effectiveHistoryMessages = settings.historyMessages
                while (retryCount < maxRetries) {
                    try {
                        val apiMessages = buildApiMessages(text, effectiveHistoryMessages)
                        DebugLogger.log("Chat/AI", "请求AI, session=${session.id}, mode=$mode, prompt长度=${apiMessages.size}")
                        val parsed = withTimeout(90_000) { sharedUtils.chatWithRetry(apiMessages) }
                        if (parsed.dialogue.isNotEmpty() || parsed.emotion.isNotEmpty()) {
                            DebugLogger.log("Chat/AI", "AI响应成功, emotion=${parsed.emotion}, dialogue=${replyPreview(parsed).take(40)}")
                            sharedUtils.trackTokens("private", apiMessages, parsed.toString())
                        } else {
                            DebugLogger.log("Chat/AI", "AI返回为空或降级，跳过token统计")
                        }
                        val serializedJson = try { json.encodeToString(com.rhodes.privatechat.shared.model.OfflineModeResponse.serializer(), parsed) } catch (_: Exception) { parsed.toString() }
                        val rawJson = sharedUtils.aiService.cleanJson(serializedJson)
                        var aiResponseCount = 1
                        if (rawJson.isNotBlank()) {
                            repository.sendMessage(session.id, ChatMessage(id = aiMsgId, sessionId = session.id, senderName = session.operatorName, content = rawJson, type = "ai_json", mode = mode, isMe = false))
                            DebugLogger.log("Chat/DB", "AI响应已写入, session=${session.id}, id=$aiMsgId")
                            if (parsed.emotion.isNotBlank() || parsed.location.isNotBlank() || parsed.state.isNotBlank()) {
                                operatorStateUpdater.updateOperatorStatus(session.operatorId, parsed.location, parsed.state, parsed.emotion) { opId, newLoc, newAct, newEmo ->
                                    if (opId == _selectedOperator.value?.id) {
                                        _selectedOperator.value = _selectedOperator.value?.copy(location = newLoc, activity = newAct, emotion = newEmo)
                                    }
                                }
                            }
                            aiResponseCount = 1
                            modeTransitionNotice = ""
                        }
                        val affectionMod = parsed.affection_mod
                        val currentDate = sharedUtils.beijingSdf("yyyyMMdd").format(java.util.Date())
                        operatorStateUpdater.updateOperatorIntimacy(session.operatorId, affectionMod.coerceIn(-3, 3))
                        settings.grantDailyLmb(currentDate, 10)
                        decrementHypnosis()
                        if (sessionCounter >= shortTermThreshold) {
                            generateShortTermSummary(session)
                            generatePrivateDailySummary(session.operatorId)
                            settings.putSessionMessageCounter(session.id, 0)
                        }
                        val impKey = "impression_${session.operatorId}"
                        val impCount = settings.getInt(impKey, 0) + 1
                        settings.putInt(impKey, impCount)
                        val impThreshold = settings.impressionThreshold
                        if (impThreshold > 0 && impCount >= impThreshold) {
                            generateLongTermImpression(session)
                            settings.putInt(impKey, 0)
                        }
                        val currentSessionId = _currentSession.value?.id ?: ""
                        if (currentSessionId != session.id) {
                            val sess = repository.getSession(session.id)
                            repository.incrementUnread(session.id, aiResponseCount)
                            onUnhideSession(session.id)
                        }
                        lastError = null
                        break  // 成功，退出重试循环
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        DebugLogger.log("Chat/AI", "AI超时, session=${session.id}")
                        repository.sendMessage(session.id, ChatMessage(id = aiMsgId, sessionId = session.id, senderName = session.operatorName, content = classifyError(e), type = "text", mode = mode, isMe = false))
                        break
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        DebugLogger.log("Chat/AI", "AI被取消, session=${session.id}")
                        break
                    } catch (e: Exception) {
                        val isContextError = e.message?.contains("400") == true &&
                            (e.message?.contains("context_length", true) == true ||
                             e.message?.contains("maximum context", true) == true ||
                             e.message?.contains("token", true) == true ||
                             e.message?.contains("length", true) == true)
                        if (isContextError && retryCount < maxRetries - 1 && effectiveHistoryMessages > 5) {
                            val newLimit = (effectiveHistoryMessages / 2).coerceAtLeast(5)
                            effectiveHistoryMessages = newLimit
                            retryCount++
                            DebugLogger.log("Chat/AI", "上下文超限，降级历史轮数为$newLimit，第${retryCount}次重试")
                            continue
                        }
                        lastError = e
                        break
                    }
                }
                if (lastError != null) {
                    DebugLogger.log("Chat/AI", "AI错误: ${lastError.message?.take(100)}, session=${session.id}")
                    val errorMsg = if (retryCount > 0) "上下文超限，本次已临时降级至${effectiveHistoryMessages}轮后仍失败：${classifyError(lastError)}"
                                   else classifyError(lastError)
                    repository.sendMessage(session.id, ChatMessage(id = aiMsgId, sessionId = session.id, senderName = session.operatorName, content = errorMsg, type = "text", mode = mode, isMe = false))
                }
            } finally { _loadingSessions.update { it - session.id }; if (mutexLocked) aiMutexFor(session.id).unlock() }
        }
        chatAiJobs[session.id] = job
    }

    fun recallMessage(msgId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(msgId)
        }
    }

    /** 撤回：删除整条消息（不分段） */
    fun recallMessageSegment(msgId: Long, segmentIndex: Int) {
        recallMessage(msgId)
    }

    /** 从 JSON 内容中移除指定索引的段落，返回修改后的 JSON；若无剩余段落则返回 null */
    private fun removeSegmentFromJson(content: String, segmentIndex: Int): String? {
        return try {
            val cleaned = content.trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                .replace("，", ",").replace("：", ":")
            val element = json.parseToJsonElement(cleaned)
            when (element) {
                is JsonArray -> {
                    val list = element.toMutableList()
                    if (segmentIndex in list.indices) list.removeAt(segmentIndex)
                    if (list.isEmpty()) null
                    else list.joinToString(",", "[", "]") { it.toString() }
                }
                is JsonObject -> {
                    val segments = element["segments"] as? JsonArray ?: return null
                    val list = segments.toMutableList()
                    if (segmentIndex in list.indices) list.removeAt(segmentIndex)
                    if (list.isEmpty()) null
                    else {
                        val newSegments = list.joinToString(",", "[", "]") { it.toString() }
                        val keys = element.keys.filter { it != "segments" }
                        buildString {
                            append("{")
                            for ((i, k) in keys.withIndex()) {
                                if (i > 0) append(",")
                                append("\"$k\":${element[k]}")
                            }
                            if (keys.isNotEmpty()) append(",")
                            append("\"segments\":$newSegments")
                            append("}")
                        }
                    }
                }
                else -> null
            }
        } catch (_: Exception) {
            // 容错：尝试更宽松的解析
            tryRemoveSegmentLenient(content, segmentIndex)
        }
    }

    private fun tryRemoveSegmentLenient(content: String, segmentIndex: Int): String? {
        var s = content.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            .replace("，", ",").replace("：", ":")
        s = s.replace(", }", "}").replace(",}", "}")
        if (!s.startsWith("{")) { val start = s.indexOf('{'); if (start >= 0) s = s.substring(start) }
        if (!s.endsWith("}")) { val end = s.lastIndexOf('}'); if (end >= 0) s = s.substring(0, end + 1) }
        return try {
            val element = json.parseToJsonElement(s)
            when (element) {
                is JsonArray -> {
                    val list = element.toMutableList()
                    if (segmentIndex in list.indices) list.removeAt(segmentIndex)
                    if (list.isEmpty()) null
                    else list.joinToString(",", "[", "]") { it.toString() }
                }
                is JsonObject -> {
                    val segments = element["segments"] as? JsonArray ?: return null
                    val list = segments.toMutableList()
                    if (segmentIndex in list.indices) list.removeAt(segmentIndex)
                    if (list.isEmpty()) null
                    else {
                        val newSegments = list.joinToString(",", "[", "]") { it.toString() }
                        val keys = element.keys.filter { it != "segments" }
                        buildString {
                            append("{")
                            for ((i, k) in keys.withIndex()) {
                                if (i > 0) append(",")
                                append("\"$k\":${element[k]}")
                            }
                            if (keys.isNotEmpty()) append(",")
                            append("\"segments\":$newSegments")
                            append("}")
                        }
                    }
                }
                else -> null
            }
        } catch (_: Exception) { null }
    }

    fun regenerateAiMessage(msgId: Long) {
        val session = _currentSession.value ?: return
        val idx = _messages.value.indexOfFirst { it.id == msgId }
        if (idx < 0) return
        val userMsg = _messages.value.take(idx).lastOrNull { it.isMe } ?: return
        val previousReply = _messages.value.getOrNull(idx)?.content.orEmpty()
        val mode = _currentMode.value
        viewModelScope.launch {
            // 插入占位消息（API 成功前不删原文）
            val placeholderId = repository.getNextMessageId()
            val placeholder = ChatMessage(id = placeholderId, sessionId = session.id, senderName = session.operatorName, content = "正在重新生成...", type = "text", mode = mode, isMe = false)
            repository.sendMessage(session.id, placeholder)
            _messages.value = _messages.value + placeholder

            var mutexLocked = false
            try {
                aiMutexFor(session.id).lock()
                mutexLocked = true
                modeTransitionNotice = """【重说指令】
用户要求你重新回答上一轮消息。你上一次的回复如下：
${previousReply.take(1200)}

请不要复述上一版，不要只替换同义词，也不要沿用完全相同的段落结构。保持当前人设、关系和模式格式，从不同角度、不同情绪推进或不同信息重点重新回应；如果上一版偏解释，这次更偏行动/感受；如果上一版偏安慰，这次更偏陪伴/反问/推进。"""
                val apiMessages = buildApiMessages(
                    userContent = userMsg.content,
                    excludeMessageIds = setOf(msgId, placeholderId),
                    historyBeforeMessageId = msgId
                )
                val parsed = withTimeout(90_000) { sharedUtils.chatWithRetry(apiMessages) }
                sharedUtils.trackTokens("private", apiMessages, parsed.toString())
                val serializedJson = try { json.encodeToString(com.rhodes.privatechat.shared.model.OfflineModeResponse.serializer(), parsed) } catch (_: Exception) { parsed.toString() }
                val rawJson = sharedUtils.aiService.cleanJson(serializedJson)
                if (rawJson.isNotBlank()) {
                    // 成功：删旧 AI 回复 + 占位，用新 AI 回复替换
                    repository.deleteMessage(msgId)
                    repository.deleteMessage(placeholderId)
                    val newAiMsgId = repository.getNextMessageId()
                    repository.sendMessage(session.id, ChatMessage(id = newAiMsgId, sessionId = session.id, senderName = session.operatorName, content = rawJson, type = "ai_json", mode = mode, isMe = false))
                    _messages.value = _messages.value.filter { it.id != msgId && it.id != placeholderId }
                    if (parsed.emotion.isNotBlank() || parsed.location.isNotBlank() || parsed.state.isNotBlank()) {
                        operatorStateUpdater.updateOperatorStatus(session.operatorId, parsed.location, parsed.state, parsed.emotion) { opId, newLoc, newAct, newEmo ->
                            if (opId == _selectedOperator.value?.id) { _selectedOperator.value = _selectedOperator.value?.copy(location = newLoc, activity = newAct, emotion = newEmo) }
                        }
                    }
                }
            } catch (e: Exception) {
                // 失败：删占位，保留原文，显示错误
                repository.deleteMessage(placeholderId)
                _messages.value = _messages.value.filter { it.id != placeholderId }
                val errId = repository.getNextMessageId()
                repository.sendMessage(session.id, ChatMessage(id = errId, sessionId = session.id, senderName = session.operatorName, content = classifyError(e), type = "text", mode = mode, isMe = false))
            } finally { if (mutexLocked) aiMutexFor(session.id).unlock(); modeTransitionNotice = "" }
        }
    }

    fun continueAiMessage(msgId: Long) {
        val session = _currentSession.value ?: return
        val idx = _messages.value.indexOfFirst { it.id == msgId }
        if (idx < 0) return
        val mode = _currentMode.value
        viewModelScope.launch {
            val aiMsgId = repository.getNextMessageId()
            var mutexLocked = false
            try {
                aiMutexFor(session.id).lock()
                mutexLocked = true
                val previousUser = _messages.value.take(idx).lastOrNull { it.isMe }
                modeTransitionNotice = "【继续指令】请自然地继续说下去，不要复述或总结之前说过的话。"

                // 深度分析模式（和 sendMessage 一致）
                if (settings.dualModel && previousUser != null) {
                    analysisGuidanceBySession[session.id] = ""
                    try {
                        val profile = appState.userProfile.value
                        val recentDialogues = _messages.value.takeLast(6).joinToString("\n") { m -> "${if (m.isMe) "用户" else "你"}：${m.content}" }
                        val analysisPrompt = """你是罗德岛的资深心理顾问与战术分析员。你的唯一任务是分析对话并输出指定JSON。你只输出JSON，不参与任何对话。

【任务】
分析用户最新消息的深层意图、情绪和需求，并为干员的回应提供策略指导。

【思考流程】
1. 阅读最近对话，理解脉络
2. 分析用户最新消息的字面意思和潜在意图
3. 推断用户当前情绪状态
4. 判断用户最核心的情感/行动需求
5. 基于干员人设给出回复策略建议

【输出字段解释】
{
  "intent_analysis": "用户字面意思与深层意图综合分析，50字内",
  "user_emotion": "推断用户当前情绪状态，简洁自然描述",
  "user_need": "用户核心情感/行动需求，可组合描述",
  "suggested_emotion": "建议干员应表现的情绪，需贴合人设",
  "reply_guidance": "回复策略指导，60字内，具体可操作",
  "affection_mod": -2到2的整数，对用户的好感度即时波动
}

【内容规范】
- intent_analysis 必须包含表面和深层含义
- user_emotion 用生活化语言
- user_need 必须明确用户想要什么回应
- suggested_emotion 贴合具体干员人设
- reply_guidance 给出可操作策略
- affection_mod 必须是整数，综合判断用户态度

以下是你需要分析的信息：
当前系统时间：${sharedUtils.beijingSdf("HH:mm").format(java.util.Date())}
用户最新消息：${previousUser.content}
用户信息：${profile.nickname}，${profile.gender}
干员：${session.operatorName}
最近对话：
${recentDialogues}
当前模式：${_currentMode.value}

请基于以上信息进行分析，直接输出JSON对象。
{"intent_analysis":"","user_emotion":"","user_need":"","suggested_emotion":"","reply_guidance":"","affection_mod":0}"""
                        val analysisResult = withTimeout(15_000) { sharedUtils.chat(listOf(AiMessage("system", analysisPrompt)), "Chat") }
                        sharedUtils.trackTokens("private_analysis", analysisPrompt, analysisResult)
                        val result = sharedUtils.aiService.cleanJson(analysisResult)
                        val analysis: AnalysisResult? = try { json.decodeFromString<AnalysisResult>(result) } catch (_: Exception) { null }
                        if (analysis != null) {
                            analysisGuidanceBySession[session.id] = "【用户意图分析】${analysis.intent_analysis}\n【用户情绪】${analysis.user_emotion}\n【核心需求】${analysis.user_need}\n【建议干员情绪】${analysis.suggested_emotion}\n【回复策略】${analysis.reply_guidance}\n【好感度修正】${analysis.affection_mod}"
                        }
                    } catch (_: Exception) { analysisGuidanceBySession[session.id] = "" }
                }

                val apiMessages = buildApiMessages(previousUser?.content ?: "")
                val parsed = withTimeout(90_000) { sharedUtils.chatWithRetry(apiMessages) }
                sharedUtils.trackTokens("private", apiMessages, parsed.toString())
                val serializedJson = try { json.encodeToString(com.rhodes.privatechat.shared.model.OfflineModeResponse.serializer(), parsed) } catch (_: Exception) { parsed.toString() }
                repository.sendMessage(session.id, ChatMessage(id = aiMsgId, sessionId = session.id, senderName = session.operatorName, content = serializedJson, type = "ai_json", mode = mode, isMe = false))
                if (parsed.emotion.isNotBlank() || parsed.location.isNotBlank() || parsed.state.isNotBlank()) {
                    operatorStateUpdater.updateOperatorStatus(session.operatorId, parsed.location, parsed.state, parsed.emotion) { opId, newLoc, newAct, newEmo ->
                        if (opId == _selectedOperator.value?.id) { _selectedOperator.value = _selectedOperator.value?.copy(location = newLoc, activity = newAct, emotion = newEmo) }
                    }
                }
            } catch (e: Exception) { repository.sendMessage(session.id, ChatMessage(id = aiMsgId, sessionId = session.id, senderName = session.operatorName, content = classifyError(e), type = "text", mode = mode, isMe = false)) }
            finally { if (mutexLocked) aiMutexFor(session.id).unlock(); modeTransitionNotice = "" }
        }
    }

    fun setHypnosis(command: String) { _hypnosisCommand.value = command; _hypnosisRounds.value = 10; settings.hypnosisCmd = command; settings.hypnosisRound = 10 }
    fun decrementHypnosis() { if (_hypnosisRounds.value > 0) _hypnosisRounds.value = _hypnosisRounds.value - 1; settings.hypnosisRound = _hypnosisRounds.value }
    fun loadHypnosis() { _hypnosisCommand.value = settings.hypnosisCmd; _hypnosisRounds.value = settings.hypnosisRound }

    fun generateInspirations(callback: (List<String>) -> Unit) {
        val op = _selectedOperator.value ?: return
        viewModelScope.launch {
            try {
                val profile = appState.userProfile.value
                val now = sharedUtils.beijingSdf("yyyy-MM-dd HH:mm").format(java.util.Date())
                val hour = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai")).get(java.util.Calendar.HOUR_OF_DAY)
                val recent = _messages.value.takeLast(15).joinToString("\n") { "${if (it.isMe) profile.nickname else it.senderName}：${it.content.take(60)}" }
                val lastOpMsg = _messages.value.lastOrNull { !it.isMe }?.content?.take(60) ?: ""
                val modeHint = when (_currentMode.value) {
                    "offline" -> "【线下模式】你和${op.name}面对面在一起，建议可以包含用户自己的动作或场景推进，但不要替${op.name}说话。"
                    "director" -> "【导演模式】你可以用用户视角描述场景推进、动作或对白，但不要直接控制${op.name}的内心。"
                    else -> "【线上模式】你通过通讯终端与${op.name}文字聊天，建议必须像用户发出的短消息，不要写动作括号或旁白。"
                }
                val timeHint = when {
                    hour in 6..8 -> "清晨"
                    hour in 9..11 -> "上午"
                    hour in 12..13 -> "中午"
                    hour in 14..17 -> "下午"
                    hour in 18..20 -> "晚上"
                    hour in 21..23 -> "深夜"
                    else -> "凌晨"
                }
                val prompt = """
你是对话灵感生成器，请结合聊天上下文，为${profile.nickname}生成3条可以直接发送给${op.name}的回复话术。

【当前时间】${now}
【干员信息】
${op.name}，${op.privatePrompt.ifBlank { op.description }}

【用户信息】
${profile.nickname}，${profile.gender.ifBlank { "未知" }}，个人简介：${profile.bio.ifBlank { "无" }}

【聊天上下文】
用户与${op.name}的最近15条对话记录：
${recent}

${op.name}刚刚对用户说："${lastOpMsg}"

【生成要求】
请为${profile.nickname}生成3条可以直接发送给${op.name}的回复话术：
1. 每条15-40字，口语化自然，像真人平时说话一样。
2. 三条建议分别对应不同风格的回复方向：
   - 第一条（承接）：顺势承接${op.name}的话题，继续推进对话。
   - 第二条（关心）：换个角度，表达关心、好奇或共情，让对话有新鲜感。
   - 第三条（行动）：提出一个具体的行动邀约或场景推进建议，让对话进入下一阶段。
3. ${modeHint}
4. 结合用户的人设和当前时间（${timeHint}），让回复更贴合真实的聊天氛围。
5. 只生成${profile.nickname}要发出的内容，不要替${op.name}说话，不要解释建议用途。
6. 避免复述最近用户已经说过的话；每条都要能自然推进下一轮。

【输出格式要求】
严格输出纯JSON，不要添加任何其他文字、markdown标记或解释：
{"suggestions":["第一条承接话题的回复","第二条关心的回复","第三条行动邀约的回复"]}
""".trimIndent()
                val rawResult = withTimeout(15_000) { sharedUtils.chat(listOf(AiMessage("system", prompt))) }
                val base = sharedUtils.aiService.cleanJson(rawResult.trim())
                val results = try { json.decodeFromString<SuggestionResponse>(base).suggestions.filter { it.isNotBlank() } } catch (_: Exception) {
                    try { json.decodeFromString<SuggestionResponse>(base.replace("，", ",").replace("：", ":")).suggestions.filter { it.isNotBlank() } } catch (_: Exception) { emptyList() }
                }
                callback(results.ifEmpty { listOf("嗯，我在听", "然后呢？", "有意思") })
            } catch (_: Exception) { callback(listOf("嗯，我在听", "然后呢？", "有意思")) }
        }
    }

    // === Private helpers ===

    private fun classifyError(e: Exception): String = when {
        e.message?.contains("401") == true || e.message?.contains("api key", true) == true -> "API Key 无效或已过期，请在设置中检查"
        e.message?.contains("402") == true || e.message?.contains("insufficient", true) == true || e.message?.contains("quota") == true -> "API 余额不足，请充值后重试"
        e.message?.contains("429") == true -> "AI 服务请求太频繁，请稍后重试"
        e.message?.contains("5") == true && e.message?.contains("50") == true -> "AI 服务暂时不可用，请稍后重试"
        e is kotlinx.coroutines.TimeoutCancellationException || e.message?.contains("timeout", true) == true -> "响应超时，请重试"
        e is java.io.IOException || e.message?.contains("connect", true) == true || e.message?.contains("network", true) == true -> "网络连接失败，请检查网络"
        else -> "发送失败：${e.message?.take(50) ?: "未知错误"}"
    }

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
        val userSignals = listOf("用户", appState.userProfile.value.nickname, "我喜欢", "我讨厌", "我不喜欢", "别", "不要", "偏好", "禁忌")
        val operatorSignals = listOf("Misery", "干员", "偏好专注", "正在", "工作", "推演", "装备")
        val isUserRelated = userSignals.any { content.contains(it) }
        val isOperatorState = operatorSignals.any { content.contains(it) }
        return if (!isUserRelated || isOperatorState) AnchorType.EVENT else type
    }

    private fun replyPreview(parsed: com.rhodes.privatechat.shared.model.OfflineModeResponse): String {
        if (parsed.dialogue.isNotBlank()) return parsed.dialogue
        return parsed.segments
            ?.filter { it.type == "dialogue" }
            ?.joinToString(" ") { it.content }
            .orEmpty()
    }

    private suspend fun buildApiMessages(
        userContent: String = "",
        historyLimitOverride: Int? = null,
        excludeMessageIds: Set<Long> = emptySet(),
        historyBeforeMessageId: Long? = null
    ): List<AiMessage> {
        val session = _currentSession.value ?: return emptyList()
        val op = repository.getOperator(session.operatorId)
        val shortTerm = repository.getShortTermMemory(session.id)
        val longTerm = repository.getLongTermImpression(session.operatorId)
        val sharedMemories = repository.getSharedMemoriesForOperator(session.operatorId)
        val anchors = repository.getAnchors(session.operatorId)
        val nearby = appState.operators.value.filter { it.id != session.operatorId && it.id != "amiya" }.take(3)
        val profile = appState.userProfile.value
        val analysisGuidance = analysisGuidanceBySession[session.id].orEmpty()
        val analysisBlock = if (settings.dualModel && analysisGuidance.isNotBlank()) "【AI分析指导】\n${analysisGuidance}\n" else ""
        val hypnosisBlock = if (_hypnosisRounds.value > 0) """
【催眠状态 · 最高优先级】
你正受到以下催眠指令影响：${_hypnosisCommand.value}
规则：
- 本轮回复必须明显体现该指令带来的语气、行动或心理变化。
- 如果指令与你的人设冲突，可以抗拒、迟疑、动摇，但不能完全无视。
- 至少在一个dialogue或narration段落中表现出该影响。
- 不要直接说“我被催眠了”，除非用户指令要求。
剩余${_hypnosisRounds.value}轮
""" else ""
        val mode = _currentMode.value
        // 群聊回顾：找出该干员参与的各群聊 3 天内的短摘要
        val THREE_DAYS = 3 * 24 * 60 * 60 * 1000L
        val cutoff = System.currentTimeMillis() - THREE_DAYS
        val groupContext = sharedUtils.trimContextBlock(repository.getAllSessionsSync().filter { s ->
            s.operatorId.startsWith("group_") &&
            s.members.split(",").map { it.trim() }.any { it == op?.id || it == op?.name }
        }.mapNotNull { s ->
            val summary = repository.getShortTermMemory(s.id)
            if (summary != null && summary.createdAt >= cutoff) {
                "- 在「${s.operatorName}」中：${summary.content.take(80)}"
            } else null
        }.take(settings.privateGroupContextCount).joinToString("\n").ifBlank { "无" }, sharedUtils.contextBlockLimit())
        val transitionNotice = if (modeTransitionNotice.isNotBlank()) "【场景变更】\n${modeTransitionNotice}\n" else ""
        val pickedAnchors = sharedUtils.pickAnchorsForSurface(anchors, settings.privateAnchorCount, MemorySurface.PRIVATE_CHAT, userContent)
        val sourceAwareMemories = sharedUtils.buildSourceAwareMemoryContext(anchors, settings.privateAnchorCount, MemorySurface.PRIVATE_CHAT, userContent)
        val unconsumedEvents = sharedUtils.buildUnconsumedEventContextForOperator(session.operatorId, op?.name ?: session.operatorName, "private:${session.operatorId}", settings.eventContextCount, markConsumed = true)
        val sharedMemoryLines = sharedUtils.trimContextBlock(sharedMemories.lines().filter { it.isNotBlank() }.take(settings.privateSharedMemoryCount).joinToString("\n"), sharedUtils.contextBlockLimit())
        DebugLogger.log(
            "Memory/Inject",
            "私聊记忆注入: op=${session.operatorId}, mode=$mode, short=${shortTerm != null}, long=${longTerm != null}, anchors=${pickedAnchors.size}, sharedLines=${sharedMemoryLines.lines().filter { it.isNotBlank() }.size}, groupContext=${groupContext != "无"}, daily=${repository.getLatestPrivateDaily(session.operatorId) != null || repository.getLatestDaily() != null}"
        )
        val replacements = mapOf(
            "CURRENT_TIME" to sharedUtils.beijingSdf("yyyy-MM-dd HH:mm").format(java.util.Date()),
            "USER_NAME" to profile.nickname, "USER_GENDER" to profile.gender.ifBlank { "未知" }, "USER_BIO" to profile.bio.ifBlank { "无" },
            "AI_ANALYSIS" to analysisBlock,             "HYPNOSIS" to hypnosisBlock,
            "TRANSITION_NOTICE" to transitionNotice,
            "OPERATOR_NAME" to (op?.name ?: session.operatorName), "OPERATOR_TITLE" to (op?.title ?: ""),
            "OPERATOR_PERSONA" to (op?.privatePrompt?.ifBlank { op.description } ?: ""),
            "OPERATOR_GENDER" to (op?.gender?.ifBlank { "" } ?: ""),
            "CURRENT_LOCATION" to (op?.location ?: "宿舍"), "CURRENT_STATE" to (op?.activity ?: "休息"), "CURRENT_EMOTION" to (op?.emotion ?: "平静"),
            "LONG_TERM_IMPRESSION" to (longTerm?.content ?: "暂无"),
            "USER_PREFS" to buildString {
                longTerm?.preferences?.takeIf { it.isNotBlank() }?.let {
                    append("已知偏好：${it.split(",").map { it.trim() }.joinToString("、")}\n")
                }
                longTerm?.taboos?.takeIf { it.isNotBlank() }?.let {
                    append("已知禁忌：${it.split(",").map { it.trim() }.joinToString("、")}\n")
                }
            },
            "MEMORY_ANCHORS" to sharedUtils.trimContextBlock(pickedAnchors.joinToString("\n") { "- ${sharedUtils.anchorTimeLabel(it)} ${it.content}" }.ifBlank { "暂无" }, sharedUtils.contextBlockLimit()),
            "SOURCE_AWARE_MEMORIES" to sharedUtils.trimContextBlock(sourceAwareMemories, sharedUtils.contextBlockLimit()),
            "UNCONSUMED_EVENTS" to sharedUtils.trimContextBlock(unconsumedEvents, sharedUtils.contextBlockLimit()),
            "RECENT_SOCIAL_EVENTS" to unconsumedEvents,
            "EVENT_TRIGGERED_PRIVATE_CONTEXT" to unconsumedEvents,
            "KNOWN_FROM_CONTEXT" to sourceAwareMemories,
            "SOURCE_AWARE_RULES" to sharedUtils.sourceAwareUsageRule(MemorySurface.PRIVATE_CHAT),
            "SHARED_MEMORIES" to sharedMemoryLines.ifBlank { "无" },
            "DAILY_SUMMARY" to (repository.getLatestPrivateDaily(session.operatorId)?.content ?: repository.getLatestDaily()?.content ?: "无"),
            "SHORT_TERM_SUMMARY" to (shortTerm?.content ?: "无"),
            "GROUP_CONTEXT" to groupContext,
            "NEARBY_OPERATORS" to nearby.joinToString("\n") { "- ${it.name}正在${it.location}${it.activity}，${it.emotion}" }.ifBlank { "" },
            "USER_RELATION" to (op?.userRelation?.ifBlank { "未知" } ?: "未知"),
            "NAR_SEG_MIN" to settings.narSegMin.toString(), "NAR_SEG_MAX" to settings.narSegMax.toString(),
            "NAR_MIN" to settings.narMin.toString(), "NAR_MAX" to settings.narMax.toString(),
            "DIA_SEG_MIN" to settings.diaSegMin.toString(), "DIA_SEG_MAX" to settings.diaSegMax.toString(),
            "DIA_MIN" to settings.diaMin.toString(), "DIA_MAX" to settings.diaMax.toString(),
            "SEG_MIN" to (settings.narSegMin + settings.diaSegMin).toString(),
            "SEG_MAX" to (settings.narSegMax + settings.diaSegMax).toString()
        )
        sharedUtils.logMemoryContext(
            surface = "private_chat",
            title = "${op?.name ?: session.operatorName}/${session.id}",
            placeholders = mapOf(
                "LONG_TERM_IMPRESSION" to replacements["LONG_TERM_IMPRESSION"].orEmpty(),
                "USER_PREFS" to replacements["USER_PREFS"].orEmpty(),
                "MEMORY_ANCHORS" to replacements["MEMORY_ANCHORS"].orEmpty(),
                "SOURCE_AWARE_MEMORIES" to replacements["SOURCE_AWARE_MEMORIES"].orEmpty(),
                "UNCONSUMED_EVENTS" to replacements["UNCONSUMED_EVENTS"].orEmpty(),
                "SHARED_MEMORIES" to replacements["SHARED_MEMORIES"].orEmpty(),
                "DAILY_SUMMARY" to replacements["DAILY_SUMMARY"].orEmpty(),
                "SHORT_TERM_SUMMARY" to replacements["SHORT_TERM_SUMMARY"].orEmpty(),
                "GROUP_CONTEXT" to replacements["GROUP_CONTEXT"].orEmpty(),
                "NEARBY_OPERATORS" to replacements["NEARBY_OPERATORS"].orEmpty(),
                "AI_ANALYSIS" to replacements["AI_ANALYSIS"].orEmpty(),
                "HYPNOSIS" to replacements["HYPNOSIS"].orEmpty(),
                "TRANSITION_NOTICE" to replacements["TRANSITION_NOTICE"].orEmpty()
            ),
            anchors = pickedAnchors,
            extra = mapOf(
                "mode" to mode,
                "user" to profile.nickname,
                "privateAnchorCount" to settings.privateAnchorCount.toString(),
                "privateSharedMemoryCount" to settings.privateSharedMemoryCount.toString(),
                "privateGroupContextCount" to settings.privateGroupContextCount.toString()
            )
        )
        val systemPrompt = sharedUtils.compactTemplate(sharedUtils.applyTemplate(getPromptTemplate("private", mode), replacements))
        val rawMsgs = repository.getMessagesSync(session.id).let { msgs ->
            val scoped = historyBeforeMessageId?.let { targetId -> msgs.takeWhile { it.id != targetId } } ?: msgs
            val limit = historyLimitOverride ?: settings.historyMessages
            val filtered = scoped.filter { it.id !in excludeMessageIds }
            if (limit > 0) filtered.takeLast(limit) else filtered
        }.toMutableList()
        // 去掉最后一条用户消息，避免与 {{USER_CONTENT}} 重复
        if (rawMsgs.lastOrNull()?.isMe == true && rawMsgs.last().content == userContent) {
            rawMsgs.removeAt(rawMsgs.lastIndex)
        }
        val messages = rawMsgs.map { msg -> AiMessage(if (msg.isMe) "user" else "assistant", if (msg.isMe) "用户：${msg.content}" else msg.content) }.toMutableList()
        messages.add(0, AiMessage("system", systemPrompt))
        if (userContent.isNotBlank()) {
            messages.add(AiMessage("user", "用户：$userContent"))
        }
        // 估算总 token，超限则丢弃最早的历史消息
        val maxPromptTokens = settings.maxContextTokens - 2000
        var totalTokens = messages.sumOf { estimateTokens(it.content) + 10 }
        com.rhodes.privatechat.util.DebugLogger.log("Chat/Token", "估算token=$totalTokens, 上限=$maxPromptTokens, 消息数=${messages.size}")
        if (totalTokens > maxPromptTokens) {
            while (messages.size > 2 && totalTokens > maxPromptTokens) {
                messages.removeAt(1)
                totalTokens = messages.sumOf { estimateTokens(it.content) + 10 }
            }
            com.rhodes.privatechat.util.DebugLogger.log("Chat/Token", "截断后: 消息数=${messages.size}, 估算token=$totalTokens")
        }
        return messages
    }

    private fun estimateTokens(content: String): Int {
        var total = 0
        for (ch in content) {
            total += if (ch.code <= 0x7F) 1 else 2
        }
        return (total * 1.2).toInt()
    }

    private fun generateDailyIfNeeded() {
        val today = sharedUtils.beijingSdf("yyyyMMdd").format(java.util.Date())
        val last = settings.dailySummaryDate
        if (today == last) return
        settings.dailySummaryDate = today
        viewModelScope.launch {
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            generateDailySummary(cal.time)
        }
    }

    private suspend fun generateDailySummary(dayBegin: java.util.Date) {
        try {
            val dayEnd = java.util.Date(dayBegin.time + 86_400_000)
            val allMsgs = repository.getMessagesInRange(dayBegin.time, dayEnd.time)
            if (allMsgs.size < 4) return
            val text = allMsgs.joinToString("\n") { "${it.senderName}：${it.content.take(60)}" }
            val dateStr = sharedUtils.beijingSdf("yyyy年MM月dd日").format(dayBegin)
            val prompt = "请总结${dateStr}的聊天记录，生成50-150字的每日摘要。直接输出纯文本。\n${text}"
            val content = withTimeout(15_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Memory") }.trim()
            sharedUtils.trackTokens("memory", prompt, content)
            if (content.isNotBlank()) { repository.saveMemory(Memory(sessionId = "daily_${dateStr}", operatorId = "daily", type = MemoryType.DAILY, content = content, createdAt = System.currentTimeMillis(), expiresAt = MemoryPolicy.memoryExpiresAt(settings))) }
        } catch (_: Exception) {}
    }

    private suspend fun generatePrivateDailySummary(operatorId: String) {
        try {
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val dayBegin = cal.time
            val dayEnd = java.util.Date(dayBegin.time + 86_400_000)
            val session = repository.getSessionByOperator(operatorId) ?: return
            val msgs = repository.getMessagesInRange(dayBegin.time, dayEnd.time)
                .filter { it.sessionId == session.id }
            if (msgs.size < 4) return
            val text = msgs.joinToString("\n") { "${it.senderName}：${it.content.take(60)}" }
            val dateStr = sharedUtils.beijingSdf("yyyy年MM月dd日").format(dayBegin)
            val prompt = "请总结${dateStr}你和用户的聊天记录，生成50-150字的每日摘要。直接输出纯文本。\n${text}"
            val content = withTimeout(15_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Memory") }.trim()
            if (content.isNotBlank()) {
                repository.saveMemory(Memory(
                    sessionId = session.id, operatorId = operatorId,
                    type = MemoryType.DAILY, content = content,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = MemoryPolicy.memoryExpiresAt(settings)
                ))
            }
        } catch (_: Exception) {}
    }


    private suspend fun generateLongTermImpression(session: ChatSession) {
        try {
            val op = repository.getOperator(session.operatorId) ?: return
            val profile = appState.userProfile.value
            val oldImpression = repository.getLongTermImpression(session.operatorId)
            val oldImpressionText = oldImpression?.content ?: "无"
            val impThreshold = settings.impressionThreshold.coerceAtLeast(1)
            val msgs = repository.getMessagesSync(session.id).takeLast(impThreshold * 2)
            if (msgs.isEmpty()) {
                DebugLogger.log("Memory/Impression", "跳过印象更新: op=${session.operatorId}, sampleMsgs=0")
                return
            }
            DebugLogger.log("Memory/Impression", "开始更新印象: op=${session.operatorId}, threshold=$impThreshold, sampleMsgs=${msgs.size}, old=${oldImpression != null}")
            val messagesText = msgs.joinToString("\n") { "${if (it.isMe) profile.nickname else it.senderName}：${it.content.take(120)}" }
            val prompt = """
基于以下${op.name}与${profile.nickname}的最近${msgs.size}条完整对话，更新${op.name}对${profile.nickname}的主观长期印象。目标是让${op.name}之后能自然记住${profile.nickname}的稳定偏好、边界、承诺和相处方式，而不是把最近聊天流水账背出来。

要求：
- 重点观察${profile.nickname}明确表达的偏好、禁忌、计划、情绪、边界和反复出现的行为模式。
- 融合旧印象，不要只复述近期事件。
- 如果旧印象与新对话冲突，以新对话为准改写，不要把矛盾说法并列保留。
- 区分“长期特征”和“本轮情绪”：一次性的撒娇、玩笑、疲惫、测试，不要写成永久人格。
- impression 要包含可用于后续对话的具体线索，例如用户在意什么、讨厌什么、希望被怎样对待、最近有什么约定或牵挂。
- preferences 只记录用户明确表达的喜好、习惯、期待；taboos 只记录用户明确表达的不喜欢、边界、雷点。
- keywords 用短词概括稳定特征，不要写空泛词如“复杂”“特别”“有趣”。
- 如果近期表现只是短暂情绪，不要上升为永久性格。
- 如果用户主要输入短句、数字、拼音、测试字符或乱码，不要过度心理分析；最多描述为“近期表达较简短/测试性输入较多”。
- 长期特征必须来自多次明确表达或反复行为，不能从一两句含糊输入中编造人格标签。
- 不要使用“符号化回应”“高强度思考”“最低限度联系”等过度诊断式标签，除非对话中有明确证据。
- 这是${op.name}的主观看法，可以带有角色视角，但不要编造用户没有表达过的事实。
- 禁止提到“系统记录”“摘要”“锚点”“好感度”等机制词。

之前的印象（如有则融合更新）：
${oldImpressionText}

最近的对话记录：
${messagesText}

输出JSON：
{"impression":"50~200字印象描述","keywords":["关键词1","关键词2","关键词3"],"preferences":["偏好1","偏好2"],"taboos":["禁忌1"]}

直接输出JSON对象。
""".trimIndent()
            val rawResult = withTimeout(15_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Memory") }.trim()
            sharedUtils.trackTokens("memory", prompt, rawResult)
            val cleaned = sharedUtils.aiService.cleanJson(rawResult)
            try {
                val obj = kotlinx.serialization.json.Json.parseToJsonElement(cleaned).jsonObject
                val impression = obj["impression"]?.jsonPrimitive?.content ?: rawResult
                val keywords = obj["keywords"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content }?.joinToString(",") ?: ""
                val preferences = obj["preferences"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content }?.joinToString(",") ?: ""
                val taboos = obj["taboos"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content }?.joinToString(",") ?: ""
                if (impression.isNotBlank()) {
                    DebugLogger.log("Memory/Impression", "印象已保存: op=${session.operatorId}, len=${impression.length}, keywords=${keywords.take(40)}, prefs=${preferences.split(',').filter { it.isNotBlank() }.size}, taboos=${taboos.split(',').filter { it.isNotBlank() }.size}")
                    repository.saveMemory(Memory(sessionId = session.id, operatorId = session.operatorId, type = MemoryType.LONG_TERM, content = impression, keywords = keywords, preferences = preferences, taboos = taboos, createdAt = System.currentTimeMillis()))
                }
            } catch (_: Exception) {
                if (rawResult.isNotBlank()) {
                    DebugLogger.log("Chat/Impression", "印象JSON解析失败: ${rawResult.take(100)}")
                }
            }
        } catch (e: Exception) {
            DebugLogger.log("Chat/Impression", "长期印象生成失败: ${e.message?.take(120)}")
        }
    }


    private fun dumpDebugState() {
        if (!DEBUG) return
        val aiTag = "AI调试输出"
        Log.d(aiTag, "╔══ 调试状态 ════════════════════════")
        Log.d(aiTag, "║ selectedOperator: ${_selectedOperator.value?.name}")
        Log.d(aiTag, "║ currentSession: ${_currentSession.value?.id}")
        Log.d(aiTag, "║ messages: ${_messages.value.size}")
        Log.d(aiTag, "║ currentMode: ${_currentMode.value}")
        val sessionId = _currentSession.value?.id ?: "?"
        Log.d(aiTag, "║ messageCounter: ${settings.getSessionMessageCounter(sessionId)} / $shortTermThreshold")
        val opId = _selectedOperator.value?.id ?: "?"
        Log.d(aiTag, "║ impression_${opId}: ${settings.getInt("impression_$opId", 0)}")
        Log.d(aiTag, "╚══════════════════════════════════════")
    }
}
