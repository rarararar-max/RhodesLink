package com.rhodes.privatechat.ui.model

import android.content.ClipboardManager
import android.content.Context
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.shared.network.providers
import com.rhodes.privatechat.shared.network.AIService
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.shared.modelgateway.VisionAnalyzeRequest
import com.rhodes.privatechat.shared.modelgateway.createVisionGateway
import com.rhodes.privatechat.shared.voice.TtsRequest
import com.rhodes.privatechat.shared.voice.createTtsGateway
import com.rhodes.privatechat.shared.voice.defaultTtsVoiceId
import com.rhodes.privatechat.shared.voice.AsrRequest
import com.rhodes.privatechat.shared.voice.createAsrGateway
import com.rhodes.privatechat.shared.vector.testEmbeddingGateway
import com.rhodes.privatechat.shared.vector.MemoryVectorService
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.viewmodel.MainViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.coroutines.launch

@Composable
fun ModelSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings: SettingsRepository = koinInject()
    val aiService: AIService = koinInject()
    val memoryVectorService: MemoryVectorService = koinInject()
    val viewModel: MainViewModel = koinViewModel()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val providerIds = providers.keys.toList()
    val providerNames = providerIds.map { providers.getValue(it).name }
    val savedProvider = settings.provider.takeIf { it in providers } ?: "deepseek"

    var selectedProvider by remember { mutableIntStateOf(providerIds.indexOf(savedProvider).coerceAtLeast(0)) }
    val currentProviderId = providerIds.getOrElse(selectedProvider) { "deepseek" }
    val currentConfig = providers[currentProviderId] ?: providers.getValue("deepseek")

    val savedModel = settings.modelName
    var selectedModelIdx by remember {
        val savedIndex = currentConfig.models.indexOf(savedModel)
        mutableIntStateOf(if (savedIndex >= 0) savedIndex else currentConfig.models.size)
    }
    var customModelName by remember { mutableStateOf(savedModel) }
    var customUrl by remember { mutableStateOf(settings.customUrl) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var visionBaseUrl by remember { mutableStateOf(settings.visionBaseUrl) }
    var visionProvider by remember { mutableStateOf(settings.visionProvider) }
    var visionModelName by remember { mutableStateOf(settings.visionModelName) }
    var visionApiKey by remember { mutableStateOf(settings.visionApiKey) }
    var vectorProviderMode by remember { mutableStateOf(settings.vectorProviderMode) }
    var vectorProvider by remember { mutableStateOf(settings.vectorProvider) }
    var vectorBaseUrl by remember { mutableStateOf(settings.vectorBaseUrl) }
    var vectorModelName by remember { mutableStateOf(settings.vectorModelName) }
    var vectorApiKey by remember { mutableStateOf(settings.vectorApiKey) }
    var showRebuildVectorIndex by remember { mutableStateOf(false) }
    var invalidatingVectorIndex by remember { mutableStateOf(false) }
    var rebuildingVectorIndex by remember { mutableStateOf(false) }
    var vectorIndexFlowPending by remember { mutableStateOf(false) }
    var rebuildVectorResult by remember { mutableStateOf("") }
    var rebuildVectorProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var rebuildEligibleCount by remember { mutableStateOf<Int?>(null) }
    var pendingVectorSignature by remember { mutableStateOf("") }
    var asrBaseUrl by remember { mutableStateOf(settings.asrBaseUrl) }
    var asrProvider by remember { mutableStateOf(settings.asrProvider) }
    var asrModelName by remember { mutableStateOf(settings.asrModelName) }
    var asrApiKey by remember { mutableStateOf(settings.asrApiKey) }
    var ttsBaseUrl by remember { mutableStateOf(settings.ttsBaseUrl) }
    var ttsProvider by remember { mutableStateOf(settings.ttsProvider) }
    var ttsModelName by remember { mutableStateOf(settings.ttsModelName) }
    var ttsApiKey by remember { mutableStateOf(settings.ttsApiKey) }
    var errorText by remember { mutableStateOf("") }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf<String?>(null) }
    var chatTestResult by remember { mutableStateOf("") }
    var visionTestResult by remember { mutableStateOf("") }
    var asrTestResult by remember { mutableStateOf("") }
    var ttsTestResult by remember { mutableStateOf("") }
    var vectorTestResult by remember { mutableStateOf("") }
    var didSave by remember { mutableStateOf(false) }

    val isCustom = currentProviderId == "custom"
    val modelOptions = if (isCustom) listOf("自填") else currentConfig.models + "自填"
    val currentModelName = if (isCustom || selectedModelIdx >= currentConfig.models.size) customModelName.trim() else currentConfig.models[selectedModelIdx].trim()
    val hasChanges = currentProviderId != settings.provider || currentModelName != settings.modelName || customUrl.trim() != settings.customUrl || apiKey.trim() != settings.apiKey ||
        visionProvider != settings.visionProvider || visionBaseUrl.trim() != settings.visionBaseUrl || visionModelName.trim() != settings.visionModelName || visionApiKey.trim() != settings.visionApiKey ||
        vectorProviderMode != settings.vectorProviderMode || vectorProvider != settings.vectorProvider || vectorBaseUrl.trim() != settings.vectorBaseUrl || vectorModelName.trim() != settings.vectorModelName || vectorApiKey.trim() != settings.vectorApiKey ||
        asrProvider != settings.asrProvider || asrBaseUrl.trim() != settings.asrBaseUrl || asrModelName.trim() != settings.asrModelName || asrApiKey.trim() != settings.asrApiKey ||
        ttsProvider != settings.ttsProvider || ttsBaseUrl.trim() != settings.ttsBaseUrl || ttsModelName.trim() != settings.ttsModelName || ttsApiKey.trim() != settings.ttsApiKey

    fun validateSettings(): String? {
        fun hasScheme(value: String, vararg schemes: String): Boolean = runCatching {
            val scheme = java.net.URI(value.trim()).scheme?.lowercase()
            scheme != null && scheme in schemes
        }.getOrDefault(false)
        val modelName = if (isCustom || selectedModelIdx >= currentConfig.models.size) {
            customModelName.trim()
        } else {
            currentConfig.models[selectedModelIdx].trim()
        }
        if (apiKey.trim().isBlank()) return "请填写 API 密钥"
        if (modelName.isBlank() || modelName == "自填") return "请填写模型名"
        if (isCustom) {
            val url = customUrl.trim()
            if (url.isBlank()) return "请填写 API 地址"
            if (!hasScheme(url, "http", "https")) return "API 地址需以 http:// 或 https:// 开头"
        }
        if (visionBaseUrl.isNotBlank() && !hasScheme(visionBaseUrl, "http", "https")) return "识图 API 地址需以 http:// 或 https:// 开头"
        if (vectorProviderMode == "third_party" && vectorBaseUrl.isNotBlank() && !hasScheme(vectorBaseUrl, "http", "https")) return "向量 API 地址需以 http:// 或 https:// 开头"
        if (asrBaseUrl.isNotBlank() && if (asrProvider == "xiaomi") !hasScheme(asrBaseUrl, "http", "https") else !hasScheme(asrBaseUrl, "ws", "wss")) return if (asrProvider == "xiaomi") "小米语音识别地址需以 http:// 或 https:// 开头" else "语音识别地址需以 ws:// 或 wss:// 开头"
        return null
    }

    val saveSettings: () -> Boolean = {
        didSave = false
        // Saving is independent from connecting. Users must be able to configure providers
        // incrementally, while the individual test buttons validate the required fields.
        val modelName = currentModelName
        settings.saveModelConfiguration(
                provider = currentProviderId,
                modelName = modelName,
                customUrl = customUrl.trim(),
                apiKey = apiKey.trim(),
                visionBaseUrl = visionBaseUrl.trim(),
                visionProvider = visionProvider,
                visionModelName = visionModelName.trim(),
                visionApiKey = visionApiKey.trim(),
                vectorProviderMode = vectorProviderMode,
                vectorProvider = vectorProvider,
                vectorBaseUrl = vectorBaseUrl.trim(),
                vectorModelName = vectorModelName.trim(),
                vectorApiKey = vectorApiKey.trim(),
                asrBaseUrl = asrBaseUrl.trim(),
                asrProvider = asrProvider,
                asrModelName = asrModelName.trim(),
                asrApiKey = asrApiKey.trim(),
                ttsBaseUrl = ttsBaseUrl.trim(),
                ttsProvider = ttsProvider,
                ttsModelName = ttsModelName.trim(),
                ttsApiKey = ttsApiKey.trim(),
        )
        val newVectorSignature = if (vectorProviderMode == "local") "local-hash-384-v1" else "${vectorProvider}:${vectorBaseUrl.trim()}:${vectorModelName.trim()}"
        val previousVectorSignature = settings.vectorIndexSignature
        val vectorSignatureChanged = previousVectorSignature.isNotBlank() && previousVectorSignature != newVectorSignature
        if (previousVectorSignature.isBlank()) {
            // First configuration has no prior index to clear or rebuild.
            settings.vectorIndexSignature = newVectorSignature
        }
        if (vectorSignatureChanged) {
            pendingVectorSignature = newVectorSignature
            vectorIndexFlowPending = true
            invalidatingVectorIndex = true
            scope.launch {
                runCatching { viewModel.invalidateAllMemoryIndexes() }
                    .onFailure { rebuildVectorResult = "旧索引清理失败：${it.message?.take(60) ?: "未知错误"}" }
                invalidatingVectorIndex = false
                if (rebuildVectorResult.isBlank()) {
                    rebuildEligibleCount = runCatching { viewModel.countEligibleMemoryIndexes() }.getOrNull()
                    showRebuildVectorIndex = true
                } else {
                    vectorIndexFlowPending = false
                }
            }
        }
        errorText = ""
        didSave = true
        vectorSignatureChanged
    }

    fun requestBack() {
        if (hasChanges) showUnsavedDialog = true else onBack()
    }

    fun testChat() {
        if (testing != null) return
        testing = "chat"
        chatTestResult = "正在测试..."
        scope.launch {
            try {
                val validationError = validateSettings()
                if (validationError != null) throw IllegalArgumentException(validationError)
                val result = aiService.chat(
                    apiKey.trim(), listOf(AiMessage("user", "只回复：连接成功")),
                    currentProviderId, currentModelName, customUrl.trim(), temperature = 0.1
                )
                chatTestResult = if (result.content.isBlank()) "测试失败：服务没有返回内容" else "连接成功：${result.content.trim().take(60)}"
            } catch (e: Exception) {
                chatTestResult = "测试失败：${e.message?.take(100) ?: "请检查地址、模型和密钥"}"
            } finally { testing = null }
        }
    }

    fun testVision() {
        if (testing != null) return
        testing = "vision"
        visionTestResult = "正在测试..."
        scope.launch {
            try {
                val key = visionApiKey.trim().ifBlank { apiKey.trim() }
                require(visionBaseUrl.trim().startsWith("http")) { "请填写正确的识图 API 地址" }
                require(visionModelName.trim().isNotBlank()) { "请填写识图模型名" }
                require(key.isNotBlank()) { "请填写识图密钥，或先填写聊天密钥" }
                val testImage = runCatching {
                    val bmp = android.graphics.BitmapFactory.decodeResource(ctx.resources, com.rhodes.privatechat.R.drawable.sleep_idle_001)
                    val out = java.io.ByteArrayOutputStream()
                    bmp?.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                    if (bmp == null) throw IllegalStateException("null")
                    "data:image/jpeg;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                }.getOrNull() ?: throw IllegalStateException("无法加载测试图片")
                val result = createVisionGateway(visionProvider, visionBaseUrl.trim(), key, visionModelName.trim()).analyzeImage(
                    VisionAnalyzeRequest(testImage, "请用中文描述这张图片中的内容，只输出描述文字。")
                )
                val display = result.text.take(300)
                val summary: String? = runCatching {
                    val element = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.parseToJsonElement(display)
                    val obj = element as? kotlinx.serialization.json.JsonObject ?: return@runCatching null
                    obj["visibleSummary"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                }.getOrNull()
                visionTestResult = if (display.isBlank()) "测试失败：服务没有返回识图结果" else
                    "连接成功\n${if (summary != null) "可见分析：$summary\n" else ""}原始返回：$display"
            } catch (e: Exception) {
                visionTestResult = "测试失败：${e.message?.take(100) ?: "请检查识图配置"}"
            } finally { testing = null }
        }
    }

    fun testTts() {
        if (testing != null) return
        testing = "tts"
        ttsTestResult = "正在测试..."
        scope.launch {
            try {
                val key = ttsApiKey.trim().ifBlank { apiKey.trim() }
                require(ttsBaseUrl.trim().isNotBlank()) { "请填写文字转语音地址" }
                require(ttsModelName.trim().isNotBlank()) { "请填写文字转语音模型名" }
                require(key.isNotBlank()) { "请填写文字转语音密钥，或先填写聊天密钥" }
                require(ttsProvider != "vocu") { "Vocu 没有公共默认音色，请在角色编辑页填写音色 ID 后测试" }
                val audioBytes = createTtsGateway(ttsBaseUrl.trim(), key, ttsModelName.trim(), ttsProvider)
                    .synthesize(TtsRequest("测试成功", defaultTtsVoiceId(ttsProvider))).audioBytes
                if (audioBytes == null || audioBytes.isEmpty()) {
                    ttsTestResult = "测试失败：服务没有返回音频数据"
                } else {
                    val file = java.io.File(ctx.cacheDir, "tts_test.mp3")
                    file.writeBytes(audioBytes)
                    MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                        start()
                        setOnCompletionListener { release(); file.delete() }
                    }
                    ttsTestResult = "连接成功（测试语音正在播放）"
                }
            } catch (e: Exception) {
                ttsTestResult = "测试失败：${e.message?.take(100) ?: "请检查文字转语音配置"}"
            } finally { testing = null }
        }
    }

    fun testVector() {
        if (testing != null) return
        testing = "vector"
        vectorTestResult = "正在测试..."
        scope.launch {
            try {
                if (vectorProviderMode != "third_party") {
                    vectorTestResult = "本地索引可直接使用：384 维，不联网，无额外 Embedding API 费用"
                    return@launch
                }
                val key = vectorApiKey.trim().ifBlank { apiKey.trim() }
                require(vectorBaseUrl.trim().startsWith("http")) { "记忆增强地址需要以 http:// 或 https:// 开头" }
                require(vectorModelName.trim().isNotBlank()) { "请填写记忆增强模型名" }
                require(key.isNotBlank()) { "请填写记忆增强密钥，或先填写聊天密钥" }
                val values = testEmbeddingGateway(vectorBaseUrl.trim(), key, vectorModelName.trim())
                require(values.isNotEmpty()) { "服务没有返回记忆数据" }
                require(values.all { it.isFinite() }) { "服务返回了无效的记忆数据" }
                require(values.size >= 64) { "返回维度过低，可能不是 Embedding 模型" }
                vectorTestResult = "连接成功：返回 ${values.size} 维向量。此测试已发起一次真实 Embedding API 请求。"
            } catch (e: Exception) { vectorTestResult = "测试失败：${e.message?.take(100) ?: "请检查地址、模型和密钥"}" }
            finally { testing = null }
        }
    }

    fun testAsr() {
        if (testing != null) return
        testing = "asr"
        asrTestResult = "正在测试..."
        scope.launch {
            try {
                val key = asrApiKey.trim().ifBlank { apiKey.trim() }
                require(if (asrProvider == "xiaomi") asrBaseUrl.trim().startsWith("http") else asrBaseUrl.trim().startsWith("ws")) { if (asrProvider == "xiaomi") "小米语音识别地址需要以 http:// 或 https:// 开头" else "语音识别地址需要以 ws:// 或 wss:// 开头" }
                require(asrModelName.trim().isNotBlank()) { "请填写语音识别模型配置" }
                require(key.isNotBlank()) { "请填写语音识别密钥，或先填写聊天密钥" }
                createAsrGateway(asrBaseUrl.trim(), key, asrModelName.trim(), asrProvider).transcribe(AsrRequest(ByteArray(3200)))
                asrTestResult = "连接成功"
            } catch (e: Exception) {
                asrTestResult = "测试失败：${e.message?.take(100) ?: "请检查语音识别配置"}"
            } finally { testing = null }
        }
    }

    BackHandler(onBack = { requestBack() })

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { requestBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Spacer(Modifier.weight(1f))
            Text("模型设置", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            TextButton(enabled = !invalidatingVectorIndex && !rebuildingVectorIndex, onClick = { val stayForVectorFlow = saveSettings(); if (didSave && !stayForVectorFlow) onBack() }) {
                Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(20.dp))
                Text("保存", color = Primary, fontWeight = FontWeight.SemiBold)
            }
        }
        HorizontalDivider(color = Divider)

        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            // Vendor
            DropDown("厂商", providerNames, selectedProvider) { i -> selectedProvider = i; selectedModelIdx = 0; customModelName = ""; errorText = "" }
            Spacer(Modifier.height(12.dp))

            // Model
            if (!isCustom) DropDown("模型", modelOptions, selectedModelIdx.coerceIn(modelOptions.indices)) { selectedModelIdx = it; errorText = "" }
            Spacer(Modifier.height(12.dp))

            // Custom model name
            if (isCustom || selectedModelIdx >= currentConfig.models.size) {
                LabeledField("自定义模型名") { OutlinedTextField(value = customModelName, onValueChange = { customModelName = it; errorText = "" }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp), colors = fieldColors()) }
                Spacer(Modifier.height(12.dp))
            }

            // Custom URL
            if (isCustom) {
                LabeledField("完整 Chat Completions API 地址") {
                    Row { OutlinedTextField(value = customUrl, onValueChange = { customUrl = it; errorText = "" }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp), colors = fieldColors()); Spacer(Modifier.width(4.dp)); PasteBtn(ctx) { customUrl = it; errorText = "" } }
                }
                Spacer(Modifier.height(12.dp))
            }

            // API Key
            LabeledField("API 密钥") {
                SecretInput(apiKey, { apiKey = it; errorText = "" }, ctx)
            }
            if (errorText.isNotBlank()) {
                Text(errorText, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }
            TestButton("测试聊天模型", testing == "chat", chatTestResult, onClick = ::testChat)
            Spacer(Modifier.height(12.dp))

            SettingsSection("识图模型") {
                DropDown("服务商", listOf("阿里千问", "豆包", "小米 MiMo", "Anthropic Claude", "OpenAI 兼容/自填"), when (visionProvider) { "ali" -> 0; "doubao" -> 1; "xiaomi" -> 2; "anthropic" -> 3; else -> 4 }) { index ->
                    visionProvider = listOf("ali", "doubao", "xiaomi", "anthropic", "openai")[index]
                    when (visionProvider) {
                        "ali" -> { visionBaseUrl = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"; visionModelName = "qwen3-vl-plus" }
                        "doubao" -> { visionBaseUrl = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"; visionModelName = "" }
                        "xiaomi" -> { visionBaseUrl = "https://api.xiaomimimo.com/v1/chat/completions"; visionModelName = "mimo-v2.5" }
                        "anthropic" -> { visionBaseUrl = "https://api.anthropic.com/v1/messages"; visionModelName = "claude-sonnet-4-20250514" }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(when (visionProvider) {
                    "ali" -> "使用阿里千问视觉接口。"
                    "xiaomi" -> "使用小米 MiMo 图片理解接口，API Key 使用 api-key 请求头。"
                    "anthropic" -> "使用 Anthropic Messages 图片理解接口，要求传入 Base64 图片。"
                    else -> "请选择支持图片理解的模型。"
                }, fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                LabeledField("API 地址") { TextInput(visionBaseUrl, { visionBaseUrl = it }, ctx) }
                Spacer(Modifier.height(10.dp))
                LabeledField("模型名") { TextInput(visionModelName, { visionModelName = it }, ctx, placeholder = "qwen3-vl-plus") }
                Spacer(Modifier.height(10.dp))
                LabeledField("API Key（空则复用聊天 API Key）") { SecretInput(visionApiKey, { visionApiKey = it }, ctx) }
                TestButton("测试识图模型", testing == "vision", visionTestResult, onClick = ::testVision)
            }

            SettingsSection("向量模型") {
                Text("用于帮助角色找回较早的相关聊天。不开启也能正常聊天，索引始终保存在本机。", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                DropDown("模式", listOf("手机自带的免费版", "付费 Embedding（更好的记忆力）"), if (vectorProviderMode == "third_party") 1 else 0) { vectorProviderMode = if (it == 1) "third_party" else "local" }
                Spacer(Modifier.height(10.dp))
                if (vectorProviderMode == "local") Text("在手机本地建立检索索引，不上传聊天内容，也不产生额外 Embedding API 费用。对近义表达和复杂语义的理解较弱。", fontSize = 12.sp, color = TextSecondary)
                else {
                Text("会把需要建立或查询的记忆文本发送给你配置的服务，通常语义召回更准确，但可能产生供应商费用。测试、写入和检索都会发起真实请求。", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(10.dp))
                    DropDown("接口预设", listOf("阿里百炼兼容 Endpoint 预设", "自定义 OpenAI 兼容 Endpoint"), if (vectorProvider == "ali") 0 else 1) { index ->
                    vectorProvider = if (index == 0) "ali" else "openai"
                    if (index == 0) { vectorBaseUrl = "https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/embeddings"; vectorModelName = "text-embedding-v4" }
                    }
                    Text("两个预设当前都使用 OpenAI 兼容 Embedding 协议；阿里预设会填入推荐地址和模型名，需将 URL 中的 {WorkspaceId} 替换为实际工作区。", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(10.dp))
                LabeledField("API 地址") { TextInput(vectorBaseUrl, { vectorBaseUrl = it }, ctx) }
                Spacer(Modifier.height(10.dp))
                LabeledField("模型名") { TextInput(vectorModelName, { vectorModelName = it }, ctx, placeholder = "text-embedding-v4") }
                Spacer(Modifier.height(10.dp))
                LabeledField("API Key（空则复用聊天 API Key）") { SecretInput(vectorApiKey, { vectorApiKey = it }, ctx) }
                TestButton("测试付费 Embedding", testing == "vector", vectorTestResult, onClick = ::testVector)
                }
                if (rebuildVectorResult.isNotBlank()) Text(rebuildVectorResult, fontSize = 12.sp, color = TextSecondary)
            }

            SettingsSection("语音识别 ASR") {
                DropDown("服务商", listOf("阿里千问实时识别", "小米 MiMo", "自填（阿里兼容）"), when (asrProvider) { "ali" -> 0; "xiaomi" -> 1; else -> 2 }) { index ->
                    asrProvider = listOf("ali", "xiaomi", "custom")[index]
                    if (asrProvider == "xiaomi") { asrBaseUrl = "https://api.xiaomimimo.com/v1/chat/completions"; asrModelName = "mimo-v2.5-asr" }
                }
                Text(if (asrProvider == "xiaomi") "小米 MiMo 使用 HTTP 非流式识别，应用会将录音 PCM 自动封装为 WAV 后上传。" else "自填服务必须兼容阿里实时 WebSocket 协议与音频格式。", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(10.dp))
                LabeledField(if (asrProvider == "xiaomi") "API 地址" else "WebSocket 地址") { TextInput(asrBaseUrl, { asrBaseUrl = it }, ctx) }
                Spacer(Modifier.height(10.dp))
                LabeledField("模型配置") { TextInput(asrModelName, { asrModelName = it }, ctx, placeholder = "realtime|transcription") }
                Spacer(Modifier.height(10.dp))
                LabeledField("API Key（空则复用聊天 API Key）") { SecretInput(asrApiKey, { asrApiKey = it }, ctx) }
                TestButton("测试语音识别", testing == "asr", asrTestResult, onClick = ::testAsr)
            }

            SettingsSection("文字转语音 TTS") {
                DropDown("服务商", listOf("MiniMax", "小米 MiMo", "Vocu", "自填（MiniMax 兼容）"), when (ttsProvider) { "minimax" -> 0; "xiaomi" -> 1; "vocu" -> 2; else -> 3 }) { index ->
                    ttsProvider = listOf("minimax", "xiaomi", "vocu", "custom")[index]
                    if (index == 0) { ttsBaseUrl = "wss://api.minimaxi.com/ws/v1/t2a_v2"; ttsModelName = "speech-2.8-hd" }
                    if (index == 1) { ttsBaseUrl = "https://api.xiaomimimo.com/v1/chat/completions"; ttsModelName = "mimo-v2.5-tts" }
                    if (index == 2) { ttsBaseUrl = "https://v1.vocu.ai/api/tts/simple-generate"; ttsModelName = "v3.0" }
                }
                Text(when (ttsProvider) {
                    "xiaomi" -> "小米 MiMo 使用 HTTP 非流式 TTS，支持 mimo_default、冰糖、茉莉、苏打等预置音色。"
                    "vocu" -> "Vocu 使用同步 HTTP TTS。请在角色编辑页填写 Vocu 的 Voice ID（UUID）并测试，生成和测试都会消耗点数。"
                    else -> "自填服务必须兼容 MiniMax WebSocket TTS 协议。"
                }, fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(10.dp))
                LabeledField(if (ttsProvider == "minimax" || ttsProvider == "custom") "WebSocket 地址" else "API 地址") { TextInput(ttsBaseUrl, { ttsBaseUrl = it }, ctx) }
                Spacer(Modifier.height(10.dp))
                if (ttsProvider == "vocu") {
                    Text("Vocu 接口不需要填写模型名。", fontSize = 12.sp, color = TextSecondary)
                } else {
                    LabeledField("模型名") { TextInput(ttsModelName, { ttsModelName = it }, ctx, placeholder = "speech-2.8-hd") }
                }
                Spacer(Modifier.height(10.dp))
                LabeledField("API Key（空则复用聊天 API Key）") { SecretInput(ttsApiKey, { ttsApiKey = it }, ctx) }
                TestButton("测试文字转语音", testing == "tts", ttsTestResult, onClick = ::testTts)
            }
        }
    }
    }
    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("有未保存的修改", color = TextPrimary) },
            text = { Text("你已经修改了模型设置。要保存后离开，还是放弃这些修改？", color = TextSecondary) },
            confirmButton = { TextButton(onClick = {
                val stayForVectorFlow = saveSettings()
                if (didSave) {
                    showUnsavedDialog = false
                    if (!stayForVectorFlow) onBack()
                }
            }) { Text("保存修改", color = Primary) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { showUnsavedDialog = false; onBack() }) { Text("放弃修改", color = ErrorRed) }
                    TextButton(onClick = { showUnsavedDialog = false }) { Text("继续编辑", color = TextSecondary) }
                }
            }
        )
    }
    if (invalidatingVectorIndex) AlertDialog(
        onDismissRequest = {},
        title = { Text("正在更新记忆索引") },
        text = { Text("正在清空旧索引，请稍候。") },
        confirmButton = {}
    )
    if (showRebuildVectorIndex) AlertDialog(
        onDismissRequest = { if (!rebuildingVectorIndex) showRebuildVectorIndex = false },
        title = { Text("重建记忆索引") },
        text = {
            val progress = rebuildVectorProgress
            val eligibleText = rebuildEligibleCount?.let { "当前有 $it 条有效记忆可重建。\n" }.orEmpty()
            Text(
                if (rebuildingVectorIndex && progress != null) {
                    "正在重建：${progress.first} / ${progress.second}"
                } else {
                    eligibleText + "记忆检索方式已变更，旧索引已清空。现在重建全部有效记忆索引吗？付费 Embedding 模式最多会为每条有效记忆发起一次真实 API 请求，可能产生服务商费用。"
                }
            )
        },
        confirmButton = { TextButton(onClick = {
            rebuildingVectorIndex = true
            rebuildVectorProgress = 0 to (rebuildEligibleCount ?: 0)
            scope.launch {
                val result = runCatching { viewModel.rebuildAllMemoryIndexes { done, total -> rebuildVectorProgress = done to total } }.getOrNull()
                rebuildVectorResult = result?.let {
                    "索引重建完成：有效 ${it.eligible}，成功 ${it.succeeded}，失败 ${it.failed}，跳过 ${it.skipped}" +
                        if (it.errors.isNotEmpty()) "。错误：${it.errors.joinToString("；")}" else ""
                } ?: "索引重建失败，请稍后在角色记忆页重试"
                if (result != null && result.failed == 0) {
                    settings.vectorIndexSignature = pendingVectorSignature
                }
                rebuildingVectorIndex = false
                rebuildVectorProgress = null
                vectorIndexFlowPending = false
                showRebuildVectorIndex = false
            }
        }, enabled = !rebuildingVectorIndex) { Text(if (rebuildingVectorIndex) "重建中..." else "立即重建") } },
        dismissButton = { TextButton(enabled = !rebuildingVectorIndex, onClick = { vectorIndexFlowPending = false; showRebuildVectorIndex = false }) { Text("稍后处理") } }
    )
}

