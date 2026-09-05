package com.rhodes.privatechat.shared.network


import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.shared.model.ChatCompletionRequest
import com.rhodes.privatechat.shared.model.StreamChunk
import com.rhodes.privatechat.shared.model.StreamError
import com.rhodes.privatechat.shared.model.NonStreamResponse
import com.rhodes.privatechat.shared.model.OfflineModeResponse
import com.rhodes.privatechat.shared.model.GoogleGenerationRequest
import com.rhodes.privatechat.shared.model.GoogleGenerationConfig
import com.rhodes.privatechat.shared.model.GoogleContent
import com.rhodes.privatechat.shared.model.GooglePart
import com.rhodes.privatechat.shared.model.GoogleGenerateResponse
import com.rhodes.privatechat.shared.model.ResponseFormat
import com.rhodes.privatechat.shared.model.Segment
import com.rhodes.privatechat.shared.model.ThinkingParam
import com.rhodes.privatechat.shared.model.SummaryResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AIService(private val client: HttpClient = createHttpClient()) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        private const val TAG = "AIService"
    }

    class EmptyModelResponseException(modelName: String) : Exception("模型 $modelName 返回空内容，请重试或更换模型")
    class InvalidModelResponseException : Exception("模型返回内容无法解析")

    private fun normalizeModelName(providerId: String, modelName: String): String =
        if (providerId == "deepseek" && modelName in setOf("deepseek-chat", "deepseek-reasoner")) "deepseek-v4-flash" else modelName

    // --- Response parsing utilities ---

    fun parseOfflineResponse(raw: String): OfflineModeResponse {
        return normalizeOfflineResponse(raw)
    }

    fun normalizeOfflineResponse(raw: String, maxNestedDepth: Int = 2): OfflineModeResponse {
        val candidates = listOf(raw, cleanJson(raw), compactJsonBlock(raw)).distinct().filter { it.isNotBlank() }
        for (candidate in candidates) {
            parseStrictOffline(candidate)?.let { parsed ->
                return normalizeNestedDialogue(parsed, maxNestedDepth)
            }
        }

        parseScriptResponse(raw).takeIf { !it.segments.isNullOrEmpty() || it.dialogue.isNotBlank() }?.let { return it }
        parseSegmentsLenient(raw).takeIf { it.isNotEmpty() }?.let { return OfflineModeResponse(segments = it) }
        extractFirstReadableField(raw)?.let { text ->
            if (looksLikeJson(text) && maxNestedDepth > 0) {
                val nested = normalizeOfflineResponse(text, maxNestedDepth - 1)
                if (!nested.segments.isNullOrEmpty() || nested.dialogue.isNotBlank()) return nested
            }
            return OfflineModeResponse(segments = listOf(Segment(type = "dialogue", content = text)))
        }
        // A readable plain-text reply is more useful than a failed turn when a provider ignores
        // the tag protocol entirely. JSON-like garbage remains an error instead of being shown.
        raw.trim().takeIf { it.isNotBlank() && !looksLikeJson(it) }?.let { text ->
            return OfflineModeResponse(segments = listOf(Segment(type = "dialogue", content = text)))
        }

        throw InvalidModelResponseException()
    }

    private fun parseStrictOffline(raw: String): OfflineModeResponse? = try {
        val parsed = json.decodeFromString<OfflineModeResponse>(raw)
        if (parsed.segments.isNullOrEmpty() && parsed.dialogue.isBlank() && parsed.narration.isBlank()) null else parsed
    } catch (_: Exception) {
        null
    }

    private fun normalizeNestedDialogue(response: OfflineModeResponse, depth: Int): OfflineModeResponse {
        val directSegments = response.segments?.filter { it.content.isNotBlank() }
        if (!directSegments.isNullOrEmpty()) return response.copy(segments = directSegments)
        val nestedText = response.dialogue
        if (depth > 0 && looksLikeJson(nestedText)) {
            val nested = normalizeOfflineResponse(nestedText, depth - 1)
            if (!nested.segments.isNullOrEmpty() || nested.dialogue.isNotBlank()) {
                return nested.copy(
                    emotion = nested.emotion.ifBlank { response.emotion },
                    state = nested.state.ifBlank { response.state },
                    location = nested.location.ifBlank { response.location },
                    affection_mod = nested.affection_mod.takeIf { it != 0 } ?: response.affection_mod,
                    userIntentAnalysis = nested.userIntentAnalysis.ifBlank { response.userIntentAnalysis },
                    turnUserType = nested.turnUserType.ifBlank { response.turnUserType },
                    turnAnchor = nested.turnAnchor.ifBlank { response.turnAnchor },
                    turnAdvance = nested.turnAdvance.ifBlank { response.turnAdvance },
                    turnStatus = nested.turnStatus.ifBlank { response.turnStatus },
                    turnUnresolved = nested.turnUnresolved.ifBlank { response.turnUnresolved }
                )
            }
        }
        val legacySegments = buildList {
            response.narration.takeIf { it.isNotBlank() }?.let { add(Segment(type = "narration", content = it)) }
            response.dialogue.takeIf { it.isNotBlank() }?.let { add(Segment(type = "dialogue", content = it)) }
        }
        return if (legacySegments.isNotEmpty()) response.copy(segments = legacySegments, dialogue = "", narration = "") else response
    }

    fun parseScriptResponse(raw: String): OfflineModeResponse {
        // Accept model output even when it uses mixed Chinese/ASCII brackets, inline tags, or
        // small bracket mismatches such as 【台词]. Tags are protocol hints, not failure points.
        // Models occasionally emit HTML line-break tags between segments; they are formatting
        // noise, not user-visible content.
        val normalizedRaw = raw.replace(Regex("(?i)</?br\\s*/?>"), "\n")
        val tagPattern = Regex("""[【\[［]\s*(状态|情绪|心情|位置|本轮简述|用户发言意图分析|私聊回合状态|当前主线|用户本轮作用|本轮承接|本轮新增推进|主线状态|未收束事项|旁白|台词|台詞)\s*(?:[：:]\s*([^】\]］]*))?[】\]］]""")
        val segments = mutableListOf<Segment>()
        var emotion = ""
        var location = ""
        var state = ""
        var continuity = ""
        var userIntentAnalysis = ""
        var turnUserType = ""
        var turnAnchor = ""
        var turnAdvance = ""
        var turnStatus = ""
        var turnUnresolved = ""
        val matches = tagPattern.findAll(normalizedRaw).toList()
        val leadingText = matches.firstOrNull()?.let { normalizedRaw.substring(0, it.range.first).trim() }.orEmpty()
        matches.forEachIndexed { index, match ->
            val label = match.groupValues[1]
            val inline = match.groupValues[2].trim()
            val following = normalizedRaw.substring(match.range.last + 1, matches.getOrNull(index + 1)?.range?.first ?: normalizedRaw.length).trim()
            val text = if (inline.isNotBlank()) inline else following
            when (label) {
                "私聊回合状态" -> Unit
                "状态" -> if (state.isBlank()) state = text.take(20)
                "情绪", "心情" -> if (emotion.isBlank()) emotion = text.take(10)
                "位置" -> if (location.isBlank()) location = text.take(30)
                "本轮简述" -> if (continuity.isBlank()) continuity = text.take(160)
                "用户发言意图分析" -> if (userIntentAnalysis.isBlank()) userIntentAnalysis = text.take(800)
                "当前主线" -> if (continuity.isBlank()) continuity = text.take(160)
                "用户本轮作用" -> if (turnUserType.isBlank()) turnUserType = text.take(16)
                "本轮承接" -> if (turnAnchor.isBlank()) turnAnchor = text.take(160)
                "本轮新增推进" -> if (turnAdvance.isBlank()) turnAdvance = text.take(160)
                "主线状态" -> if (turnStatus.isBlank()) turnStatus = text.take(16)
                "未收束事项" -> if (turnUnresolved.isBlank()) turnUnresolved = text.take(160)
                "旁白" -> if (text.isNotBlank()) segments += Segment(type = "narration", content = text)
                "台词", "台詞" -> if (text.isNotBlank()) segments += Segment(type = "dialogue", content = text)
            }
        }
        if (leadingText.isNotBlank()) {
            val firstContentSegment = segments.indexOfFirst { it.type == "narration" || it.type == "dialogue" }
            if (firstContentSegment >= 0) {
                val previous = segments[firstContentSegment]
                segments[firstContentSegment] = previous.copy(content = "$leadingText\n${previous.content}")
            }
        }
        if (segments.isEmpty()) {
            val tagged = Regex("""(?m)^(?:干员动作|旁白|动作)\s*[：:]\s*(.+)$|^(?:干员台词|台词|台詞|对话)\s*[：:]\s*(.+)$""")
            for (match in tagged.findAll(normalizedRaw)) {
                val narration = match.groupValues[1].trim()
                val dialogue = match.groupValues[2].trim()
                when {
                    narration.isNotBlank() -> segments += Segment(type = "narration", content = narration)
                    dialogue.isNotBlank() -> segments += Segment(type = "dialogue", content = dialogue)
                }
            }
        }
        return OfflineModeResponse(emotion = emotion, location = location, state = state, continuity = continuity, userIntentAnalysis = userIntentAnalysis, turnUserType = turnUserType, turnAnchor = turnAnchor, turnAdvance = turnAdvance, turnStatus = turnStatus, turnUnresolved = turnUnresolved, segments = segments.ifEmpty { null })
    }

    private fun parseSegmentsLenient(raw: String): List<Segment> {
        val clean = compactJsonBlock(raw)
        val parsed = try { json.parseToJsonElement(clean) } catch (_: Exception) { null }
        if (parsed is JsonObject) {
            val arr = (parsed["segments"] as? JsonArray) ?: (parsed["messages"] as? JsonArray)
            if (arr != null) {
                return arr.mapNotNull { item ->
                    val obj = item as? JsonObject ?: item.jsonObject
                    val type = obj["type"]?.jsonPrimitive?.content ?: "dialogue"
                    val content = obj["content"]?.jsonPrimitive?.content
                        ?: obj["message"]?.jsonPrimitive?.content
                        ?: obj["text"]?.jsonPrimitive?.content
                    if (content.isNullOrBlank()) null else Segment(type = normalizeSegmentType(type), content = content)
                }
            }
        }
        val segments = mutableListOf<Segment>()
        val segRegex = Regex("""\"type\"\s*:\s*\"(narration|dialogue)\"[^}]*\"(?:content|message|text)\"\s*:\s*\"((?:[^"\\]|\\.)*)\"""")
        for (m in segRegex.findAll(raw)) {
            segments.add(Segment(type = m.groupValues[1], content = m.groupValues[2].replace("\\\"", "\"")))
        }
        return segments
    }

    private fun extractFirstReadableField(raw: String): String? {
        val keys = listOf("dialogue", "content", "message", "text", "narration")
        for (key in keys) {
            Regex("""\"$key\"\s*:\s*\"((?:[^"\\]|\\.)*)\"""").find(raw)?.groupValues?.getOrNull(1)
                ?.replace("\\\"", "\"")
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return null
    }

    private fun compactJsonBlock(raw: String): String {
        var s = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            .replace(Regex(",\\s*([}\\]])"), "$1")
        val start = listOf(s.indexOf('{'), s.indexOf('[')).filter { it >= 0 }.minOrNull() ?: return s
        val open = s[start]
        val close = if (open == '{') '}' else ']'
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until s.length) {
            val c = s[i]
            if (esc) { esc = false; continue }
            if (c == '\\') { esc = true; continue }
            if (c == '"') { inStr = !inStr; continue }
            if (inStr) continue
            if (c == open) depth++
            if (c == close) {
                depth--
                if (depth == 0) return s.substring(start, i + 1)
            }
        }
        return s.substring(start)
    }

    private fun looksLikeJson(text: String): Boolean {
        val t = text.trim()
        return (t.startsWith("{") || t.startsWith("[")) && (t.contains("segments") || t.contains("dialogue") || t.contains("content") || t.contains("message"))
    }

    private fun normalizeSegmentType(type: String): String = if (type.equals("narration", true) || type == "旁白") "narration" else "dialogue"

    fun cleanJson(raw: String): String {
        var s = raw.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            .replace("　", " ")
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
        try {
            val obj = json.parseToJsonElement(s)
            return obj.toString()
        } catch (_: Exception) {}
        s = s.replace(Regex(",\\s*([}\\]])"), "$1")
        val firstBrace = s.indexOf('{')
        val firstBracket = s.indexOf('[')
        val start = when {
            firstBrace < 0 && firstBracket < 0 -> -1
            firstBrace < 0 -> firstBracket
            firstBracket < 0 -> firstBrace
            else -> minOf(firstBrace, firstBracket)
        }
        if (start > 0) s = s.substring(start)
        if (start >= 0) {
            val openChar = s[0]
            val closeChar = if (openChar == '{') '}' else ']'
            var depth = 0
            var end = -1
            var inStr = false
            var esc = false
            for (i in s.indices) {
                val c = s[i]
                if (esc) { esc = false; continue }
                if (c == '\\') { esc = true; continue }
                if (c == '"') { inStr = !inStr; continue }
                if (inStr) continue
                if (c == openChar) depth++
                else if (c == closeChar) {
                    depth--
                    if (depth == 0) { end = i; break }
                }
            }
            if (end >= 0) s = s.substring(0, end + 1)
        }
        return s
    }

    fun parseSummaryResponse(raw: String): SummaryResponse {
        val clean = raw.trim().removePrefix("```json").removeSuffix("```").trim()
        return try {
            json.decodeFromString<SummaryResponse>(clean)
        } catch (_: Exception) {
            SummaryResponse(summary = raw)
        }
    }

    // --- Non-streaming chat (primary) ---

    data class ChatResult(
        val content: String,
        val inputTokens: Int = 0,
        val outputTokens: Int = 0,
        val promptCacheHitTokens: Int? = null,
        val promptCacheMissTokens: Int? = null,
        val reasoningContent: String? = null,
        val thinkingDisabled: Boolean = false,
    )

    /**
     * Emits request-shape diagnostics without logging prompts or generated content. The system
     * fingerprint is only useful for comparing whether consecutive requests shared a prefix.
     */
    private fun logRequestMetrics(
        providerId: String,
        modelName: String,
        messages: List<AiMessage>,
        elapsedMs: Long,
        inputTokens: Int,
        outputTokens: Int,
        cacheHitTokens: Int? = null,
        cacheMissTokens: Int? = null,
        requestType: String,
        outcome: String
    ) {
        val system = messages.filter { it.role == "system" }.joinToString("\n\n") { it.content }
        val cacheTotal = if (cacheHitTokens != null && cacheMissTokens != null) cacheHitTokens + cacheMissTokens else null
        val cacheRate = cacheTotal?.takeIf { it > 0 }?.let { cacheHitTokens!! * 100 / it }
        println(
            "RHODES_AI_METRIC requestType=$requestType provider=$providerId model=$modelName outcome=$outcome " +
                "messages=${messages.size} systemChars=${system.length} " +
                "systemFingerprint=${systemFingerprint(system)} inputTokens=$inputTokens " +
                "outputTokens=$outputTokens promptCacheHitTokens=${cacheHitTokens ?: "unavailable"} " +
                "promptCacheMissTokens=${cacheMissTokens ?: "unavailable"} promptCacheTokenHitRate=${cacheRate ?: "unavailable"} elapsedMs=$elapsedMs"
        )
    }

    private fun systemFingerprint(system: String): String {
        var hash = 0xcbf29ce484222325UL
        system.encodeToByteArray().forEach { byte ->
            hash = hash xor byte.toUByte().toULong()
            hash *= 0x100000001b3UL
        }
        return hash.toString(16)
    }

    suspend fun chat(
        apiKey: String,
        messages: List<AiMessage>,
        providerId: String = "deepseek",
        modelName: String = "deepseek-v4-flash",
        customUrl: String = "",
        temperature: Double = 0.95,
        jsonMode: Boolean = false,
        maxOutputTokens: Int? = null,
        requestType: String = "Chat"
    ): ChatResult {
        val requestStartedAt = kotlin.time.TimeSource.Monotonic.markNow()
        val config = providers[providerId] ?: throw IllegalArgumentException("不支持的厂商: $providerId")
        val model = normalizeModelName(config.id, modelName)
        try {

        // 自填厂商 URL 校验
        if (config.id == "custom" && customUrl.isBlank()) {
            throw Exception("自填厂商的 URL 不能为空，请在设置中填写 API 地址")
        }
        val url = if (config.id == "custom") customUrl else config.baseUrl

        if (config.isOpenAICompat) {
            // OpenAI 兼容格式
            val requestBody = ChatCompletionRequest(
                model = model,
                messages = messages,
                stream = false,
                temperature = temperature,
                max_tokens = maxOutputTokens,
                // DeepSeek V4 Flash can emit whitespace-only completions when API JSON mode is combined
                // with a long structured roleplay prompt. The prompt and local parser already enforce JSON.
                response_format = if (jsonMode && supportsJsonMode(config.id) && config.id != "deepseek") ResponseFormat("json_object") else null,
                // DeepSeek enables thinking by default. Always opt out so response latency,
                // temperature behavior, and cost remain consistent with normal chat.
                thinking = if (config.id == "deepseek") ThinkingParam("disabled") else null
            )
            val response: HttpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                if (config.id == "xiaomi") header("api-key", apiKey)
                else header("Authorization", "Bearer $apiKey")
                setBody(requestBody)
            }
            if (!response.status.isSuccess()) {
                val detail = response.bodyAsText().trim().replace(Regex("\\s+"), " ").take(1000)
                throw Exception("API error ${response.status.value}${if (detail.isBlank()) "" else ": $detail"}")
            }
            val responseBody = response.bodyAsText()
            val completion = json.decodeFromString<NonStreamResponse>(responseBody)
            val msg = completion.choices?.firstOrNull()?.message
            val rawContent = msg?.content ?: ""
            val content = if (rawContent.isBlank() || rawContent.all { it.isWhitespace() }) "" else rawContent
            val inputTokens = completion.usage?.promptTokens ?: 0
            val outputTokens = completion.usage?.completionTokens ?: 0
            val cacheHit = completion.usage?.promptCacheHitTokens
            val cacheMiss = completion.usage?.promptCacheMissTokens
            logRequestMetrics(
                providerId = config.id,
                modelName = model,
                messages = messages,
                elapsedMs = requestStartedAt.elapsedNow().inWholeMilliseconds,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                cacheHitTokens = cacheHit,
                cacheMissTokens = cacheMiss,
                requestType = requestType,
                outcome = if (content.isBlank()) "empty" else "success"
            )
            return ChatResult(
                content = content,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                promptCacheHitTokens = completion.usage?.promptCacheHitTokens,
                promptCacheMissTokens = completion.usage?.promptCacheMissTokens,
                reasoningContent = msg?.reasoningContent,
                thinkingDisabled = config.id == "deepseek",
            )
        }

        if (config.id == "anthropic") {
            val system = messages.filter { it.role == "system" }.joinToString("\n\n") { it.content }
            // Anthropic requires strictly alternating user/assistant turns. Queue batching can
            // legitimately produce adjacent user turns, so combine adjacent same-role content.
            val conversation = messages.filter { it.role != "system" }.fold(mutableListOf<AiMessage>()) { acc, message ->
                val role = if (message.role == "assistant") "assistant" else "user"
                val previous = acc.lastOrNull()
                if (previous?.role == role) acc[acc.lastIndex] = previous.copy(content = "${previous.content}\n${message.content}")
                else acc += message.copy(role = role)
                acc
            }
            if (conversation.isEmpty() || conversation.first().role != "user") {
                conversation.add(0, AiMessage("user", "请按系统指令完成任务。"))
            }
            val body = buildJsonObject {
                put("model", model)
                put("max_tokens", maxOutputTokens ?: 4096)
                if (system.isNotBlank()) put("system", system)
                put("temperature", temperature)
                put("messages", buildJsonArray {
                    conversation.forEach { message ->
                        add(buildJsonObject {
                            put("role", if (message.role == "assistant") "assistant" else "user")
                            put("content", message.content)
                        })
                    }
                })
            }
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                setBody(body)
            }
            val responseBody = response.bodyAsText()
            if (!response.status.isSuccess()) {
                val detail = responseBody.trim().replace(Regex("\\s+"), " ").take(1000)
                throw Exception("Anthropic API error ${response.status.value}${if (detail.isBlank()) "" else ": $detail"}")
            }
            val root = json.parseToJsonElement(responseBody).jsonObject
            val content = (root["content"] as? JsonArray).orEmpty()
                .firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
                ?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()
            val usage = root["usage"]?.jsonObject
            val inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            logRequestMetrics(
                providerId = config.id,
                modelName = model,
                messages = messages,
                elapsedMs = requestStartedAt.elapsedNow().inWholeMilliseconds,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                requestType = requestType,
                outcome = if (content.isBlank()) "empty" else "success"
            )
            return ChatResult(
                content = content,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
            )
        }

        // === Google Gemini 专用格式 ===
        if (config.id == "google") {
            val systemMsg = messages.filter { it.role == "system" }
                .joinToString("\n\n") { it.content }
                .takeIf { it.isNotBlank() }
            val chatMsgs = messages.filter { it.role != "system" }
            val googleBody = GoogleGenerationRequest(
                contents = chatMsgs.map { msg ->
                    GoogleContent(
                        parts = listOf(GooglePart(text = msg.content)),
                        role = if (msg.role == "user") "user" else "model"
                    )
                },
                systemInstruction = systemMsg?.let { content ->
                    GoogleContent(parts = listOf(GooglePart(text = content)))
                },
                generationConfig = maxOutputTokens?.let(::GoogleGenerationConfig)
            )
            val googleUrl = "${url}/${model}:generateContent"
            val response: HttpResponse = client.post(googleUrl) {
                contentType(ContentType.Application.Json)
                header("X-Goog-Api-Key", apiKey)
                setBody(googleBody)
            }
            if (!response.status.isSuccess()) {
                val detail = response.bodyAsText().trim().replace(Regex("\\s+"), " ").take(1000)
                throw Exception("Google API error ${response.status.value}${if (detail.isBlank()) "" else ": $detail"}")
            }
            val responseBody = response.bodyAsText()
            val googleResp = json.decodeFromString<GoogleGenerateResponse>(responseBody)
            val content = googleResp.candidates?.firstOrNull()?.content?.parts?.joinToString("") { it.text } ?: ""
            val inputTokens = googleResp.usageMetadata?.promptTokenCount ?: 0
            val outputTokens = googleResp.usageMetadata?.candidatesTokenCount ?: 0
            logRequestMetrics(
                providerId = config.id,
                modelName = model,
                messages = messages,
                elapsedMs = requestStartedAt.elapsedNow().inWholeMilliseconds,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                requestType = requestType,
                outcome = if (content.isBlank()) "empty" else "success"
            )
            return ChatResult(content, inputTokens, outputTokens)
        }

        throw Exception("不支持的厂商: ${config.id}")
        } catch (e: CancellationException) {
            logRequestMetrics(
                providerId = config.id,
                modelName = model,
                messages = messages,
                elapsedMs = requestStartedAt.elapsedNow().inWholeMilliseconds,
                inputTokens = 0,
                outputTokens = 0,
                requestType = requestType,
                outcome = if (e is kotlinx.coroutines.TimeoutCancellationException) "timeout" else "cancelled"
            )
            throw e
        } catch (e: Exception) {
            val outcome = when {
                e.message?.startsWith("API error") == true || e.message?.startsWith("Anthropic API error") == true || e.message?.startsWith("Google API error") == true -> "http_error"
                e is kotlinx.serialization.SerializationException -> "parse_error"
                else -> "request_error"
            }
            logRequestMetrics(
                providerId = config.id,
                modelName = model,
                messages = messages,
                elapsedMs = requestStartedAt.elapsedNow().inWholeMilliseconds,
                inputTokens = 0,
                outputTokens = 0,
                requestType = requestType,
                outcome = outcome
            )
            throw e
        }
    }

    // --- Streaming chat (保留用于兼容) ---

    fun streamChat(
        apiKey: String,
        messages: List<AiMessage>,
        providerId: String = "deepseek",
        modelName: String = "deepseek-v4-flash",
        customUrl: String = "",
        temperature: Double = 0.95
    ): Flow<String> = flow {
        val config = providers[providerId] ?: providers["deepseek"]!!
        val url = if (config.id == "custom") customUrl else config.baseUrl
        val model = normalizeModelName(config.id, modelName)

        val requestBody = ChatCompletionRequest(
            model = model,
            messages = messages,
            stream = true,
            temperature = temperature,
            thinking = if (config.id == "deepseek") ThinkingParam("disabled") else null
        )

        var finalUrl = url
        if (config.id == "google") {
            finalUrl = "$url/$model:streamGenerateContent?alt=sse"
        }

        val response: HttpResponse = client.post(finalUrl) {
            contentType(ContentType.Application.Json)
            header("Accept", "text/event-stream")
            if (config.id == "google") {
                header("X-Goog-Api-Key", apiKey)
            } else {
                header("Authorization", "Bearer $apiKey")
            }
            setBody(requestBody)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsChannel().readUTF8Line() ?: "Unknown error"
            throw Exception("API error ${response.status.value}: $errorBody")
        }

        val channel = response.bodyAsChannel()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: continue
            if (line.startsWith("data: ")) {
                val data = line.removePrefix("data: ")
                if (data == "[DONE]") break
                try {
                    val chunk = json.decodeFromString<StreamChunk>(data)
                    val content = chunk.choices?.firstOrNull()?.delta?.content
                    if (!content.isNullOrBlank()) {
                        emit(content)
                    }
                } catch (_: Exception) {
                    try {
                        val error = json.decodeFromString<StreamError>(data)
                        if (error.error?.message != null) {
                            throw Exception(error.error.message)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }.flowOn(Dispatchers.Default)

    private fun supportsJsonMode(providerId: String): Boolean = providerId in setOf(
        "deepseek", "ali", "zhipu", "siliconflow", "openai_compat", "custom"
    )
}
