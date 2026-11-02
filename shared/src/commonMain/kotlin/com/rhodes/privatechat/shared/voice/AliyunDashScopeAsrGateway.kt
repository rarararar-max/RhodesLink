package com.rhodes.privatechat.shared.voice

import com.rhodes.privatechat.shared.network.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.withTimeoutOrNull

class AliyunDashScopeAsrGateway(
    private val apiKey: String,
    private val modelName: String = DEFAULT_DASHSCOPE_ASR_MODEL_CONFIG,
    private val endpoint: String = DEFAULT_DASHSCOPE_ASR_ENDPOINT,
    private val client: HttpClient? = null,
) : AsrGateway {
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun transcribe(request: AsrRequest): AsrResult {
        if (request.pcm16kMonoAudio.isEmpty()) return AsrResult("")
        validateAsrAudio(request)
        require(request.sampleRate == 16000 && request.inputAudioFormat == "pcm") { "阿里实时识别需要16kHz单声道16位PCM录音" }
        require(apiKey.isNotBlank()) { "请填写语音识别密钥" }
        val url = io.ktor.http.URLBuilder(endpoint.trim()).apply {
            require(protocol.name == "wss" || protocol.name == "ws") { "实时识别地址需要以 ws:// 或 wss:// 开头" }
            parameters["model"] = parseRealtimeModel(modelName)
        }.buildString()
        val transport = client ?: dashScopeAsrClient()
        val transcript = DashScopeTranscript()
        try {
            val result = withTimeoutOrNull(90_000L) {
                transport.webSocket(urlString = url, request = { header("Authorization", "Bearer ${apiKey.trim()}") }) {
                    send(Frame.Text(json.encodeToString(SessionUpdateEvent.manual(request, parseTranscriptionModel(modelName)))))
                    // Do not upload audio until the server has accepted this session configuration.
                    while (true) {
                        val frame = incoming.receiveCatching().getOrThrow()
                            ?: error("语音连接在会话就绪前关闭，请检查密钥、模型和服务区域")
                        if (frame is Frame.Text && transcript.accept(frame.readText()) == "session.updated") break
                    }
                    var offset = 0
                    while (offset < request.pcm16kMonoAudio.size) {
                        val end = minOf(offset + ASR_CHUNK_SIZE, request.pcm16kMonoAudio.size)
                        send(Frame.Text(json.encodeToString(AppendAudioEvent(audio = Base64.encode(request.pcm16kMonoAudio.copyOfRange(offset, end))))))
                        offset = end
                    }
                    send(Frame.Text(json.encodeToString(CommitAudioEvent())))
                    while (!transcript.completed) {
                        val frame = incoming.receiveCatching().getOrThrow()
                            ?: error("语音连接中断，未收到完整识别结果，请重试")
                        if (frame is Frame.Text) transcript.accept(frame.readText())
                    }
                    // Completion, including a final empty transcript (silence), is authoritative.
                    // Do not add a five-second UI delay waiting for session.finished.
                    try {
                        send(Frame.Text(json.encodeToString(FinishSessionEvent())))
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* Final transcript already received. */ }
                }
                transcript.result()
            }
            return result ?: throw IllegalStateException("语音识别超时，请检查网络后重试")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A malformed peer close frame must not discard an already completed transcript.
            if (transcript.completed) return transcript.result()
            throw IllegalStateException(com.rhodes.privatechat.shared.network.ModelErrorMessages.redact(asrFailureMessage(e), apiKey), e)
        } finally {
            if (client == null) transport.close()
        }
    }
}

/** Protocol state shared by every ASR entry point. Interim text is never a successful result. */
class DashScopeTranscript {
    var completed: Boolean = false
        private set
    private var finalText = ""
    private var emotion: String? = null

    fun accept(raw: String): String? {
        val event = try { json.decodeFromString(AsrEvent.serializer(), raw) }
            catch (_: Exception) { error("语音服务返回了无法解析的数据，请检查实时识别接口") }
        event.failureDescription()?.let { error("$ASR_ERROR_PREFIX $it") }
        if (event.type == "conversation.item.input_audio_transcription.completed") {
            require(event.transcript != null) { "语音服务的完成事件缺少识别文字" }
            finalText = event.transcript
            emotion = event.emotion ?: emotion
            completed = true
        } else if (event.type == "conversation.item.input_audio_transcription.text" ||
            event.type == "conversation.item.input_audio_transcription.delta") {
            emotion = event.emotion ?: emotion
        } else if (event.type == "session.finished" && !completed) {
            error("语音会话提前结束，未收到完整识别结果，请重试")
        }
        return event.type
    }

    fun result(): AsrResult {
        check(completed) { "语音识别未完成，请重试" }
        return AsrResult(finalText.trim(), emotion)
    }
}

