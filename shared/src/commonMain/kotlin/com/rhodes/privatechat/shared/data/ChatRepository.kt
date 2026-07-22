package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.model.*
import com.rhodes.privatechat.shared.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BfsNode(
    val operatorId: String,
    val operatorName: String,
    val depth: Int,
    val parentId: String,
    val relType: RelationshipType? = null,
    val isReverse: Boolean = false
)

data class SenderCount(
    val senderName: String,
    val cnt: Long
)

class ChatRepository(private val wrapper: DatabaseWrapper, settings: SettingsRepository? = null) {
    val operators = OperatorRepository(wrapper)
    val sessions = SessionRepository(wrapper)
    val messages = MessageRepository(wrapper)
    val memories = MemoryRepository(wrapper)
    val anchors = AnchorRepository(wrapper, settings)
    val relationships = RelationshipRepository(wrapper)
    val moments = MomentRepository(wrapper)
    val diaries = DiaryRepository(wrapper)
    val dispatches = DispatchRepository(wrapper)
    val mahjong = MahjongRepository(wrapper)
    val cleanup = CleanupRepository(wrapper)
    val memoryV2 = MemoryV2Repository(wrapper)
    val sharedExperiences = SharedExperienceRepository(wrapper)

    // --- Backward-compatible forwarding methods ---
    val allOperators: Flow<List<Operator>> get() = operators.allOperators
    suspend fun getAllOperatorsSync() = operators.getAllOperatorsSync()
    suspend fun getOperator(id: String) = operators.getOperator(id)
    suspend fun insertPresetOperators() = operators.insertPresetOperators()
    suspend fun deleteOperator(id: String) = operators.deleteOperator(id)
    suspend fun updateOperator(op: Operator) = operators.updateOperator(op)
    suspend fun updateIntimacy(id: String, intimacy: Int) = operators.updateIntimacy(id, intimacy)
    suspend fun insertOperator(op: Operator) = operators.insertOperator(op)

    val allSessions: Flow<List<ChatSession>> get() = sessions.allSessions
    suspend fun getAllSessionsSync() = sessions.getAllSessionsSync()
    suspend fun getOrCreateSession(operatorId: String, operatorName: String, avatarUri: String = "") = sessions.getOrCreateSession(operatorId, operatorName, avatarUri)
    suspend fun getSession(id: String) = sessions.getSession(id)
    suspend fun insertSession(session: ChatSession) = sessions.insertSession(session)
    suspend fun updatePinned(sessionId: String, pinned: Boolean) = sessions.updatePinned(sessionId, pinned)
    suspend fun deleteSession(id: String) = sessions.deleteSession(id)
    suspend fun updateSessionMode(sessionId: String, mode: String) = sessions.updateSessionMode(sessionId, mode)
    suspend fun markAllRead() = sessions.markAllRead()
    suspend fun markSessionRead(sessionId: String) = sessions.markSessionRead(sessionId)
    suspend fun incrementUnread(sessionId: String, delta: Int = 1) = sessions.incrementUnread(sessionId, delta)
    suspend fun getSessionCount() = sessions.getSessionCount()
    suspend fun getGroupCount() = sessions.getGroupCount()
    suspend fun updateLastMessage(sessionId: String, lastMessage: String, lastTime: Long) = sessions.updateLastMessage(sessionId, lastMessage, lastTime)
    suspend fun getSessionByOperator(operatorId: String) = sessions.getSessionByOperator(operatorId)
    suspend fun getLastUserMessageTime(sessionId: String) = sessions.getLastUserMessageTime(sessionId)
    suspend fun initPresetGroups() = sessions.initPresetGroups()
    suspend fun getPrivateChatSummary(operatorId: String) = sessions.getPrivateChatSummary(operatorId)
    suspend fun getPrivateChatContext(operatorId: String) = sessions.getPrivateChatContext(operatorId)

