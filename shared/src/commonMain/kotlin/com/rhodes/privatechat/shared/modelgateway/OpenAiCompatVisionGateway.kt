package com.rhodes.privatechat.shared.modelgateway

import com.rhodes.privatechat.shared.network.createHttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

class OpenAiCompatVisionGateway(
    private val endpoint: String,
    private val apiKey: String,
    private val modelName: String,
) : VisionGateway {
    private val client = createHttpClient()

    override suspend fun analyzeImage(request: VisionAnalyzeRequest): VisionAnalyzeResponse {
        val response = client.post(endpoint) {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(VisionChatRequest(modelName, listOf(VisionChatMessage(content = listOf(
                VisionPart(type = "image_url", imageUrl = VisionImageUrl(request.imageUrlOrBase64)),
                VisionPart(type = "text", text = request.prompt)
            )))))
        }.body<VisionChatResponse>()
        return VisionAnalyzeResponse(response.choices.firstOrNull()?.message?.content.orEmpty())
    }
}

@Serializable private data class VisionChatRequest(val model: String, val messages: List<VisionChatMessage>)
@Serializable private data class VisionChatMessage(val role: String = "user", val content: List<VisionPart>)
@Serializable private data class VisionPart(val type: String, val text: String? = null, @kotlinx.serialization.SerialName("image_url") val imageUrl: VisionImageUrl? = null)
@Serializable private data class VisionImageUrl(val url: String)
@Serializable private data class VisionChatResponse(val choices: List<VisionChoice> = emptyList())
@Serializable private data class VisionChoice(val message: VisionMessage? = null)
@Serializable private data class VisionMessage(val content: String = "")
