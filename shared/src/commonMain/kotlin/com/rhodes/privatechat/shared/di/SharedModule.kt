package com.rhodes.privatechat.shared.di

import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.knowledge.KnowledgeBaseImportService
import com.rhodes.privatechat.shared.knowledge.KnowledgeBaseIndexService
import com.rhodes.privatechat.shared.knowledge.KnowledgeBaseContextBuilder
import com.rhodes.privatechat.shared.network.AIService
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.shared.modelgateway.AliyunQwenVlGateway
import com.rhodes.privatechat.shared.modelgateway.DisabledVisionGateway
import com.rhodes.privatechat.shared.modelgateway.VisionGateway
import com.rhodes.privatechat.shared.modelgateway.createVisionGateway
import com.rhodes.privatechat.shared.voice.AliyunDashScopeAsrGateway
import com.rhodes.privatechat.shared.voice.AsrGateway
import com.rhodes.privatechat.shared.voice.DisabledAsrGateway
import com.rhodes.privatechat.shared.vector.LocalVectorStoreGateway
import com.rhodes.privatechat.shared.vector.MemoryVectorService
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
    single { get<ChatRepository>().knowledgeBases }
    single { get<ChatRepository>().replyTurns }
    single { KnowledgeBaseIndexService(get(), get(), get()) }
    single { KnowledgeBaseContextBuilder(get(), get()) }
    single { KnowledgeBaseImportService(get(), get(), get()) }

    single<VectorStoreGateway> {
        LocalVectorStoreGateway(get())
    }

    single { MemoryVectorService(get(), get()) }
    single<VisionGateway> {
        val settings: SettingsRepository = get()
        createVisionGateway(settings)
    }
    single<AsrGateway> {
        val settings: SettingsRepository = get()
        val key = settings.asrApiKey.ifBlank { settings.apiKey }
        if (settings.asrBaseUrl.isNotBlank() && key.isNotBlank()) AliyunDashScopeAsrGateway(apiKey = key, modelName = settings.asrModelName, endpoint = settings.asrBaseUrl) else DisabledAsrGateway()
    }
    single { AIService() }
}
