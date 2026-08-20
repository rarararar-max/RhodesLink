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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
)

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
        restoreConversation()
        viewModelScope.launch { prepareManual() }
    }

    private suspend fun prepareManual() {
        try {
            manual = getApplication<Application>().assets.open("support/product_manual_zh.md").bufferedReader().use { it.readText() }
            manualSections = manual.split(Regex("(?m)(?=^##\\s+)")).map { it.trim() }.filter { it.isNotBlank() }
            manualHash = manual.hashCode().toString()
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
            if (settings.vectorProviderMode == "third_party" && settings.supportRemoteVectorEnabled && settings.supportRemoteEmbeddingConfirmedSignature != currentEmbeddingSignature()) {
                _remoteConfirmation.value = true
                _notice.value = "检测到第三方向量模型，可用于提升客服说明检索的同义问题匹配。"
            }
            _manualReady.value = true
        } catch (error: Exception) {
            _notice.value = "客服说明书准备失败，将使用内置章节检索：${error.message?.take(100).orEmpty()}"
            _manualReady.value = true
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
                        settings.supportRemoteEmbeddingConfirmedSignature = currentEmbeddingSignature()
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
        settings.supportRemoteEmbeddingConfirmedSignature = currentEmbeddingSignature()
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

    fun ask(question: String, imageUri: String = "", imageForModel: String? = null) {
        val trimmed = question.trim()
        if ((trimmed.isBlank() && imageUri.isBlank()) || _busy.value) return
        if (!_manualReady.value) {
            _notice.value = "正在准备客服说明，请稍候…"
            return
        }
        val agent = _currentAgent.value
        val userMessageId = ++nextMessageId
        _messages.value = _messages.value + AiSupportMessage(userMessageId, "user", trimmed, agentId = agent.id, imageUri = imageUri)
        persistConversation()
        requestJob = viewModelScope.launch {
            _busy.value = true
            try {
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
                val recent = AiSupportContract.historyAfter(_messages.value.dropLast(1), settings.supportConversationContextStartId)
                val prompt = """
                    ${agent.prompt}

                    ${currentSupportStatus(agent)}

                    除上述人设外，你还必须遵守以下规则：
                    1. 先理解用户想完成的操作或遇到的故障，再给出最短、可执行的步骤；不要机械复述说明书，也不要先堆砌背景。
                    2. 操作类回答优先使用编号步骤。页面名称、功能名称和条件必须以产品说明资料为准，不得自行改写或猜测。
                    3. 只能依据产品说明资料和用户当前问题回答。资料没有明确答案时，必须明确说“当前产品说明未覆盖这个问题”，不得猜测，不得承诺替用户确认、查询、反馈或稍后回复。
                    4. 不得假装查看用户设备、聊天记录、日志、API Key、余额、账户或服务器状态；不得索要 API Key、密码、验证码、完整隐私聊天记录或其他敏感信息。
                    5. 用户描述错误或困惑时，先用一句符合人设但简短的回应承接问题，再给排查步骤。
                    6. 清晰、准确、可执行的说明优先于角色口癖。不要为了人设省略条件、模糊步骤、过度使用语气词或延长回答。
                    7. 不要自行输出、编造或重复资料来源；界面会自动展示实际检索到的章节。
                    8. 使用简洁自然的中文。简单问题直接回答，不重复欢迎语。
                    9. 当前客服状态是固定的虚构角色设定，用于让远程聊天更自然。可以在开头或用户询问时自然提及正在做的事，但不要每次都重复；不得把它说成可验证的现实事实，也不得借此声称看到了用户所在环境或任何设备、账户、后台信息。
                """.trimIndent()
                val context = "【产品说明资料】\n$reference\n\n【当前用户问题】\n$questionForModel"
                val raw = aiService.chat(settings.apiKey, listOf(AiMessage("system", prompt)) + recent + AiMessage("user", context), settings.provider, settings.modelName, settings.customUrl, AiSupportContract.temperature, maxOutputTokens = AiSupportContract.maxOutputTokens, requestType = "AiSupport").content
                val sources = AiSupportContract.sources(reference)
                _messages.value = _messages.value + AiSupportMessage(++nextMessageId, "assistant", raw.ifBlank { "模型没有返回内容，请稍后重试。" }, sources, agent.id)
                persistConversation()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) {
                    _notice.value = AiSupportContract.userError(error)
                } else {
                    _messages.value = _messages.value + AiSupportMessage(++nextMessageId, "assistant", AiSupportContract.userError(error), agentId = agent.id)
                    persistConversation()
                }
            } finally { _busy.value = false; requestJob = null }
        }
    }

    fun cancelRequest() { requestJob?.cancel() }

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
            settings.supportRemoteEmbeddingConfirmedSignature == currentEmbeddingSignature()
        if (id.isNotBlank() && (settings.vectorProviderMode == "local" || remoteVerified)) {
            val vector = contextBuilder.forKnowledgeBase(id, query, 4_000)
            if (vector != "无") return vector
        }
        return AiSupportContract.localReference(manualSections, query)
    }

    private fun restoreConversation() {
        runCatching { Json.decodeFromString<List<AiSupportMessage>>(settings.supportConversation) }.getOrDefault(emptyList()).let {
            _messages.value = it.takeLast(100)
            nextMessageId = it.maxOfOrNull(AiSupportMessage::id) ?: 0L
        }
    }

    private fun persistConversation() {
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

    private fun currentEmbeddingSignature(): String = listOf(settings.vectorProviderMode, settings.vectorProvider, settings.vectorModelName, settings.vectorBaseUrl).joinToString("|")
}
