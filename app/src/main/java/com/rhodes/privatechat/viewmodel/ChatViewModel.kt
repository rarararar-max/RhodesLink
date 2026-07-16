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
import com.rhodes.privatechat.shared.model.MemoryLevel
import com.rhodes.privatechat.shared.model.MemorySourceKind
import com.rhodes.privatechat.shared.model.MemoryType
import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.memory.AnchorSourcePolicy
import com.rhodes.privatechat.shared.model.AnalysisResult
import com.rhodes.privatechat.shared.model.SuggestionResponse
import com.rhodes.privatechat.shared.model.UnifiedMemoryResponse
import com.rhodes.privatechat.shared.network.AIService
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.shared.modelgateway.VisionAnalyzeRequest
import com.rhodes.privatechat.shared.modelgateway.VisionGateway
import com.rhodes.privatechat.shared.modelgateway.createVisionGateway
import com.rhodes.privatechat.shared.vector.MemoryVectorService
import com.rhodes.privatechat.shared.vector.VectorMemory
import com.rhodes.privatechat.util.ChatTrace
import com.rhodes.privatechat.util.DebugLogger
import com.rhodes.privatechat.notification.RhodesAppVisibility
import com.rhodes.privatechat.notification.RhodesNotificationCenter
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.viewmodel.shared.OperatorStateUpdater
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.shared.PromptTemplates
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.rhodes.privatechat.viewmodel.shared.UserProfile
import com.rhodes.privatechat.viewmodel.shared.MemoryPolicy
import com.rhodes.privatechat.viewmodel.shared.MemorySurface
import com.rhodes.privatechat.viewmodel.shared.MemoryV2Pipeline
import com.rhodes.privatechat.viewmodel.shared.MemoryContextBuilder
import com.rhodes.privatechat.viewmodel.shared.UnifiedMemoryContext
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
    private val memoryVectorService: MemoryVectorService? = null,
    private val visionGateway: VisionGateway? = null,
    private val onShowToast: (String) -> Unit,
    private val onUnhideSession: suspend (String) -> Unit,
    private val onRefreshOperatorStatus: suspend () -> Unit
) : AndroidViewModel(application) {
    companion object {
        const val DEBUG = false
        private const val CHAT_PAGE_SIZE = 50L
    }

    // === Chat state ===
    private val _selectedOperator = MutableStateFlow<Operator?>(null)
    val selectedOperator: StateFlow<Operator?> = _selectedOperator.asStateFlow()

    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoadingOlderMessages = MutableStateFlow(false)
    val isLoadingOlderMessages: StateFlow<Boolean> = _isLoadingOlderMessages.asStateFlow()

    private val _hasMoreMessages = MutableStateFlow(true)
    val hasMoreMessages: StateFlow<Boolean> = _hasMoreMessages.asStateFlow()

    private val _sessionRestartAt = MutableStateFlow(0L)
    val sessionRestartAt: StateFlow<Long> = _sessionRestartAt.asStateFlow()

    private val _scrollToMessageId = MutableStateFlow<Long?>(null)
    val scrollToMessageId: StateFlow<Long?> = _scrollToMessageId.asStateFlow()

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
    private val activeRequestBySession = ConcurrentHashMap<String, Long>()
    private val requestSequence = java.util.concurrent.atomic.AtomicLong(0L)
    private fun beginLoading(sessionId: String, requestId: Long) {
        activeRequestBySession[sessionId] = requestId
        _loadingSessions.update { it + sessionId }
    }
    private fun finishLoading(sessionId: String, requestId: Long) {
        if (activeRequestBySession.remove(sessionId, requestId)) {
            _loadingSessions.update { it - sessionId }
        }
    }
    private var modeTransitionNotice = ""
    private var messagesJob: Job? = null
    private val chatAiJobs = ConcurrentHashMap<String, Job>()
    private val pageSize: Long get() = CHAT_PAGE_SIZE
    private val memoryV2Pipeline = MemoryV2Pipeline(repository, settings, sharedUtils.aiService, memoryVectorService) { appState.userProfile.value.nickname }
    private val memoryContextBuilder = MemoryContextBuilder(settings, memoryVectorService)

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
            val allMsgs = messageSource ?: repository.getMessagesSync(session.id)
            val retain = settings.summaryRetain.coerceAtLeast(1)
            val cursor = if (settings.summaryCursorEnabled && messageSource == null) settings.getSummaryCursor(session.id) else 0L
            val scopedMsgs = if (cursor > 0L) allMsgs.filter { it.id > cursor } else allMsgs
            val recent = scopedMsgs.takeLast(retain)
            val older = scopedMsgs.dropLast(retain)
            if (older.isEmpty()) {
                DebugLogger.log("Memory/Summary", "跳过短期摘要: session=${session.id}, totalMsgs=${allMsgs.size}, scoped=${scopedMsgs.size}, retain=$retain, older=0")
                return
            }
            val oldSummary = repository.getShortTermMemory(session.id)?.content?.takeIf { it.isNotBlank() } ?: "无"
            val text = older.joinToString("\n") { message ->
                val raw = formatPrivateMessageForMemory(message, if (message.isMe) 380 else 220)
                val important = listOf("不要", "别", "喜欢", "讨厌", "答应", "约定", "明天", "记得", "秘密", "害怕", "难受", "以后")
                if (message.isMe && important.any { message.content.contains(it) }) raw.take(500) else raw
            }
            DebugLogger.log("Memory/Summary", "开始短期摘要: session=${session.id}, operator=${session.operatorName}, totalMsgs=${allMsgs.size}, scoped=${scopedMsgs.size}, older=${older.size}, retain=$retain, oldSummary=${oldSummary != "无"}")
            val prompt = if (settings.unifiedMemoryEnabled) """
你是罗德岛的随行记录员，负责把角色真正会记住的事整理成可长期使用的记忆。

请融合“已有摘要”和“新增对话”，生成一份连续摘要和若干高价值记忆锚点。目标是让${session.operatorName}下次聊天时能自然想起具体事情，而不是背诵流水账。

输出JSON：{"summary":"150~250字四段结构摘要；复杂时最多300字","anchors":[]}

            摘要规则：
            - summary：默认150~250字；仅当存在多项承诺、边界或未解决事项时可到300字。必须按以下四段输出：
              【稳定事实与偏好】用户明确表达且仍有效的偏好、边界；无则写“无”。
              【近期事件与情绪】本轮真正发生的事、双方情绪或关系变化。
              【约定、提醒与未解决事项】明确约定、待办、未来时间点；已完成的不要保留。
              【下次可自然接续的话题】一个具体可继续的话题或行动。
              不要写聊天流水账，不要为了凑长度重复。
- 如果已有摘要和新增对话冲突，以新增对话为准，改写旧理解，不要并列保留矛盾信息。

锚点规则：
- anchors：固定输出空数组。正式记忆由统一 L1/L2/L3 管道处理。
- content：30字内，必须具体到“用户喜欢什么/约定了什么/发生了什么/谁对谁态度变化”，避免“聊得很开心”这种空泛句。
- type：event=具体事件，preference=用户偏好，plan=约定/待办，emotion=重要情绪，taboo=禁忌/边界，relation=关系变化。
- preference/taboo 只能记录用户明确表达的偏好和边界，不能把干员自己的习惯、职业、人设当作用户偏好。
- isPrivate=true：用户负面情绪、隐私、自我怀疑、亲密/暧昧内容、明确“不想让别人知道”的内容；普通日常、公开约定、轻松正向互动可为false。
- importance：strong=会影响后续互动的重要偏好/禁忌/承诺/关系变化；medium=近期可接话题；weak=普通小事。

禁止：
- content 和 summary 中禁止出现“好感度提升/下降”“affection”“系统数值”“锚点”“摘要”等系统机制词。

已有摘要：
${oldSummary}

新增对话：
${text}""" else """
你是罗德岛的随行记录员，负责把角色真正会记住的事整理成可长期使用的记忆。

请融合“已有摘要”和“新增对话”，生成一份连续的新摘要和高价值记忆锚点。目标是让${session.operatorName}下次聊天时能自然想起具体事情，而不是背诵流水账。

输出JSON：{"summary":"150~250字四段结构摘要；复杂时最多300字","anchors":[]}

字段说明：
- summary：默认150~250字；仅当存在多项承诺、边界或未解决事项时可到300字。固定输出【稳定事实与偏好】【近期事件与情绪】【约定、提醒与未解决事项】【下次可自然接续的话题】四段；无内容的段写“无”。旧理解冲突时以新对话改写。
- anchors：固定输出空数组。正式记忆由统一 L1/L2/L3 管道处理。
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
                repository.replaceShortTermMemory(Memory(
                    sessionId = session.id, operatorId = session.operatorId,
                    type = MemoryType.SHORT_TERM, content = parsed.summary,
                    keywords = parsed.keywords.joinToString(","),
                    createdAt = now,
                    expiresAt = MemoryPolicy.memoryExpiresAt(settings)
                ))
                ingestPrivateMemoryV2(session, older)
                if (settings.summaryCursorEnabled) older.maxOfOrNull { it.id }?.let { settings.putSummaryCursor(session.id, it) }
            }
        } catch (e: Exception) {
            DebugLogger.log("Memory/Summary", "短期摘要生成失败: ${e.message?.take(120)}")
        }
    }

    // === Public API ===

    fun selectOperator(operator: Operator) {
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
        viewModelScope.launch {
            val session = repository.getOrCreateSession(operator.id, operator.name, operator.avatarUri)
            val sameSession = _currentSession.value?.id == session.id
            ChatTrace.d("ChatVM", "select op=${operator.id} session=${session.id} sameSession=$sameSession jobActive=${messagesJob?.isActive}")
            _currentSession.value = session
            _sessionRestartAt.value = settings.getSessionRestartAt(session.id)
            _currentMode.value = settings.getLastMode(operator.id)
            markSessionRead(session.id)
            if (sameSession && messagesJob?.isActive == true) return@launch
            if (sameSession) {
                ChatTrace.d("ChatVM", "select restarting dead job for session=${session.id}")
            }
            messagesJob?.cancel()
            if (!sameSession) {
                _hasMoreMessages.value = true
            }
            messagesJob = viewModelScope.launch {
                try {
                    repository.getRecentMessages(session.id, pageSize).collect { msgs ->
                        ChatTrace.d("ChatVM", "flow session=${session.id} count=${msgs.size}")
                        mergeMessagesFromFlow(msgs)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    ChatTrace.d("ChatVM", "flow.CANCEL session=${session.id}")
                } catch (e: Exception) {
                    ChatTrace.e("ChatVM", "flow.ERROR session=${session.id} err=${e.message}", e)
                }
            }
        }
    }

    suspend fun selectOperatorSync(operator: Operator) {
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
            val sameSession = _currentSession.value?.id == session.id
            ChatTrace.d("ChatVM", "selectSync op=${operator.id} session=${session.id} sameSession=$sameSession jobActive=${messagesJob?.isActive}")
            _currentSession.value = session
            _sessionRestartAt.value = settings.getSessionRestartAt(session.id)
            _currentMode.value = settings.getLastMode(operator.id)
            markSessionRead(session.id)
            if (sameSession && messagesJob?.isActive == true) return
            if (sameSession) {
                ChatTrace.d("ChatVM", "selectSync restarting dead job for session=${session.id}")
            }
            messagesJob?.cancel()
            if (!sameSession) {
                _hasMoreMessages.value = true
            }
            messagesJob = viewModelScope.launch {
                try {
                    repository.getRecentMessages(session.id, pageSize).collect { msgs ->
                        ChatTrace.d("ChatVM", "flowSync session=${session.id} count=${msgs.size}")
                        mergeMessagesFromFlow(msgs)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    ChatTrace.d("ChatVM", "flowSync.CANCEL session=${session.id}")
                } catch (e: Exception) {
                    ChatTrace.e("ChatVM", "flowSync.ERROR session=${session.id} err=${e.message}", e)
                }
            }
        } catch (e: Exception) {
            ChatTrace.e("ChatVM", "selectSync.ERROR op=${operator.id} err=${e.message}", e)
            _selectedOperator.value = operator
        }
    }

    fun clearSelection() {
        _selectedOperator.value = null
        _currentSession.value = null
        _messages.value = emptyList()
        _sessionRestartAt.value = 0L
        _hasMoreMessages.value = true
        _isLoadingOlderMessages.value = false
    }

    fun clearMessages() {
        restartSession()
    }

    fun restartSession() {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            chatAiJobs.remove(session.id)?.cancel()
            activeRequestBySession.remove(session.id)
            _loadingSessions.update { it - session.id }
            val now = System.currentTimeMillis()
            settings.putSessionRestartAt(session.id, now)
            settings.putSummaryCursor(session.id, 0L)
            _sessionRestartAt.value = now
            repository.clearSessionPreview(session.id, now)
            repository.sendMessage(session.id, ChatMessage(
                id = repository.getNextMessageId(),
                sessionId = session.id,
                senderName = "系统",
                content = "新的篇章从这里开始。上方为已归档历史，后续回复只参考本篇章内容。",
                type = "system",
                mode = _currentMode.value,
                timestamp = now,
                isMe = false
            ))
        }
    }

    fun erasePrivateSessionAndRestart() {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            chatAiJobs.remove(session.id)?.cancel()
            activeRequestBySession.remove(session.id)
            _loadingSessions.update { it - session.id }
            runCatching {
                val now = System.currentTimeMillis()
                val privateV2Vectors = repository.erasePrivateRelationship(
                    session.operatorId, session.id, repository.getNextMessageId(), _currentMode.value, now
                )
                settings.putSessionRestartAt(session.id, 0L)
                settings.putSummaryCursor(session.id, 0L)
                _sessionRestartAt.value = 0L
                repository.clearSessionPreview(session.id, now)
                privateV2Vectors.forEach { memoryVectorService?.deleteMemory(it) }
            }.onFailure { error ->
                DebugLogger.log("Memory/Erase", "清空私聊关系失败: ${error.message?.take(100)}")
            }
        }
    }

    /** 通话结束后插入一条系统记录，如 "📞 语音通话 01:23" */
    fun saveCallSummary(sessionId: String, durationSeconds: Int) {
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        val content = "📞 语音通话 ${minutes}:${seconds.toString().padStart(2, '0')}"
        Log.d("RHODES_AUDIO", "saveCallSummary: sessionId=$sessionId content=$content")
        viewModelScope.launch {
            repository.sendMessage(sessionId, ChatMessage(
                id = repository.getNextMessageId(),
                sessionId = sessionId,
                senderName = "系统",
                content = content,
                type = "system",
                mode = "online",
                isMe = false,
                timestamp = System.currentTimeMillis()
            ))
        }
    }

    fun saveVoiceExchange(userText: String, operatorText: String, source: String) {
        val session = _currentSession.value ?: return
        if (userText.isBlank() || operatorText.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.sendMessage(session.id, ChatMessage(id = repository.getNextMessageId(), sessionId = session.id, senderName = "我", content = userText, type = source, mode = _currentMode.value, isMe = true, timestamp = now))
            repository.sendMessage(session.id, ChatMessage(id = repository.getNextMessageId(), sessionId = session.id, senderName = session.operatorName, content = operatorText, type = "text", mode = _currentMode.value, isMe = false, timestamp = now + 1))
        }
    }

    suspend fun searchCurrentSessionMessages(keyword: String, limit: Long = 200): List<ChatMessage> {
        val session = _currentSession.value ?: return emptyList()
        val q = keyword.trim()
        if (q.isBlank()) return emptyList()
        return repository.searchMessagesInSession(session.id, q, limit)
    }

    suspend fun getCurrentSessionMessageDates(): List<String> {
        val session = _currentSession.value ?: return emptyList()
        return repository.getMessageDatesBySession(session.id)
    }

    suspend fun getCurrentSessionMessagesByDate(date: String): List<ChatMessage> {
        val session = _currentSession.value ?: return emptyList()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
        }
        val start = runCatching { sdf.parse(date)?.time ?: 0L }.getOrDefault(0L)
        if (start <= 0L) return emptyList()
        return repository.getMessagesBySessionInRange(session.id, start, start + 86_400_000L - 1)
    }

    fun jumpToCurrentSessionMessage(messageId: Long) {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            val all = repository.getMessagesSync(session.id)
            if (all.isNotEmpty()) {
                _messages.value = all.distinctBy { it.id }.sortedWith(compareBy<ChatMessage> { it.timestamp }.thenBy { it.id })
                _hasMoreMessages.value = false
            }
            _scrollToMessageId.value = messageId
        }
    }

    fun consumeScrollTarget() {
        _scrollToMessageId.value = null
    }

    fun loadOlderMessages() {
        val session = _currentSession.value ?: return
        val first = _messages.value.firstOrNull() ?: return
        if (_isLoadingOlderMessages.value || !_hasMoreMessages.value) return
        viewModelScope.launch {
            _isLoadingOlderMessages.value = true
            try {
                val older = repository.getMessagesBefore(session.id, first.timestamp, first.id, pageSize)
                ChatTrace.d("ChatVM", "loadOlder session=${session.id} before=${first.id}/${first.timestamp} result=${older.size} ids=${ChatTrace.ids(older.map { it.id })}")
                if (older.isEmpty() || older.size < pageSize) _hasMoreMessages.value = false
                if (older.isNotEmpty()) {
                    _messages.value = (older + _messages.value).distinctBy { it.id }.sortedWith(compareBy<ChatMessage> { it.timestamp }.thenBy { it.id })
                }
            } catch (e: Exception) {
                DebugLogger.log("Chat/Paging", "加载历史消息失败: ${e.message}")
            } finally {
                _isLoadingOlderMessages.value = false
            }
        }
    }

    private fun mergeMessagesFromFlow(messages: List<ChatMessage>) {
        val sortedIncoming = messages.distinctBy { it.id }.sortedWith(compareBy<ChatMessage> { it.timestamp }.thenBy { it.id })
        val olderLoaded = sortedIncoming.firstOrNull()?.let { firstRecent ->
            _messages.value.filter { it.timestamp < firstRecent.timestamp || (it.timestamp == firstRecent.timestamp && it.id < firstRecent.id) }
        } ?: emptyList()
        val merged = (olderLoaded + sortedIncoming)
            .distinctBy { it.id }
            .sortedWith(compareBy<ChatMessage> { it.timestamp }.thenBy { it.id })
        _messages.value = merged
        if (merged.size == sortedIncoming.size) {
            _hasMoreMessages.value = sortedIncoming.size >= pageSize
        }
        ChatTrace.d("ChatVM", "merge incoming=${sortedIncoming.size} total=${merged.size} hasMore=${_hasMoreMessages.value}")
    }

    fun updateInputText(text: String) { _inputText.value = text }

    suspend fun sharedChatForFeature(messages: List<AiMessage>): String = sharedUtils.chat(messages, "FeatureChat")

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

    private suspend fun markUnreadIfNotCurrent(sessionId: String, count: Int = 1) {
        if ((_currentSession.value?.id ?: "") != sessionId) {
            repository.incrementUnread(sessionId, count)
            onUnhideSession(sessionId)
        }
    }

    private fun visiblePrivateSegmentCount(parsed: com.rhodes.privatechat.shared.model.OfflineModeResponse, mode: String): Int {
        val segments = parsed.segments.orEmpty().filter { it.content.isNotBlank() }
        val count = if (mode == "online") segments.count { it.type != "narration" } else segments.size
        return count.coerceAtLeast(if (parsed.dialogue.isNotBlank()) 1 else 0).coerceAtLeast(1)
    }

    private fun ensureVisiblePrivateReply(
        parsed: com.rhodes.privatechat.shared.model.OfflineModeResponse,
        mode: String
    ): com.rhodes.privatechat.shared.model.OfflineModeResponse {
        val source = parsed.segments.orEmpty().filter { it.content.isNotBlank() }
        if (mode == "online") {
            val dialogue = source.filterNot { it.type.equals("narration", true) }
            val fallback = parsed.dialogue.ifBlank { dialogue.firstOrNull()?.content.orEmpty() }.trim()
            return if (fallback.isBlank()) parsed else parsed.copy(dialogue = "", segments = listOf(com.rhodes.privatechat.shared.model.Segment(type = "dialogue", content = fallback)))
        }
        if (source.isEmpty()) return parsed
        val normalized = mutableListOf<com.rhodes.privatechat.shared.model.Segment>()
        val actionPrefix = Regex("""^[（(]([^）)]{1,180})[）)]\s*""")
        for (segment in source) {
            val content = segment.content.trim()
            if (segment.type.equals("dialogue", true)) {
                val match = actionPrefix.find(content)
                if (match != null) {
                    normalized += com.rhodes.privatechat.shared.model.Segment("narration", match.groupValues[1])
                    val spoken = content.removeRange(match.range).trim()
                    if (spoken.isNotBlank()) normalized += com.rhodes.privatechat.shared.model.Segment("dialogue", spoken)
                } else {
                    normalized += com.rhodes.privatechat.shared.model.Segment("dialogue", content)
                }
            } else {
                normalized += com.rhodes.privatechat.shared.model.Segment("narration", content)
            }
        }
        val merged = normalized.fold(mutableListOf<com.rhodes.privatechat.shared.model.Segment>()) { acc, segment ->
            val previous = acc.lastOrNull()
            if (previous?.type == segment.type) acc[acc.lastIndex] = previous.copy(content = "${previous.content}\n${segment.content}") else acc += segment
            acc
        }
        if (merged.none { it.type == "dialogue" }) return parsed
        if (merged.lastOrNull()?.type != "dialogue") {
            val fallback = parsed.dialogue.ifBlank { "嗯，我在听。" }
            merged += com.rhodes.privatechat.shared.model.Segment("dialogue", fallback)
        }
        return parsed.copy(dialogue = "", narration = "", segments = merged)
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
        val requestId = requestSequence.incrementAndGet()
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
                DebugLogger.log("Chat/DB", "用户消息已写入, session=${session.id}, id=$msgId, length=${text.length}")
                aiMsgId = repository.getNextMessageId()
                DebugLogger.log("Chat/DB", "AI消息ID已获取, aiMsgId=$aiMsgId")
                beginLoading(session.id, requestId)

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
                        logPrivatePromptTrace(
                            stage = "REQUEST",
                            sessionId = session.id,
                            operatorName = session.operatorName,
                            mode = mode,
                            messages = apiMessages
                        )
                        var parsed = withTimeout(90_000) { sharedUtils.chatWithRetry(apiMessages) }
                        parsed = ensureVisiblePrivateReply(parsed, mode)
                        logPrivatePromptTrace(
                            stage = "RESPONSE",
                            sessionId = session.id,
                            operatorName = session.operatorName,
                            mode = mode,
                            response = parsed.toString()
                        )
                        if (parsed.dialogue.isNotEmpty() || parsed.emotion.isNotEmpty()) {
                            DebugLogger.log("Chat/AI", "AI响应成功, emotion=${parsed.emotion}, dialogue=${replyPreview(parsed).take(40)}")
                            sharedUtils.trackTokens("private", apiMessages, parsed.toString())
                        } else {
                            DebugLogger.log("Chat/AI", "AI返回为空或降级，跳过token统计")
                        }
                        val serializedJson = try { json.encodeToString(com.rhodes.privatechat.shared.model.OfflineModeResponse.serializer(), parsed) } catch (_: Exception) { parsed.toString() }
                        val rawJson = sharedUtils.aiService.cleanJson(serializedJson)
                        var aiResponseCount = visiblePrivateSegmentCount(parsed, mode)
                        if (rawJson.isNotBlank()) {
                            repository.sendMessage(session.id, ChatMessage(id = aiMsgId, sessionId = session.id, senderName = session.operatorName, content = rawJson, type = "ai_json", mode = mode, isMe = false))
                            notifyIfBackground(session.operatorName, replyPreview(parsed).ifBlank { "发来一条消息" })
                            DebugLogger.log("Chat/DB", "AI响应已写入, session=${session.id}, id=$aiMsgId")
                            markPrivateEventsConsumed(session)
                            modeTransitionNotice = ""
                        }
                        // Every successful private exchange earns baseline affinity; model sentiment only adjusts it.
                        val affectionMod = 2 + parsed.affection_mod.coerceIn(-2, 2)
                        val currentDate = sharedUtils.beijingSdf("yyyyMMdd").format(java.util.Date())
                        operatorStateUpdater.updateOperatorIntimacy(session.operatorId, affectionMod.coerceIn(0, 4))
                        settings.grantDailyLmb(currentDate, 10)
                        decrementHypnosis()
                        if (sessionCounter >= shortTermThreshold) {
                            generateShortTermSummary(session)
                            settings.putSessionMessageCounter(session.id, 0)
                        }
                        markUnreadIfNotCurrent(session.id, aiResponseCount)
                        lastError = null
                        break  // 成功，退出重试循环
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        DebugLogger.log("Chat/AI", "AI超时, session=${session.id}")
                        repository.sendMessage(session.id, ChatMessage(id = aiMsgId, sessionId = session.id, senderName = session.operatorName, content = classifyError(e), type = "text", mode = mode, isMe = false))
                        markUnreadIfNotCurrent(session.id)
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
                        Log.e("ChatVM", "私聊AI失败 session=${session.id} mode=$mode provider=${settings.provider} model=${settings.modelName} err=${e.message?.take(120)}")
                        break
                    }
                }
                if (lastError != null) {
                    Log.e("ChatVM", "私聊AI最终错误 session=${session.id} err=${lastError.message?.take(120)}")
                    DebugLogger.log("Chat/AI", "AI错误: ${lastError.message?.take(100)}, session=${session.id}")
                    val errorMsg = if (retryCount > 0) "上下文超限，本次已临时降级至${effectiveHistoryMessages}轮后仍失败：${classifyError(lastError)}"
                                   else classifyError(lastError)
                    repository.sendMessage(session.id, ChatMessage(id = aiMsgId, sessionId = session.id, senderName = session.operatorName, content = errorMsg, type = "text", mode = mode, isMe = false))
                    markUnreadIfNotCurrent(session.id)
                }
            } finally { finishLoading(session.id, requestId); if (mutexLocked) aiMutexFor(session.id).unlock() }
        }
        chatAiJobs[session.id] = job
    }

    fun sendImageMessage(imageUri: String, imageForModel: String?, caption: String = "", onResult: (Boolean) -> Unit = {}) {
        val session = _currentSession.value ?: run {
            Log.w("RHODES_DEBUG", "[Vision] sendImageMessage: session is null"); onResult(false); return
        }
        Log.d("RHODES_DEBUG", "[Vision] sendImageMessage 入口: imageUri=$imageUri caption=$caption sessionId=${session.id}")
        if (sharedUtils.getApiKey().isBlank()) {
            Log.w("RHODES_DEBUG", "[Vision] API Key 为空")
            onShowToast("请先在设置中配置 API Key")
            onResult(false)
            return
        }
        Log.d("RHODES_VISION", "配置检查: visionBaseUrl='${settings.visionBaseUrl}' visionModelName='${settings.visionModelName}' visionApiKey=${settings.visionApiKey.isNotBlank()} apiKey=${settings.apiKey.isNotBlank()}")
        if (!isVisionConfigured()) {
            onShowToast("图片聊天需要先设置识图模型，请在模型设置中填写识图地址、模型名和密钥。")
            onResult(false)
            return
        }
        if (imageForModel.isNullOrBlank()) {
            Log.w("RHODES_DEBUG", "[Vision] imageForModel 为空")
            onShowToast("无法读取这张图片，请重新选择后再试。")
            onResult(false)
            return
        }
        Log.d("RHODES_VISION", "imageForModel 长度=${imageForModel.length}")
        _inputText.value = ""
        generateDailyIfNeeded()
        chatAiJobs[session.id]?.cancel()
        val requestId = requestSequence.incrementAndGet()

        // 异步：先保存图片占位 → 分析图片 → 更新消息 → AI 回复
        val job = viewModelScope.launch {
            val mode = _currentMode.value

            // 1. 立即保存图片消息（占位 visionSummary）
            val imageMsgId = repository.getNextMessageId()
            val placeholderJson = json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), JsonObject(mapOf(
                "imageUri" to kotlinx.serialization.json.JsonPrimitive(imageUri),
                "caption" to kotlinx.serialization.json.JsonPrimitive(caption.trim()),
                "visionSummary" to kotlinx.serialization.json.JsonPrimitive("")
            )))
            repository.sendMessage(session.id, ChatMessage(id = imageMsgId, sessionId = session.id, senderName = "我", content = placeholderJson, type = "image", mode = mode, isMe = true))
            Log.d("RHODES_VISION", "图片占位消息已保存 id=$imageMsgId")
            // Persisting the image is enough to restore the composer. Vision/role reply continues in background.
            onResult(true)
            var mutexLocked = false
            beginLoading(session.id, requestId)
            try {
                val gateway = currentVisionGateway()
                Log.d("RHODES_VISION", "开始调用 vision API: gatewayClass=${gateway::class.simpleName}")
                val visionText = try {
                    val result = gateway.analyzeImage(VisionAnalyzeRequest(
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
                    ))
                    Log.d("RHODES_VISION", "vision API 返回 length=${result.text.length}")
                    result?.text?.take(2000).orEmpty().ifBlank { throw IllegalStateException("没有识别到图片内容") }
                } catch (e: Exception) {
                    Log.e("RHODES_VISION", "vision API 异常: ${e.message}", e)
                    DebugLogger.log("Vision", "私聊识图失败: ${e.message?.take(100)}")
                    val failedJson = json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), JsonObject(mapOf(
                        "imageUri" to kotlinx.serialization.json.JsonPrimitive(imageUri),
                        "caption" to kotlinx.serialization.json.JsonPrimitive(caption.trim()),
                        "visionSummary" to kotlinx.serialization.json.JsonPrimitive(""),
                        "status" to kotlinx.serialization.json.JsonPrimitive("failed")
                    )))
                    repository.updateMessageContent(imageMsgId, failedJson)
                    onShowToast("图片已保留，但识别失败。请检查识图模型后重新发送。")
                    onResult(false)
                    return@launch
                }
                Log.d("RHODES_VISION", "visionText 长度=${visionText.length}")

                // 3. 更新图片消息的 visionSummary
                val updatedJson = json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), JsonObject(mapOf(
                    "imageUri" to kotlinx.serialization.json.JsonPrimitive(imageUri),
                    "caption" to kotlinx.serialization.json.JsonPrimitive(caption.trim()),
                    "visionSummary" to kotlinx.serialization.json.JsonPrimitive(visionText.take(800))
                )))
                repository.updateMessageContent(imageMsgId, updatedJson)
                Log.d("RHODES_VISION", "图片消息更新完成 msgId=$imageMsgId")

                aiMutexFor(session.id).lock()
                mutexLocked = true

                val cleanText = visionText.trim().removePrefix("```json").removePrefix("```").trim().removeSuffix("```").trim()
                val visionSummary = runCatching {
                    json.parseToJsonElement(cleanText).jsonObject["visibleSummary"]?.jsonPrimitive?.content
                }.getOrNull()?.take(500)
                Log.d("RHODES_VISION", "visibleSummary解析: success=${visionSummary != null} value=${visionSummary?.take(80)}")
                val userContent = buildString {
                    append("用户发送了一张图片。")
                    if (caption.isNotBlank()) append("\n用户附带文字：${caption.trim()}")
                    append("\n【用户发送的图片分析】")
                    append("\n画面内容：${visionSummary ?: visionText.take(500)}")
                    append("\n请你作为当前角色自然回应这张图片和用户的话，不要像识图工具一样机械描述。")
                }
                Log.d("RHODES_VISION", "userContent(前300): ${userContent.take(300)}")

                val apiMessages = buildApiMessages(userContent, settings.historyMessages)
                Log.d("RHODES_VISION", "开始 AI 调用: apiMessages 数量=${apiMessages.size}")
                var parsed = withTimeout(90_000) { sharedUtils.chatWithRetry(apiMessages) }
                Log.d("RHODES_VISION", "图片角色回复解析成功")
                parsed = ensureVisiblePrivateReply(parsed, mode)
                val rawJson = sharedUtils.aiService.cleanJson(json.encodeToString(com.rhodes.privatechat.shared.model.OfflineModeResponse.serializer(), parsed))
                val aiMsgId = repository.getNextMessageId()
                if (rawJson.isNotBlank()) {
                    repository.sendMessage(session.id, ChatMessage(id = aiMsgId, sessionId = session.id, senderName = session.operatorName, content = rawJson, type = "ai_json", mode = mode, isMe = false))
                    notifyIfBackground(session.operatorName, replyPreview(parsed).ifBlank { "发来一条消息" })
                    Log.d("RHODES_VISION", "AI 回复已保存 msgId=$aiMsgId")
                }
                saveVisionMemory(session, caption, visionText)
                markUnreadIfNotCurrent(session.id, visiblePrivateSegmentCount(parsed, mode))
                Log.d("RHODES_VISION", "sendImageMessage 完成")
            } catch (e: Exception) {
                Log.e("RHODES_VISION", "sendImageMessage 异常: ${e.message}", e)
                val errId = repository.getNextMessageId()
                repository.sendMessage(session.id, ChatMessage(id = errId, sessionId = session.id, senderName = session.operatorName, content = classifyError(e), type = "text", mode = mode, isMe = false))
                markUnreadIfNotCurrent(session.id)
                onResult(false)
            } finally {
                if (mutexLocked) aiMutexFor(session.id).unlock()
                finishLoading(session.id, requestId)
            }
        }
        chatAiJobs[session.id] = job
    }

    private fun isVisionConfigured(): Boolean {
        val ok = settings.visionBaseUrl.isNotBlank() &&
            settings.visionModelName.isNotBlank() &&
            settings.visionApiKey.ifBlank { settings.apiKey }.isNotBlank()
        if (!ok) Log.d("RHODES_VISION", "isVisionConfigured=false baseUrl='${settings.visionBaseUrl}' model='${settings.visionModelName}' hasKey=${(settings.visionApiKey.ifBlank { settings.apiKey }).isNotBlank()}")
        return ok
    }

    private fun currentVisionGateway(): VisionGateway = createVisionGateway(settings)

    private suspend fun saveVisionMemory(session: ChatSession, caption: String, visionText: String) {
        if (visionText.isBlank() || visionText.startsWith("[")) return
        val cleanText = visionText.trim().removePrefix("```json").removePrefix("```").trim().removeSuffix("```").trim()
        val visionSummary = runCatching {
            json.parseToJsonElement(cleanText).jsonObject["visibleSummary"]?.jsonPrimitive?.content
        }.getOrNull() ?: visionText.take(500)
        val content = buildString {
            append("用户发送图片，识图内容：$visionSummary")
            if (caption.isNotBlank()) append("；用户附带文字：${caption.take(120)}")
        }
        val now = System.currentTimeMillis()
        val anchor = AnchorSourcePolicy.buildAnchor(
            source = "vision",
            sourceName = "图片消息",
            sourceActor = appState.userProfile.value.nickname,
            sourceTarget = session.operatorName,
            operatorId = session.operatorId,
            type = AnchorType.EVENT,
            content = content,
            importance = AnchorSourcePolicy.MEDIUM,
            sessionId = session.id,
            createdAt = now,
            expiresAt = MemoryPolicy.anchorExpiresAt(settings, AnchorType.EVENT),
            isPrivate = true
        )
        repository.saveAnchor(anchor)
        saveAnchorToVector(anchor)
    }

    private fun notifyIfBackground(title: String, content: String) {
        if (!RhodesAppVisibility.isForeground) {
            val session = _currentSession.value
            RhodesNotificationCenter.show(
                getApplication(), title, content.take(120), sessionId = session?.id, isGroup = false,
                avatarUri = _selectedOperator.value?.avatarUri.orEmpty()
            )
        }
    }

    private suspend fun saveAnchorToVector(anchor: MemoryAnchor) {
        val service = memoryVectorService ?: return
        try {
            service.saveMemory(VectorMemory(
                id = "anchor_${anchor.operatorId}_${anchor.createdAt}_${anchor.content.hashCode()}",
                ownerType = "operator",
                ownerId = anchor.operatorId,
                sourceType = "anchor_${anchor.source.ifBlank { anchor.type.name.lowercase() }}",
                sourceId = anchor.sessionId,
                content = anchor.content,
                importance = if (anchor.importance == AnchorSourcePolicy.STRONG) 1.0 else 0.6,
                tags = anchor.type.name,
                visibility = if (anchor.isPrivate) "private" else "shared",
                createdAt = anchor.createdAt,
                expiresAt = anchor.expiresAt
            ))
        } catch (e: Exception) {
            DebugLogger.log("Vector/Save", "私聊锚点向量写入失败: ${e.message?.take(80)}")
        }
    }

    fun recallMessage(msgId: Long) {
        _messages.value = _messages.value.filter { it.id != msgId }
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
        chatAiJobs[session.id]?.cancel()
        val requestId = requestSequence.incrementAndGet()
        val job = viewModelScope.launch {
            // 插入占位消息（API 成功前不删原文）
            val placeholderId = repository.getNextMessageId()
            val placeholder = ChatMessage(id = placeholderId, sessionId = session.id, senderName = session.operatorName, content = "正在重新生成...", type = "text", mode = mode, isMe = false)
            repository.sendMessage(session.id, placeholder)
            _messages.value = _messages.value + placeholder

            var mutexLocked = false
            try {
                beginLoading(session.id, requestId)
                aiMutexFor(session.id).lock()
                mutexLocked = true
                val apiMessages = buildRegenerateApiMessages(
                    userContent = userMsg.content,
                    previousReply = previousReply,
                    excludeMessageIds = setOf(msgId, placeholderId),
                    historyBeforeMessageId = msgId
                )
                var parsed = withTimeout(90_000) { sharedUtils.chatWithRetry(apiMessages) }
                parsed = ensureVisiblePrivateReply(parsed, mode)
                sharedUtils.trackTokens("private", apiMessages, parsed.toString())
                val serializedJson = try { json.encodeToString(com.rhodes.privatechat.shared.model.OfflineModeResponse.serializer(), parsed) } catch (_: Exception) { parsed.toString() }
                val rawJson = sharedUtils.aiService.cleanJson(serializedJson)
                if (rawJson.isNotBlank()) {
                    // 成功：删旧 AI 回复 + 占位，用新 AI 回复替换
                    repository.deleteMessage(msgId)
                    repository.deleteMessage(placeholderId)
                    val newAiMsgId = repository.getNextMessageId()
                    repository.sendMessage(session.id, ChatMessage(id = newAiMsgId, sessionId = session.id, senderName = session.operatorName, content = rawJson, type = "ai_json", mode = mode, isMe = false))
                    markUnreadIfNotCurrent(session.id)
                    _messages.value = _messages.value.filter { it.id != msgId && it.id != placeholderId }
                }
            } catch (e: Exception) {
                // 失败：删占位，保留原文，显示错误
                repository.deleteMessage(placeholderId)
                _messages.value = _messages.value.filter { it.id != placeholderId }
                val errId = repository.getNextMessageId()
                repository.sendMessage(session.id, ChatMessage(id = errId, sessionId = session.id, senderName = session.operatorName, content = classifyError(e), type = "text", mode = mode, isMe = false))
                markUnreadIfNotCurrent(session.id)
            } finally { finishLoading(session.id, requestId); if (mutexLocked) aiMutexFor(session.id).unlock(); modeTransitionNotice = "" }
        }
        chatAiJobs[session.id] = job
    }

    fun continueAiMessage(msgId: Long) {
        val session = _currentSession.value ?: return
        val idx = _messages.value.indexOfFirst { it.id == msgId }
        if (idx < 0) return
        val mode = _currentMode.value
        chatAiJobs[session.id]?.cancel()
        val requestId = requestSequence.incrementAndGet()
        val job = viewModelScope.launch {
            val aiMsgId = repository.getNextMessageId()
            var mutexLocked = false
            try {
                beginLoading(session.id, requestId)
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
                var parsed = withTimeout(90_000) { sharedUtils.chatWithRetry(apiMessages) }
                parsed = ensureVisiblePrivateReply(parsed, mode)
                sharedUtils.trackTokens("private", apiMessages, parsed.toString())
                val serializedJson = try { json.encodeToString(com.rhodes.privatechat.shared.model.OfflineModeResponse.serializer(), parsed) } catch (_: Exception) { parsed.toString() }
                repository.sendMessage(session.id, ChatMessage(id = aiMsgId, sessionId = session.id, senderName = session.operatorName, content = serializedJson, type = "ai_json", mode = mode, isMe = false))
                markUnreadIfNotCurrent(session.id)
            } catch (e: Exception) {
                repository.sendMessage(session.id, ChatMessage(id = aiMsgId, sessionId = session.id, senderName = session.operatorName, content = classifyError(e), type = "text", mode = mode, isMe = false))
                markUnreadIfNotCurrent(session.id)
            }
            finally { finishLoading(session.id, requestId); if (mutexLocked) aiMutexFor(session.id).unlock(); modeTransitionNotice = "" }
        }
        chatAiJobs[session.id] = job
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
        e.message?.contains("返回空内容") == true -> "模型没有返回有效内容，请重试；若持续出现，请更换模型或降低提示词复杂度"
        e.message?.contains("401") == true || e.message?.contains("api key", true) == true -> "API Key 无效或已过期，请在设置中检查"
        e.message?.contains("402") == true || e.message?.contains("insufficient", true) == true || e.message?.contains("quota") == true -> "API 余额不足，请充值后重试"
        e.message?.contains("429") == true -> "AI 服务请求太频繁，请稍后重试"
        e.message?.contains("5") == true && e.message?.contains("50") == true -> "AI 服务暂时不可用，请稍后重试"
        e is kotlinx.coroutines.TimeoutCancellationException || e.message?.contains("timeout", true) == true -> "响应超时，请重试"
        e is java.io.IOException || e.message?.contains("connect", true) == true || e.message?.contains("network", true) == true -> "网络连接失败，请检查网络"
        else -> "发送失败：${e.message?.take(50) ?: "未知错误"}"
    }

    private fun logPrivatePromptTrace(
        stage: String,
        sessionId: String,
        operatorName: String,
        mode: String,
        messages: List<AiMessage> = emptyList(),
        response: String = ""
    ) {
    }

    private fun logTraceChunks(label: String, text: String) {
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

    private suspend fun saveAnchorsToVector(anchors: List<MemoryAnchor>) {
        anchors.filter { it.content.isNotBlank() }.forEach { anchor ->
            repository.saveAnchor(anchor)
            val service = memoryVectorService ?: return@forEach
            try {
                service.saveMemory(VectorMemory(
                    id = "anchor_${anchor.operatorId}_${anchor.createdAt}_${anchor.content.hashCode()}",
                    ownerType = "operator",
                    ownerId = anchor.operatorId,
                    sourceType = "anchor_${anchor.type.name.lowercase()}",
                    sourceId = anchor.sessionId,
                    content = anchor.content,
                    importance = when (anchor.importance) {
                        AnchorSourcePolicy.STRONG -> 1.0
                        AnchorSourcePolicy.MEDIUM -> 0.6
                        AnchorSourcePolicy.WEAK -> 0.25
                        else -> 0.4
                    },
                    tags = anchor.type.name,
                    visibility = if (anchor.isPrivate) "private" else "shared",
                    createdAt = anchor.createdAt,
                    expiresAt = anchor.expiresAt
                ))
            } catch (e: Exception) {
                DebugLogger.log("Vector/Save", "锚点向量写入失败: ${e.message?.take(80)}")
            }
        }
    }

    private suspend fun recallVectorMemories(operatorId: String, userContent: String): String {
        val restartAt = _currentSession.value?.let { settings.getSessionRestartAt(it.id) } ?: 0L
        return memoryContextBuilder.privateVectorContext(operatorId, userContent, restartAt)
    }

    private suspend fun buildRelationNetworkMemoryContext(operatorId: String, userContent: String): String {
        if (settings.privateSharedMemoryCount <= 0) return "无"
        return try {
            val now = System.currentTimeMillis()
            val relations = repository.getRelationships(operatorId)
                .filter { it.intimacy >= 20 }
                .sortedByDescending { it.intimacy }
                .take(5)
            val lines = mutableListOf<String>()
            for (rel in relations) {
                val items = listOf(MemoryLevel.L3, MemoryLevel.L2).flatMap { level ->
                    repository.getActiveMemoryItemsByLevel("operator", rel.relatedOperatorId, level, now)
                }
                    .filter { it.privacy != "private" && it.content.isNotBlank() }
                    .sortedByDescending { relationMemoryScore(it.content, userContent, it.importance) }
                    .take(1)
                items.forEach { item ->
                    lines += "- ${rel.relatedOperatorName}可能听说：${item.content.take(90)}"
                }
            }
            UnifiedMemoryContext.mergeBlocks(sharedUtils.contextBlockLimit(), lines.joinToString("\n"))
        } catch (_: Exception) {
            "无"
        }
    }

    private fun relationMemoryScore(content: String, userContent: String, importance: Int): Int {
        val chars = userContent.filter { !it.isWhitespace() }.toSet()
        val overlap = chars.count { content.contains(it) }.coerceAtMost(10) * 6
        return importance + overlap
    }

    private suspend fun ingestPrivateMemoryV2(session: ChatSession, messages: List<ChatMessage>) {
        if (!settings.memoryV2Enabled) return
        if (messages.isEmpty()) return
        try {
            memoryV2Pipeline.ingestPrivateChat(
                sessionId = session.id,
                operatorId = session.operatorId,
                operatorName = session.operatorName,
                messages = messages,
                currentRound = settings.getSessionMessageCounter(session.id)
            )
        } catch (e: Exception) {
            DebugLogger.log("MemoryV2", "私聊L1写入失败: ${e.message?.take(80)}")
        }
    }

    private fun replyPreview(parsed: com.rhodes.privatechat.shared.model.OfflineModeResponse): String {
        if (parsed.dialogue.isNotBlank()) return parsed.dialogue
        return parsed.segments
            ?.filter { it.type == "dialogue" }
            ?.joinToString(" ") { it.content }
            .orEmpty()
    }

    private fun formatPrivateHistoryForPrompt(msg: ChatMessage): String {
        if (msg.type == "image" && msg.isMe) return formatImageMessageForPrompt(msg)
        if (msg.isMe) return "用户：${msg.content.take(500)}"
        if (msg.type != "ai_json") return msg.content.take(500)
        return try {
            val parsed = sharedUtils.aiService.normalizeOfflineResponse(msg.content)
            val segments = parsed.segments.orEmpty()
                .map { it.copy(content = it.content.trim().take(500)) }
                .filter { it.content.isNotBlank() }
            val safe = if (segments.isNotEmpty()) parsed.copy(dialogue = "", narration = "", segments = segments)
            else if (parsed.dialogue.isNotBlank()) parsed.copy(segments = listOf(com.rhodes.privatechat.shared.model.Segment("dialogue", parsed.dialogue.take(500))))
            else null
            safe?.let { json.encodeToString(com.rhodes.privatechat.shared.model.OfflineModeResponse.serializer(), it) }
                ?: "[上一条回复不可用]"
        } catch (_: Exception) {
            "[上一条回复不可用]"
        }
    }

    private fun formatPrivateMessageForMemory(msg: ChatMessage, limit: Int): String {
        if (msg.type == "system") return ""
        if (msg.type == "image" && msg.isMe) return formatImageMessageForPrompt(msg).take(limit)
        if (!msg.isMe && msg.type != "ai_json") return ""
        if (msg.isMe) return "用户：${msg.content.take(limit)}"
        if (msg.type != "ai_json") return "${msg.senderName}：${msg.content.take(limit)}"
        return try {
            val parsed = sharedUtils.aiService.normalizeOfflineResponse(msg.content)
            val lines = mutableListOf<String>()
            parsed.segments.orEmpty().forEach { seg ->
                val text = seg.content.trim().take(limit)
                if (text.isNotBlank()) {
                    lines += if (seg.type == "narration") "${msg.senderName}动作：$text" else "${msg.senderName}台词：$text"
                }
            }
            if (lines.isEmpty() && parsed.dialogue.isNotBlank()) lines += "${msg.senderName}台词：${parsed.dialogue.take(limit)}"
            lines.joinToString("\n").ifBlank { "${msg.senderName}回复：[格式异常]" }
        } catch (_: Exception) {
            "${msg.senderName}回复：[格式异常]"
        }
    }

    private fun formatImageMessageForPrompt(msg: ChatMessage): String = try {
        val obj = json.parseToJsonElement(msg.content).jsonObject
        val caption = obj["caption"]?.jsonPrimitive?.content.orEmpty()
        val rawSummary = obj["visionSummary"]?.jsonPrimitive?.content.orEmpty()
        val summary = runCatching {
            json.parseToJsonElement(rawSummary).jsonObject["visibleSummary"]?.jsonPrimitive?.content
        }.getOrNull() ?: rawSummary
        buildString {
            append("用户发送图片")
            if (summary.isNotBlank()) append("：${summary.take(200)}")
            if (caption.isNotBlank()) append("；附言：$caption")
        }
    } catch (_: Exception) { "用户发送了一张图片" }

    private suspend fun buildApiMessages(
        userContent: String = "",
        historyLimitOverride: Int? = null,
        excludeMessageIds: Set<Long> = emptySet(),
        historyBeforeMessageId: Long? = null
    ): List<AiMessage> {
        val session = _currentSession.value ?: return emptyList()
        val op = repository.getOperator(session.operatorId)
        val restartAt = settings.getSessionRestartAt(session.id)
        val shortTerm = repository.getShortTermMemory(session.id)?.takeIf { restartAt <= 0L || it.createdAt >= restartAt }
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
        val transitionNotice = if (modeTransitionNotice.isNotBlank()) "【场景变更】\n${modeTransitionNotice}\n" else ""
        val wantsRecall = UnifiedMemoryContext.shouldIncludeTimeSummary(userContent)
        val recallQuery = repository.getMessagesSync(session.id)
            .takeLast(3)
            .joinToString("\n") { message ->
                "${if (message.isMe) "用户" else op?.name ?: session.operatorName}：${message.content.take(120)}"
            }
            .ifBlank { userContent }
        val memoryV2Context = sharedUtils.trimContextBlock(
            memoryV2Pipeline.buildPrivateMemoryContext(
                operatorId = session.operatorId,
                limitL1 = if (wantsRecall) settings.privateAnchorCount else 2,
                limitL2 = if (wantsRecall) 5 else 3,
                limitL3 = if (wantsRecall) 4 else 2,
                query = recallQuery
            ).ifBlank { "无" },
            sharedUtils.contextBlockLimit()
        )
        val publicMemoryContext = memoryV2Pipeline.buildPublicMemoryContext(recallQuery, limit = 2).ifBlank { "无" }
        val eventConsumer = "private:${session.operatorId}"
        val unconsumedEvents = sharedUtils.buildUnconsumedEventContextForOperator(session.operatorId, op?.name ?: session.operatorName, eventConsumer, settings.eventContextCount, markConsumed = false)
        val unifiedMemoryContext = UnifiedMemoryContext.mergeBlocks(
            maxChars = sharedUtils.contextBlockLimit(2),
            memoryV2Context,
            publicMemoryContext.takeIf { it != "无" }?.let { "【公开动态与评论】\n$it" }.orEmpty(),
            unconsumedEvents
        )
        DebugLogger.log(
            "Memory/Inject",
            "统一记忆注入: op=${session.operatorId}, mode=$mode, summary=${shortTerm != null}, memory=${memoryV2Context != "无"}"
        )
        val replacements = mapOf(
            "CURRENT_TIME" to sharedUtils.beijingSdf("yyyy-MM-dd HH:mm").format(java.util.Date()),
            "USER_NAME" to profile.nickname, "USER_GENDER" to profile.gender.ifBlank { "未知" }, "USER_BIO" to profile.bio.ifBlank { "无" },
            "AI_ANALYSIS" to analysisBlock,             "HYPNOSIS" to hypnosisBlock,
            "TRANSITION_NOTICE" to transitionNotice,
            "OPERATOR_NAME" to (op?.name ?: session.operatorName), "OPERATOR_TITLE" to (op?.title ?: ""),
            "OPERATOR_PERSONA" to (op?.privatePrompt?.ifBlank { op.description } ?: ""),
            "OPERATOR_GENDER" to (op?.gender?.ifBlank { "" } ?: ""),
            "LONG_TERM_IMPRESSION" to "无",
            "USER_PREFS" to "无",
            "MEMORY_ANCHORS" to unifiedMemoryContext,
            "MEMORY_V2_CONTEXT" to memoryV2Context,
            "OPERATOR_MEMORY_INJECTION" to "",
            "SOURCE_AWARE_MEMORIES" to "无",
            "UNCONSUMED_EVENTS" to sharedUtils.trimContextBlock(unconsumedEvents, sharedUtils.contextBlockLimit()),
            "RECENT_SOCIAL_EVENTS" to unconsumedEvents,
            "EVENT_TRIGGERED_PRIVATE_CONTEXT" to unconsumedEvents,
            "KNOWN_FROM_CONTEXT" to "无",
            "SOURCE_AWARE_RULES" to sharedUtils.sourceAwareUsageRule(MemorySurface.PRIVATE_CHAT),
            "SHARED_MEMORIES" to "无",
            "DAILY_SUMMARY" to "无",
            "SHORT_TERM_SUMMARY" to (shortTerm?.content ?: "无"),
            "GROUP_CONTEXT" to "无",
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
                "MEMORY_V2_CONTEXT" to memoryV2Context,
                "SOURCE_AWARE_MEMORIES" to replacements["SOURCE_AWARE_MEMORIES"].orEmpty(),
                "UNCONSUMED_EVENTS" to replacements["UNCONSUMED_EVENTS"].orEmpty(),
                "SHARED_MEMORIES" to replacements["SHARED_MEMORIES"].orEmpty(),
                "DAILY_SUMMARY" to replacements["DAILY_SUMMARY"].orEmpty(),
                "SHORT_TERM_SUMMARY" to replacements["SHORT_TERM_SUMMARY"].orEmpty(),
                "GROUP_CONTEXT" to replacements["GROUP_CONTEXT"].orEmpty(),
                "AI_ANALYSIS" to replacements["AI_ANALYSIS"].orEmpty(),
                "HYPNOSIS" to replacements["HYPNOSIS"].orEmpty(),
                "TRANSITION_NOTICE" to replacements["TRANSITION_NOTICE"].orEmpty()
            ),
            extra = mapOf(
                "mode" to mode,
                "user" to profile.nickname,
                "memoryRecallMode" to settings.memoryRecallMode
            )
        )
        // Custom templates keep literal placeholder semantics. The extra structured layers improve
        // cache reuse for stable persona/context without changing what a user-authored template means.
        val protocol = sharedUtils.compactTemplate(sharedUtils.applyTemplate(getPromptTemplate("private", mode), replacements))
        val persona = """CACHE_PERSONA_V1:private:${session.operatorId}
            |角色：${op?.name ?: session.operatorName}
            |性别：${op?.gender.orEmpty()}
            |身份：${op?.title.orEmpty()}
            |完整私聊人设：${op?.privatePrompt?.ifBlank { op.description }.orEmpty()}
            |与用户关系：${op?.userRelation.orEmpty().ifBlank { "未知" }}
        """.trimMargin()
        val context = """CACHE_CONTEXT_V1:private
            |当前时间：${sharedUtils.beijingSdf("yyyy-MM-dd HH时").format(java.util.Date())}
            |用户：${profile.nickname}，${profile.gender.ifBlank { "未知" }}，${profile.bio.ifBlank { "无" }}
            |角色记得的你：$memoryV2Context
            |用户偏好与边界：${replacements["USER_PREFS"].orEmpty().ifBlank { "无" }}
            |滚动摘要：${shortTerm?.content ?: "无"}
            |相关记忆：$unifiedMemoryContext
            |共同经历引用风格：${when (settings.personalMemoryReferenceStyle) { "restrained" -> "只在用户明确问起或话题高度相关时提及"; "proactive" -> "话题有联系时可主动自然提及共同经历"; else -> "话题相关时自然提及共同经历，不要无故翻旧账" }}
            |来源提示：无
            |群聊回顾：无
            |待处理事件：$unconsumedEvents
            |临时指令：${listOf(analysisBlock, hypnosisBlock, transitionNotice).filter { it.isNotBlank() }.joinToString("\n").ifBlank { "无" }}
            |格式边界：${if (mode == "offline" || mode == "director") "必须至少有一条 dialogue；旁白段数为 ${settings.narSegMin} 到 ${settings.narSegMax} 段。动作、表情、环境只写 narration；dialogue 只写说出口台词，禁止括号动作。" else "只允许 dialogue；禁止旁白、动作和环境描写。"}
        """.trimMargin()
        val rawMsgs = repository.getMessagesSync(session.id).let { msgs ->
            val scoped = historyBeforeMessageId?.let { targetId -> msgs.takeWhile { it.id != targetId } } ?: msgs
            val restartAt = settings.getSessionRestartAt(session.id)
            val currentConversation = if (restartAt > 0L) scoped.filter { it.timestamp >= restartAt } else scoped
            val limit = historyLimitOverride ?: settings.historyMessages
            val filtered = currentConversation.filter { it.id !in excludeMessageIds && it.type != "system" }
            if (limit > 0) filtered.takeLast(limit) else filtered
        }.toMutableList()
        // 去掉最后一条用户消息，避免与 {{USER_CONTENT}} 重复
        if (rawMsgs.lastOrNull()?.isMe == true && rawMsgs.last().content == userContent) {
            rawMsgs.removeAt(rawMsgs.lastIndex)
        }
        val messages = mutableListOf(AiMessage("system", "CACHE_PRIVATE_V5:private:$mode\n$protocol\n\n$persona\n\n$context"))
        rawMsgs.forEach { msg ->
            val formatted = formatPrivateHistoryForPrompt(msg).take(1200)
            if (formatted.isNotBlank()) {
                messages.add(AiMessage(if (msg.isMe) "user" else "assistant", formatted))
            }
        }
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

    private suspend fun markPrivateEventsConsumed(session: ChatSession) {
        try {
            val op = repository.getOperator(session.operatorId)
            sharedUtils.buildUnconsumedEventContextForOperator(
                session.operatorId,
                op?.name ?: session.operatorName,
                "private:${session.operatorId}",
                settings.eventContextCount,
                markConsumed = true
            )
        } catch (_: Exception) { }
    }

    private suspend fun buildRegenerateApiMessages(
        userContent: String,
        previousReply: String,
        excludeMessageIds: Set<Long> = emptySet(),
        historyBeforeMessageId: Long? = null
    ): List<AiMessage> {
        val angle = listOf(
            "从行动推进切入，减少解释，把场景往前推一步",
            "从情绪反差切入，表现出和上一版不同的迟疑、克制或主动",
            "从关系互动切入，多回应用户当下感受，少复述背景",
            "从具体细节切入，换一个动作、位置或关注点",
            "从短句和反问切入，让回复更像临场反应",
            "从陪伴和试探切入，不沿用上一版安慰方式"
        ).random()
        val cur = _currentSession.value
        val avoid = formatPrivateHistoryForPrompt(ChatMessage(id = 0L, sessionId = cur?.id ?: "", content = previousReply, type = "ai_json", senderName = cur?.operatorName ?: "干员", isMe = false)).take(1600)
        modeTransitionNotice = """【重说任务 · 最高优先级】
