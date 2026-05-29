package com.example.rhodesterminal.shared.di

import com.example.rhodesterminal.shared.data.ChatRepository
import com.example.rhodesterminal.shared.db.DatabaseWrapper
import com.example.rhodesterminal.shared.network.AIService
import com.example.rhodesterminal.shared.settings.SettingsRepository
import org.koin.dsl.module

fun sharedModule(databaseWrapper: DatabaseWrapper) = module {
    single { databaseWrapper }
    single { ChatRepository(get()) }
    single { SettingsRepository(com.example.rhodesterminal.shared.settings.createPlatformSettings()) }
    single { AIService() }
}
