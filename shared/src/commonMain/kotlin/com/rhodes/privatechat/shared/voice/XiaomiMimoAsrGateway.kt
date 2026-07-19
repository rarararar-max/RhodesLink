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

/** MiMo ASR accepts a WAV/MP3 data URL through its Chat Completions audio content part. */
class XiaomiMimoAsrGateway(
    private val endpoint: String,
    private val apiKey: String,
    private val modelName: String = "mimo-v2.5-asr",
) : AsrGateway {
    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun transcribe(request: AsrRequest): AsrResult {
        if (request.pcm16kMonoAudio.isEmpty()) return AsrResult("")
        val wav = pcm16ToWav(request.pcm16kMonoAudio, request.sampleRate)
        val body = buildJsonObject {
            put("model", modelName)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "input_audio")
                            put("input_audio", buildJsonObject {
                                put("data", "data:audio/wav;base64,${Base64.encode(wav)}")
                            })
                        })
                    })
                })
            })
            put("asr_options", buildJsonObject { put("language", request.language.ifBlank { "auto" }) })
        }
        val response = createHttpClient().post(endpoint) {
            header("api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) error("小米 MiMo ASR 错误 ${response.status.value}: ${raw.take(500)}")
        val content = json.parseToJsonElement(raw).jsonObject["choices"]?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content.orEmpty()
        return AsrResult(content.trim())
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

private fun pcm16ToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
    val header = ByteArray(44)
    fun putAscii(offset: Int, value: String) = value.forEachIndexed { index, char -> header[offset + index] = char.code.toByte() }
    fun putInt(offset: Int, value: Int) { repeat(4) { index -> header[offset + index] = (value shr (8 * index)).toByte() } }
    fun putShort(offset: Int, value: Int) { header[offset] = value.toByte(); header[offset + 1] = (value shr 8).toByte() }
    putAscii(0, "RIFF"); putInt(4, pcm.size + 36); putAscii(8, "WAVEfmt ")
    putInt(16, 16); putShort(20, 1); putShort(22, 1); putInt(24, sampleRate)
    putInt(28, sampleRate * 2); putShort(32, 2); putShort(34, 16); putAscii(36, "data"); putInt(40, pcm.size)
    return header + pcm
}
