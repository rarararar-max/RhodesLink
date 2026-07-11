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
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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

        client.webSocket(
            urlString = "$endpoint?model=${realtimeModel.encodeURLParameter()}",
            request = { header("Authorization", "Bearer $apiKey") },
        ) {
            send(Frame.Text(json.encodeToString(SessionUpdateEvent.manual(request, transcriptionModel))))

            request.pcm16kMonoAudio.asList().chunked(ASR_CHUNK_SIZE).forEach { chunk ->
                send(Frame.Text(json.encodeToString(AppendAudioEvent(audio = Base64.encode(chunk.toByteArray())))))
            }
            send(Frame.Text(json.encodeToString(CommitAudioEvent())))

            while (true) {
                val frame = incoming.receiveCatching().getOrNull() ?: break
                val text = (frame as? Frame.Text)?.readText() ?: continue
                val event = runCatching { json.decodeFromString(AsrEvent.serializer(), text) }.getOrNull() ?: continue
                if (event.error != null || event.code != null) {
                    error("DashScope ASR error: code=${event.code.orEmpty()} message=${event.message.orEmpty()}")
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
                        break
                    }
                    "input_audio_buffer.committed", "session.created", "session.updated" -> Unit
                    "error" -> error("DashScope ASR error: ${event.message.orEmpty()}")
                }
            }

            send(Frame.Text(json.encodeToString(FinishSessionEvent())))
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
    val error: String? = null,
    val code: String? = null,
    val message: String? = null,
)

const val DEFAULT_DASHSCOPE_ASR_ENDPOINT = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime"
const val DEFAULT_DASHSCOPE_ASR_MODEL_CONFIG = "qwen3.5-omni-flash-realtime|qwen3-asr-flash-realtime"

private const val ASR_CHUNK_SIZE = 3200

private fun parseRealtimeModel(modelName: String): String {
    return modelName.substringBefore("|", "qwen3.5-omni-flash-realtime").ifBlank { "qwen3.5-omni-flash-realtime" }
}

private fun parseTranscriptionModel(modelName: String): String {
    return modelName.substringAfter("|", "qwen3-asr-flash-realtime").ifBlank { "qwen3-asr-flash-realtime" }
}

private fun nextEventId(): String = "event_${kotlin.random.Random.nextLong().toString().replace("-", "")}" 

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun dashScopeAsrClient(): HttpClient = createHttpClient {
    install(WebSockets)
}
