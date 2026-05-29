package com.example.rhodesterminal.shared.network

import com.example.rhodesterminal.shared.model.AiMessage
import com.example.rhodesterminal.shared.model.ChatCompletionRequest
import com.example.rhodesterminal.shared.model.StreamChunk
import com.example.rhodesterminal.shared.model.StreamError
import com.example.rhodesterminal.shared.model.NonStreamResponse
import com.example.rhodesterminal.shared.model.OfflineModeResponse
import com.example.rhodesterminal.shared.model.Segment
import com.example.rhodesterminal.shared.model.SummaryResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json

class AIService(private val client: HttpClient = createHttpClient()) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // --- Response parsing utilities ---

    fun parseOfflineResponse(raw: String): OfflineModeResponse {
        val clean = cleanJson(raw)
        return try {
            json.decodeFromString<OfflineModeResponse>(clean)
        } catch (_: Exception) {
            val segments = mutableListOf<Segment>()
            val segRegex = Regex("""\"type\"\s*:\s*\"(narration|dialogue)\"[^}]*\"content\"\s*:\s*\"((?:[^"\\]|\\.)*)\"""")
            for (m in segRegex.findAll(raw)) {
                segments.add(Segment(type = m.groupValues[1], content = m.groupValues[2]))
            }
            if (segments.isNotEmpty()) {
                OfflineModeResponse(segments = segments)
            } else {
                val dialogue = Regex("\"dialogue\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(raw)?.groupValues?.getOrNull(1) ?: raw
                OfflineModeResponse(dialogue = dialogue)
            }
        }
    }

    fun parseScriptResponse(raw: String): OfflineModeResponse {
        val emotion = Regex("【情绪：([^】]*)】").find(raw)?.groupValues?.getOrNull(1)?.trim() ?: ""
        val segments = mutableListOf<Segment>()
        val regex = Regex("【(旁白|台词)：([^】]*)】")
        for (m in regex.findAll(raw)) {
            val type = if (m.groupValues[1] == "旁白") "narration" else "dialogue"
            val content = m.groupValues[2].trim()
            if (content.isNotBlank()) {
                segments.add(Segment(type = type, content = content))
            }
        }
        return OfflineModeResponse(emotion = emotion, segments = segments.ifEmpty { null })
    }

    fun cleanJson(raw: String): String {
        var s = raw.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            .replace("，", ",").replace("：", ":")
        try {
            val obj = json.parseToJsonElement(s)
            return obj.toString()
        } catch (_: Exception) {}
        s = s.replace(", }", "}").replace(",}", "}")
        if (!s.startsWith("{")) { val start = s.indexOf('{'); if (start >= 0) s = s.substring(start) }
        if (!s.endsWith("}")) { val end = s.lastIndexOf('}'); if (end >= 0) s = s.substring(0, end + 1) }
        return s
    }

    fun parseSummaryResponse(raw: String): SummaryResponse {
        val clean = raw.trim().removePrefix("```json").removeSuffix("```").trim()
        return try {
            json.decodeFromString<SummaryResponse>(clean)
        } catch (_: Exception) {
            SummaryResponse(summary = raw)
        }
    }

    // --- Streaming chat ---

    fun streamChat(
        apiKey: String,
        messages: List<AiMessage>,
        providerId: String = "deepseek",
        modelName: String = "deepseek-chat",
        customUrl: String = "",
        temperature: Double = 0.95
    ): Flow<String> = flow {
        val config = providers[providerId] ?: providers["deepseek"]!!
        val url = if (config.id == "custom") customUrl else config.baseUrl
        val model = modelName

        val requestBody = ChatCompletionRequest(
            model = model,
            messages = messages,
            stream = true,
            temperature = temperature
        )

        var finalUrl = url
        if (config.id == "google") {
            finalUrl = "$url/$model:streamGenerateContent?alt=sse"
        }

        val response: HttpResponse = client.post(finalUrl) {
            contentType(ContentType.Application.Json)
            header("Accept", "text/event-stream")
            if (config.id == "google") {
                header("X-Goog-Api-Key", apiKey)
            } else {
                header("Authorization", "Bearer $apiKey")
            }
            setBody(requestBody)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsChannel().readUTF8Line() ?: "Unknown error"
            throw Exception("API error ${response.status.value}: $errorBody")
        }

        val channel = response.bodyAsChannel()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: continue
            if (line.startsWith("data: ")) {
                val data = line.removePrefix("data: ")
                if (data == "[DONE]") break
                try {
                    val chunk = json.decodeFromString<StreamChunk>(data)
                    val content = chunk.choices?.firstOrNull()?.delta?.content
                    if (!content.isNullOrBlank()) {
                        emit(content)
                    }
                } catch (_: Exception) {
                    try {
                        val error = json.decodeFromString<StreamError>(data)
                        if (error.error?.message != null) {
                            throw Exception(error.error.message)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }.flowOn(Dispatchers.Default)

    fun chat(
        apiKey: String,
        messages: List<AiMessage>,
        providerId: String = "deepseek",
        modelName: String = "deepseek-chat",
        customUrl: String = "",
        temperature: Double = 0.95
    ): Flow<String> = flow {
        val config = providers[providerId] ?: providers["deepseek"]!!
        val url = if (config.id == "custom") customUrl else config.baseUrl
        val model = modelName

        val requestBody = ChatCompletionRequest(
            model = model,
            messages = messages,
            stream = false,
            temperature = temperature
        )

        val response: HttpResponse = client.post(url) {
            contentType(ContentType.Application.Json)
            if (config.id == "google") {
                header("X-Goog-Api-Key", apiKey)
            } else {
                header("Authorization", "Bearer $apiKey")
            }
            setBody(requestBody)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsChannel().readUTF8Line() ?: "Unknown error"
            throw Exception("API error ${response.status.value}: $errorBody")
        }

        val responseBody = response.bodyAsText()
        val completion = json.decodeFromString<NonStreamResponse>(responseBody)
        val content = completion.choices?.firstOrNull()?.message?.content ?: ""
        emit(content)
    }.flowOn(Dispatchers.Default)
}
