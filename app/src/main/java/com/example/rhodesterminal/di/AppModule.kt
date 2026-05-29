package com.example.rhodesterminal.di

import com.example.rhodesterminal.viewmodel.MainViewModel
import com.example.rhodesterminal.viewmodel.shared.AppStateHolder
import com.example.rhodesterminal.viewmodel.shared.OperatorStateUpdater
import com.example.rhodesterminal.viewmodel.shared.Prefs
import com.example.rhodesterminal.viewmodel.shared.SharedUtils
import com.example.rhodesterminal.shared.data.ChatRepository
import com.example.rhodesterminal.shared.settings.SettingsRepository
import com.example.rhodesterminal.shared.network.AIService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // === Prefs (temporary, will be replaced by SettingsRepository) ===
    single { Prefs(androidApplication()) }

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
    viewModel {
        MainViewModel(get(), get(), get(), get(), get(), get())
    }
}
