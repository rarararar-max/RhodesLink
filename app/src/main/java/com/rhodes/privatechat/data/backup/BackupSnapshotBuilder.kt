package com.rhodes.privatechat.data.backup

import com.rhodes.privatechat.data.ExportPayload
import com.rhodes.privatechat.data.MessageExport
import com.rhodes.privatechat.data.OperatorExport
import com.rhodes.privatechat.data.RelationshipExport
import com.rhodes.privatechat.data.SessionExport
import com.rhodes.privatechat.data.KnowledgeBaseExport
import com.rhodes.privatechat.data.KnowledgeBaseChunkExport
import com.rhodes.privatechat.data.OperatorKnowledgeBaseAssignmentExport
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.shared.settings.SettingsRepository
import android.os.SystemClock

/** Builds authoritative user content only; vectors, leases, queues and credentials stay excluded. */
class BackupSnapshotBuilder(
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
) {
    suspend fun build(operators: List<Operator>? = null, onRead: (String, Long) -> Unit = { _, _ -> }): BackupPayload =
        repository.withBackupReader { safeRepository ->
            BackupSnapshotBuilder(safeRepository, settings).buildSnapshot(operators, onRead)
        }

    private suspend fun buildSnapshot(operators: List<Operator>?, onRead: (String, Long) -> Unit): BackupPayload {
        suspend fun <T> read(name: String, block: suspend () -> T): T {
            val started = SystemClock.elapsedRealtime()
            onRead(name, -1L)
            val result = block()
            onRead(name, SystemClock.elapsedRealtime() - started)
            return result
        }
        val snapshotOperators = operators ?: read("operators") { repository.getAllOperatorsSync() }
        val sessions = read("sessions") { repository.getAllSessionsSync() }
        val relationships = read("relationships") { repository.getAllRelationshipsForBackup() }
        val messages = read("messages") { repository.getAllMessagesForBackup() }
        val knowledgeBases = read("knowledge_bases") { repository.knowledgeBases.getAll() }
        val knowledgeBaseChunks = read("knowledge_chunks") { repository.knowledgeBases.getAllChunksForBackup() }
        val knowledgeBaseAssignments = read("knowledge_assignments") { repository.knowledgeBases.getAllAssignmentsForBackup() }
        val operatorIds = snapshotOperators.mapTo(mutableSetOf()) { it.id }
        val sessionIds = sessions.mapTo(mutableSetOf()) { it.id }
        val memories = read("memories") { repository.getAllMemoriesForBackup() }.filter { memory ->
            (memory.type == com.rhodes.privatechat.shared.model.MemoryType.DAILY && memory.operatorId == "daily" && memory.sessionId.startsWith("daily_")) ||
                (memory.operatorId in operatorIds && memory.sessionId in sessionIds)
        }
        val anchors = read("anchors") { repository.getAllAnchorsForBackup() }.filter { it.operatorId in operatorIds && it.sessionId in sessionIds }
        return BackupPayload(
            content = ExportPayload(
                version = 5,
                type = "full_backup",
                operators = snapshotOperators.map(OperatorExport::fromEntity),
                relationships = relationships.map(RelationshipExport::fromEntity),
                sessions = sessions.map(SessionExport::fromEntity),
                messages = messages.map(MessageExport::fromEntity),
                memories = memories,
                anchors = anchors,
                moments = read("moments") { repository.getAllMomentsSync() },
                momentLikes = read("moment_likes") { repository.getAllLikesForBackup() },
                momentComments = read("moment_comments") { repository.getAllCommentsForBackup() },
                diaries = read("diaries") { repository.getAllDiariesForBackup() },
                memoryItems = read("memory_items") { repository.getAllMemoryItems() }.map { it.copy(vectorId = "") },
                knowledgeBases = knowledgeBases.map { KnowledgeBaseExport(it.id, it.name, it.rawContent, it.sourceFileName, it.sourceFormat, it.sourceType, it.chunkingMode, it.createdAt, it.updatedAt) },
                knowledgeBaseChunks = knowledgeBaseChunks.map { KnowledgeBaseChunkExport(it.id, it.knowledgeBaseId, it.ordinal, it.sourceHeading, it.content, it.userKeywords, it.enabled, it.createdAt, it.updatedAt) },
                operatorKnowledgeBaseAssignments = knowledgeBaseAssignments.map { OperatorKnowledgeBaseAssignmentExport(it.operatorId, it.knowledgeBaseId, it.enabled, it.sortOrder) },
                settings = read("settings") { PortableSettings.snapshot(repository, settings) },
            ),
            displayEvents = read("display_events") { repository.messages.getAllDisplayEvents() },
            chatArchives = read("chat_archives") { repository.archives.getAllArchives() },
            chatHistorySegments = read("chat_history_segments") { repository.archives.getAllHistorySegments() },
            giftRecords = read("gifts") { repository.getAllGifts() },
            dispatchRecords = read("dispatches") { repository.dispatches.getAllDispatches() },
            mahjongSave = read("mahjong") { repository.mahjong.getMahjongSave() },
            sharedExperiences = read("shared_experiences") { repository.getAllSharedExperiences() },
            sharedExperienceParticipants = read("shared_participants") { repository.getAllSharedExperienceParticipants() },
        )
    }

}
