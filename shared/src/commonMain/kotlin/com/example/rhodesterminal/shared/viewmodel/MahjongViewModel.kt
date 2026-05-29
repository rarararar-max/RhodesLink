package com.example.rhodesterminal.shared.viewmodel

import com.example.rhodesterminal.shared.model.*
import com.example.rhodesterminal.shared.settings.SettingsRepository

class MahjongViewModel(private val locator: ServiceLocator) : CommonViewModel() {

    private val settings = locator.settingsRepository

    suspend fun loadMahjongSave(): MahjongSave? {
        return null
    }

    fun saveMahjongGame(json: String, ruleType: String) {
        // TODO: 实现保存麻将存档
    }

    fun deleteMahjongSave() {
        // TODO: 实现删除麻将存档
    }
}
