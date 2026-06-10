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
import com.rhodes.privatechat.shared.model.AnalysisResult
import com.rhodes.privatechat.shared.model.SuggestionResponse
import com.rhodes.privatechat.shared.network.AIService
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.viewmodel.shared.OperatorStateUpdater
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.shared.PromptTemplates
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.rhodes.privatechat.viewmodel.shared.UserProfile
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
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
    private val _mindReadRounds = MutableStateFlow(0)
    val mindReadRounds: StateFlow<Int> = _mindReadRounds.asStateFlow()
    private val _mindReadContent = MutableStateFlow("")
    val mindReadContent: StateFlow<String> = _mindReadContent.asStateFlow()

    // Internal state
    private var messageCounter: Int
        get() = settings.messageCounter
        set(v) { settings.messageCounter = v }
    private var impressionMsgCounter: Int
        get() = settings.impressionMsgCounter
        set(v) { settings.impressionMsgCounter = v }
    private val sessionMessageCounter = mutableMapOf<String, Int>()
    private val shortTermThreshold: Int get() = settings.summaryThreshold
    private val updateMutex = Mutex()
    private var lastDbUpdate = 0L
    private val chatAiMutexes = mutableMapOf<String, Mutex>()
    private fun aiMutexFor(sessionId: String): Mutex = chatAiMutexes.getOrPut(sessionId) { Mutex() }
    private var analysisGuidance = ""
    private var modeTransitionNotice = ""
    private var messagesJob: Job? = null

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
            val retain = settings.summaryRetain
            val recent = msgs.takeLast(retain)
            val older = msgs.dropLast(retain)
            if (older.isEmpty()) return
            val text = older.joinToString("\n") { "${it.senderName}：${it.content.take(100)}" }
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
${text}"""
            val rawResult = withTimeout(15_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Memory") }.trim()
            sharedUtils.trackTokens("memory", prompt, rawResult)
            val parsed = sharedUtils.aiService.parseSummaryResponse(rawResult)
            if (parsed.summary.isNotBlank()) {
                repository.saveMemory(Memory(
                    sessionId = session.id, operatorId = session.operatorId,
                    type = MemoryType.SHORT_TERM, content = parsed.summary,
                    keywords = parsed.keywords.joinToString(","),
                    expiresAt = System.currentTimeMillis() + settings.cleanDays * 86_400_000L
                ))
                if (parsed.anchors.isNotEmpty()) {
                    val anchors = parsed.anchors.map { a ->
                        MemoryAnchor(
                            sessionId = session.id, operatorId = session.operatorId,
                            type = try { AnchorType.valueOf(a.type.uppercase()) } catch (_: Exception) { AnchorType.EVENT },
                            content = a.content, isPrivate = a.isPrivate,
                            createdAt = System.currentTimeMillis(),
                            expiresAt = System.currentTimeMillis() + settings.cleanDays * 86_400_000L
                        )
                    }
                    repository.saveAnchors(anchors)
                }
                repository.enforceMemoryRetain(session.id, settings.summaryRetain)
            }
        } catch (_: Exception) {}
    }

    // === Public API ===

    fun selectOperator(operator: Operator) {
        // 保存当前干员的催眠/读心状态
        val prevOp = _selectedOperator.value
        if (prevOp != null) {
            settings.putString("hypnosis_cmd_${prevOp.id}", _hypnosisCommand.value)
            settings.putInt("hypnosis_round_${prevOp.id}", _hypnosisRounds.value)
            settings.putString("mind_read_${prevOp.id}", _mindReadContent.value)
            settings.putInt("mind_read_rounds_${prevOp.id}", _mindReadRounds.value)
        }
        _selectedOperator.value = operator
        messageCounter = 0
        // 恢复新干员的催眠/读心状态
        _hypnosisCommand.value = settings.getString("hypnosis_cmd_${operator.id}", "")
        _hypnosisRounds.value = settings.getInt("hypnosis_round_${operator.id}", 0)
        _mindReadContent.value = settings.getString("mind_read_${operator.id}", "")
        _mindReadRounds.value = settings.getInt("mind_read_rounds_${operator.id}", 0)
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
                repository.getMessages(session.id).collect { msgs -> _messages.value = msgs }
            }
        }
    }

    suspend fun selectOperatorSync(operator: Operator) {
        val prevOp = _selectedOperator.value
        if (prevOp != null) {
            settings.putString("hypnosis_cmd_${prevOp.id}", _hypnosisCommand.value)
            settings.putInt("hypnosis_round_${prevOp.id}", _hypnosisRounds.value)
            settings.putString("mind_read_${prevOp.id}", _mindReadContent.value)
            settings.putInt("mind_read_rounds_${prevOp.id}", _mindReadRounds.value)
        }
        _selectedOperator.value = operator
        messageCounter = 0
        _hypnosisCommand.value = settings.getString("hypnosis_cmd_${operator.id}", "")
        _hypnosisRounds.value = settings.getInt("hypnosis_round_${operator.id}", 0)
        _mindReadContent.value = settings.getString("mind_read_${operator.id}", "")
        _mindReadRounds.value = settings.getInt("mind_read_rounds_${operator.id}", 0)
        settings.hypnosisCmd = _hypnosisCommand.value
        settings.hypnosisRound = _hypnosisRounds.value
        val session = repository.getOrCreateSession(operator.id, operator.name, operator.avatarUri)
        _currentSession.value = session
        val savedMode = settings.getLastMode(operator.id)
        _currentMode.value = savedMode
        markSessionRead(session.id)
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.getMessages(session.id).collect { msgs -> _messages.value = msgs }
        }
    }

    fun clearSelection() {
        _selectedOperator.value = null
        _currentSession.value = null
        _messages.value = emptyList()
        messageCounter = 0
    }

    fun clearMessages() {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            repository.deleteSessionMessages(session.id)
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

        viewModelScope.launch {
            // 步骤1：用户消息插入（无锁，即时显示）
            messageCounter++
            val msgId = repository.getNextMessageId()
            repository.sendMessage(session.id, ChatMessage(
                id = msgId, sessionId = session.id,
                senderName = "我", content = text, type = "text", mode = _currentMode.value, isMe = true
            ))
            _loadingSessions.value = _loadingSessions.value + session.id

            // 步骤2：AI 处理（持锁，串行化，防止多条消息的 AI 回复乱序）
            aiMutexFor(session.id).lock()
            val aiMsgId = repository.getNextMessageId()
            val mode = _currentMode.value
            try {

                if (settings.dualModel) {
                    analysisGuidance = ""
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
                            analysisGuidance = "【用户意图分析】${analysis.intent_analysis}\n【用户情绪】${analysis.user_emotion}\n【核心需求】${analysis.user_need}\n【建议干员情绪】${analysis.suggested_emotion}\n【回复策略】${analysis.reply_guidance}\n【好感度修正】${analysis.affection_mod}"
                        }
                    } catch (_: Exception) { analysisGuidance = "" }
                }

                val apiMessages = buildApiMessages(text)
                val parsed = withTimeout(60_000) { sharedUtils.chatWithRetry(apiMessages) }
                sharedUtils.trackTokens("private", apiMessages, parsed.toString())
                val serializedJson = try { json.encodeToString(com.rhodes.privatechat.shared.model.OfflineModeResponse.serializer(), parsed) } catch (_: Exception) { parsed.toString() }
                val rawJson = sharedUtils.aiService.cleanJson(serializedJson)
                var aiResponseCount = 1
                if (rawJson.isNotBlank()) {
                    repository.sendMessage(session.id, ChatMessage(id = aiMsgId, sessionId = session.id, senderName = session.operatorName, content = rawJson, type = "ai_json", mode = mode, isMe = false))
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
                operatorStateUpdater.updateOperatorIntimacy(session.operatorId, 1 + affectionMod.coerceIn(-3, 3))
                val today = settings.rewardDate
                val currentDate = sharedUtils.beijingSdf("yyyyMMdd").format(java.util.Date())
                if (today != currentDate) { settings.rewardDate = currentDate; settings.dailyLmbCount = 0 }
                val dailyCount = settings.dailyLmbCount
                if (dailyCount < 5000) { val balance = settings.lmb; settings.lmb = balance + 10; settings.dailyLmbCount = dailyCount + 1 }
                decrementHypnosis()
                decrementMindRead()
                if (messageCounter >= shortTermThreshold) { generateShortTermSummary(session); messageCounter = 0 }
                impressionMsgCounter++
                val impThreshold = settings.impressionThreshold
                if (impThreshold > 0 && impressionMsgCounter >= impThreshold) { generateLongTermImpression(session); impressionMsgCounter = 0 }
                val currentSessionId = _currentSession.value?.id ?: ""
                if (currentSessionId != session.id) {
                    val sess = repository.getSession(session.id)
                    if (sess != null) repository.insertSession(sess.copy(unreadCount = sess.unreadCount + aiResponseCount))
                    onUnhideSession(session.id)
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                repository.sendMessage(session.id, ChatMessage(id = aiMsgId, sessionId = session.id, senderName = session.operatorName, content = "响应超时，请重试", type = "text", mode = mode, isMe = false))
            } catch (e: Exception) {
                repository.sendMessage(session.id, ChatMessage(id = aiMsgId, sessionId = session.id, senderName = session.operatorName, content = "对方网络不太好，没有收到信息", type = "text", mode = mode, isMe = false))
            } finally { _loadingSessions.value = _loadingSessions.value - session.id; aiMutexFor(session.id).unlock() }
        }
    }

    fun recallMessage(msgId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(msgId)
            _messages.value = _messages.value.filter { it.id != msgId }
        }
    }

    /** 撤回多段 JSON 消息中的单个段落，而非整条消息 */
    fun recallMessageSegment(msgId: Long, segmentIndex: Int) {
        if (segmentIndex < 0) { recallMessage(msgId); return }
        val msg = _messages.value.find { it.id == msgId } ?: return
        if (msg.type != "ai_json") { recallMessage(msgId); return }
        viewModelScope.launch {
            val newContent = removeSegmentFromJson(msg.content, segmentIndex)
            if (DEBUG) Log.d("ChatVM", "recallSegment msgId=$msgId segIdx=$segmentIndex newContent=${newContent?.take(80)}")
            if (newContent == null) {
                repository.deleteMessage(msgId)
                _messages.value = _messages.value.filter { it.id != msgId }
            } else {
                repository.updateMessageContent(msgId, newContent)
                _messages.value = _messages.value.map { if (it.id == msgId) it.copy(content = newContent) else it }
            }
        }
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
        viewModelScope.launch { repository.deleteMessage(msgId); repository.deleteMessage(userMsg.id) }
        _messages.value = _messages.value.filter { it.id != msgId && it.id != userMsg.id }
        _inputText.value = userMsg.content
        sendMessage()
    }

    fun continueAiMessage(msgId: Long) {
        val session = _currentSession.value ?: return
        val idx = _messages.value.indexOfFirst { it.id == msgId }
        if (idx < 0) return
        val mode = _currentMode.value
        viewModelScope.launch {
            aiMutexFor(session.id).lock()
            try {
                val aiMsgId = repository.getNextMessageId()
                val previousUser = _messages.value.take(idx).lastOrNull { it.isMe }
                modeTransitionNotice = "【继续指令】请自然地继续说下去，不要复述或总结之前说过的话。"
                val apiMessages = buildApiMessages(previousUser?.content ?: "")
                val parsed = withTimeout(60_000) { sharedUtils.chatWithRetry(apiMessages) }
                sharedUtils.trackTokens("private", apiMessages, parsed.toString())
                val serializedJson = try { json.encodeToString(com.rhodes.privatechat.shared.model.OfflineModeResponse.serializer(), parsed) } catch (_: Exception) { parsed.toString() }
                repository.sendMessage(session.id, ChatMessage(id = aiMsgId, sessionId = session.id, senderName = session.operatorName, content = serializedJson, type = "ai_json", mode = mode, isMe = false))
                if (parsed.emotion.isNotBlank() || parsed.location.isNotBlank() || parsed.state.isNotBlank()) {
                    operatorStateUpdater.updateOperatorStatus(session.operatorId, parsed.location, parsed.state, parsed.emotion) { opId, newLoc, newAct, newEmo ->
                        if (opId == _selectedOperator.value?.id) { _selectedOperator.value = _selectedOperator.value?.copy(location = newLoc, activity = newAct, emotion = newEmo) }
                    }
                }
            } catch (e: Exception) { /* 错误已通过占位消息的更新处理 */ }
            finally { aiMutexFor(session.id).unlock(); modeTransitionNotice = "" }
        }
    }

    fun setHypnosis(command: String) { _hypnosisCommand.value = command; _hypnosisRounds.value = 10; settings.hypnosisCmd = command; settings.hypnosisRound = 10 }
    fun decrementHypnosis() { if (_hypnosisRounds.value > 0) _hypnosisRounds.value = _hypnosisRounds.value - 1; settings.hypnosisRound = _hypnosisRounds.value }
    fun loadHypnosis() { _hypnosisCommand.value = settings.hypnosisCmd; _hypnosisRounds.value = settings.hypnosisRound }
    fun setMindRead(innerThought: String) { _mindReadContent.value = innerThought; _mindReadRounds.value = 3 }
    fun decrementMindRead() { if (_mindReadRounds.value > 0) _mindReadRounds.value = _mindReadRounds.value - 1 }

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
                    "offline" -> "1. 【线下模式】你和${op.name}面对面在一起，回复要像当面说话一样自然，可用括号带动作描述。"
                    "director" -> "2. 【导演模式】你可以自由描述场景和行动，回复可以是动作、心理活动或对话。"
                    else -> "3. 【线上模式】你通过通讯终端与${op.name}文字聊天，回复要像打字聊天一样简洁。"
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

【输出格式要求】
严格输出纯JSON，不要添加任何其他文字、markdown标记或解释：
{"suggestions":["第一条承接话题的回复","第二条关心的回复","第三条行动邀约的回复"]}
""".trimIndent()
                val rawResult = withTimeout(10_000) { sharedUtils.chat(listOf(AiMessage("system", prompt))) }
                val base = rawResult.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val results = try { json.decodeFromString<SuggestionResponse>(base).suggestions.filter { it.isNotBlank() } } catch (_: Exception) {
                    try { json.decodeFromString<SuggestionResponse>(base.replace("，", ",").replace("：", ":")).suggestions.filter { it.isNotBlank() } } catch (_: Exception) { emptyList() }
                }
                callback(results.ifEmpty { listOf("嗯，我在听", "然后呢？", "有意思") })
            } catch (_: Exception) { callback(listOf("嗯，我在听", "然后呢？", "有意思")) }
        }
    }

    // === Private helpers ===

    private fun updateAiMessage(msgId: Long, content: String) {
        viewModelScope.launch {
            updateMutex.withLock {
                val now = System.currentTimeMillis()
                if (now - lastDbUpdate < 100) delay(100 - (now - lastDbUpdate))
                repository.updateMessageContent(msgId, content)
                lastDbUpdate = System.currentTimeMillis()
            }
            if (_currentSession.value != null) {
                _messages.value = _messages.value.map { if (it.id == msgId) it.copy(content = content) else it }
            }
        }
    }

    private suspend fun buildApiMessages(userContent: String = ""): List<AiMessage> {
        val session = _currentSession.value ?: return emptyList()
        val op = repository.getOperator(session.operatorId)
        val shortTerm = repository.getShortTermMemory(session.id)
        val longTerm = repository.getLongTermImpression(session.operatorId)
        val sharedMemories = repository.getSharedMemoriesForOperator(session.operatorId)
        val anchors = repository.getAnchors(session.operatorId)
        val nearby = appState.operators.value.filter { it.id != session.operatorId && it.id != "amiya" }.take(3)
        val profile = appState.userProfile.value
        val analysisBlock = if (settings.dualModel && analysisGuidance.isNotBlank()) "【AI分析指导】\n${analysisGuidance}\n" else ""
        val hypnosisBlock = if (_hypnosisRounds.value > 0) "【催眠状态】\n${_hypnosisCommand.value}\n剩余${_hypnosisRounds.value}轮\n" else ""
        val mindReadBlock = if (_mindReadRounds.value > 0) "【读心术生效中】\n你能看到${profile.nickname}的内心独白：${_mindReadContent.value}\n剩余${_mindReadRounds.value}轮\n" else ""
        val mode = _currentMode.value
        // 群聊回顾：找出该干员参与的各群聊 3 天内的短摘要
        val THREE_DAYS = 3 * 24 * 60 * 60 * 1000L
        val cutoff = System.currentTimeMillis() - THREE_DAYS
        val groupContext = repository.getAllSessionsSync().filter { s ->
            s.operatorId.startsWith("group_") &&
            s.members.split(",").map { it.trim() }.any { it == op?.id || it == op?.name }
        }.mapNotNull { s ->
            val summary = repository.getShortTermMemory(s.id)
            if (summary != null && summary.createdAt >= cutoff) {
                "- 在「${s.operatorName}」中：${summary.content.take(80)}"
            } else null
        }.joinToString("\n").ifBlank { "无" }
        val transitionNotice = if (modeTransitionNotice.isNotBlank()) "【场景变更】\n${modeTransitionNotice}\n" else ""
        val systemPrompt = sharedUtils.compactTemplate(sharedUtils.applyTemplate(getPromptTemplate("private", mode), mapOf(
            "CURRENT_TIME" to sharedUtils.beijingSdf("yyyy-MM-dd HH:mm").format(java.util.Date()),
            "USER_NAME" to profile.nickname, "USER_GENDER" to profile.gender.ifBlank { "未知" }, "USER_BIO" to profile.bio.ifBlank { "无" },
            "USER_CONTENT" to userContent, "AI_ANALYSIS" to analysisBlock,             "HYPNOSIS" to hypnosisBlock, "MIND_READ" to mindReadBlock,
            "TRANSITION_NOTICE" to transitionNotice,
            "OPERATOR_NAME" to (op?.name ?: session.operatorName), "OPERATOR_TITLE" to (op?.title ?: ""),
            "OPERATOR_PERSONA" to (op?.privatePrompt?.ifBlank { op.description } ?: ""),
            "CURRENT_LOCATION" to (op?.location ?: "宿舍"), "CURRENT_STATE" to (op?.activity ?: "休息"), "CURRENT_EMOTION" to (op?.emotion ?: "平静"),
            "LONG_TERM_IMPRESSION" to (longTerm?.content ?: "暂无"),
            "MEMORY_ANCHORS" to sharedUtils.pickAnchors(anchors, 5).joinToString("\n") { "- ${sharedUtils.anchorTimeLabel(it)} ${it.content}" }.ifBlank { "暂无" },
            "SHARED_MEMORIES" to sharedMemories.ifBlank { "无" },
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
        )))
        val messages = repository.getMessagesSync(session.id).let { msgs ->
            val limit = settings.historyMessages
            if (limit > 0) msgs.takeLast(limit) else msgs
        }.map { msg -> AiMessage(if (msg.isMe) "user" else "assistant", if (msg.isMe) "用户：${msg.content}" else msg.content) }.toMutableList()
        messages.add(0, AiMessage("system", systemPrompt))
        return messages
    }

    private fun generateDailyIfNeeded() {
        val today = sharedUtils.beijingSdf("yyyyMMdd").format(java.util.Date())
        val last = settings.dailySummaryDate
        if (today == last) return
        settings.dailySummaryDate = today
        viewModelScope.launch { generateDailySummary(java.util.Date(System.currentTimeMillis() - 86_400_000)) }
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
            if (content.isNotBlank()) { repository.saveMemory(Memory(sessionId = "daily_${dateStr}", operatorId = "daily", type = MemoryType.DAILY, content = content, expiresAt = System.currentTimeMillis() + settings.cleanDays * 86_400_000L)) }
        } catch (_: Exception) {}
    }

    private suspend fun generatePrivateDailySummary(operatorId: String) {
        try {
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
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
                    expiresAt = System.currentTimeMillis() + settings.cleanDays * 86_400_000L
                ))
            }
        } catch (_: Exception) {}
    }


    private suspend fun generateLongTermImpression(session: ChatSession) {
        try {
            val op = repository.getOperator(session.operatorId) ?: return
            val shortTerm = repository.getShortTermMemory(session.id)
            val anchors = repository.getAnchors(session.operatorId)
            val anchorText = sharedUtils.pickAnchors(anchors, 5).joinToString("\n") { "- ${it.content}" }
            val profile = appState.userProfile.value
            val oldImpression = repository.getLongTermImpression(session.operatorId)
            val oldImpressionText = oldImpression?.content ?: "无"
            val summaries = listOfNotNull(shortTerm?.content, anchorText.ifBlank { null }).joinToString("\n\n").ifBlank { "无" }
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
                    repository.saveMemory(Memory(sessionId = session.id, operatorId = session.operatorId, type = MemoryType.LONG_TERM, content = impression, keywords = keywords, preferences = preferences, taboos = taboos, createdAt = System.currentTimeMillis(), expiresAt = System.currentTimeMillis() + settings.cleanDays * 86_400_000L))
                }
            } catch (_: Exception) {
                if (rawResult.isNotBlank()) {
                    repository.saveMemory(Memory(sessionId = session.id, operatorId = session.operatorId, type = MemoryType.LONG_TERM, content = rawResult, createdAt = System.currentTimeMillis(), expiresAt = System.currentTimeMillis() + settings.cleanDays * 86_400_000L))
                }
            }
        } catch (_: Exception) {}
    }


    private fun dumpDebugState() {
        if (!DEBUG) return
        val aiTag = "AI调试输出"
        Log.d(aiTag, "╔══ 调试状态 ════════════════════════")
        Log.d(aiTag, "║ selectedOperator: ${_selectedOperator.value?.name}")
        Log.d(aiTag, "║ currentSession: ${_currentSession.value?.id}")
        Log.d(aiTag, "║ messages: ${_messages.value.size}")
        Log.d(aiTag, "║ currentMode: ${_currentMode.value}")
        Log.d(aiTag, "║ messageCounter: $messageCounter / $shortTermThreshold")
        Log.d(aiTag, "║ impressionMsgCounter: $impressionMsgCounter")
        Log.d(aiTag, "╚══════════════════════════════════════")
    }
}
