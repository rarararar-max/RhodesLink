package com.rhodes.privatechat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rhodes.privatechat.shared.knowledge.KnowledgeBaseContextBuilder
import com.rhodes.privatechat.shared.knowledge.KnowledgeBaseImportService
import com.rhodes.privatechat.shared.knowledge.KnowledgeBaseIndexService
import com.rhodes.privatechat.shared.data.KnowledgeBaseRepository
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.shared.network.AIService
import com.rhodes.privatechat.shared.modelgateway.VisionAnalyzeRequest
import com.rhodes.privatechat.shared.modelgateway.createVisionGateway
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.shared.settings.AgentProfile
import com.rhodes.privatechat.shared.settings.AgentProfiles
import com.rhodes.privatechat.ui.support.AiSupportContract
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.rhodes.privatechat.util.DebugLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import java.util.TimeZone

@Serializable
data class AiSupportMessage(
    val id: Long,
    val role: String,
    val text: String,
    val sources: List<String> = emptyList(),
    val agentId: String = "",
    val imageUri: String = "",
    val imageSummary: String = "",
    val redPacketAmount: Int = 0,
    val redPacketClaimed: Boolean = false,
)

/** Repairs old transcripts before Compose receives them as keyed lazy-list items. */
internal fun sanitizeSupportConversation(messages: List<AiSupportMessage>): List<AiSupportMessage> =
    messages.asReversed()
        .asSequence()
        .filter { it.id > 0L }
        .distinctBy { it.id }
        .take(100)
        .toList()
        .asReversed()

