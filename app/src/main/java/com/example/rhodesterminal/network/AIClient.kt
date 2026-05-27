package com.example.rhodesterminal.network

import com.google.gson.Gson
import com.google.gson.JsonParser
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

data class ProviderConfig(
    val id: String, val name: String, val baseUrl: String,
    val models: List<String>, val isOpenAICompat: Boolean = true
)

val providers = mapOf(
    "deepseek" to ProviderConfig("deepseek", "DeepSeek", "https://api.deepseek.com/chat/completions", listOf("deepseek-chat", "deepseek-reasoner", "deepseek-v4-flash", "deepseek-v4-pro")),
    "minimax" to ProviderConfig("minimax", "MiniMax", "https://api.minimax.chat/v1/chat/completions", listOf("abab6.5-chat", "abab5.5-chat")),
    "byte" to ProviderConfig("byte", "豆包(字节)", "https://ark.cn-beijing.volces.com/api/v3/chat/completions", listOf("doubao-lite-32k", "doubao-seed-1-6-flash", "doubao-seed-1-6", "doubao-seed-2-0-pro")),
    "google" to ProviderConfig("google", "Google", "https://generativelanguage.googleapis.com/v1beta/models", listOf("gemini-1.5-pro", "gemini-1.5-flash"), isOpenAICompat = false),
    "ali" to ProviderConfig("ali", "阿里千问", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", listOf("qwen-flash", "qwen-plus", "qwen3-max")),
    "zhipu" to ProviderConfig("zhipu", "智谱AI", "https://open.bigmodel.cn/api/paas/v4/chat/completions", listOf("glm-4", "glm-3-turbo", "glm-4v")),
    "siliconflow" to ProviderConfig("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1/chat/completions", listOf("Qwen/Qwen2.5-7B-Instruct", "Qwen/Qwen2.5-32B-Instruct", "deepseek-ai/DeepSeek-R1")),
    "openai_compat" to ProviderConfig("openai_compat", "OpenAI兼容", "https://api.openai.com/v1/chat/completions", listOf("gpt-4o", "gpt-4o-mini")),
    "custom" to ProviderConfig("custom", "自填", "", listOf("自填"))
)

object AIClient {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
    private val gson = Gson()

    fun streamChat(apiKey: String, messages: List<Message>, providerId: String = "deepseek", modelName: String = "deepseek-chat", customUrl: String = "", temperature: Double = 0.95): Flow<String> = flow {
        val config = providers[providerId] ?: providers["deepseek"]!!
        val url = if (config.id == "custom") customUrl else config.baseUrl
        val model = modelName

        val requestBody = ChatCompletionRequest(model = model, messages = messages, stream = true, temperature = temperature)
        val jsonBody = gson.toJson(requestBody)

        var finalUrl = url
        if (config.id == "google") finalUrl = "$url/$model:streamGenerateContent?alt=sse"

        val requestBuilder = Request.Builder().url(finalUrl)
            .addHeader("Content-Type", "application/json").addHeader("Accept", "text/event-stream")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))

        if (config.id == "google") {
            requestBuilder.addHeader("X-Goog-Api-Key", apiKey)
        } else {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            val err = response.body?.string() ?: "Unknown error"
            throw Exception("API error ${response.code}: $err")
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
                        if (!content.isNullOrBlank()) emit(content)
                    } catch (_: Exception) {
                        try {
                            val tree = JsonParser.parseString(data).asJsonObject
                            val text = tree?.getAsJsonArray("candidates")?.get(0)?.asJsonObject?.getAsJsonObject("content")?.getAsJsonArray("parts")?.get(0)?.asJsonObject?.get("text")?.asString
                            if (!text.isNullOrBlank()) emit(text)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
