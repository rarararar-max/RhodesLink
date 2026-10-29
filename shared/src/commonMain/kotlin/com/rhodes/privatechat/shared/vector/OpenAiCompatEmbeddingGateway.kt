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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.Serializable

class OpenAiCompatEmbeddingGateway(
    private val endpoint: String,
    private val apiKey: String,
    private val modelName: String = "text-embedding-3-small",
    private val client: HttpClient = createHttpClient(),
) : EmbeddingGateway {
    override suspend fun embed(text: String): List<Double> {
        val response = client.post(endpoint) {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(EmbeddingRequest(input = text, model = modelName))
        }
        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw EmbeddingGatewayException(formatEmbeddingError(response.status.value, raw))
        }
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }
            .getOrElse { throw EmbeddingGatewayException("Embedding 返回不是有效 JSON：${raw.take(240)}") }
        val vector = root["data"]?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("embedding")
            ?.jsonArray
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.toDoubleOrNull() }
            .orEmpty()
        return vector.takeIf { it.isNotEmpty() && it.all(Double::isFinite) }
            ?: throw EmbeddingGatewayException(formatEmbeddingError(response.status.value, raw))
    }
}

@Serializable
private data class EmbeddingRequest(val input: String, val model: String)

@Serializable
private data class OpenAiEmbeddingData(val embedding: List<Double> = emptyList())

@Serializable
private data class OpenAiEmbeddingResponse(val data: List<OpenAiEmbeddingData> = emptyList())

private val json = Json { ignoreUnknownKeys = true; isLenient = true }
