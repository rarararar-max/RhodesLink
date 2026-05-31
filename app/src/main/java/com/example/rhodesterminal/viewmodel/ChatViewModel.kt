package com.example.rhodesterminal.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rhodesterminal.shared.model.ChatMessage
import com.example.rhodesterminal.shared.model.ChatSession
import com.example.rhodesterminal.shared.model.MemoryAnchor
import com.example.rhodesterminal.shared.model.Memory
import com.example.rhodesterminal.shared.model.MemoryType
import com.example.rhodesterminal.shared.model.Operator
import com.example.rhodesterminal.shared.data.ChatRepository
import com.example.rhodesterminal.shared.model.AnalysisResult
import com.example.rhodesterminal.shared.model.SuggestionResponse
import com.example.rhodesterminal.shared.network.AIService
import com.example.rhodesterminal.shared.model.AiMessage
import com.example.rhodesterminal.viewmodel.shared.AppStateHolder
import com.example.rhodesterminal.viewmodel.shared.OperatorStateUpdater
import com.example.rhodesterminal.shared.settings.SettingsRepository
import com.example.rhodesterminal.viewmodel.shared.PromptTemplates
import com.example.rhodesterminal.viewmodel.shared.SharedUtils
import com.example.rhodesterminal.viewmodel.shared.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
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
    private var messageCounter = 0
    private var impressionMsgCounter = 0
    private val sessionMessageCounter = mutableMapOf<String, Int>()
    private val shortTermThreshold: Int get() = settings.summaryThreshold
    private val updateMutex = Mutex()
    private var lastDbUpdate = 0L
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
            val prompt = "请将以下聊天记录压缩为一段简短的滚动摘要（不超过200字），保留关键信息。直接输出纯文本。\n${text}"
            val sb = StringBuilder()
            withTimeout(15_000) { sharedUtils.streamChat(listOf(AiMessage("system", prompt)), "Memory").collect { sb.append(it) } }
            sharedUtils.trackTokens("memory", prompt, sb.toString())
            val content = sb.toString().trim()
            if (content.isNotBlank()) {
                repository.saveMemory(Memory(sessionId = session.id, operatorId = session.operatorId, type = MemoryType.SHORT_TERM, content = content, expiresAt = System.currentTimeMillis() + settings.cleanDays * 86_400_000L))
            }
        } catch (_: Exception) {}
    }

    // === Public API ===

    fun selectOperator(operator: Operator) {
        _selectedOperator.value = operator
        messageCounter = 0
        _hypnosisCommand.value = ""
        _hypnosisRounds.value = 0
        _mindReadRounds.value = 0
        _mindReadContent.value = ""
        settings.hypnosisCmd = ""
        settings.hypnosisRound = 0
        viewModelScope.launch {
            val session = repository.getOrCreateSession(operator.id, operator.name, operator.avatarUri)
            _currentSession.value = session
            val savedMode = settings.lastMode
            _currentMode.value = savedMode
            markSessionRead(session.id)
            messagesJob?.cancel()
            messagesJob = viewModelScope.launch {
                repository.getMessages(session.id).collect { msgs -> _messages.value = msgs }
            }
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
            settings.lastMode = mode
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
        if (text.isEmpty() || session.id in _loadingSessions.value) return
        if (sharedUtils.getApiKey().isBlank()) {
            onShowToast("请先在设置中配置 API Key")
            return
        }
        _inputText.value = ""
        generateDailyIfNeeded()
        messageCounter++

        viewModelScope.launch {
            repository.sendMessage(session.id, ChatMessage(
                id = repository.getNextMessageId(), sessionId = session.id,
                senderName = "我", content = text, type = "text", mode = _currentMode.value, isMe = true
            ))
            val aiMsgId = repository.getNextMessageId()
            repository.sendMessage(session.id, ChatMessage(
                id = aiMsgId, sessionId = session.id,
                senderName = session.operatorName, content = "...", type = "ai_json", mode = _currentMode.value, isMe = false
            ))
            _loadingSessions.value = _loadingSessions.value + session.id

            try {
                if (settings.dualModel) {
                    analysisGuidance = ""
                    try {
                        val analysisSb = StringBuilder()
                        val profile = appState.userProfile.value
                        withTimeout(15_000) {
                            val prompt = buildString {
                                append("你是罗德岛的资深心理顾问与战术分析员。分析用户最新消息的深层意图、情绪和需求，为干员回应提供策略指导。\n\n")
                                append("当前系统时间：${sharedUtils.beijingSdf("HH:mm").format(java.util.Date())}\n")
                                append("用户最新消息：${text}\n用户信息：${profile.nickname}，${profile.gender}\n干员：${session.operatorName}\n")
                                append("最近对话：${_messages.value.takeLast(6).joinToString("\\n") { m -> "${if (m.isMe) "用户" else "你"}：${m.content}" }}\n")
                                append("当前模式：${_currentMode.value}\n\n")
                                append("输出JSON：{\"intent_analysis\":\"\",\"user_emotion\":\"\",\"user_need\":\"\",\"suggested_emotion\":\"\",\"reply_guidance\":\"\",\"affection_mod\":0}")
                            }
                            sharedUtils.streamChat(listOf(AiMessage("system", prompt)), "Chat").collect { analysisSb.append(it) }
                        }
                        val result = sharedUtils.aiService.cleanJson(analysisSb.toString())
                        val analysis: AnalysisResult? = try { json.decodeFromString<AnalysisResult>(result) } catch (_: Exception) { null }
                        if (analysis != null) {
                            analysisGuidance = "【用户意图分析】${analysis.intent_analysis}\n【用户情绪】${analysis.user_emotion}\n【核心需求】${analysis.user_need}\n【建议干员情绪】${analysis.suggested_emotion}\n【回复策略】${analysis.reply_guidance}\n【好感度修正】${analysis.affection_mod}"
                        }
                    } catch (_: Exception) { analysisGuidance = "" }
                }

                val apiMessages = buildApiMessages(text)
                val sb = StringBuilder()
                withTimeout(60_000) { sharedUtils.streamChat(apiMessages).collect { chunk -> sb.append(chunk) } }
                val promptText = apiMessages.firstOrNull()?.content ?: ""
                sharedUtils.trackTokens("private", promptText, sb.toString())
                val mode = _currentMode.value
                val rawJson = sharedUtils.aiService.cleanJson(sb.toString().trim())
                var aiResponseCount = 1
                if (rawJson.isNotBlank()) {
                    repository.sendMessage(session.id, ChatMessage(id = aiMsgId, sessionId = session.id, senderName = session.operatorName, content = rawJson, type = "ai_json", mode = mode, isMe = false))
                    if (_currentSession.value?.id == session.id) {
                        _messages.value = _messages.value.map { if (it.id == aiMsgId) it.copy(content = rawJson, type = "ai_json") else it }
                    }
                    val parsed = sharedUtils.aiService.parseOfflineResponse(rawJson)
                    if (parsed.emotion.isNotBlank() || parsed.location.isNotBlank() || parsed.state.isNotBlank()) {
                        operatorStateUpdater.updateOperatorStatus(session.operatorId, parsed.location, parsed.state, parsed.emotion) { opId, newLoc, newAct, newEmo ->
                            if (opId == _selectedOperator.value?.id) {
                                _selectedOperator.value = _selectedOperator.value?.copy(location = newLoc, activity = newAct, emotion = newEmo)
                            }
                        }
                    }
                    aiResponseCount = 1
                }
                val affectionMod = try { val obj = Json.parseToJsonElement(sb.toString()).jsonObject; obj["affection_mod"]?.jsonPrimitive?.int ?: 0 } catch (_: Exception) { 0 }
                operatorStateUpdater.updateOperatorIntimacy(session.operatorId, 1 + affectionMod.coerceIn(-3, 3))
                val today = settings.rewardDate
                val currentDate = sharedUtils.beijingSdf("yyyyMMdd").format(java.util.Date())
                if (today != currentDate) { settings.rewardDate = currentDate; settings.dailyLmbCount = 0 }
                val dailyCount = settings.dailyLmbCount
                if (dailyCount < 2000) { val balance = settings.lmb; settings.lmb = balance + 10; settings.dailyLmbCount = dailyCount + 1 }
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
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) { updateAiMessage(aiMsgId, "响应超时，请重试")
            } catch (e: Exception) { updateAiMessage(aiMsgId, "错误: ${e.message}")
            } finally { _loadingSessions.value = _loadingSessions.value - session.id }
        }
    }

    fun recallMessage(msgId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(msgId)
            _messages.value = _messages.value.filter { it.id != msgId }
        }
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
            val aiMsgId = repository.getNextMessageId()
            repository.sendMessage(session.id, ChatMessage(id = aiMsgId, sessionId = session.id, senderName = session.operatorName, content = "...", type = "ai_json", mode = mode, isMe = false))
            _loadingSessions.value = _loadingSessions.value + session.id
            val previousUser = _messages.value.take(idx).lastOrNull { it.isMe }
            modeTransitionNotice = "【继续指令】请自然地继续说下去，不要复述或总结之前说过的话。"
            try {
                val apiMessages = buildApiMessages(previousUser?.content ?: "")
                val sb = StringBuilder()
                sharedUtils.streamChat(apiMessages).collect { chunk -> sb.append(chunk) }
                val raw = sb.toString().trim()
                repository.sendMessage(session.id, ChatMessage(id = aiMsgId, sessionId = session.id, senderName = session.operatorName, content = raw, type = "ai_json", mode = mode, isMe = false))
                if (_currentSession.value?.id == session.id) { _messages.value = _messages.value.map { if (it.id == aiMsgId) it.copy(content = raw, type = "ai_json") else it } }
                val parsed = sharedUtils.aiService.parseOfflineResponse(raw)
                if (parsed.emotion.isNotBlank() || parsed.location.isNotBlank() || parsed.state.isNotBlank()) {
                    operatorStateUpdater.updateOperatorStatus(session.operatorId, parsed.location, parsed.state, parsed.emotion) { opId, newLoc, newAct, newEmo ->
                        if (opId == _selectedOperator.value?.id) { _selectedOperator.value = _selectedOperator.value?.copy(location = newLoc, activity = newAct, emotion = newEmo) }
                    }
                }
            } catch (e: Exception) { updateAiMessage(aiMsgId, "错误: ${e.message}")
            } finally { _loadingSessions.value = _loadingSessions.value - session.id; modeTransitionNotice = "" }
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
                val modeHint = when (_currentMode.value) { "offline" -> "线下模式"; "director" -> "导演模式"; else -> "线上模式" }
                val prompt = "你是对话灵感生成器。用户${profile.nickname}与${op.name}的最近15条对话：\n${recent}\n${op.name}刚说：${lastOpMsg}\n模式：${modeHint}\n生成3条回复话术，JSON格式：{\"suggestions\":[\"...\"]}"
                val sb = StringBuilder()
                withTimeout(10_000) { sharedUtils.streamChat(listOf(AiMessage("system", prompt))).collect { sb.append(it) } }
                val cleaned = sb.toString().trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim().replace("，", ",").replace("：", ":")
                val results = try { val resp = json.decodeFromString<SuggestionResponse>(cleaned); resp.suggestions.filter { it.isNotBlank() } } catch (_: Exception) { emptyList() }
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
        val systemPrompt = sharedUtils.applyTemplate(getPromptTemplate("private", mode), mapOf(
            "CURRENT_TIME" to sharedUtils.beijingSdf("yyyy-MM-dd HH:mm").format(java.util.Date()),
            "USER_NAME" to profile.nickname, "USER_GENDER" to profile.gender.ifBlank { "未知" }, "USER_BIO" to profile.bio.ifBlank { "无" },
            "USER_CONTENT" to userContent, "AI_ANALYSIS" to analysisBlock, "HYPNOSIS" to hypnosisBlock, "MIND_READ" to mindReadBlock,
            "OPERATOR_NAME" to (op?.name ?: session.operatorName), "OPERATOR_TITLE" to (op?.title ?: ""),
            "OPERATOR_PERSONA" to (op?.privatePrompt?.ifBlank { op.description } ?: ""),
            "CURRENT_LOCATION" to (op?.location ?: "宿舍"), "CURRENT_STATE" to (op?.activity ?: "休息"), "CURRENT_EMOTION" to (op?.emotion ?: "平静"),
            "LONG_TERM_IMPRESSION" to (longTerm?.content ?: "暂无"),
            "MEMORY_ANCHORS" to sharedUtils.pickAnchors(anchors, 5).joinToString("\n") { "- ${sharedUtils.anchorTimeLabel(it)} ${it.content}" }.ifBlank { "暂无" },
            "SHARED_MEMORIES" to sharedMemories.ifBlank { "无" },
            "DAILY_SUMMARY" to (repository.getLatestDaily()?.content ?: "无"),
            "SHORT_TERM_SUMMARY" to (shortTerm?.content ?: "无"),
            "NEARBY_OPERATORS" to nearby.joinToString("\n") { "- ${it.name}正在${it.location}${it.activity}，${it.emotion}" }.ifBlank { "" },
            "USER_RELATION" to (op?.userRelation?.ifBlank { "未知" } ?: "未知"),
            "NAR_SEG_MIN" to settings.narSegMin.toString(), "NAR_SEG_MAX" to settings.narSegMax.toString(),
            "NAR_MIN" to settings.narMin.toString(), "NAR_MAX" to settings.narMax.toString(),
            "DIA_SEG_MIN" to settings.diaSegMin.toString(), "DIA_SEG_MAX" to settings.diaSegMax.toString(),
            "DIA_MIN" to settings.diaMin.toString(), "DIA_MAX" to settings.diaMax.toString(),
            "SEG_MIN" to (settings.narSegMin + settings.diaSegMin).toString(),
            "SEG_MAX" to (settings.narSegMax + settings.diaSegMax).toString()
        ))
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
            val sb = StringBuilder()
            withTimeout(15_000) { sharedUtils.streamChat(listOf(AiMessage("system", prompt)), "Memory").collect { sb.append(it) } }
            sharedUtils.trackTokens("memory", prompt, sb.toString())
            val content = sb.toString().trim()
            if (content.isNotBlank()) { repository.saveMemory(Memory(sessionId = "daily_${dateStr}", operatorId = "daily", type = MemoryType.DAILY, content = content, expiresAt = System.currentTimeMillis() + settings.cleanDays * 86_400_000L)) }
        } catch (_: Exception) {}
    }


    private suspend fun generateLongTermImpression(session: ChatSession) {
        try {
            val op = repository.getOperator(session.operatorId) ?: return
            val shortTerm = repository.getShortTermMemory(session.id)
            val anchors = repository.getAnchors(session.operatorId)
            val anchorText = sharedUtils.pickAnchors(anchors, 5).joinToString("\n") { "- ${it.content}" }
            val profile = appState.userProfile.value
            val prompt = """你是记忆分析员。基于以下信息，为${op.name}对${profile.nickname}的长期印象写一段简洁总结（50-100字）。

干员：${op.name}，${op.description}
用户：${profile.nickname}，${profile.bio}
近期聊天摘要：${shortTerm?.content ?: "无"}
近期事件：${anchorText}

直接输出印象总结文本。"""
            val sb = StringBuilder()
            withTimeout(15_000) { sharedUtils.streamChat(listOf(AiMessage("system", prompt)), "Memory").collect { sb.append(it) } }
            sharedUtils.trackTokens("memory", prompt, sb.toString())
            val content = sb.toString().trim()
            if (content.isNotBlank()) {
                repository.saveMemory(Memory(sessionId = session.id, operatorId = session.operatorId, type = MemoryType.LONG_TERM, content = content, expiresAt = System.currentTimeMillis() + settings.cleanDays * 86_400_000L))
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