    fun getMessages(sessionId: String) = messages.getMessages(sessionId)
    fun getRecentMessages(sessionId: String, limit: Long = 200) = messages.getRecentMessages(sessionId, limit)
    suspend fun getMessagesSync(sessionId: String) = messages.getMessagesSync(sessionId)
    suspend fun getRecentMessagesSync(sessionId: String, limit: Long = 200) = messages.getRecentMessagesSync(sessionId, limit)
    suspend fun getMessagesBefore(sessionId: String, beforeTimestamp: Long, beforeId: Long, limit: Long = 100) = messages.getMessagesBefore(sessionId, beforeTimestamp, beforeId, limit)
    suspend fun updateMessageContent(id: Long, content: String) = messages.updateMessageContent(id, content)
    suspend fun updateMessageContentAndPreview(sessionId: String, id: Long, content: String, timestamp: Long) =
        messages.updateMessageContentAndPreview(sessionId, id, content, timestamp)
    suspend fun sendMessage(sessionId: String, message: ChatMessage) = messages.sendMessage(sessionId, message)
    suspend fun restoreMessage(message: ChatMessage) = messages.restoreMessage(message)
    suspend fun getNextMessageId() = messages.getNextMessageId()
    suspend fun deleteSessionMessages(sessionId: String) = messages.deleteSessionMessages(sessionId)
    suspend fun deleteMessage(id: Long) = messages.deleteMessage(id)
    suspend fun getMessageCount() = messages.getMessageCount()
    suspend fun deleteOldMessages(cutoff: Long) = messages.deleteOldMessages(cutoff)
    suspend fun getMessageCountPerSender() = messages.getMessageCountPerSender()
    suspend fun getMessageCountPerSenderSince(since: Long) = messages.getMessageCountPerSenderSince(since)
    suspend fun getMessagesInRange(start: Long, end: Long) = messages.getMessagesInRange(start, end)
    suspend fun searchMessagesInSession(sessionId: String, keyword: String, limit: Long = 200) = messages.searchMessagesInSession(sessionId, keyword, limit)
    suspend fun getMessagesBySessionInRange(sessionId: String, start: Long, end: Long) = messages.getMessagesBySessionInRange(sessionId, start, end)
    suspend fun getMessageDatesBySession(sessionId: String) = messages.getMessageDatesBySession(sessionId)

    suspend fun getShortTermMemory(sessionId: String) = memories.getShortTermMemory(sessionId)
    suspend fun clearSessionPreview(sessionId: String, timestamp: Long) = messages.clearSessionPreview(sessionId, timestamp)
    suspend fun getLongTermImpression(operatorId: String) = memories.getLongTermImpression(operatorId)
    suspend fun saveMemory(memory: Memory) = memories.saveMemory(memory)
    suspend fun replaceShortTermMemory(memory: Memory) = memories.replaceShortTermMemory(memory)
    suspend fun replaceLongTermImpression(memory: Memory) = memories.replaceLongTermImpression(memory)
    suspend fun getAllLongTermImpressions() = memories.getAllLongTermImpressions()
    suspend fun getAllMemoriesForBackup() = memories.getAllMemoriesForBackup()
    suspend fun getLatestDaily() = memories.getLatestDaily()
    suspend fun getLatestDailyBySession(sessionId: String) = memories.getLatestDailyBySession(sessionId)
    suspend fun getLatestPrivateDaily(operatorId: String) = memories.getLatestPrivateDaily(operatorId)
    suspend fun getDailyBySessionAndDate(sessionId: String, dateKey: String) = memories.getDailyBySessionAndDate(sessionId, dateKey)
    suspend fun replaceDailyBySessionAndDate(memory: Memory, dateKey: String) = memories.replaceDailyBySessionAndDate(memory, dateKey)
    suspend fun enforceMemoryRetain(sessionId: String, keepCount: Int) = memories.enforceMemoryRetain(sessionId, keepCount)
    suspend fun deleteAllImpressions() = memories.deleteAllImpressions()
    suspend fun deleteMemoriesBySession(sessionId: String) = memories.deleteMemoriesBySession(sessionId)
    suspend fun deleteMemoriesByOperator(operatorId: String) = memories.deleteMemoriesByOperator(operatorId)
    suspend fun deleteLongTermByOperator(operatorId: String) = memories.deleteLongTermByOperator(operatorId)
    suspend fun deleteMemoryV2BySession(sessionId: String) = memoryV2.deleteBySession(sessionId)
    suspend fun deleteMemoryV2ByOwnerAndSourceKind(ownerType: String, ownerId: String, sourceKind: MemorySourceKind) = memoryV2.deleteByOwnerAndSourceKind(ownerType, ownerId, sourceKind)
    suspend fun deleteMemoryItemsBySession(sessionId: String) = memoryV2.deleteMemoryItemsBySession(sessionId)
    suspend fun deleteMemoryV2BySource(sourceKind: MemorySourceKind, sourceRefId: String) = memoryV2.deleteBySource(sourceKind, sourceRefId)

