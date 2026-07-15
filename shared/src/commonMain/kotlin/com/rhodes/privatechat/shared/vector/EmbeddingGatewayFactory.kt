package com.rhodes.privatechat.shared.vector

import com.rhodes.privatechat.shared.settings.SettingsRepository

fun createEmbeddingGateway(settings: SettingsRepository): EmbeddingGateway {
    if (settings.vectorProviderMode == "local") return LocalHashEmbeddingGateway()
    val key = settings.vectorApiKey.ifBlank { settings.apiKey }
    if (settings.vectorProviderMode != "third_party" || settings.vectorBaseUrl.isBlank() || key.isBlank()) return DisabledEmbeddingGateway()
    return OpenAiCompatEmbeddingGateway(settings.vectorBaseUrl, key, settings.vectorModelName)
}

suspend fun testEmbeddingGateway(endpoint: String, apiKey: String, modelName: String): List<Double> {
    val gateway = OpenAiCompatEmbeddingGateway(endpoint, apiKey, modelName)
    return gateway.embed("罗德岛干员正在执行任务")
}
