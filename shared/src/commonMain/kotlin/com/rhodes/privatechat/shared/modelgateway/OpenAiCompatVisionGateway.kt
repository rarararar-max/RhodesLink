package com.rhodes.privatechat.shared.modelgateway

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
) : VisionGateway {
    private val client = createHttpClient()

    override suspend fun analyzeImage(request: VisionAnalyzeRequest): VisionAnalyzeResponse {
        val response = client.post(endpoint) {
            if (useApiKeyHeader) header("api-key", apiKey) else bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(VisionChatRequest(modelName, listOf(VisionChatMessage(content = listOf(
                VisionPart(type = "image_url", imageUrl = VisionImageUrl(request.imageUrlOrBase64)),
                VisionPart(type = "text", text = request.prompt)
            )))))
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
        return content.ifBlank { message["reasoning_content"]?.jsonPrimitive?.textOrEmpty().orEmpty() }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

private fun JsonPrimitive.textOrEmpty(): String = if (this is JsonNull) "" else content

@Serializable private data class VisionChatRequest(val model: String, val messages: List<VisionChatMessage>)
@Serializable private data class VisionChatMessage(val role: String = "user", val content: List<VisionPart>)
@Serializable private data class VisionPart(val type: String, val text: String? = null, @kotlinx.serialization.SerialName("image_url") val imageUrl: VisionImageUrl? = null)
@Serializable private data class VisionImageUrl(val url: String)