    /** Raw public content and its unified-memory projections must expire together. */
    suspend fun deleteExpiredSocialContent(momentCutoff: Long?, commentCutoff: Long?, userName: String) = withContext(Dispatchers.Default) {
        val db = wrapper.database
        if (momentCutoff != null) {
            val momentIds = db.momentsQueries.getOldMomentIds(momentCutoff).executeAsList()
            momentIds.forEach { memoryV2.deleteBySource(MemorySourceKind.MOMENT, it.toString()) }
            db.momentsQueries.deleteOldMoments(momentCutoff)
        }
        if (commentCutoff != null) {
            val commentIds = db.momentCommentsQueries.getOldUserCommentIds(commentCutoff, userName).executeAsList()
            commentIds.forEach { memoryV2.deleteBySource(MemorySourceKind.MOMENT_COMMENT, it.toString()) }
            db.momentCommentsQueries.deleteOldUserComments(commentCutoff, userName)
        }
    }
    suspend fun saveSharedExperience(experience: SharedExperience, participants: List<String>) = sharedExperiences.saveIfAbsent(experience, participants)
    suspend fun deleteSharedExperiencesBySource(sourceKind: String, sourceRefId: String) = sharedExperiences.deleteBySource(sourceKind, sourceRefId)

    /** Removes a session and every derived record that can make its content reappear in recall. */
    suspend fun purgeSessionData(sessionId: String) = withContext(Dispatchers.Default) {
        val db = wrapper.database
        memoryV2.invalidateDerivedBySession(sessionId)
        db.transaction {
            db.chatMessagesQueries.deleteSessionMessages(sessionId)
            db.memoriesQueries.deleteMemoriesBySession(sessionId)
            db.memoryAnchorsQueries.deleteAnchorsBySession(sessionId)
            // Delete vectors while their owning memory rows still exist.  This catches both
            // L1 and promoted L2/L3 records, including copied group-member knowledge.
            db.vectorMemoriesQueries.deleteVectorsForMemorySession(sessionId)
            db.memoryItemsQueries.deleteMemoryItemsBySession(sessionId)
            db.memorySourceQueueQueries.deleteMemorySourcesBySession(sessionId)
            db.vectorMemoriesQueries.deleteVectorMemoriesBySourceId(sessionId)
            db.memoryLinksQueries.deleteOrphanedMemoryLinks()
        }
    }

    /** Removes an operator's private and derived state before deleting the operator row. */
    suspend fun purgeOperatorData(operatorId: String) = withContext(Dispatchers.Default) {
        val db = wrapper.database
        // Public copies live in the global vector partition, so remove them before their raw rows.
        val operatorMoments = moments.getMomentsByOperator(operatorId)
        val comments = buildList {
            db.momentCommentsQueries.getCommentsByOperator(operatorId) { id, momentId, opId, opName, content, parentCommentId, replyToName, createdAt, isRead ->
                MomentComment(id, momentId, opId, opName, content, parentCommentId, replyToName, createdAt, isRead != 0L)
            }.executeAsList().forEach { add(it) }
            operatorMoments.forEach { moment ->
                db.momentCommentsQueries.getComments(moment.id) { id, momentId, opId, opName, content, parentCommentId, replyToName, createdAt, isRead ->
                    MomentComment(id, momentId, opId, opName, content, parentCommentId, replyToName, createdAt, isRead != 0L)
                }.executeAsList().forEach { add(it) }
            }
        }.distinctBy { it.id }
        operatorMoments.forEach { memoryV2.deleteBySource(MemorySourceKind.MOMENT, it.id.toString()) }
        comments.forEach { memoryV2.deleteBySource(MemorySourceKind.MOMENT_COMMENT, it.id.toString()) }
        db.transaction {
            db.memoriesQueries.deleteMemoriesByOperator(operatorId)
            db.memoryAnchorsQueries.deleteAnchorsByOperator(operatorId)
            db.memoryItemsQueries.deleteMemoryItemsByOwner("operator", operatorId)
            db.memoryBatchesQueries.deleteMemoryBatchesByOwner("operator", operatorId)
            db.memorySourceQueueQueries.deleteMemorySourcesByOwner("operator", operatorId)
            db.vectorMemoriesQueries.deleteVectorMemoriesByOwner("operator", operatorId)
            db.relationshipsQueries.deleteByOperatorAnyDirection(operatorId, operatorId)
            db.memoryLinksQueries.deleteOrphanedMemoryLinks()
        }
        // Moments/comments are public source records and must not survive a deleted operator.
        moments.deleteMomentsByOperator(operatorId)
    }

