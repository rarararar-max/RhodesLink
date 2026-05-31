package com.example.rhodesterminal.viewmodel

import com.example.rhodesterminal.data.ExportHelper
import com.example.rhodesterminal.data.ExportPayload
import com.example.rhodesterminal.data.MessageExport
import com.example.rhodesterminal.data.OperatorExport
import com.example.rhodesterminal.data.RelationshipExport
import com.example.rhodesterminal.data.SessionExport
import com.example.rhodesterminal.shared.data.SenderCount
import com.example.rhodesterminal.shared.model.Memory
import com.example.rhodesterminal.shared.data.ChatRepository
import com.example.rhodesterminal.shared.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DataViewModel(
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope
) {
    data class DataStats(
        val chatSessions: Int, val groups: Int, val diaries: Int, val anchors: Int,
        val messages: Int, val operators: Int, val moments: Int = 0, val dispatches: Int = 0
    )

    suspend fun getDataStats(operatorsCount: Int, momentsCount: Int): DataStats = DataStats(
        chatSessions = repository.getSessionCount(),
        groups = repository.getGroupCount(),
        diaries = repository.getDiaryCount(),
        anchors = repository.getAnchorCount(),
        messages = repository.getMessageCount(),
        operators = operatorsCount,
        moments = momentsCount,
        dispatches = repository.getHistoryDispatches().size
    )

    fun cleanupAllExpired() {
        scope.launch {
            val now = System.currentTimeMillis()
            val msgDays = settings.cleanDaysMessages
            repository.enforceMemoryRetain("", 0)
            val anchorDays = settings.cleanDaysAnchors
            repository.deleteOldAnchors(now - anchorDays * 86400000L)
            val diaryDays = settings.cleanDaysDiaries
            repository.deleteOldDiaries(now - diaryDays * 86400000L)
            val momentDays = settings.cleanDaysMoments
            repository.deleteOldMoments(now - momentDays * 86400000L)
            val dispatchDays = settings.cleanDaysDispatches
            repository.deleteOldDispatches(now - dispatchDays * 86400000L)
        }
    }

    suspend fun getMessageRanking(): List<SenderCount> = repository.getMessageCountPerSender()

    suspend fun getAllImpressions(): List<Memory> = repository.getAllLongTermImpressions()

    suspend fun deleteAllImpressions() = repository.deleteAllImpressions()

    suspend fun exportAllOperators(context: android.content.Context, operators: List<com.example.rhodesterminal.shared.model.Operator>): java.io.File {
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
