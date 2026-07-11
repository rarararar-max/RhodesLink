package com.rhodes.privatechat.shared.voice

import com.rhodes.privatechat.shared.network.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MinimaxTtsGateway(
    private val endpoint: String,
    private val apiKey: String,
    private val modelName: String = "speech-2.8-hd",
    private val client: HttpClient = createHttpClient { install(WebSockets) },
) : TtsGateway {
    override suspend fun synthesize(request: TtsRequest): TtsResult {
        val chunks = mutableListOf<ByteArray>()
        client.webSocket(
            urlString = endpoint,
            request = { header("Authorization", "Bearer $apiKey") },
        ) {
            val connected = incoming.receiveCatching().getOrNull() as? Frame.Text
            val connectedEvent = connected?.readText().orEmpty()
            if (!connectedEvent.contains("connected_success")) {
                error("Minimax TTS connect failed: ${connectedEvent.ifBlank { "empty event" }}")
            }

            send(Frame.Text(json.encodeToString(TtsTaskStart(
                model = modelName,
                voiceSetting = TtsVoiceSetting(voiceId = request.voiceId, speed = request.speed),
                audioSetting = TtsAudioSetting(format = request.format),
            ))))

            val started = incoming.receiveCatching().getOrNull() as? Frame.Text
            val startedEvent = started?.readText().orEmpty()
            if (!startedEvent.contains("task_started")) {
                error("Minimax TTS task_start failed: ${startedEvent.ifBlank { "empty event" }}")
            }

            send(Frame.Text(json.encodeToString(TtsTaskContinue(text = request.text))))

            while (true) {
                val frame = incoming.receiveCatching().getOrNull() ?: break
                val text = (frame as? Frame.Text)?.readText() ?: continue
                if (text.contains("error", ignoreCase = true) || text.contains("failed", ignoreCase = true)) {
                    error("Minimax TTS stream error: $text")
                }
                val event = runCatching { json.decodeFromString(TtsStreamEvent.serializer(), text) }.getOrNull()
                event?.data?.audio?.takeIf { it.isNotBlank() }?.let { hex ->
                    chunks += hexToBytes(hex)
                }
                if (event?.isFinal == true) break
            }

            send(Frame.Text(json.encodeToString(TtsTaskFinish())))
        }

        val audioBytes = chunks.fold(ByteArray(0)) { acc, bytes -> acc + bytes }
        if (audioBytes.isEmpty()) error("Minimax TTS returned empty audio")
        return TtsResult(audioBytes = audioBytes, durationMs = (request.text.length.coerceAtLeast(1) * 180L).coerceAtMost(60_000L))
    }
}

@Serializable
private data class TtsTaskStart(
    val model: String,
    @kotlinx.serialization.SerialName("voice_setting") val voiceSetting: TtsVoiceSetting,
    @kotlinx.serialization.SerialName("audio_setting") val audioSetting: TtsAudioSetting,
)

@Serializable
private data class TtsVoiceSetting(
    @kotlinx.serialization.SerialName("voice_id") val voiceId: String,
    val speed: Double = 1.0,
)

@Serializable
private data class TtsAudioSetting(
    val format: String = "mp3",
)

@Serializable
private data class TtsTaskContinue(
    val text: String,
)

@Serializable
private data class TtsTaskFinish(
    val action: String = "finish",
)

@Serializable
private data class TtsStreamEvent(
    val data: TtsStreamData? = null,
    @kotlinx.serialization.SerialName("is_final") val isFinal: Boolean = false,
)

@Serializable
private data class TtsStreamData(
    val audio: String? = null,
)

private fun hexToBytes(hex: String): ByteArray {
    val len = hex.length / 2
    val bytes = ByteArray(len)
    for (i in 0 until len) {
        bytes[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
    return bytes
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