    /** Erases one operator's entire private relationship while preserving public content. */
    suspend fun erasePrivateRelationship(operatorId: String, sessionId: String, systemMessageId: Long, mode: String, now: Long): List<String> = withContext(Dispatchers.Default) {
        val db = wrapper.database
        val vectorIds = memoryV2.getMemoryItemsByOwner("operator", operatorId)
            .filter { it.sourceKind == MemorySourceKind.PRIVATE_CHAT || it.sourceKind == MemorySourceKind.MANUAL_MEMORY && it.privacy == "private" }
            .map { it.vectorId }.filter { it.isNotBlank() }
        db.transaction {
            db.chatMessagesQueries.deleteSessionMessages(sessionId)
            db.memoriesQueries.deleteMemoriesBySession(sessionId)
            db.memoriesQueries.deleteLongTermByOperator(operatorId)
            db.memoryAnchorsQueries.deleteAnchorsBySession(sessionId)
            db.memoryLinksQueries.deleteMemoryLinksForOwnerPrivateSource("operator", operatorId, "operator", operatorId)
            db.memoryBatchesQueries.deletePrivateMemoryBatchesByOwner("operator", operatorId)
            db.memoryItemsQueries.deletePrivateRelationshipMemoryItems("operator", operatorId)
            db.memorySourceQueueQueries.deletePrivateRelationshipSources("operator", operatorId)
            db.vectorMemoriesQueries.deleteVectorMemoriesBySourceId(sessionId)
            db.chatMessagesQueries.insertMessage(systemMessageId, sessionId, "", "系统", "已清空与该角色的私聊关系记录和私密记忆，现在开始新的会话。", "system", mode, "", "", "", "", "", 0L, now, 0L)
        }
        vectorIds
    }

    suspend fun saveAnchor(anchor: MemoryAnchor): Boolean = anchors.saveAnchor(anchor)
    suspend fun saveAnchors(anchors: List<MemoryAnchor>) = this.anchors.saveAnchors(anchors)
    suspend fun getPublicAnchors(operatorId: String) = anchors.getPublicAnchors(operatorId)
    suspend fun getAnchors(operatorId: String) = anchors.getAnchors(operatorId)
    suspend fun getAnchorCount() = anchors.getAnchorCount()
    suspend fun getAllAnchorsForBackup() = anchors.getAllAnchorsForBackup()
    suspend fun deleteOldAnchors(cutoff: Long) = anchors.deleteOldAnchors(cutoff)
    suspend fun deleteAnchorsBySession(sessionId: String) = anchors.deleteAnchorsBySession(sessionId)
    suspend fun deleteAnchorsByOperator(operatorId: String) = anchors.deleteAnchorsByOperator(operatorId)
    suspend fun enforceAnchorRetain(operatorId: String, keepCount: Int = 200) = anchors.enforceAnchorRetain(operatorId, keepCount)

    suspend fun migrateOldRelationships() = relationships.migrateOldRelationships()
    suspend fun insertPresetRelationships() = relationships.insertPresetRelationships()
    suspend fun getRelationships(operatorId: String) = relationships.getRelationships(operatorId)
    suspend fun getReverseRelationships(opId: String) = relationships.getReverseRelationships(opId)
    suspend fun getRelationship(operatorId: String, relatedOperatorId: String) = relationships.getRelationship(operatorId, relatedOperatorId)
    suspend fun insertRelationship(rel: Relationship) = relationships.insertRelationship(rel)
    suspend fun deleteRelationshipByOperator(operatorId: String) = relationships.deleteRelationshipByOperator(operatorId)
    suspend fun bfsRelationGraph(centerId: String) = relationships.bfsRelationGraph(centerId)
    suspend fun getSharedMemoriesForOperator(operatorId: String) = relationships.getSharedMemoriesForOperator(operatorId)

