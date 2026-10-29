package com.rhodes.privatechat.shared.voice

import com.rhodes.privatechat.shared.network.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.encodeURLParameter
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
    private val client: HttpClient = dashScopeAsrClient(),
) : AsrGateway {
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun transcribe(request: AsrRequest): AsrResult {
        if (request.pcm16kMonoAudio.isEmpty()) return AsrResult(text = "")
        var finalText = ""
        var emotion: String? = null
        val realtimeModel = parseRealtimeModel(modelName)
        val transcriptionModel = parseTranscriptionModel(modelName)

        try {
            client.webSocket(
                urlString = "$endpoint?model=${realtimeModel.encodeURLParameter()}",
                request = { header("Authorization", "Bearer $apiKey") },
            ) {
                send(Frame.Text(json.encodeToString(SessionUpdateEvent.manual(request, transcriptionModel))))

                request.pcm16kMonoAudio.asList().chunked(ASR_CHUNK_SIZE).forEach { chunk ->
                    send(Frame.Text(json.encodeToString(AppendAudioEvent(audio = Base64.encode(chunk.toByteArray())))))
                }
                send(Frame.Text(json.encodeToString(CommitAudioEvent())))

                var finishSent = false
                var sessionFinished = false
                while (!sessionFinished) {
                    val frame = incoming.receiveCatching().getOrNull() ?: break
                    val text = (frame as? Frame.Text)?.readText() ?: continue
                    val event = runCatching { json.decodeFromString(AsrEvent.serializer(), text) }.getOrNull() ?: continue
                    val failure = event.failureDescription()
                    if (failure != null) {
                        // Once audio has been transcribed, a trailing server complaint (for example the
                        // harmless rejection of session.finish) must not throw the transcript away.
                        if (finalText.isNotBlank()) break
                        error("$ASR_ERROR_PREFIX $failure")
                    }
                    when (event.type) {
                        "conversation.item.input_audio_transcription.text" -> {
                            finalText = event.text.orEmpty() + event.stash.orEmpty()
                            emotion = event.emotion ?: emotion
                        }
                        "conversation.item.input_audio_transcription.delta" -> {
                            finalText = event.text.orEmpty() + event.stash.orEmpty()
                            emotion = event.emotion ?: emotion
                        }
                        "conversation.item.input_audio_transcription.completed" -> {
                            finalText = event.transcript ?: finalText
                            emotion = event.emotion ?: emotion
                            if (!finishSent) {
                                send(Frame.Text(json.encodeToString(FinishSessionEvent())))
                                finishSent = true
                            }
                        }
                        "session.finished" -> {
                            sessionFinished = true
                        }
                        "input_audio_buffer.committed", "session.created", "session.updated" -> Unit
                        "error" -> {
                            if (finalText.isNotBlank()) break
                            error("$ASR_ERROR_PREFIX ${event.message.orEmpty()}".trim())
                        }
                    }
                }
                if (!finishSent && finalText.isNotBlank()) {
                    send(Frame.Text(json.encodeToString(FinishSessionEvent())))
                    withTimeoutOrNull(5_000) {
                        while (true) {
                            val trailing = incoming.receiveCatching().getOrNull() ?: break
                            val trailingText = (trailing as? Frame.Text)?.readText() ?: continue
                            val trailingEvent = runCatching { json.decodeFromString(AsrEvent.serializer(), trailingText) }.getOrNull()
                            if (trailingEvent?.type == "session.finished") break
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A rejected handshake surfaces as an opaque "expected status code 101 but was 401".
            // Turn it into something a user can act on instead of a bare "语音识别报错".
            throw IllegalStateException(asrFailureMessage(e), e)
        }

        return AsrResult(text = finalText.trim(), emotion = emotion)
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
        val element = error
        val resolvedCode = code ?: element.stringField("code")
        val resolvedMessage = message ?: element.stringField("message") ?: element.asPlainString()
        if (resolvedCode == null && resolvedMessage == null) {
            return element?.let { "unparsed=$it" }
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
            "实时语音连接关闭帧异常：通常是模型配置或结束会话流程不匹配。请确认使用 qwen3-asr-flash-realtime，并重试。"
        raw.contains("401") -> "语音识别鉴权失败(401)：语音识别需要阿里云百炼(DashScope)的 Key，不能与文本模型(如 DeepSeek)的 Key 混用。"
        raw.contains("403") -> "语音识别鉴权失败(403)：该 Key 未开通实时语音(Realtime)权限，请在百炼控制台确认模型与业务空间。"
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
        configured == "qwen3.5-omni-flash-realtime" -> "qwen3-asr-flash-realtime"
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
