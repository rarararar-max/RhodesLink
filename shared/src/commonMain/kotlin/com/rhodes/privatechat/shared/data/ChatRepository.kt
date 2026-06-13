package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.model.*
import kotlinx.coroutines.flow.Flow

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

class ChatRepository(wrapper: DatabaseWrapper) {
    val operators = OperatorRepository(wrapper)
    val sessions = SessionRepository(wrapper)
    val messages = MessageRepository(wrapper)
    val memories = MemoryRepository(wrapper)
    val anchors = AnchorRepository(wrapper)
    val relationships = RelationshipRepository(wrapper)
    val moments = MomentRepository(wrapper)
    val diaries = DiaryRepository(wrapper)
    val dispatches = DispatchRepository(wrapper)
    val mahjong = MahjongRepository(wrapper)
    val cleanup = CleanupRepository(wrapper)

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
    suspend fun deleteSession(id: String) = sessions.deleteSession(id)
    suspend fun updateSessionMode(sessionId: String, mode: String) = sessions.updateSessionMode(sessionId, mode)
    suspend fun markAllRead() = sessions.markAllRead()
    suspend fun getSessionCount() = sessions.getSessionCount()
    suspend fun getGroupCount() = sessions.getGroupCount()
    suspend fun updateLastMessage(sessionId: String, lastMessage: String, lastTime: Long) = sessions.updateLastMessage(sessionId, lastMessage, lastTime)
    suspend fun getSessionByOperator(operatorId: String) = sessions.getSessionByOperator(operatorId)
    suspend fun getLastUserMessageTime(sessionId: String) = sessions.getLastUserMessageTime(sessionId)
    suspend fun initPresetGroups() = sessions.initPresetGroups()
    suspend fun getPrivateChatSummary(operatorId: String) = sessions.getPrivateChatSummary(operatorId)
    suspend fun getPrivateChatContext(operatorId: String) = sessions.getPrivateChatContext(operatorId)

    fun getMessages(sessionId: String) = messages.getMessages(sessionId)
    suspend fun getMessagesSync(sessionId: String) = messages.getMessagesSync(sessionId)
    suspend fun updateMessageContent(id: Long, content: String) = messages.updateMessageContent(id, content)
    suspend fun sendMessage(sessionId: String, message: ChatMessage) = messages.sendMessage(sessionId, message)
    suspend fun getNextMessageId() = messages.getNextMessageId()
    suspend fun deleteSessionMessages(sessionId: String) = messages.deleteSessionMessages(sessionId)
    suspend fun deleteMessage(id: Long) = messages.deleteMessage(id)
    suspend fun getMessageCount() = messages.getMessageCount()
    suspend fun deleteOldMessages(cutoff: Long) = messages.deleteOldMessages(cutoff)
    suspend fun getMessageCountPerSender() = messages.getMessageCountPerSender()
    suspend fun getMessageCountPerSenderSince(since: Long) = messages.getMessageCountPerSenderSince(since)
    suspend fun getMessagesInRange(start: Long, end: Long) = messages.getMessagesInRange(start, end)

    suspend fun getShortTermMemory(sessionId: String) = memories.getShortTermMemory(sessionId)
    suspend fun getLongTermImpression(operatorId: String) = memories.getLongTermImpression(operatorId)
    suspend fun saveMemory(memory: Memory) = memories.saveMemory(memory)
    suspend fun getAllLongTermImpressions() = memories.getAllLongTermImpressions()
    suspend fun getLatestDaily() = memories.getLatestDaily()
    suspend fun getLatestDailyBySession(sessionId: String) = memories.getLatestDailyBySession(sessionId)
    suspend fun getLatestPrivateDaily(operatorId: String) = memories.getLatestPrivateDaily(operatorId)
    suspend fun enforceMemoryRetain(sessionId: String, keepCount: Int) = memories.enforceMemoryRetain(sessionId, keepCount)
    suspend fun deleteAllImpressions() = memories.deleteAllImpressions()

    suspend fun saveAnchor(anchor: MemoryAnchor) = anchors.saveAnchor(anchor)
    suspend fun saveAnchors(anchors: List<MemoryAnchor>) = this.anchors.saveAnchors(anchors)
    suspend fun getPublicAnchors(operatorId: String) = anchors.getPublicAnchors(operatorId)
    suspend fun getAnchors(operatorId: String) = anchors.getAnchors(operatorId)
    suspend fun getAnchorCount() = anchors.getAnchorCount()
    suspend fun deleteOldAnchors(cutoff: Long) = anchors.deleteOldAnchors(cutoff)
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
    fun getLikesFlow(momentId: Long) = moments.getLikesFlow(momentId)
    fun getComments(momentId: Long) = moments.getComments(momentId)
    suspend fun insertLike(like: MomentLike) = moments.insertLike(like)
    suspend fun insertComment(comment: MomentComment) = moments.insertComment(comment)
    suspend fun getMaxCommentId() = moments.getMaxCommentId()
    suspend fun getCommentById(commentId: Long) = moments.getCommentById(commentId)
    suspend fun markCommentRead(id: Long) = moments.markCommentRead(id)
    suspend fun markAllCommentsRead(userName: String) = moments.markAllCommentsRead(userName)
    suspend fun deleteOldUserComments(cutoff: Long, userName: String) = moments.deleteOldUserComments(cutoff, userName)
    suspend fun updateLikeCount(momentId: Long, count: Int) = moments.updateLikeCount(momentId, count)
    suspend fun updateCommentCount(momentId: Long, count: Int) = moments.updateCommentCount(momentId, count)
    suspend fun getLikeCount(momentId: Long) = moments.getLikeCount(momentId)
    suspend fun getLike(momentId: Long, operatorId: String) = moments.getLike(momentId, operatorId)
    suspend fun getMomentsPaged(limit: Int, offset: Int) = moments.getMomentsPaged(limit, offset)
    suspend fun getInboxComments(cutoff: Long, userName: String) = moments.getInboxComments(cutoff, userName)
    suspend fun getUnreadCommentCount(cutoff: Long, userName: String) = moments.getUnreadCommentCount(cutoff, userName)
    suspend fun getMomentsByOperator(operatorId: String) = moments.getMomentsByOperator(operatorId)
    suspend fun deleteLike(momentId: Long, operatorId: String) = moments.deleteLike(momentId, operatorId)
    suspend fun getMoment(id: Long) = moments.getMoment(id)
    suspend fun deleteOldMoments(cutoff: Long) = moments.deleteOldMoments(cutoff)

    suspend fun insertDiary(diary: Diary) = diaries.insertDiary(diary)
    suspend fun getDiary(operatorId: String, date: String) = diaries.getDiary(operatorId, date)
    fun getDiariesByOperator(operatorId: String) = diaries.getDiariesByOperator(operatorId)
    suspend fun getDiaryDates(operatorId: String) = diaries.getDiaryDates(operatorId)
    suspend fun getDiaryCount() = diaries.getDiaryCount()
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
