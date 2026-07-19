package com.rhodes.privatechat.shared.voice

import com.rhodes.privatechat.shared.network.createHttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Xiaomi MiMo uses Chat Completions with audio attached to choices[0].message.audio.data. */
class XiaomiMimoTtsGateway(
    private val endpoint: String,
    private val apiKey: String,
    private val modelName: String = "mimo-v2.5-tts",
) : TtsGateway {
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun synthesize(request: TtsRequest): TtsResult {
        val speechText = prepareTtsSpeech(request.text, 800, "我在。")
        val body = buildJsonObject {
            put("model", modelName)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "assistant"); put("content", speechText) })
            })
            put("audio", buildJsonObject {
                put("format", request.format.ifBlank { "mp3" })
                put("voice", request.voiceId.ifBlank { "mimo_default" })
            })
        }
        val response = createHttpClient().post(endpoint) {
            header("api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) error("小米 MiMo TTS 错误 ${response.status.value}: ${raw.take(500)}")
        val message = json.parseToJsonElement(raw).jsonObject["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
        val audio = message?.get("audio")?.jsonObject
        val encoded = audio?.get("data")?.jsonPrimitive?.content.orEmpty()
        if (encoded.isBlank()) error("小米 MiMo TTS 未返回音频数据")
        val bytes = Base64.decode(encoded)
        if (bytes.isEmpty()) error("小米 MiMo TTS 返回空音频")
        val durationMs = (speechText.length.coerceAtLeast(1) * 180L).coerceAtMost(60_000L)
        return TtsResult(audioBytes = bytes, durationMs = durationMs)
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
