package com.example.rhodesterminal.di

import com.example.rhodesterminal.data.repository.ChatRepository
import com.example.rhodesterminal.viewmodel.MainViewModel
import com.example.rhodesterminal.viewmodel.shared.AppStateHolder
import com.example.rhodesterminal.viewmodel.shared.OperatorStateUpdater
import com.example.rhodesterminal.viewmodel.shared.Prefs
import com.example.rhodesterminal.viewmodel.shared.SharedUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    // === ChatRepository (wraps shared ChatRepository) ===
    single { ChatRepository(get<com.example.rhodesterminal.shared.data.ChatRepository>()) }

    // === Prefs (temporary, will be replaced by SettingsRepository) ===
    single { Prefs(androidApplication()) }

    // === Infrastructure ===
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single { AppStateHolder(get(), get<com.example.rhodesterminal.shared.data.ChatRepository>(), get()) }

    single {
        val appState = get<AppStateHolder>()
        SharedUtils(get<com.example.rhodesterminal.shared.data.ChatRepository>(), get(), get()) { appState.operators.value }
    }

    single {
        val appState = get<AppStateHolder>()
        OperatorStateUpdater(get<com.example.rhodesterminal.shared.data.ChatRepository>(), get(), get()) { appState.operators.value }
    }

    // === MainViewModel ===
    viewModel {
        MainViewModel(get(), get(), get(), get(), get(), get())
    }
}
