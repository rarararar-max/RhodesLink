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
    private val scope: CoroutineScope,
    private val onMemoryImportCompleted: suspend () -> Unit = {}
) {
    private var lastCleanupLogAt = 0L

    data class DataStats(
        val chatSessions: Int, val groups: Int, val diaries: Int, val anchors: Int,
        val messages: Int, val operators: Int, val moments: Int = 0, val dispatches: Int = 0,
        val memoryItems: Int = 0,
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
        memoryItems = repository.getAllMemoryItems().size,
    )

    suspend fun cleanupAllExpired() {
        val now = System.currentTimeMillis()
        val msgDays = settings.cleanDaysMessages
        if (msgDays > 0) repository.deleteOldMessages(now - msgDays * 86400000L)
        val diaryDays = settings.cleanDaysDiaries
        if (diaryDays > 0) repository.deleteOldDiaries(now - diaryDays * 86400000L)
        val momentDays = settings.cleanDaysMoments
        // Moments and comments share one retention control in the current product UI.
        if (momentDays > 0) repository.deleteExpiredSocialContent(
            momentCutoff = now - momentDays * 86400000L,
            commentCutoff = now - momentDays * 86400000L,
            userName = settings.userName,
        )
        val dispatchDays = settings.cleanDaysDispatches
        if (dispatchDays > 0) repository.deleteOldDispatches(now - dispatchDays * 86400000L)
        val anchorDays = settings.cleanDaysAnchors
        if (anchorDays > 0) repository.deleteOldAnchors(now - anchorDays * 86400000L)
        if (now - lastCleanupLogAt > 5_000L) {
            DebugLogger.log("Data/Cleanup", "清理过期数据: messages=${msgDays}天, anchors=${anchorDays}天, diaries=${diaryDays}天, moments=${momentDays}天, dispatches=${dispatchDays}天")
            lastCleanupLogAt = now
        }
    }

    suspend fun updateMemoryRetention(days: Int) {
        settings.cleanDaysMemoryItems = days
        val now = System.currentTimeMillis()
        val expiresAt = if (days < 0) Long.MAX_VALUE else now + days * 86_400_000L
        repository.updateActiveMemoryExpiry(expiresAt, now)
        if (days < 0) repository.restoreExpiredMemoryItems(expiresAt, now)
        DebugLogger.log("Data/MemoryRetention", "向量记忆保留期已更新: ${if (days < 0) "永不" else "${days}天"}")
    }

    /** Old builds assigned short expiries before permanent retention became the default. */
    suspend fun restorePermanentMemoryRetentionIfNeeded() {
        if (settings.cleanDaysMemoryItems >= 0) return
        val now = System.currentTimeMillis()
        repository.updateActiveMemoryExpiry(Long.MAX_VALUE, now)
        repository.restoreExpiredMemoryItems(Long.MAX_VALUE, now)
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

    suspend fun deleteImpression(operatorId: String) = repository.deleteLongTermByOperator(operatorId)

    suspend fun updateImpression(impression: Memory) = repository.replaceLongTermImpression(impression)

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

    suspend fun exportFullBackup(context: android.content.Context, operators: List<com.rhodes.privatechat.shared.model.Operator>): java.io.File {
        val sessions = repository.getAllSessionsSync()
        val allMessages = sessions.flatMap { repository.getMessagesSync(it.id) }
        val allRels = operators.flatMap { op -> repository.getRelationships(op.id) }
        val payload = ExportPayload(
            version = 3,
            type = "full_backup",
            operators = operators.map { OperatorExport.fromEntity(it) },
            relationships = allRels.map { RelationshipExport.fromEntity(it) },
            sessions = sessions.map { SessionExport.fromEntity(it) },
            messages = allMessages.map { MessageExport.fromEntity(it) },
            memories = repository.getAllMemoriesForBackup(),
            anchors = repository.getAllAnchorsForBackup(),
            moments = repository.getAllMomentsSync(),
            momentLikes = repository.getAllLikesForBackup(),
            momentComments = repository.getAllCommentsForBackup(),
            diaries = repository.getAllDiariesForBackup(),
            memoryItems = repository.getAllMemoryItems(),
            settings = exportSettingsSnapshot()
        )
        return ExportHelper.exportToFile(context, payload, "rhodes_full_backup_${System.currentTimeMillis()}.json")
    }

    fun importFullBackup(payload: ExportPayload) {
        if (payload.type != "full_backup") return
        scope.launch(Dispatchers.IO) {
            payload.operators.orEmpty().forEach { repository.insertOperator(it.toEntity()) }
            payload.relationships.orEmpty().forEach { repository.insertRelationship(it.toEntity()) }
            payload.sessions.orEmpty().forEach { repository.insertSession(it.toEntity()) }
            payload.messages.orEmpty().forEach { repository.restoreMessage(it.toEntity()) }
            payload.memories.orEmpty().forEach { repository.saveMemory(it) }
            payload.anchors.orEmpty().forEach { repository.saveAnchor(it) }
            repository.restoreSocialBackup(
                payload.moments.orEmpty(),
                payload.momentLikes.orEmpty(),
                payload.momentComments.orEmpty()
            )
            payload.diaries.orEmpty().forEach { repository.insertDiary(it) }
            // Imported row IDs cannot preserve memory_links. Keep only source-level memories active;
            // derived L2/L3 conclusions are rebuilt later so deleted source material cannot survive.
            payload.memoryItems.orEmpty().forEach { item ->
                repository.insertMemoryItem(item.copy(
                    id = 0,
                    vectorId = "",
                    status = if (item.memoryLevel == com.rhodes.privatechat.shared.model.MemoryLevel.L1) item.status else "archived"
                ))
            }
            importSettingsSnapshot(payload.settings.orEmpty())
            onMemoryImportCompleted()
        }
    }

    private fun exportSettingsSnapshot(): Map<String, String> = mapOf(
        "user_name" to settings.userName,
        "user_gender" to settings.userGender,
        "user_signature" to settings.userSignature,
        "hidden_ids" to settings.hiddenIds.joinToString(","),
        "daily_summary_date" to settings.dailySummaryDate
    )

    private fun importSettingsSnapshot(values: Map<String, String>) {
        values["user_name"]?.let { settings.userName = it }
        values["user_gender"]?.let { settings.userGender = it }
        values["user_signature"]?.let { settings.userSignature = it }
        values["hidden_ids"]?.let { settings.hiddenIds = it.split(",").filter { id -> id.isNotBlank() }.toSet() }
        values["daily_summary_date"]?.let { settings.dailySummaryDate = it }
    }
}
