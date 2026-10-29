package com.rhodes.privatechat.shared.vector

import com.rhodes.privatechat.shared.network.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Alibaba DashScope's native text embedding protocol.
 *
 * This is deliberately separate from the OpenAI-compatible gateway: the request uses
 * input.texts and the successful response is rooted at output.embeddings.
 */
class AliyunNativeEmbeddingGateway(
    private val endpoint: String,
    private val apiKey: String,
    private val modelName: String,
    private val client: HttpClient = createHttpClient(),
) : EmbeddingGateway {
    override suspend fun embed(text: String): List<Double> {
        val response = client.post(endpoint) {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("model", modelName)
                put("input", buildJsonObject {
                    put("texts", JsonArray(listOf(JsonPrimitive(text))))
                })
            })
        }
        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw EmbeddingGatewayException(formatEmbeddingError(response.status.value, raw))
        }
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }
            .getOrElse { throw EmbeddingGatewayException("阿里原生 Embedding 返回不是有效 JSON：${raw.take(240)}") }
        val vector = root["output"]
            ?.jsonObjectOrNull()
            ?.get("embeddings")
            ?.jsonArrayOrNull()
            ?.firstOrNull()
            ?.jsonObjectOrNull()
            ?.get("embedding")
            ?.jsonArrayOrNull()
            ?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }
            .orEmpty()
        return vector.takeIf { it.isNotEmpty() && it.all(Double::isFinite) }
            ?: throw EmbeddingGatewayException(formatEmbeddingError(response.status.value, raw))
    }
}

internal fun formatEmbeddingError(status: Int, raw: String): String {
    val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
    val error = root?.get("error")?.jsonObjectOrNull()
    val code = root?.stringValue("code") ?: error?.stringValue("code")
    val message = root?.stringValue("message") ?: error?.stringValue("message")
    val requestId = root?.stringValue("request_id")
        ?: root?.stringValue("requestId")
        ?: error?.stringValue("request_id")
    return buildString {
        append("Embedding 请求失败 HTTP ").append(status)
        if (!code.isNullOrBlank()) append(" code=").append(code)
        if (!message.isNullOrBlank()) append(" message=").append(message)
        if (!requestId.isNullOrBlank()) append(" request_id=").append(requestId)
        if (code.isNullOrBlank() && message.isNullOrBlank()) append("：").append(raw.take(240))
    }
}

internal class EmbeddingGatewayException(message: String) : IllegalStateException(message)

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray
private fun JsonObject.stringValue(name: String): String? = (this[name] as? JsonPrimitive)?.content
private val JsonPrimitive.doubleOrNull: Double?
    get() = content.toDoubleOrNull()
