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
            "xiaomi" -> OpenAiCompatVisionGateway(endpoint, apiKey, modelName, useApiKeyHeader = true)
            "doubao", "openai" -> OpenAiCompatVisionGateway(endpoint, apiKey, modelName)
            else -> OpenAiCompatVisionGateway(endpoint, apiKey, modelName)
        }
    } else {
        DisabledVisionGateway()
    }
}
