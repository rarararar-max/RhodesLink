package com.rhodes.privatechat.shared.modelgateway

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.rhodes.privatechat.shared.network.createHttpClient

class AliyunQwenVlGateway(
    private val endpoint: String,
    private val apiKey: String,
    private val modelName: String = "qwen3-vl-plus",
    private val client: HttpClient = createHttpClient(),
) : VisionGateway {
    override suspend fun analyzeImage(request: VisionAnalyzeRequest): VisionAnalyzeResponse {
        val raw = client.post(endpoint) {
            bearerAuth(apiKey)
            header("X-DashScope-SSE", "enable")
            contentType(ContentType.Application.Json)
            setBody(NativeVisionRequest(
                model = modelName,
                input = NativeVisionInput(messages = listOf(
                    NativeVisionMessage(role = "user", content = listOf(
                        NativeVisionContent(image = request.imageUrlOrBase64),
                        NativeVisionContent(text = request.prompt),
                    ))
                )),
                parameters = NativeVisionParameters(enableThinking = false, incrementalOutput = true, thinkingBudget = 0),
            ))
        }.body<String>()

        val parsed = parseResponse(raw)
        return parsed
    }

    private fun parseResponse(raw: String): VisionAnalyzeResponse {
        runCatching { json.decodeFromString(NativeVisionResponse.serializer(), raw) }
            .getOrNull()
            ?.output?.choices?.firstOrNull()?.message?.content
            .orEmpty()
            .mapNotNull { it.text }
            .joinToString("")
            .takeIf { it.isNotBlank() }
            ?.let { return VisionAnalyzeResponse(text = it) }

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

        return VisionAnalyzeResponse(text = text.ifBlank { raw })
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
