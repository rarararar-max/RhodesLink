package com.rhodes.privatechat.shared.voice

import com.rhodes.privatechat.shared.network.createHttpClient
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.json.*

class DeepSeekAsrGateway(private val endpoint: String, private val apiKey: String) : AsrGateway {
    override suspend fun transcribe(request: AsrRequest): AsrResult {
        if (request.pcm16kMonoAudio.isEmpty()) return AsrResult("")
        validateDeepSeekAudio(request)
        require(apiKey.isNotBlank()) { "请填写 DeepSeek 语音识别密钥" }
        val wav = pcm16ToWav(request.pcm16kMonoAudio, request.sampleRate)
        val client = createHttpClient()
        try {
            return withVoiceDeadline(150_000L, "DeepSeek语音识别") {
                val response = client.post(endpoint.trim()) {
                    header(HttpHeaders.Authorization, "Bearer ${apiKey.trim()}")
                    setBody(MultiPartFormDataContent(formData {
                        append("file", wav, Headers.build {
                            append(HttpHeaders.ContentType, "audio/wav")
                            append(HttpHeaders.ContentDisposition, "filename=speech.wav")
                        })
                    }))
                }
                val raw = response.bodyAsText()
                if (!response.status.isSuccess()) error("DeepSeek语音识别失败（${response.status.value}）：" + when(response.status.value) {
                    400 -> "音频格式无效"; 401,403 -> "请检查密钥与账号权限"
                    413 -> "音频超过8 MiB"; 429 -> "请求过于频繁，请稍后重试"
                    408,504 -> "识别超时，请稍后重试"; 503 -> "服务繁忙，请稍后重试"
                    else -> "请检查网络与服务配置"
                })
                AsrResult(parseDeepSeekAsrText(raw))
            }
        } finally { client.close() }
    }
}
fun validateDeepSeekAudio(request: AsrRequest) {
    require(request.inputAudioFormat == "pcm" && request.sampleRate in setOf(8000,16000,22050,24000,32000,44100,48000) && request.pcm16kMonoAudio.size % 2 == 0) { "需要单声道16位PCM录音" }
    require(request.pcm16kMonoAudio.size.toLong() <= request.sampleRate.toLong()*2*120) { "DeepSeek录音最长120秒" }
    require(request.pcm16kMonoAudio.size.toLong()+44 <= 8*1024*1024) { "DeepSeek音频最大8 MiB" }
}
fun parseDeepSeekAsrText(raw: String): String {
    val value = Json.parseToJsonElement(raw).jsonObject["text"]
    require(value is JsonPrimitive && value.isString) { "DeepSeek响应缺少有效的识别文字" }
    return value.content.trim()
}