@Serializable
private data class SessionUpdateEvent(
    @SerialName("event_id") val eventId: String = nextEventId(),
    val type: String = "session.update",
    val session: SessionConfig,
) {
    companion object {
        fun manual(request: AsrRequest, transcriptionModel: String): SessionUpdateEvent = SessionUpdateEvent(
            session = SessionConfig(
                modalities = listOf("text"),
                turnDetection = null,
                inputAudioFormat = request.inputAudioFormat,
                inputAudioTranscription = TranscriptionConfig(
                    model = transcriptionModel,
                    language = request.language,
                ),
            )
        )
    }
}

@Serializable
private data class SessionConfig(
    val modalities: List<String>,
    @SerialName("turn_detection") val turnDetection: String? = null,
    @SerialName("input_audio_format") val inputAudioFormat: String,
    @SerialName("input_audio_transcription") val inputAudioTranscription: TranscriptionConfig,
)

@Serializable
private data class TranscriptionConfig(
    val model: String,
    val language: String,
)

@Serializable
private data class AppendAudioEvent(
    @SerialName("event_id") val eventId: String = nextEventId(),
    val type: String = "input_audio_buffer.append",
    val audio: String,
)

@Serializable
private data class CommitAudioEvent(
    @SerialName("event_id") val eventId: String = nextEventId(),
    val type: String = "input_audio_buffer.commit",
)

@Serializable
private data class FinishSessionEvent(
    @SerialName("event_id") val eventId: String = nextEventId(),
    val type: String = "session.finish",
)

@Serializable
private data class AsrEvent(
    val type: String? = null,
    val text: String? = null,
    val stash: String? = null,
    val transcript: String? = null,
    val emotion: String? = null,
    // DashScope reports failures either as a plain string or as an object ({code, message, type}).
    // A String-typed field made the whole frame fail to decode, so the error frame was dropped
    // silently and users only saw an unexplained disconnect.
    val error: JsonElement? = null,
    val code: String? = null,
    val message: String? = null,
) {
    /** Readable failure text for this frame, or null when the frame is not an error. */
    fun failureDescription(): String? {
        val element = error?.takeUnless { it is kotlinx.serialization.json.JsonNull }
        if (type != "error" && element == null && type != "conversation.item.input_audio_transcription.failed") return null
        val resolvedCode = code ?: element.stringField("code")
        val resolvedMessage = message ?: element.stringField("message") ?: element.asPlainString()
        if (resolvedCode == null && resolvedMessage == null) {
            return element?.let { "unparsed=$it" } ?: "语音服务返回错误但未提供原因"
        }
        return "code=${resolvedCode.orEmpty()} message=${resolvedMessage.orEmpty()}"
    }
}

private fun JsonElement?.stringField(name: String): String? =
    ((this as? JsonObject)?.get(name) as? JsonPrimitive)?.contentOrNull

private fun JsonElement?.asPlainString(): String? = (this as? JsonPrimitive)?.contentOrNull

private const val ASR_ERROR_PREFIX = "DashScope ASR error:"

/** Maps transport-level failures (rejected handshake, wrong endpoint) to an actionable message. */
private fun asrFailureMessage(error: Throwable): String {
    val raw = error.message.orEmpty().ifBlank { error.javaClass.simpleName }
    if (raw.startsWith(ASR_ERROR_PREFIX)) return raw
    val hint = when {
        raw.contains("Control frame must be less than 125", ignoreCase = true) ->
            "实时语音 WebSocket 控制帧异常（125字节限制）：请核对服务区域、密钥与模型是否匹配；这不是识别文字过长。"
        raw.contains("401") -> "语音识别鉴权失败(401)：语音识别需要阿里云百炼(DashScope)的 Key，不能与文本模型(如 DeepSeek)的 Key 混用。"
        raw.contains("403") -> "语音识别鉴权失败(403)：请求被拒绝，请在百炼控制台核对模型权限、业务空间和服务区域。"
        raw.contains("404") -> "语音识别接口返回 404：请检查实时语音地址与模型配置。"
        raw.contains("101") -> "语音识别连接被拒绝：请检查地址、密钥与模型配置。"
        else -> null
    }
    return if (hint == null) raw else "$hint（原始错误：$raw）"
}

const val DEFAULT_DASHSCOPE_ASR_ENDPOINT = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime"
const val DEFAULT_DASHSCOPE_ASR_MODEL_CONFIG = "qwen3-asr-flash-realtime"

private const val ASR_CHUNK_SIZE = 3200

private fun parseRealtimeModel(modelName: String): String {
    val configured = modelName.substringBefore("|").trim()
    return when {
        configured.isBlank() -> "qwen3-asr-flash-realtime"
        else -> configured
    }
}

private fun parseTranscriptionModel(modelName: String): String {
    val configured = modelName.substringAfter("|", "").trim()
    return configured.ifBlank { parseRealtimeModel(modelName) }
}

private fun nextEventId(): String = "event_${kotlin.random.Random.nextLong().toString().replace("-", "")}" 

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun dashScopeAsrClient(): HttpClient = createHttpClient {
    install(WebSockets)
}