用户要求你重新回答上一轮消息。
本次重写角度：$angle

上一版回复如下，只能作为避重复参考，禁止复刻：
$avoid

重写规则：
- 保持同一角色、人设、关系、当前模式和 JSON 输出格式。
- 必须回应同一条用户消息，但换一个切入角度、情绪节奏、动作安排或信息重点。
- 禁止复用上一版开头、核心动作、段落顺序和连续 8 个字以上的原句。
- 不要只做同义词替换；如果上一版偏解释，这次偏行动/感受；如果上一版偏安慰，这次偏陪伴/反问/推进。
- 不要提到“重说”“上一版”“重新生成”。"""
        return buildApiMessages(
            userContent = userContent,
            excludeMessageIds = excludeMessageIds,
            historyBeforeMessageId = historyBeforeMessageId
        )
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
        viewModelScope.launch {
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            if (generateDailySummary(cal.time)) settings.dailySummaryDate = today
        }
    }

    private suspend fun generateDailySummary(dayBegin: java.util.Date): Boolean {
        try {
            repository.getAllSessionsSync()
                .filterNot { it.operatorId.startsWith("group_") }
                .forEach { generatePrivateDailySummary(it.operatorId, dayBegin) }
            val dayEnd = java.util.Date(dayBegin.time + 86_400_000)
            val allMsgs = repository.getMessagesInRange(dayBegin.time, dayEnd.time)
            if (allMsgs.size < 4) return true
            val text = allMsgs.mapNotNull { formatPrivateMessageForMemory(it, 60).takeIf { line -> line.isNotBlank() } }.joinToString("\n")
            if (text.isBlank()) return true
            val dateStr = sharedUtils.beijingSdf("yyyy年MM月dd日").format(dayBegin)
            val prompt = "请总结${dateStr}的聊天记录，生成50-150字的每日摘要。直接输出纯文本。\n${text}"
            val content = withTimeout(15_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Memory") }.trim()
            sharedUtils.trackTokens("memory", prompt, content)
            if (content.isNotBlank()) {
                repository.saveMemory(Memory(sessionId = "daily_${dateStr}", operatorId = "daily", type = MemoryType.DAILY, content = content, createdAt = System.currentTimeMillis(), expiresAt = MemoryPolicy.memoryExpiresAt(settings)))
            }
            return true
        } catch (_: Exception) {}
        return false
    }

    private suspend fun generatePrivateDailySummary(operatorId: String, dayBegin: java.util.Date) {
        try {
            val dayEnd = java.util.Date(dayBegin.time + 86_400_000)
            val session = repository.getSessionByOperator(operatorId) ?: return
            val dateKey = sharedUtils.beijingSdf("yyyyMMdd").format(dayBegin)
            if (repository.getDailyBySessionAndDate(session.id, dateKey) != null) return
            val msgs = repository.getMessagesInRange(dayBegin.time, dayEnd.time)
                .filter { it.sessionId == session.id }
            if (msgs.size < 4) return
            val text = msgs.joinToString("\n") { formatPrivateMessageForMemory(it, if (it.isMe) 320 else 180) }
            val dateStr = sharedUtils.beijingSdf("yyyy年MM月dd日").format(dayBegin)
            val prompt = """请总结${dateStr}你和用户的聊天记录，生成120-250字的每日摘要。直接输出纯文本，并按以下四段组织：
【当天重要事件】
【明确约定与待办】
【关系与情绪变化】
【次日可自然继续的话题】
不要记录普通寒暄；已完成事项不要保留为待办。\n${text}"""
            val content = withTimeout(15_000) { sharedUtils.chat(listOf(AiMessage("system", prompt)), "Memory") }.trim()
            if (content.isNotBlank()) {
                repository.replaceDailyBySessionAndDate(Memory(
                    sessionId = session.id, operatorId = operatorId,
                    type = MemoryType.DAILY, content = content,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = MemoryPolicy.memoryExpiresAt(settings)
                ), dateKey)
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
            val messagesText = msgs.joinToString("\n") { formatPrivateMessageForMemory(it, 120) }
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
                    repository.replaceLongTermImpression(Memory(sessionId = session.id, operatorId = session.operatorId, type = MemoryType.LONG_TERM, content = impression, keywords = keywords, preferences = preferences, taboos = taboos, createdAt = System.currentTimeMillis()))
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
    }
}
