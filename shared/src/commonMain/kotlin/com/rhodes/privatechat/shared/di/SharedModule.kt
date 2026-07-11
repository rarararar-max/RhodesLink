package com.rhodes.privatechat.shared.di

import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.network.AIService
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.shared.modelgateway.AliyunQwenVlGateway
import com.rhodes.privatechat.shared.modelgateway.DisabledVisionGateway
import com.rhodes.privatechat.shared.modelgateway.VisionGateway
import com.rhodes.privatechat.shared.voice.AliyunDashScopeAsrGateway
import com.rhodes.privatechat.shared.voice.AsrGateway
import com.rhodes.privatechat.shared.voice.DisabledAsrGateway
import com.rhodes.privatechat.shared.voice.DisabledTtsGateway
import com.rhodes.privatechat.shared.voice.MinimaxTtsGateway
import com.rhodes.privatechat.shared.voice.TtsGateway
import com.rhodes.privatechat.shared.vector.AliyunTextEmbeddingGateway
import com.rhodes.privatechat.shared.vector.DisabledEmbeddingGateway
import com.rhodes.privatechat.shared.vector.EmbeddingGateway
import com.rhodes.privatechat.shared.vector.LocalHashEmbeddingGateway
import com.rhodes.privatechat.shared.vector.LocalVectorStoreGateway
import com.rhodes.privatechat.shared.vector.MemoryVectorService
import com.rhodes.privatechat.shared.vector.OpenAiCompatEmbeddingGateway
import com.rhodes.privatechat.shared.vector.VectorStoreGateway
import org.koin.dsl.module

fun sharedModule(databaseWrapper: DatabaseWrapper) = module {
    single { databaseWrapper }
    single { SettingsRepository(com.rhodes.privatechat.shared.settings.createPlatformSettings()) }
    single { ChatRepository(get(), get()) }
    single { get<ChatRepository>().operators }
    single { get<ChatRepository>().sessions }
    single { get<ChatRepository>().messages }
    single { get<ChatRepository>().memories }
    single { get<ChatRepository>().anchors }
    single { get<ChatRepository>().relationships }
    single { get<ChatRepository>().moments }
    single { get<ChatRepository>().diaries }
    single { get<ChatRepository>().dispatches }
    single { get<ChatRepository>().mahjong }
    single { get<ChatRepository>().cleanup }

    single<EmbeddingGateway> {
        val settings: SettingsRepository = get()
        when (settings.vectorProviderMode) {
            "local" -> LocalHashEmbeddingGateway()
            "third_party" -> {
                val url = settings.vectorBaseUrl
                val key = settings.vectorApiKey.ifBlank { settings.apiKey }
                val model = settings.vectorModelName.ifBlank { "text-embedding-v4" }
                if (url.isNotBlank() && key.isNotBlank()) {
                    when (settings.vectorProvider) {
                        "ali" -> AliyunTextEmbeddingGateway(endpoint = url, apiKey = key, modelName = model)
                        else -> OpenAiCompatEmbeddingGateway(endpoint = url, apiKey = key, modelName = model)
                    }
                } else DisabledEmbeddingGateway()
            }
            else -> DisabledEmbeddingGateway()
        }
    }

    single<VectorStoreGateway> {
        LocalVectorStoreGateway(get())
    }

    single { MemoryVectorService(get(), get()) }
    single<VisionGateway> {
        val settings: SettingsRepository = get()
        val url = settings.visionBaseUrl
        val key = settings.visionApiKey.ifBlank { settings.apiKey }
        val model = settings.visionModelName.ifBlank { "qwen3-vl-plus" }
        if (url.isNotBlank() && key.isNotBlank()) AliyunQwenVlGateway(endpoint = url, apiKey = key, modelName = model) else DisabledVisionGateway()
    }
    single<AsrGateway> {
        val settings: SettingsRepository = get()
        val key = settings.asrApiKey.ifBlank { settings.apiKey }
        if (settings.asrBaseUrl.isNotBlank() && key.isNotBlank()) AliyunDashScopeAsrGateway(apiKey = key, modelName = settings.asrModelName, endpoint = settings.asrBaseUrl) else DisabledAsrGateway()
    }
    single<TtsGateway> {
        val settings: SettingsRepository = get()
        val key = settings.ttsApiKey.ifBlank { settings.apiKey }
        if (settings.ttsBaseUrl.isNotBlank() && key.isNotBlank()) MinimaxTtsGateway(endpoint = settings.ttsBaseUrl, apiKey = key, modelName = settings.ttsModelName) else DisabledTtsGateway()
    }
    single { AIService() }
}