class AiSupportViewModel(
    application: Application,
    private val settings: SettingsRepository,
    private val sharedUtils: SharedUtils,
    private val aiService: AIService,
    private val importService: KnowledgeBaseImportService,
    private val indexService: KnowledgeBaseIndexService,
    private val contextBuilder: KnowledgeBaseContextBuilder,
    private val knowledgeBases: KnowledgeBaseRepository,
) : AndroidViewModel(application) {
    private val _messages = MutableStateFlow<List<AiSupportMessage>>(emptyList())
    val messages: StateFlow<List<AiSupportMessage>> = _messages.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _manualReady = MutableStateFlow(false)
    val manualReady: StateFlow<Boolean> = _manualReady.asStateFlow()
    private val _notice = MutableStateFlow("")
    val notice: StateFlow<String> = _notice.asStateFlow()
    private val _remoteConfirmation = MutableStateFlow(false)
    val remoteConfirmation: StateFlow<Boolean> = _remoteConfirmation.asStateFlow()
    private val _currentAgent = MutableStateFlow(AgentProfiles.byId(settings.supportAgentId))
    val currentAgent: StateFlow<AgentProfile> = _currentAgent.asStateFlow()
    val remoteVectorEnabled: Boolean get() = settings.supportRemoteVectorEnabled
    private var nextMessageId = 0L
    private var requestJob: Job? = null

    fun setAgent(agent: AgentProfile) {
        if (agent.id == _currentAgent.value.id) return
        requestJob?.cancel()
        settings.supportAgentId = agent.id
        // Keep the transcript for users, but isolate a new persona from prior model context.
        settings.supportConversationContextStartId = nextMessageId
        _currentAgent.value = agent
        _notice.value = "已切换到客服「${agent.name}」，此前记录仅供查看。"
        persistConversation()
    }

    private var manual = ""
    private var manualSections = emptyList<String>()
    private var manualHash = ""

    init {
        if (settings.supportPersistConversation) restoreConversation()
        viewModelScope.launch { prepareManual() }
    }

    private suspend fun prepareManual() {
        try {
            manual = getApplication<Application>().assets.open("support/product_manual_zh.md").bufferedReader().use { it.readText() }
            manualSections = manual.split(Regex("(?m)(?=^##\\s+)")).map { it.trim() }.filter { it.isNotBlank() }
            manualHash = manual.hashCode().toString()
            _manualReady.value = true
            _notice.value = "客服说明已就绪，正在优化检索资料。"
            prepareKnowledgeBase()
        } catch (error: Exception) {
            // Keep the send path available even if the shipped asset cannot be opened.
            manualSections = listOf("## 客服说明\n\n当前无法读取内置说明书。请描述问题、页面名称和看到的提示，我们会根据现有信息协助排查。")
            _manualReady.value = true
            _notice.value = "客服说明书准备失败，已使用基础说明继续服务。"
            DebugLogger.diagnostic("AiSupport/Manual", "status=failed,errorClass=${error.javaClass.simpleName}")
        }
    }

    /** The local manual is sufficient for support. Database indexing must never block sending. */
    private suspend fun prepareKnowledgeBase() {
        try {
            val existing = settings.supportKnowledgeBaseId
            val existingBook = if (existing.isNotBlank()) knowledgeBases.get(existing) else null
            if (existingBook == null) {
                val book = importService.saveText("应用使用说明（内置）", manual, "md", sourceType = "system_support")
                settings.supportKnowledgeBaseId = book.id
                settings.supportManualContentHash = manualHash
            } else if (settings.supportManualContentHash != manualHash) {
                importService.updateText(existingBook.copy(sourceType = "system_support"), "应用使用说明（内置）", manual)
                settings.supportManualContentHash = manualHash
                settings.supportRemoteEmbeddingConfirmedSignature = ""
                _notice.value = "产品说明已更新，正在刷新客服检索资料。"
            } else if (existingBook.sourceType != "system_support") {
                knowledgeBases.save(existingBook.copy(sourceType = "system_support"), knowledgeBases.getChunks(existingBook.id))
            }
            if (settings.vectorProviderMode == "third_party" && settings.supportRemoteVectorEnabled && settings.supportRemoteEmbeddingConfirmedSignature != vectorSignature()) {
                _remoteConfirmation.value = true
                _notice.value = "检测到第三方向量模型，可用于提升客服说明检索的同义问题匹配。"
            } else {
                _notice.value = "客服说明已就绪。"
            }
        } catch (error: Exception) {
            _notice.value = "客服说明已就绪，知识库同步失败，继续使用本地章节检索。"
            DebugLogger.diagnostic("AiSupport/KnowledgeBase", "status=failed,errorClass=${error.javaClass.simpleName}")
        }
    }

    fun confirmRemoteEmbedding() {
        viewModelScope.launch {
            val id = settings.supportKnowledgeBaseId
            if (id.isBlank()) return@launch
            _remoteConfirmation.value = false
            _notice.value = "正在使用第三方向量模型建立客服说明索引。"
            runCatching {
                indexService.enqueueIndex(id, remoteConfirmed = true) { result ->
                    if (result.failed == 0 && result.succeeded > 0) {
                        settings.supportRemoteVectorEnabled = true
                        settings.supportRemoteEmbeddingConfirmedSignature = vectorSignature()
                        _notice.value = "客服说明索引完成，已启用第三方向量检索。"
                    } else {
                        settings.supportRemoteVectorEnabled = false
                        _notice.value = "第三方向量索引失败，已继续使用内置章节检索。"
                    }
                }
            }.onFailure {
                settings.supportRemoteVectorEnabled = false
                _notice.value = "第三方向量索引未启动，已继续使用本地章节检索：${it.message?.take(100).orEmpty()}"
            }
        }
    }

    fun dismissRemoteEmbedding() {
        _remoteConfirmation.value = false
        settings.supportRemoteVectorEnabled = false
        settings.supportRemoteEmbeddingConfirmedSignature = vectorSignature()
        _notice.value = "已继续使用本地章节检索。"
    }

    fun disableRemoteEmbedding() {
        settings.supportRemoteVectorEnabled = false
        _remoteConfirmation.value = false
        _notice.value = "已关闭第三方向量检索，后续问题将使用本地章节检索。"
    }

    fun requestRemoteEmbedding() {
        if (settings.vectorProviderMode != "third_party") {
            _notice.value = "请先在知识库设置中选择第三方向量模型。"
            return
        }
        _remoteConfirmation.value = true
    }

    fun ask(question: String, imageUri: String = "", imageForModel: String? = null): Boolean {
        val trimmed = question.trim()
        if ((trimmed.isBlank() && imageUri.isBlank()) || _busy.value) return false
        if (!_manualReady.value) {
            _notice.value = "正在准备客服说明，请稍候…"
            return false
        }
        val agent = _currentAgent.value
        _busy.value = true
        val userMessageId = ++nextMessageId
        val debugOperationId = DebugLogger.beginOperation("客服", agent.name, "文字聊天")
        DebugLogger.conversationStep(debugOperationId, "客服", "发送入口", "已接收", "消息ID=$userMessageId")
        _messages.value = _messages.value + AiSupportMessage(userMessageId, "user", trimmed, agentId = agent.id, imageUri = imageUri)
        if (settings.supportPersistConversation) {
            runCatching { persistConversation() }.onFailure { error ->
                _busy.value = false
                _messages.value = _messages.value.dropLast(1)
                _notice.value = "客服记录保存失败，请检查设备存储后重试。"
                DebugLogger.diagnostic("AiSupport/Persist", "stage=user_message,status=failed,errorClass=${error.javaClass.simpleName}")
                DebugLogger.finishOperation(debugOperationId, "失败", "用户消息保存失败")
                return false
            }
        }
        requestJob = viewModelScope.launch {
            try {
                _notice.value = "正在检索说明并请求客服回复…"
                DebugLogger.conversationStep(debugOperationId, "客服", "说明检索", "进行中", "正在准备客服资料")
                sharedUtils.chatConfigurationError()?.let { throw IllegalStateException(it) }
                val imageSummary = if (imageUri.isBlank()) "" else analyzeImage(imageForModel)
                if (imageSummary.isNotBlank()) {
                    _messages.value = _messages.value.map { message ->
                        if (message.id == userMessageId) message.copy(imageSummary = imageSummary) else message
                    }
                    persistConversation()
                }
                val questionForModel = buildString {
                    append(trimmed.ifBlank { "请根据这张截图协助我排查问题。" })
                    if (imageSummary.isNotBlank()) append("\n【用户发送的图片摘要】").append(imageSummary)
                }
                val reference = retrieve(questionForModel)
                DebugLogger.conversationStep(debugOperationId, "客服", "说明检索", "成功", "已获取${reference.length}字参考资料")
                val recent = AiSupportContract.historyAfter(_messages.value.dropLast(1), settings.supportConversationContextStartId, agent.id)
                val supportDate = supportDate()
                val remainingLmb = settings.supportLmbRemaining(agent.id, supportDate)
                // The app controls whether this turn has a surprise-reward opportunity; the model
                // still decides whether the conversation feeling actually makes it appropriate.
                val packetOpportunity = remainingLmb > 0 && Random.nextInt(100) < 35
                val prompt = """
                    ${agent.prompt}

                    ${currentSupportStatus(agent)}

                    【客服红包能力】
                    这是应用已经授予你的真实聊天能力，不依赖产品说明资料：你可以通过回复末尾的红包标签向用户转出你当天打工赚来的龙门币，应用会自动显示可领取红包并完成入账。
                    你不是“无法转账”“无法发红包”，也不能说产品没有该功能、说明书未覆盖或你只能口头安慰。今天可转出的剩余金额是 {{SUPPORT_LMB_REMAINING}} 龙门币；这是真实可用余额，不是示例、传闻或需要用户额外操作确认的功能。

                    除上述人设外，你还必须遵守以下规则：
                    1. 先判断用户是在咨询应用操作或故障、闲聊、表达情绪、关心你，还是索要/谈论红包。只有应用操作或故障问题才优先给最短、可执行的步骤；闲聊时自然聊天，不要强行牵扯产品说明、排障、便利店、食堂或“我来帮你解决问题”。
                    2. 操作类回答优先使用编号步骤。页面名称、功能名称和条件必须以产品说明资料为准，不得自行改写或猜测。
                    3. 仅对应用功能、操作路径和故障原因依据产品说明资料。资料没有明确答案时，才说明“当前产品说明未覆盖这个问题”。这条规则不适用于自然闲聊、情绪回应或【客服红包能力】，不得据此否认红包、转账能力或当天可转余额。
                    4. 不得假装查看用户设备、聊天记录、日志、API Key、余额、账户或服务器状态；不得索要 API Key、密码、验证码、完整隐私聊天记录或其他敏感信息。
                    5. 用户描述错误或困惑时，先用一句符合人设但简短的回应承接问题，再给排查步骤；用户只是在闲聊、撒娇、玩笑、诉苦或关心你时，直接以当前客服角色自然回应即可。
                    6. 清晰、准确、可执行的说明优先于角色口癖。不要为了人设省略条件、模糊步骤、过度使用语气词或延长回答。
                    7. 不要自行输出、编造或重复资料来源、章节名称或检索过程；这些内容不在客服聊天界面显示。
                    8. 使用简洁自然的中文。简单问题直接回答，不重复欢迎语。
                    9. 当前客服状态是固定的虚构角色设定，用于让远程聊天更自然。可以在开头或用户询问时自然提及正在做的事，但不要每次都重复；不得把它说成可验证的现实事实，也不得借此声称看到了用户所在环境或任何设备、账户、后台信息。
                    10. 你每天打工赚来的龙门币可以分给聊得开心的用户。用户让你感到开心、被理解、被关心、被逗笑、心疼用户的处境，或用户解决问题后真诚表达感谢时，应优先考虑送一份小红包鼓励对方；不能因为用户直接索要就承诺必定送。今天你还能转出：{{SUPPORT_LMB_REMAINING}} 龙门币。本轮惊喜红包机会：{{SUPPORT_RED_PACKET_OPPORTUNITY}}。
                    11. 每一轮回复的最后一行都必须输出红包金额字段，字段本身不会展示给用户：不送时输出【红包金额:0】；只有“本轮惊喜红包机会”为“可送”且你真心觉得当前气氛适合时才送，输出【红包金额:1到200之间的整数】。今天可转数量为 0 或本轮不可送时必须输出【红包金额:0】。
                    12. 决定送红包时，先在正常回复中自然说一句这是自己今天打工赚的钱，再在最后一行输出对应金额。例如：今天打工赚了点钱，给你买杯奶茶。\n【红包金额:88】。红包金额不得超过今天还能转出的数量。该字段会由应用变成可领取红包并自动转入用户余额，不要解释字段、不要使用其他红包格式。即使本轮不送，也不得谎称自己没有转账或红包能力；今天打工赚的钱送完了可自然说明。
                """.trimIndent()
                    .replace("{{SUPPORT_LMB_REMAINING}}", remainingLmb.toString())
                    .replace("{{SUPPORT_RED_PACKET_OPPORTUNITY}}", if (packetOpportunity) "可送" else "本轮不可送")
                val context = "【产品说明资料】\n$reference\n\n【当前用户问题】\n$questionForModel"
                val requestMessages = listOf(AiMessage("system", prompt)) + recent + AiMessage("user", context)
                DebugLogger.attachOperationModule(debugOperationId, "完整请求", sharedUtils.logAiCallText(requestMessages), sensitive = true)
                DebugLogger.attachOperationModule(debugOperationId, "产品资料", reference, sensitive = true)
                DebugLogger.conversationStep(debugOperationId, "客服", "模型请求", "进行中", "超时45秒")
                val raw = withTimeoutOrNull(45_000L) {
                    aiService.chat(settings.apiKey, requestMessages, settings.provider, settings.modelName, settings.customUrl, AiSupportContract.temperature, maxOutputTokens = AiSupportContract.maxOutputTokens, requestType = "AiSupport").content
                } ?: throw java.net.SocketTimeoutException("客服请求超过45秒未完成")
                DebugLogger.conversationStep(debugOperationId, "客服", "模型请求", "成功", "返回${raw.length}字")
                DebugLogger.attachOperationModule(debugOperationId, "AI原始返回", raw, sensitive = true)
                DebugLogger.conversationStep(debugOperationId, "客服", "模型请求", "成功", "已收到客服回复")
                val sources = AiSupportContract.sources(reference)
                // Never leave a user-facing promise of a red packet without a real packet. The
                // opportunity controls normal model-triggered gifts; an explicit promise wins.
                val explicitPromise = AiSupportContract.looksLikeRedPacketPromise(raw)
                val packetRequest = AiSupportContract.extractRedPacket(raw)
                    ?: if (explicitPromise) Random.nextInt(20, 121) else null
                val packetAmount = if (packetRequest == null || (!packetOpportunity && !explicitPromise)) 0
                    else settings.reserveSupportLmb(agent.id, supportDate, packetRequest)
                val visibleReply = AiSupportContract.removeRedPacketMarker(raw).ifBlank { "模型没有返回内容，请稍后重试。" }
                DebugLogger.conversationStep(debugOperationId, "客服", "返回解析", "成功", "红包=${packetAmount > 0}")
                _messages.value = _messages.value + AiSupportMessage(++nextMessageId, "assistant", visibleReply, sources, agent.id, redPacketAmount = packetAmount)
                DebugLogger.conversationStep(debugOperationId, "客服", "本地保存", "进行中", "正在保存客服回复")
                persistConversation()
                DebugLogger.conversationStep(debugOperationId, "客服", "本地保存", "成功", "回复已保存")
                DebugLogger.attachOperationModule(debugOperationId, "最终保存", visibleReply + if (packetAmount > 0) "\n红包：$packetAmount 龙门币" else "", sensitive = true)
                DebugLogger.finishOperation(debugOperationId, "成功", "已回复${if (packetAmount > 0) "，并发送 $packetAmount 龙门币红包" else ""}")
                _notice.value = ""
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) {
                    _notice.value = AiSupportContract.userError(error)
                    DebugLogger.finishOperation(debugOperationId, "失败", "客服请求已取消")
                } else {
                    _messages.value = _messages.value + AiSupportMessage(++nextMessageId, "assistant", AiSupportContract.userError(error), agentId = agent.id)
                    persistConversation()
                    DebugLogger.finishOperation(debugOperationId, "失败", AiSupportContract.userError(error))
                    DebugLogger.conversationStep(debugOperationId, "客服", "本轮总览", "失败", AiSupportContract.userError(error))
                }
            } finally { _busy.value = false; requestJob = null }
        }
        return true
    }

    fun cancelRequest() { requestJob?.cancel() }

    fun claimRedPacket(messageId: Long): Int {
        val message = _messages.value.firstOrNull { it.id == messageId && it.role == "assistant" } ?: return 0
        if (message.redPacketAmount <= 0 || message.redPacketClaimed) return 0
        settings.addLmb(message.redPacketAmount)
        _messages.value = _messages.value.map { if (it.id == messageId) it.copy(redPacketClaimed = true) else it }
        persistConversation()
        return message.redPacketAmount
    }

    fun clear() {
        _messages.value = emptyList()
        settings.supportConversationContextStartId = 0L
        settings.supportConversation = ""
    }

    /** 空态开场白，跟随当前客服人设。 */
    fun greeting(): String = when (_currentAgent.value.id) {
        "nuan" -> "你好呀！我是芽衣，本应用的元气客服～ 有什么想问的尽管来，包在我身上！"
        "yu" -> "那个……你好，我是星音。有什么问题的话，我会努力帮你解决的……"
        "fei" -> "哎呀，你好呀~ 我是绯绫，遇到什么问题了慢慢说，姐姐帮你看看呢。"
        "chuan" -> "你好，我是顾川。别急，有什么问题我们一步步来解决。"
        "lin" -> "哼，遇到问题了？说清楚一点，我帮你看看。"
        else -> "诶嘿嘿～你好呀，我是团子！有什么不懂的都可以问我哦～"
    }

    private suspend fun retrieve(query: String): String {
        val id = settings.supportKnowledgeBaseId
        val remoteVerified = settings.supportRemoteVectorEnabled &&
            settings.supportRemoteEmbeddingConfirmedSignature == vectorSignature()
        if (id.isNotBlank() && (settings.vectorProviderMode == "local" || remoteVerified)) {
            val vector = withTimeoutOrNull(SUPPORT_RETRIEVAL_TIMEOUT_MS) {
                contextBuilder.forKnowledgeBase(id, query, 4_000)
            } ?: "无"
            if (vector != "无") return vector
        }
        return AiSupportContract.localReference(manualSections, query)
    }

    private fun restoreConversation() {
        val restored = runCatching { Json.decodeFromString<List<AiSupportMessage>>(settings.supportConversation) }
            .getOrDefault(emptyList())
        val sanitized = sanitizeSupportConversation(restored)
        _messages.value = sanitized
        nextMessageId = sanitized.maxOfOrNull(AiSupportMessage::id) ?: 0L
        val contextStart = settings.supportConversationContextStartId
        val repairedContextStart = contextStart.coerceAtMost(nextMessageId)
        if (repairedContextStart != contextStart) {
            settings.supportConversationContextStartId = repairedContextStart
        }
        if (sanitized.size != restored.size) {
            settings.supportConversation = Json.encodeToString(sanitized)
            DebugLogger.diagnostic("AiSupport/Transcript", "status=sanitized,before=${restored.size},after=${sanitized.size}")
        }
    }

    private fun persistConversation() {
        if (!settings.supportPersistConversation) return
        settings.supportConversation = Json.encodeToString(_messages.value.takeLast(100))
    }

    private suspend fun analyzeImage(imageForModel: String?): String {
        require(!imageForModel.isNullOrBlank()) { "无法读取这张图片，请重新选择后再试。" }
        require(settings.visionBaseUrl.isNotBlank() && settings.visionModelName.isNotBlank() && settings.visionApiKey.ifBlank { settings.apiKey }.isNotBlank()) {
            "图片求助需要先在模型设置中填写识图地址、模型名和密钥。"
        }
        return createVisionGateway(settings).analyzeImage(VisionAnalyzeRequest(
            imageForModel,
            """请提取这张应用截图中可见的页面名称、按钮、提示、报错文字和状态，用简洁中文说明。只描述可见内容；看不清时说明看不清；不要猜测设备、账户、日志或截图外的信息。"""
        )).text.take(1_200).ifBlank { throw IllegalStateException("没有识别到图片内容") }
    }

    private fun currentSupportStatus(agent: AgentProfile): String {
        val now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        val time = SimpleDateFormat("yyyy-MM-dd EEEE HH:mm", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }.format(Date(now.timeInMillis))
        val routine = AgentProfiles.routineAt(agent.id, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.DAY_OF_WEEK))
        return """
            【当前客服状态】
            当前北京时间：$time。
            当前时段：${SharedUtils.getTimeOfDay(now.get(Calendar.HOUR_OF_DAY))}。
            你的固定角色设定状态：你在${routine.location}，正在${routine.activity}。
            你正在通过本应用与用户进行远程文字聊天，用户无法看到你的虚构现场，你也无法看到用户的真实环境或设备。
        """.trimIndent()
    }

    private fun vectorSignature(): String = com.rhodes.privatechat.shared.vector.EmbeddingConfigurationSignature.create(
        settings.vectorProviderMode,
        settings.vectorProvider,
        settings.vectorBaseUrl,
        settings.vectorModelName,
    )

    private companion object {
        const val SUPPORT_RETRIEVAL_TIMEOUT_MS = 2_000L
    }

    private fun supportDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }.format(Date())
}
