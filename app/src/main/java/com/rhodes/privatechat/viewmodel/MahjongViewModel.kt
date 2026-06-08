package com.rhodes.privatechat.viewmodel

import com.rhodes.privatechat.shared.model.AnchorType
import com.rhodes.privatechat.shared.model.MahjongSave
import com.rhodes.privatechat.shared.model.MemoryAnchor
import com.rhodes.privatechat.shared.model.Moment
import com.rhodes.privatechat.shared.model.MomentComment
import com.rhodes.privatechat.shared.model.MomentLike
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MahjongViewModel(
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    private val sharedUtils: SharedUtils,
    private val scope: CoroutineScope,
    private val operatorsProvider: () -> List<com.rhodes.privatechat.shared.model.Operator>
) {
    suspend fun refreshDailyLmb() {
        val today = sharedUtils.beijingSdf("yyyyMMdd").format(java.util.Date())
        val lastRefresh = settings.lmbRefreshDate
        if (lastRefresh == today) return
        settings.lmbRefreshDate = today
        for (op in operatorsProvider()) {
            if (op.lmb < 2000) {
                repository.updateOperator(op.copy(lmb = 2000))
            }
        }
    }

    fun saveMahjongGame(json: String, ruleType: String) {
        scope.launch { repository.saveMahjong(MahjongSave(saveJson = json, ruleType = ruleType)) }
    }

    fun loadMahjongSave(callback: (MahjongSave?) -> Unit) {
        scope.launch { callback(repository.getMahjongSave()) }
    }

    fun deleteMahjongSave() {
        scope.launch { repository.deleteMahjongSave() }
    }

    fun createMahjongAnchor(content: String) {
        scope.launch {
            for (op in operatorsProvider().shuffled().take(4)) {
                repository.saveAnchor(MemoryAnchor(
                    sessionId = "anchor_${System.currentTimeMillis()}_${op.id}",
                    operatorId = op.id, type = AnchorType.EVENT,
                    content = content, isPrivate = false,
                    createdAt = System.currentTimeMillis(),
                    expiresAt = System.currentTimeMillis() + settings.cleanDays * 86_400_000L
                ))
            }
        }
    }

    fun postMahjongMoment(content: String) {
        scope.launch {
            val op = operatorsProvider().randomOrNull() ?: return@launch
            val momentId = repository.insertMoment(Moment(operatorId = op.id, operatorName = op.name, content = content, createdAt = System.currentTimeMillis()))
            // 异步加点赞和评论
            val allOps = operatorsProvider().filter { it.name != "系统" && it.id != op.id }
            val likers = allOps.shuffled().take((2..5).random())
            likers.forEach { l -> repository.insertLike(MomentLike(momentId = momentId, operatorId = l.id, operatorName = l.name, createdAt = System.currentTimeMillis())) }
            repository.updateLikeCount(momentId, likers.size)
            val commenters = allOps.shuffled().take((1..2).random())
            commenters.forEach { c ->
                repository.insertComment(MomentComment(momentId = momentId, operatorId = c.id, operatorName = c.name, content = "好局！", createdAt = System.currentTimeMillis()))
            }
            repository.updateCommentCount(momentId, commenters.size)
        }
    }
}
