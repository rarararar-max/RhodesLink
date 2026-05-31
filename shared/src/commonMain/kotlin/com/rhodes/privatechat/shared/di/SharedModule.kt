package com.rhodes.privatechat.shared.di

import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.network.AIService
import com.rhodes.privatechat.shared.settings.SettingsRepository
import org.koin.dsl.module

fun sharedModule(databaseWrapper: DatabaseWrapper) = module {
    single { databaseWrapper }
    single { ChatRepository(get()) }
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
    single { SettingsRepository(com.rhodes.privatechat.shared.settings.createPlatformSettings()) }
    single { AIService() }
}
