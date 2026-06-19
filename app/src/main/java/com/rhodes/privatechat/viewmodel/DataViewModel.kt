package com.rhodes.privatechat.viewmodel

import com.rhodes.privatechat.data.ExportHelper
import com.rhodes.privatechat.data.ExportPayload
import com.rhodes.privatechat.data.MessageExport
import com.rhodes.privatechat.data.OperatorExport
import com.rhodes.privatechat.data.RelationshipExport
import com.rhodes.privatechat.data.SessionExport
import com.rhodes.privatechat.shared.data.SenderCount
import com.rhodes.privatechat.shared.model.Memory
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DataViewModel(
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope
) {
    private var lastCleanupLogAt = 0L

    data class DataStats(
        val chatSessions: Int, val groups: Int, val diaries: Int, val anchors: Int,
        val messages: Int, val operators: Int, val moments: Int = 0, val dispatches: Int = 0,
        val worldEvents: Int = 0
    )

    suspend fun getDataStats(operatorsCount: Int, momentsCount: Int): DataStats = DataStats(
        chatSessions = repository.getSessionCount(),
        groups = repository.getGroupCount(),
        diaries = repository.getDiaryCount(),
        anchors = repository.getAnchorCount(),
        messages = repository.getMessageCount(),
        operators = operatorsCount,
        moments = momentsCount,
        dispatches = repository.getHistoryDispatches().size,
        worldEvents = repository.getWorldEventCount()
    )

    fun cleanupAllExpired() {
        scope.launch {
            val now = System.currentTimeMillis()
            val msgDays = settings.cleanDaysMessages
            if (msgDays > 0) repository.deleteOldMessages(now - msgDays * 86400000L)
            val diaryDays = settings.cleanDaysDiaries
            if (diaryDays > 0) repository.deleteOldDiaries(now - diaryDays * 86400000L)
            val momentDays = settings.cleanDaysMoments
            if (momentDays > 0) repository.deleteOldMoments(now - momentDays * 86400000L)
            val dispatchDays = settings.cleanDaysDispatches
            if (dispatchDays > 0) repository.deleteOldDispatches(now - dispatchDays * 86400000L)
            val worldDays = settings.cleanDaysWorldEvents
            if (worldDays > 0) repository.deleteExpiredWorldEvents(now - worldDays * 86400000L)
            val anchorDays = settings.cleanDaysAnchors
            if (anchorDays > 0) repository.deleteOldAnchors(now - anchorDays * 86400000L)
            if (now - lastCleanupLogAt > 5_000L) {
                DebugLogger.log("Data/Cleanup", "清理过期数据: messages=${msgDays}天, anchors=${anchorDays}天, diaries=${diaryDays}天, moments=${momentDays}天, dispatches=${dispatchDays}天")
                lastCleanupLogAt = now
            }
        }
    }

    suspend fun getMessageRanking(): List<SenderCount> = repository.getMessageCountPerSender()

    suspend fun getDailyRanking(): List<SenderCount> {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis
        val yesterdayStart = todayStart - 86400000L
        return repository.getMessageCountPerSenderSince(yesterdayStart)
    }

    suspend fun getAllImpressions(): List<Memory> = repository.getAllLongTermImpressions()

    suspend fun deleteAllImpressions() = repository.deleteAllImpressions()

    fun deleteAllWorldEvents() {
        scope.launch { repository.deleteAllWorldEvents() }
    }

    suspend fun exportAllOperators(context: android.content.Context, operators: List<com.rhodes.privatechat.shared.model.Operator>): java.io.File {
        val ops = operators.map { OperatorExport.fromEntity(it) }
        val allRels = mutableListOf<RelationshipExport>()
        for (op in ops) {
            val rels = repository.getRelationships(op.id)
            allRels.addAll(rels.map { RelationshipExport.fromEntity(it) })
        }
        val payload = ExportPayload(type = "operators", operators = ops, relationships = allRels)
        return ExportHelper.exportToFile(context, payload, "rhodes_operators_${System.currentTimeMillis()}.json")
    }

    fun importOperators(payload: ExportPayload, mode: String, targetOpId: String = "") {
        scope.launch(Dispatchers.IO) {
            val ops = payload.operators ?: return@launch
            if (mode == "new") {
                for (op in ops) {
                    val existing = repository.getOperator(op.id)
                    if (existing == null) repository.insertOperator(op.toEntity())
                }
            } else if (mode == "overwrite" && targetOpId.isNotBlank()) {
                val op = ops.find { it.id == targetOpId } ?: return@launch
                repository.insertOperator(op.toEntity())
            }
            val rels = payload.relationships ?: return@launch
            for (rel in rels) {
                repository.insertRelationship(rel.toEntity())
            }
        }
    }

    suspend fun exportChatHistory(context: android.content.Context, sessionId: String): java.io.File {
        val session = repository.getSession(sessionId)
        val msgs = repository.getMessagesSync(sessionId)
        val payload = ExportPayload(
            type = "chat",
            session = session?.let { SessionExport.fromEntity(it) },
            messages = msgs.map { MessageExport.fromEntity(it) }
        )
        return ExportHelper.exportToFile(context, payload, "rhodes_chat_${sessionId}_${System.currentTimeMillis()}.json")
    }
}
