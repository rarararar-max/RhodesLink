package com.example.rhodesterminal.shared.viewmodel

import com.example.rhodesterminal.shared.model.*
import com.example.rhodesterminal.shared.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class DiaryViewModel(private val locator: ServiceLocator) : CommonViewModel() {

    private val settings = locator.settingsRepository

    fun getDiariesByOperator(operatorId: String): Flow<List<Diary>> {
        return emptyFlow()
    }

    suspend fun getDiary(operatorId: String, date: String): Diary? {
        return null
    }

    suspend fun getDiaryDates(operatorId: String): List<String> {
        return emptyList()
    }

    fun saveDiary(diary: Diary) {
        // TODO: 实现保存日记
    }
}
