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

/** Builds authoritative user content only; vectors, leases, queues and credentials stay excluded. */
class BackupSnapshotBuilder(
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
) {
    suspend fun build(operators: List<Operator>? = null): BackupPayload {
        val snapshotOperators = operators ?: repository.getAllOperatorsSync()
        val sessions = repository.getAllSessionsSync()
        val relationships = snapshotOperators.flatMap { repository.getRelationships(it.id) }
        val messages = sessions.flatMap { repository.getMessagesSync(it.id) }
        val knowledgeBases = repository.knowledgeBases.getAll()
        val knowledgeBaseChunks = repository.knowledgeBases.getAllChunksForBackup()
        val knowledgeBaseAssignments = repository.knowledgeBases.getAllAssignmentsForBackup()
        return BackupPayload(
            content = ExportPayload(
                version = 5,
                type = "full_backup",
                operators = snapshotOperators.map(OperatorExport::fromEntity),
                relationships = relationships.map(RelationshipExport::fromEntity),
                sessions = sessions.map(SessionExport::fromEntity),
                messages = messages.map(MessageExport::fromEntity),
                memories = repository.getAllMemoriesForBackup(),
                anchors = repository.getAllAnchorsForBackup(),
                moments = repository.getAllMomentsSync(),
                momentLikes = repository.getAllLikesForBackup(),
                momentComments = repository.getAllCommentsForBackup(),
                diaries = repository.getAllDiariesForBackup(),
                memoryItems = repository.getAllMemoryItems().map { it.copy(vectorId = "") },
                knowledgeBases = knowledgeBases.map { KnowledgeBaseExport(it.id, it.name, it.rawContent, it.sourceFileName, it.sourceFormat, it.chunkingMode, it.createdAt, it.updatedAt) },
                knowledgeBaseChunks = knowledgeBaseChunks.map { KnowledgeBaseChunkExport(it.id, it.knowledgeBaseId, it.ordinal, it.sourceHeading, it.content, it.userKeywords, it.enabled, it.createdAt, it.updatedAt) },
                operatorKnowledgeBaseAssignments = knowledgeBaseAssignments.map { OperatorKnowledgeBaseAssignmentExport(it.operatorId, it.knowledgeBaseId, it.enabled, it.sortOrder) },
                settings = PortableSettings.snapshot(repository, settings),
            ),
            displayEvents = repository.messages.getAllDisplayEvents(),
            chatArchives = repository.archives.getAllArchives(),
            chatHistorySegments = repository.archives.getAllHistorySegments(),
            giftRecords = snapshotOperators.flatMap { repository.getGiftsByOperator(it.id) },
            dispatchRecords = repository.dispatches.getAllDispatches(),
            mahjongSave = repository.mahjong.getMahjongSave(),
            sharedExperiences = repository.getAllSharedExperiences(),
            sharedExperienceParticipants = repository.getAllSharedExperienceParticipants(),
        )
    }

}