    suspend fun insertMoment(moment: Moment) = moments.insertMoment(moment)
    fun getAllMoments() = moments.getAllMoments()
    suspend fun getAllMomentsSync() = moments.getAllMomentsSync()
    suspend fun getAllLikesForBackup() = moments.getAllLikesForBackup()
    suspend fun getAllCommentsForBackup() = moments.getAllCommentsForBackup()
    fun getLikesFlow(momentId: Long) = moments.getLikesFlow(momentId)
    fun getComments(momentId: Long) = moments.getComments(momentId)
    suspend fun insertLike(like: MomentLike) = moments.insertLike(like)
    suspend fun insertComment(comment: MomentComment) = moments.insertComment(comment)
    suspend fun getMaxCommentId() = moments.getMaxCommentId()
    suspend fun getCommentById(commentId: Long) = moments.getCommentById(commentId)
    suspend fun markCommentRead(id: Long) = moments.markCommentRead(id)
    suspend fun markMomentCommentsReadForUser(momentId: Long, userName: String) = moments.markMomentCommentsReadForUser(momentId, userName)
    suspend fun markAllCommentsRead(userName: String) = moments.markAllCommentsRead(userName)
    suspend fun deleteOldUserComments(cutoff: Long, userName: String) = moments.deleteOldUserComments(cutoff, userName)
    suspend fun updateLikeCount(momentId: Long, count: Int) = moments.updateLikeCount(momentId, count)
    suspend fun updateCommentCount(momentId: Long, count: Int) = moments.updateCommentCount(momentId, count)
    suspend fun getCommentCount(momentId: Long) = moments.getCommentCount(momentId)
    suspend fun getLikeCount(momentId: Long) = moments.getLikeCount(momentId)
    suspend fun backfillLikeCounts() = moments.backfillLikeCounts()
    suspend fun getLike(momentId: Long, operatorId: String) = moments.getLike(momentId, operatorId)
    suspend fun getMomentsPaged(limit: Int, offset: Int) = moments.getMomentsPaged(limit, offset)
    suspend fun getMomentsBefore(createdAt: Long, id: Long, limit: Int) = moments.getMomentsBefore(createdAt, id, limit)
    suspend fun getInboxComments(cutoff: Long, userName: String) = moments.getInboxComments(cutoff, userName)
    suspend fun getUnreadCommentCount(cutoff: Long, userName: String) = moments.getUnreadCommentCount(cutoff, userName)
    suspend fun getMomentsByOperator(operatorId: String) = moments.getMomentsByOperator(operatorId)
    suspend fun countMomentsByOperatorSince(operatorId: String, since: Long) = moments.countMomentsByOperatorSince(operatorId, since)
    suspend fun deleteLike(momentId: Long, operatorId: String) = moments.deleteLike(momentId, operatorId)
    suspend fun getMoment(id: Long) = moments.getMoment(id)
    suspend fun getMaxMomentId() = moments.getMaxMomentId()
    suspend fun deleteOldMoments(cutoff: Long) = moments.deleteOldMoments(cutoff)
    suspend fun deleteMomentsByOperator(operatorId: String) = moments.deleteMomentsByOperator(operatorId)

    suspend fun insertDiary(diary: Diary) = diaries.insertDiary(diary)
    suspend fun getDiary(operatorId: String, date: String) = diaries.getDiary(operatorId, date)
    fun getDiariesByOperator(operatorId: String) = diaries.getDiariesByOperator(operatorId)
    suspend fun getAllDiaryEntries(operatorId: String) = diaries.getAllDiaryEntries(operatorId)
    suspend fun getDiaryDates(operatorId: String) = diaries.getDiaryDates(operatorId)
    suspend fun getDiaryCount() = diaries.getDiaryCount()
    suspend fun getAllDiariesForBackup() = diaries.getAllDiariesForBackup()
    suspend fun deleteOldDiaries(cutoff: Long) = diaries.deleteOldDiaries(cutoff)

