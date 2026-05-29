package com.example.rhodesterminal.viewmodel

import com.example.rhodesterminal.data.ExportHelper
import com.example.rhodesterminal.data.ExportPayload
import com.example.rhodesterminal.data.MessageExport
import com.example.rhodesterminal.data.OperatorExport
import com.example.rhodesterminal.data.RelationshipExport
import com.example.rhodesterminal.data.SessionExport
import com.example.rhodesterminal.shared.data.SenderCount
import com.example.rhodesterminal.shared.model.Memory
import com.example.rhodesterminal.data.repository.ChatRepository
import com.example.rhodesterminal.viewmodel.shared.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DataViewModel(
    private val repository: ChatRepository,
    private val prefs: Prefs,
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
            val chatPrefs = prefs.chat
            val now = System.currentTimeMillis()
            val msgDays = chatPrefs.getInt("clean_days_messages", 30)
            repository.enforceMemoryRetain("", 0)
            val anchorDays = chatPrefs.getInt("clean_days_anchors", 3)
            repository.deleteOldAnchors(now - anchorDays * 86400000L)
            val diaryDays = chatPrefs.getInt("clean_days_diaries", 30)
            repository.deleteOldDiaries(now - diaryDays * 86400000L)
            val momentDays = chatPrefs.getInt("clean_days_moments", 30)
            repository.deleteOldMoments(now - momentDays * 86400000L)
            val dispatchDays = chatPrefs.getInt("clean_days_dispatches", 30)
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
