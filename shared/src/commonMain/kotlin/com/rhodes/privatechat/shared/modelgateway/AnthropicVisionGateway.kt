package com.rhodes.privatechat.shared.modelgateway

import com.rhodes.privatechat.shared.network.createHttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AnthropicVisionGateway(
    private val endpoint: String,
    private val apiKey: String,
    private val modelName: String,
) : VisionGateway {
    override suspend fun analyzeImage(request: VisionAnalyzeRequest): VisionAnalyzeResponse {
        val (mediaType, data) = splitDataUrl(request.imageUrlOrBase64)
        require(data.isNotBlank()) { "Anthropic 识图需要 Base64 图片数据" }
        val body = buildJsonObject {
            put("model", modelName)
            put("max_tokens", 1024)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "image")
                            put("source", buildJsonObject {
                                put("type", "base64")
                                put("media_type", mediaType)
                                put("data", data)
                            })
                        })
                        add(buildJsonObject { put("type", "text"); put("text", request.prompt) })
                    })
                })
            })
        }
        val response = createHttpClient().post(endpoint) {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) error("Anthropic 识图错误 ${response.status.value}: ${raw.take(500)}")
        val text = json.parseToJsonElement(raw).jsonObject["content"]?.jsonArray
            ?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
            ?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()
        return VisionAnalyzeResponse(text)
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

private fun splitDataUrl(value: String): Pair<String, String> {
    if (!value.startsWith("data:")) return "image/jpeg" to value
    val comma = value.indexOf(',')
    if (comma < 0) return "image/jpeg" to ""
    val mediaType = value.substringAfter("data:").substringBefore(';').ifBlank { "image/jpeg" }
    return mediaType to value.substring(comma + 1)
}
