package com.rhodes.privatechat.shared.vector

import com.rhodes.privatechat.shared.network.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
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
        }.body<OpenAiEmbeddingResponse>()
        return response.data.firstOrNull()?.embedding
            ?.takeIf { it.isNotEmpty() && it.all(Double::isFinite) }
            ?: throw IllegalStateException("Embedding API 未返回有效向量")
    }
}

@Serializable
private data class EmbeddingRequest(val input: String, val model: String)

@Serializable
private data class OpenAiEmbeddingData(val embedding: List<Double> = emptyList())

@Serializable
private data class OpenAiEmbeddingResponse(val data: List<OpenAiEmbeddingData> = emptyList())
