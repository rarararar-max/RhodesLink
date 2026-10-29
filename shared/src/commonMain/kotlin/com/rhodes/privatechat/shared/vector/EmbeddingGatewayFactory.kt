package com.rhodes.privatechat.shared.vector

import com.rhodes.privatechat.shared.settings.SettingsRepository

fun createEmbeddingGateway(settings: SettingsRepository): EmbeddingGateway {
    if (settings.vectorProviderMode == "local") return LocalHashEmbeddingGateway()
    val key = settings.vectorApiKey.ifBlank { settings.apiKey }
    if (settings.vectorProviderMode != "third_party" || settings.vectorBaseUrl.isBlank() || key.isBlank()) return DisabledEmbeddingGateway()
    return if (settings.vectorProtocol == "aliyun_native") {
        AliyunNativeEmbeddingGateway(settings.vectorBaseUrl, key, settings.vectorModelName)
    } else {
        OpenAiCompatEmbeddingGateway(settings.vectorBaseUrl, key, settings.vectorModelName)
    }
}

suspend fun testEmbeddingGateway(endpoint: String, apiKey: String, modelName: String, protocol: String = "openai_compat"): List<Double> {
    val gateway = if (protocol == "aliyun_native") {
        AliyunNativeEmbeddingGateway(endpoint, apiKey, modelName)
    } else {
        OpenAiCompatEmbeddingGateway(endpoint, apiKey, modelName)
    }
    return gateway.embed("罗德岛干员正在执行任务")
}