    suspend fun getActiveDispatches() = dispatches.getActiveDispatches()
    suspend fun getHistoryDispatches() = dispatches.getHistoryDispatches()
    suspend fun getDispatch(id: String) = dispatches.getDispatch(id)
    suspend fun insertDispatch(record: DispatchRecord) = dispatches.insertDispatch(record)
    suspend fun updateDispatch(id: String, logChain: String, status: String, endTime: Long = 0, netProfit: Int = 0) = dispatches.updateDispatch(id, logChain, status, endTime, netProfit)
    suspend fun updateDispatchFull(id: String, logChain: String, status: String, endTime: Long = 0, netProfit: Int = 0, totalSegments: Int = 0, segmentInterval: Long = 0) = dispatches.updateDispatchFull(id, logChain, status, endTime, netProfit, totalSegments, segmentInterval)
    suspend fun deleteOldDispatches(cutoff: Long) = dispatches.deleteOldDispatches(cutoff)

    suspend fun getMahjongSave() = mahjong.getMahjongSave()
    suspend fun saveMahjong(save: MahjongSave) = mahjong.saveMahjong(save)
    suspend fun deleteMahjongSave() = mahjong.deleteMahjongSave()

    suspend fun cleanupExpiredData() = cleanup.cleanupExpiredData()

    // --- Memory v2 ---
    suspend fun insertMemoryItem(item: MemoryItem) = memoryV2.insertMemoryItem(item)
    suspend fun insertMemorySource(source: MemorySourceItem) = memoryV2.insertSource(source)
    suspend fun saveMemoryBatch(batch: MemoryBatch) = memoryV2.saveBatch(batch)
    suspend fun getMemoryItemsByLevel(ownerType: String, ownerId: String, level: MemoryLevel) = memoryV2.getMemoryItemsByLevel(ownerType, ownerId, level)
    suspend fun getActiveMemoryItemsByLevel(ownerType: String, ownerId: String, level: MemoryLevel, now: Long) = memoryV2.getActiveMemoryItemsByLevel(ownerType, ownerId, level, now)
    suspend fun getMemoryItemsByOwner(ownerType: String, ownerId: String) = memoryV2.getMemoryItemsByOwner(ownerType, ownerId)
    suspend fun getAllMemoryItems() = memoryV2.getAllMemoryItems()
    suspend fun getMemoryItemsByType(ownerType: String, ownerId: String, type: String) = memoryV2.getMemoryItemsByType(ownerType, ownerId, type)
    suspend fun getActiveMemoryItemByContent(ownerType: String, ownerId: String, level: MemoryLevel, type: String, content: String) = memoryV2.getActiveMemoryItemByContent(ownerType, ownerId, level, type, content)
    suspend fun markMemorySourceProcessedL1(id: Long) = memoryV2.markSourceProcessedL1(id)
    suspend fun markMemorySourceProcessedVector(id: Long) = memoryV2.markSourceProcessedVector(id)
    suspend fun updateMemoryItemVectorId(id: Long, vectorId: String, updatedAt: Long) = memoryV2.updateMemoryItemVectorId(id, vectorId, updatedAt)
    suspend fun updateMemoryItemContent(id: Long, content: String, updatedAt: Long) = memoryV2.updateMemoryItemContent(id, content, updatedAt)
    suspend fun clearAllMemoryItemVectorIds() = memoryV2.clearAllMemoryItemVectorIds()
    suspend fun archiveMemoryItemsByLevel(ownerType: String, ownerId: String, level: MemoryLevel, updatedAt: Long) = memoryV2.archiveMemoryItemsByLevel(ownerType, ownerId, level, updatedAt)
    suspend fun archiveMemoryItem(id: Long, updatedAt: Long) = memoryV2.archiveMemoryItem(id, updatedAt)
    suspend fun markMemoryItemUsed(id: Long, now: Long) = memoryV2.markMemoryItemUsed(id, now)
    suspend fun insertMemoryLink(link: MemoryLink) = memoryV2.insertMemoryLink(link)
    suspend fun getMemoryLinksByParent(parentMemoryId: Long) = memoryV2.getMemoryLinksByParent(parentMemoryId)
    suspend fun deleteMemoryItem(id: Long) = memoryV2.deleteMemoryItem(id)

    suspend fun syncOperatorAvatar(operatorId: String, avatarUri: String) {
        val session = sessions.getSessionByOperator(operatorId)
        if (session != null && avatarUri.isNotBlank() && session.avatarUri != avatarUri) {
            sessions.insertSession(session.copy(avatarUri = avatarUri))
        }
    }

    suspend fun syncOperatorName(operatorId: String, newName: String) {
        sessions.syncOperatorName(operatorId, newName)
    }
}
