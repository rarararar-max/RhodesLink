package com.rhodes.privatechat.di

import com.rhodes.privatechat.viewmodel.MainViewModel
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.viewmodel.shared.OperatorStateUpdater
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.shared.network.AIService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // === Infrastructure ===
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single { AppStateHolder(get(), get(), get<SettingsRepository>()) }

    single {
        val appState = get<AppStateHolder>()
        SharedUtils(get(), get<SettingsRepository>(), get<AIService>()) { appState.operators.value }
    }

    single {
        val appState = get<AppStateHolder>()
        OperatorStateUpdater(get(), get<SettingsRepository>(), get()) { appState.operators.value }
    }

    // === MainViewModel ===
    single {
        MainViewModel(get(), get(), get<SettingsRepository>(), get(), get(), get())
    }
}
