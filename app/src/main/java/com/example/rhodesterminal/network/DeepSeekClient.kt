package com.example.rhodesterminal.network

import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class ChatCompletionRequest(
    val model: String = "deepseek-chat",
    val messages: List<Message>,
    val stream: Boolean = true,
    val temperature: Double = 0.95
)

data class Message(
    val role: String,
    val content: String
)

data class StreamChunk(
    val choices: List<StreamChoice>? = null
)

data class StreamChoice(
    val delta: Delta? = null
)

data class Delta(
    val content: String? = null
)

data class NonStreamResponse(
    val choices: List<NonStreamChoice>? = null
)

data class NonStreamChoice(
    val message: Message? = null
)

data class StreamError(
    val error: StreamErrorDetail? = null
)

data class StreamErrorDetail(
    val message: String? = null
)

data class OfflineModeResponse(
    val emotion: String = "",
    val state: String = "",
    val activity: String = "",
    val location: String = "",
    val narration: String = "",
    val dialogue: String = "",
    val affection_mod: Int = 0,
    val segments: List<Segment>? = null
)

data class Segment(
    val type: String = "dialogue",
    val content: String = "",
    val speaker: String = ""
)

data class AnalysisResult(
    val intent_analysis: String = "",
    val user_emotion: String = "",
    val user_need: String = "",
    val suggested_emotion: String = "",
    val suggested_location: String = "",
    val suggested_state: String = "",
    val reply_guidance: String = "",
    val affection_mod: Int = 0
)

data class OnlineModeResponse(
    val emotion: String = "",
    val dialogue: String = "",
    val affection_mod: Int = 0
)

data class SummaryResponse(
    val summary: String = "",
    val keywords: List<String> = emptyList(),
    val anchors: List<AnchorItem> = emptyList()
)

data class AnchorItem(
    val type: String = "event",
    val content: String = "",
    val isPrivate: Boolean = false
)

object DeepSeekClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val gson = Gson()

    fun streamChat(apiKey: String, messages: List<Message>): Flow<String> = flow {
        val requestBody = ChatCompletionRequest(messages = messages, stream = true)
        val jsonBody = gson.toJson(requestBody)

        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw Exception("API error ${response.code}: $errorBody")
        }

        val bodyStream = response.body?.byteStream() ?: throw Exception("Empty response body")
        BufferedReader(InputStreamReader(bodyStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue
                if (currentLine.startsWith("data: ")) {
                    val data = currentLine.removePrefix("data: ")
                    if (data == "[DONE]") break
                    try {
                        val chunk = gson.fromJson(data, StreamChunk::class.java)
                        val content = chunk.choices?.firstOrNull()?.delta?.content
                        if (!content.isNullOrBlank()) {
                            emit(content)
                        }
                    } catch (_: Exception) {
                        try {
                            val error = gson.fromJson(data, StreamError::class.java)
                            if (error.error?.message != null) {
                                throw Exception(error.error.message)
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    fun chat(apiKey: String, messages: List<Message>): Flow<String> = flow {
        val requestBody = ChatCompletionRequest(messages = messages, stream = false)
        val jsonBody = gson.toJson(requestBody)

        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw Exception("API error ${response.code}: $errorBody")
        }

        val responseBody = response.body?.string() ?: ""
        val completion = gson.fromJson(responseBody, NonStreamResponse::class.java)
        val content = completion.choices?.firstOrNull()?.message?.content ?: ""
        emit(content)
    }.flowOn(Dispatchers.IO)

    fun parseOfflineResponse(raw: String): OfflineModeResponse {
        val clean = cleanJson(raw)
        return try { gson.fromJson(clean, OfflineModeResponse::class.java) } catch (_: Exception) {
            // 兜底：正则提取 segments
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
        // 先用 Gson 解析再序列化，正确处理嵌套花括号
        try {
            val obj = gson.fromJson(s, JsonElement::class.java)
            return gson.toJson(obj)
        } catch (_: Exception) {}
        s = s.replace(", }", "}").replace(",}", "}")
        if (!s.startsWith("{")) { val start = s.indexOf('{'); if (start >= 0) s = s.substring(start) }
        if (!s.endsWith("}")) { val end = s.lastIndexOf('}'); if (end >= 0) s = s.substring(0, end + 1) }
        return s
    }

    fun parseSummaryResponse(raw: String): SummaryResponse {
        val clean = raw.trim().removePrefix("```json").removeSuffix("```").trim()
        return try {
            gson.fromJson(clean, SummaryResponse::class.java)
        } catch (_: Exception) {
            SummaryResponse(summary = raw)
        }
    }
}
