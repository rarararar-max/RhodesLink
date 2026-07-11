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
import kotlinx.serialization.json.Json

class AliyunTextEmbeddingGateway(
    private val endpoint: String,
    private val apiKey: String,
    private val modelName: String = "text-embedding-v4",
    private val client: HttpClient = createHttpClient(),
) : EmbeddingGateway {
    override suspend fun embed(text: String): List<Double> {
        val response = client.post(endpoint) {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(EmbeddingRequestBody(model = modelName, input = EmbeddingInput(texts = listOf(text))))
        }.body<EmbeddingResponseBody>()
        return response.output?.embeddings?.firstOrNull()?.embedding.orEmpty()
    }
}

@Serializable
private data class EmbeddingRequestBody(val model: String, val input: EmbeddingInput)

@Serializable
private data class EmbeddingInput(val texts: List<String>)

@Serializable
private data class EmbeddingResponseBody(val output: EmbeddingOutput? = null)

@Serializable
private data class EmbeddingOutput(val embeddings: List<EmbeddingData> = emptyList())

@Serializable
private data class EmbeddingData(val embedding: List<Double> = emptyList())

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
