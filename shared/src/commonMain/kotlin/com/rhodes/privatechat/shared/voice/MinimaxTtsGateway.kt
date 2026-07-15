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

class MinimaxTtsGateway(
    private val endpoint: String,
    private val apiKey: String,
    private val modelName: String = "speech-2.8-hd",
    private val client: HttpClient = createWsClient(),
) : TtsGateway {
    override suspend fun synthesize(request: TtsRequest): TtsResult {
        val speechText = prepareTtsSpeech(request.text, 800, "我在。")
        val chunks = mutableListOf<ByteArray>()
        println("RHODES_AUDIO MinimaxWS synthesize: endpoint=$endpoint model=$modelName voiceId=${request.voiceId} text前50=${speechText.take(50)}")

        client.webSocket(
            urlString = endpoint,
            request = { header("Authorization", "Bearer $apiKey") },
        ) {
            val connected = incoming.receiveCatching().getOrNull() as? Frame.Text
            val connectedEvent = connected?.readText().orEmpty()
            if (!connectedEvent.contains("connected_success")) {
                println("RHODES_AUDIO MinimaxWS 连接失败: ${connectedEvent.ifBlank { "empty event" }}")
                error("Minimax TTS 连接失败")
            }

            send(Frame.Text(json.encodeToString(MinimaxTaskStart(
                model = modelName,
                voiceSetting = MinimaxVoiceSetting(voiceId = request.voiceId, speed = request.speed),
                audioSetting = MinimaxAudioSetting(format = request.format),
            ))))
            val started = incoming.receiveCatching().getOrNull() as? Frame.Text
            val startedEvent = started?.readText().orEmpty()
            if (!startedEvent.contains("task_started")) {
                println("RHODES_AUDIO MinimaxWS task_start 失败: ${startedEvent.ifBlank { "empty event" }}")
                error("Minimax TTS 任务启动失败")
            }

            send(Frame.Text(json.encodeToString(MinimaxTaskContinue(text = speechText))))

            while (true) {
                val frame = incoming.receiveCatching().getOrNull() ?: break
                val text = (frame as? Frame.Text)?.readText() ?: continue
                if (text.contains("error", ignoreCase = true) || text.contains("failed", ignoreCase = true)) {
                    error("Minimax TTS 流错误: $text")
                }
                val event = runCatching { json.decodeFromString(MinimaxStreamEvent.serializer(), text) }.getOrNull()
                event?.data?.audio?.takeIf { it.isNotBlank() }?.let { hex ->
                    chunks += hexToBytes(hex)
                }
                if (event?.isFinal == true) break
            }

            send(Frame.Text(json.encodeToString(MinimaxTaskFinish())))
        }

        val audioBytes = chunks.fold(ByteArray(0)) { acc, bytes -> acc + bytes }
        if (audioBytes.isEmpty()) error("Minimax TTS 返回空音频")
        val durationMs = (speechText.length.coerceAtLeast(1) * 180L).coerceAtMost(60_000L)
        println("RHODES_AUDIO MinimaxWS 成功: chunks=${chunks.size} bytes=${audioBytes.size} durationMs=$durationMs")
        return TtsResult(audioBytes = audioBytes, durationMs = durationMs)
    }
}

@Serializable
private data class MinimaxTaskStart(
    val event: String = "task_start",
    val model: String,
    @SerialName("voice_setting") val voiceSetting: MinimaxVoiceSetting = MinimaxVoiceSetting(),
    @SerialName("audio_setting") val audioSetting: MinimaxAudioSetting = MinimaxAudioSetting(),
)

@Serializable
private data class MinimaxVoiceSetting(
    @SerialName("voice_id") val voiceId: String = "male-qn-qingse",
    val speed: Double = 1.0,
    val vol: Double = 1.0,
    val pitch: Int = 0,
    @SerialName("english_normalization") val englishNormalization: Boolean = false,
)

@Serializable
private data class MinimaxAudioSetting(
    @SerialName("sample_rate") val sampleRate: Int = 32000,
    val bitrate: Int = 128000,
    val format: String = "mp3",
    val channel: Int = 1,
)

@Serializable
private data class MinimaxTaskContinue(
    val event: String = "task_continue",
    val text: String,
)

@Serializable
private data class MinimaxTaskFinish(val event: String = "task_finish")

@Serializable
private data class MinimaxStreamEvent(
    val data: MinimaxStreamData? = null,
    @SerialName("is_final") val isFinal: Boolean = false,
)

@Serializable
private data class MinimaxStreamData(val audio: String? = null)

private fun hexToBytes(hex: String): ByteArray {
    val clean = hex.trim()
    val normalized = if (clean.length % 2 == 1) "${clean}0" else clean
    return ByteArray(normalized.length / 2) { index ->
        normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun createWsClient(): HttpClient = createHttpClient {
    install(WebSockets)
}
