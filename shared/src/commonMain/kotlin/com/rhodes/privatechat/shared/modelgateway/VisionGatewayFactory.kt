package com.rhodes.privatechat.shared.modelgateway

import com.rhodes.privatechat.shared.settings.SettingsRepository

fun createVisionGateway(settings: SettingsRepository): VisionGateway {
    val config = settings.effectiveVisionConfiguration()
    return createVisionGateway(config.provider, config.endpoint, config.apiKey, config.model)
}

fun createVisionGateway(provider: String, endpoint: String, apiKey: String, modelName: String): VisionGateway {
    return if (endpoint.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()) {
        when (provider) {
            "ali" -> if (endpoint.contains("/compatible-mode/")) {
                OpenAiCompatVisionGateway(normalizeOpenAiChatEndpoint(endpoint), apiKey, modelName, providerId = "ali")
            } else AliyunQwenVlGateway(endpoint, apiKey, modelName)
            "anthropic" -> AnthropicVisionGateway(endpoint, apiKey, modelName)
            "xiaomi" -> OpenAiCompatVisionGateway(normalizeOpenAiChatEndpoint(endpoint), apiKey, modelName, useApiKeyHeader = true, providerId = "xiaomi")
            "deepseek", "doubao", "openai", "siliconflow" -> OpenAiCompatVisionGateway(normalizeOpenAiChatEndpoint(endpoint), apiKey, modelName, providerId = provider)
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
