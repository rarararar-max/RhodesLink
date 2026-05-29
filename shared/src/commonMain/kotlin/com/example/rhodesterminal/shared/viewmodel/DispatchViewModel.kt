package com.example.rhodesterminal.shared.viewmodel

import com.example.rhodesterminal.shared.model.*
import com.example.rhodesterminal.shared.settings.SettingsRepository

class DispatchViewModel(private val locator: ServiceLocator) : CommonViewModel() {

    private val settings = locator.settingsRepository

    suspend fun getActiveDispatches(): List<DispatchRecord> {
        return emptyList()
    }

    fun startDispatch(
        id: String, taskType: String, durationHours: Int, budget: Int,
        operatorIds: String, logChain: String = "", totalSegments: Int = 0,
        segmentInterval: Long = 0, items: String = ""
    ) {
        // TODO: 实现开始派遣
    }

    fun finishDispatch(dispatchId: String, netProfit: Int) {
        // TODO: 实现完成派遣
    }

    fun cancelDispatch(dispatchId: String) {
        // TODO: 实现取消派遣
    }
}
