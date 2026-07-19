package com.rhodes.privatechat.shared.voice

import com.rhodes.privatechat.shared.network.createHttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** Vocu returns an MP3 URL, which is downloaded before returning to the shared playback path. */
class VocuTtsGateway(
    private val endpoint: String,
    private val apiKey: String,
) : TtsGateway {
    private val client = createHttpClient()

    override suspend fun synthesize(request: TtsRequest): TtsResult {
        require(request.voiceId.isNotBlank()) { "Vocu 需要在角色编辑页填写音色 ID" }
        val speechText = prepareTtsSpeech(request.text, 800, "我在。")
        // Vocu's value grows with duration, unlike the existing TTS speed setting.
        val speechRate = (1.0 / request.speed.coerceAtLeast(0.1)).coerceIn(0.5, 2.0)
        val response = client.post(endpoint) {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(VocuTtsRequest(voiceId = request.voiceId, text = speechText, speechRate = speechRate))
        }
        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) error("Vocu TTS 错误 ${response.status.value}: ${raw.take(500)}")
        val result = runCatching { json.decodeFromString(VocuTtsResponse.serializer(), raw) }
            .getOrElse { error("Vocu TTS 返回无法解析: ${raw.take(500)}") }
        if (result.status != 200) error("Vocu TTS 错误 ${result.status}: ${result.message.ifBlank { raw.take(500) }}")
        val audioUrl = result.data?.audio.orEmpty()
        if (audioUrl.isBlank()) error("Vocu TTS 未返回音频地址")
        val audioResponse = client.get(audioUrl)
        if (!audioResponse.status.isSuccess()) error("Vocu 音频下载错误 ${audioResponse.status.value}")
        val bytes = audioResponse.body<ByteArray>()
        if (bytes.isEmpty()) error("Vocu TTS 返回空音频")
        val durationMs = (speechText.length.coerceAtLeast(1) * 180L * speechRate).toLong().coerceAtMost(60_000L)
        return TtsResult(audioBytes = bytes, durationMs = durationMs)
    }

    @Serializable private data class VocuTtsRequest(
        val voiceId: String,
        val text: String,
        val promptId: String = "default",
        val preset: String = "balance",
        val break_clone: Boolean = true,
        val language: String = "auto",
        val speechRate: Double,
        val stream: Boolean = false,
        val srt: Boolean = false,
    )

    @Serializable private data class VocuTtsResponse(
        val status: Int = 0,
        val message: String = "",
        val data: VocuTtsData? = null,
    )

    @Serializable private data class VocuTtsData(val audio: String = "")

    private companion object {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }
}
