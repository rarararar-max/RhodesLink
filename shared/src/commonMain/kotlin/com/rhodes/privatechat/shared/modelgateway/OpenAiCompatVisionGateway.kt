package com.rhodes.privatechat.shared.modelgateway

import com.rhodes.privatechat.shared.model.ThinkingParam
import com.rhodes.privatechat.shared.network.createHttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OpenAiCompatVisionGateway(
    private val endpoint: String,
    private val apiKey: String,
    private val modelName: String,
    private val useApiKeyHeader: Boolean = false,
    private val providerId: String = if (endpoint.contains("deepseek", ignoreCase = true)) "deepseek" else "",
) : VisionGateway {
    private val client = createHttpClient()

    override suspend fun analyzeImage(request: VisionAnalyzeRequest): VisionAnalyzeResponse {
        val response = client.post(endpoint) {
            if (useApiKeyHeader) header("api-key", apiKey) else bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(VisionChatRequest(modelName, listOf(VisionChatMessage(content = listOf(
                VisionPart(type = "image_url", imageUrl = VisionImageUrl(request.imageUrlOrBase64)),
                VisionPart(type = "text", text = request.prompt)
            ))), thinking = visionThinkingParam(),
                enable_thinking = com.rhodes.privatechat.shared.network.ModelCapabilities.enableThinkingParameter(providerId, modelName)))
        }
        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("识图服务错误 ${response.status.value}: ${raw.take(500)}")
        }
        val text = runCatching { extractText(raw) }.getOrElse {
            error("识图服务返回了无法解析的响应: ${raw.take(500)}")
        }
        if (text.isBlank()) error("识图服务没有返回文字内容: ${raw.take(500)}")
        return VisionAnalyzeResponse(text)
    }

    /**
     * 识图只是一次工具调用，不是角色回复：DeepSeek 默认开启思考模式，会明显变慢变贵，
     * 对已适配的型号显式关闭思考；思维链永远不作为识图结果回传。
     */
    private fun visionThinkingParam(): ThinkingParam? =
        com.rhodes.privatechat.shared.network.ModelCapabilities.thinkingParameter(providerId, modelName)

    private fun extractText(raw: String): String {
        val message = json.parseToJsonElement(raw).jsonObject["choices"]
            ?.let { it as? JsonArray }
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?: return ""
        val content = when (val value = message["content"]) {
            is JsonPrimitive -> value.textOrEmpty()
            is JsonArray -> value.joinToString("") { part ->
                runCatching { part.jsonObject["text"]?.jsonPrimitive?.textOrEmpty().orEmpty() }.getOrDefault("")
            }
            else -> ""
        }
        // Reasoning is never a picture description, even when the visible answer is empty.
        return content
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

private fun JsonPrimitive.textOrEmpty(): String = if (this is JsonNull) "" else content

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable private data class VisionChatRequest(
    val model: String,
    val messages: List<VisionChatMessage>,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val thinking: ThinkingParam? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val enable_thinking: Boolean? = null,
    val stream: Boolean = false,
)
@Serializable private data class VisionChatMessage(val role: String = "user", val content: List<VisionPart>)
@Serializable private data class VisionPart(val type: String, val text: String? = null, @kotlinx.serialization.SerialName("image_url") val imageUrl: VisionImageUrl? = null)
@Serializable private data class VisionImageUrl(val url: String)
