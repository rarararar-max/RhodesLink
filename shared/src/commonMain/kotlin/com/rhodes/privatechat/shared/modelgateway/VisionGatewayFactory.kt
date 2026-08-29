package com.rhodes.privatechat.shared.modelgateway

import com.rhodes.privatechat.shared.settings.SettingsRepository

fun createVisionGateway(settings: SettingsRepository): VisionGateway {
    val apiKey = settings.visionApiKey.ifBlank { settings.apiKey }
    return createVisionGateway(settings.visionProvider, settings.visionBaseUrl, apiKey, settings.visionModelName)
}

fun createVisionGateway(provider: String, endpoint: String, apiKey: String, modelName: String): VisionGateway {
    return if (endpoint.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()) {
        when (provider) {
            "ali" -> AliyunQwenVlGateway(endpoint, apiKey, modelName)
            "anthropic" -> AnthropicVisionGateway(endpoint, apiKey, modelName)
            "xiaomi" -> OpenAiCompatVisionGateway(normalizeOpenAiChatEndpoint(endpoint), apiKey, modelName, useApiKeyHeader = true)
            "deepseek", "doubao", "openai", "siliconflow" -> OpenAiCompatVisionGateway(normalizeOpenAiChatEndpoint(endpoint), apiKey, modelName)
            else -> OpenAiCompatVisionGateway(normalizeOpenAiChatEndpoint(endpoint), apiKey, modelName)
        }
    } else {
        DisabledVisionGateway()
    }
}

fun normalizeOpenAiChatEndpoint(raw: String): String {
    val endpoint = raw.trim().trimEnd('/')
    return if (endpoint.endsWith("/chat/completions")) endpoint else "$endpoint/chat/completions"
}