@Composable private fun DropDown(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(4.dp))
        Box {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Card).clickable { expanded = true }.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(options[selected], fontSize = 14.sp, color = TextPrimary); Text("▼", fontSize = 10.sp, color = TextTertiary) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Surface) {
                options.forEachIndexed { i, opt -> DropdownMenuItem(text = { Text(opt, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal, color = if (i == selected) Primary else TextPrimary) }, onClick = { onSelect(i); expanded = false }) }
            }
        }
    }
}

@Composable private fun LabeledField(label: String, content: @Composable () -> Unit) {
    Column { Text(label, fontSize = 13.sp, color = TextSecondary); Spacer(Modifier.height(4.dp)); content() }
}

@Composable private fun PasteBtn(ctx: Context, onPaste: (String) -> Unit) {
    OutlinedButton(onClick = { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip?.getItemAt(0)?.text?.toString()?.let { onPaste(it) } }, modifier = Modifier.height(48.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)) { Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(16.dp)) }
}

@Composable private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Card, shape = RoundedCornerShape(12.dp), tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable private fun TestButton(label: String, testing: Boolean, result: String, onClick: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = onClick, enabled = !testing, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)) {
        Text(if (testing) "测试中..." else label, fontWeight = FontWeight.SemiBold)
    }
    if (result.isNotBlank()) {
        Text(result, fontSize = 12.sp, color = if (result.startsWith("连接成功")) Primary else MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable private fun TextInput(value: String, onChange: (String) -> Unit, ctx: Context, placeholder: String = "") {
    Row {
        OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp), colors = fieldColors(), placeholder = { if (placeholder.isNotBlank()) Text(placeholder, fontSize = 13.sp, color = TextTertiary) })
        Spacer(Modifier.width(4.dp))
        PasteBtn(ctx) { onChange(it) }
    }
}

@Composable private fun SecretInput(value: String, onChange: (String) -> Unit, ctx: Context) {
    var show by remember { mutableStateOf(false) }
    Row {
        OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.weight(1f), singleLine = true, visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(), shape = RoundedCornerShape(8.dp), colors = fieldColors())
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = { show = !show }, modifier = Modifier.height(48.dp)) { Text(if (show) "隐藏" else "显示", fontSize = 11.sp, color = Primary) }
        Spacer(Modifier.width(4.dp))
        PasteBtn(ctx) { onChange(it) }
    }
}

@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Divider)
