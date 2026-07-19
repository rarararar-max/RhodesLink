package com.rhodes.privatechat.shared.modelgateway

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.rhodes.privatechat.shared.network.createHttpClient

class AliyunQwenVlGateway(
    private val endpoint: String,
    private val apiKey: String,
    private val modelName: String = "qwen3-vl-plus",
    private val client: HttpClient = createHttpClient(),
) : VisionGateway {
    private val visionPrompt = """请分析这张图片，用以下 JSON 格式回答（只输出 JSON，不要 Markdown 标记）：
{
  "visibleSummary": "一句话描述画面中最重要的可见内容",
  "userStateGuess": "基于画面的谨慎推测，不确定写 unknown",
  "notableObjects": ["物体1", "物体2"],
  "sceneQuality": "clear | dim | blurry | blocked | unknown",
  "confidence": 0.0~1.0
}
要求：
- 只描述画面中确定看到的内容，不确定的字段填 "unknown" 或 0.0
- 不要编造看不见的内容
- 输出中文"""

    override suspend fun analyzeImage(request: VisionAnalyzeRequest): VisionAnalyzeResponse {
        println("RHODES_VISION AliyunQwenVlGateway.analyzeImage: endpoint=$endpoint model=$modelName imageLen=${request.imageUrlOrBase64.length}")
        val response = client.post(endpoint) {
            bearerAuth(apiKey)
            header("X-DashScope-SSE", "enable")
            header("Accept", "text/event-stream")
            contentType(ContentType.Application.Json)
            setBody(NativeVisionRequest(
                model = modelName,
                input = NativeVisionInput(messages = listOf(
                    NativeVisionMessage(role = "user", content = listOf(
                        NativeVisionContent(image = request.imageUrlOrBase64),
                        NativeVisionContent(text = visionPrompt),
                    ))
                )),
                parameters = NativeVisionParameters(enableThinking = false, incrementalOutput = true, thinkingBudget = 0),
            ))
        }
        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("识图服务错误 ${response.status.value}: ${raw.take(500)}")
        }

        println("RHODES_VISION 原始响应(前500): ${raw.take(500)}")
        val parsed = parseResponse(raw)
        println("RHODES_VISION 解析结果: text长度=${parsed.text.length} 前300=${parsed.text.take(300)}")
        return parsed
    }

    private fun parseResponse(raw: String): VisionAnalyzeResponse {
        val openAiCompatible = runCatching {
            json.parseToJsonElement(raw).jsonObject["choices"]?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.content.orEmpty()
        }.getOrDefault("")
        if (openAiCompatible.isNotBlank()) {
            println("RHODES_VISION parseResponse: OpenAI 兼容 JSON 解析成功, text长度=${openAiCompatible.length}")
            return VisionAnalyzeResponse(text = openAiCompatible)
        }
        val single = runCatching { json.decodeFromString(NativeVisionResponse.serializer(), raw) }
            .getOrNull()
            ?.output?.choices?.firstOrNull()?.message?.content
            .orEmpty()
            .mapNotNull { it.text }
            .joinToString("")
        if (single.isNotBlank()) {
            println("RHODES_VISION parseResponse: 单次 JSON 解析成功, text长度=${single.length}")
            return VisionAnalyzeResponse(text = single)
        }
        println("RHODES_VISION parseResponse: 单次 JSON 解析失败, 尝试 SSE 行解析")

        val text = raw.lineSequence()
            .map { it.removePrefix("data:").trim() }
            .filter { it.isNotBlank() && it != "[DONE]" }
            .mapNotNull { line ->
                runCatching { json.decodeFromString(NativeVisionResponse.serializer(), line) }.getOrNull()
            }
            .flatMap { it.output?.choices.orEmpty() }
            .flatMap { it.message?.content.orEmpty() }
            .mapNotNull { it.text }
            .joinToString("")

        if (text.isNotBlank()) {
            println("RHODES_VISION parseResponse: SSE 解析成功, text长度=${text.length}")
            return VisionAnalyzeResponse(text = text)
        }
        error("识图服务返回了无法解析的响应: ${raw.take(500)}")
    }
}

@Serializable
private data class NativeVisionRequest(
    val model: String,
    val input: NativeVisionInput,
    val parameters: NativeVisionParameters,
)

@Serializable
private data class NativeVisionInput(val messages: List<NativeVisionMessage>)

@Serializable
private data class NativeVisionMessage(val role: String, val content: List<NativeVisionContent>)

@Serializable
private data class NativeVisionContent(val image: String? = null, val text: String? = null)

@Serializable
private data class NativeVisionParameters(
    @SerialName("enable_thinking") val enableThinking: Boolean,
    @SerialName("incremental_output") val incrementalOutput: Boolean,
    @SerialName("thinking_budget") val thinkingBudget: Int,
)

@Serializable
private data class NativeVisionResponse(val output: NativeVisionOutput? = null)

@Serializable
private data class NativeVisionOutput(val choices: List<NativeVisionChoice> = emptyList())

@Serializable
private data class NativeVisionChoice(val message: NativeVisionMessageOut? = null)

@Serializable
private data class NativeVisionMessageOut(
    val content: List<NativeVisionContentOut> = emptyList(),
    @SerialName("reasoning_content") val reasoningContent: String? = null,
)

@Serializable
private data class NativeVisionContentOut(val text: String? = null)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
