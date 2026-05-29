package com.example.rhodesterminal.viewmodel

import com.example.rhodesterminal.shared.model.AnchorType
import com.example.rhodesterminal.shared.model.MahjongSave
import com.example.rhodesterminal.shared.model.MemoryAnchor
import com.example.rhodesterminal.shared.model.Moment
import com.example.rhodesterminal.data.repository.ChatRepository
import com.example.rhodesterminal.viewmodel.shared.Prefs
import com.example.rhodesterminal.viewmodel.shared.SharedUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MahjongViewModel(
    private val repository: ChatRepository,
    private val prefs: Prefs,
    private val sharedUtils: SharedUtils,
    private val scope: CoroutineScope,
    private val operatorsProvider: () -> List<com.example.rhodesterminal.shared.model.Operator>
) {
    suspend fun refreshDailyLmb() {
        val dp = prefs.dispatch
        val today = sharedUtils.beijingSdf("yyyyMMdd").format(java.util.Date())
        val lastRefresh = dp.getString("lmb_refresh_date", "") ?: ""
        if (lastRefresh == today) return
        dp.edit().putString("lmb_refresh_date", today).apply()
        for (op in operatorsProvider()) {
            if (op.lmb < 2000) {
                repository.updateOperator(op.copy(lmb = 2000))
            }
        }
    }

    fun saveMahjongGame(json: String, ruleType: String) {
        scope.launch { db.mahjongSaveDao().save(MahjongSave(saveJson = json, ruleType = ruleType)) }
    }

    fun loadMahjongSave(callback: (MahjongSave?) -> Unit) {
        scope.launch { callback(db.mahjongSaveDao().getSave()) }
    }

    fun deleteMahjongSave() {
        scope.launch { db.mahjongSaveDao().deleteSave() }
    }

    fun createMahjongAnchor(content: String) {
        scope.launch {
            for (op in operatorsProvider().shuffled().take(4)) {
                repository.saveAnchor(MemoryAnchor(
                    sessionId = "anchor_${System.currentTimeMillis()}_${op.id}",
                    operatorId = op.id, type = AnchorType.EVENT,
                    content = content, isPrivate = false
                ))
            }
        }
    }

    fun postMahjongMoment(content: String) {
        scope.launch {
            val op = operatorsProvider().randomOrNull() ?: return@launch
            repository.insertMoment(Moment(operatorId = op.id, operatorName = op.name, content = content, createdAt = System.currentTimeMillis()))
        }
    }
}
