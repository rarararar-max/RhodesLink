package com.rhodes.privatechat.data.repository

import com.rhodes.privatechat.shared.data.ChatRepository as SharedChatRepository
import com.rhodes.privatechat.shared.model.*
import kotlinx.coroutines.flow.Flow

/**
 * App-level ChatRepository that delegates to the shared ChatRepository.
 * This exists for backward compatibility with ViewModels that import this class.
 */
class ChatRepository(
    private val shared: SharedChatRepository
) {
    val allOperators: Flow<List<Operator>> = shared.allOperators
    val allSessions: Flow<List<ChatSession>> = shared.allSessions

    suspend fun getAllOperatorsSync(): List<Operator> = shared.getAllOperatorsSync()
    suspend fun getOperator(id: String): Operator? = shared.getOperator(id)

    suspend fun getAllSessionsSync(): List<ChatSession> = shared.getAllSessionsSync()

    suspend fun getOrCreateSession(operatorId: String, operatorName: String, avatarUri: String = ""): ChatSession =
        shared.getOrCreateSession(operatorId, operatorName, avatarUri)

    fun getMessages(sessionId: String): Flow<List<ChatMessage>> = shared.getMessages(sessionId)

    suspend fun getMessagesSync(sessionId: String): List<ChatMessage> = shared.getMessagesSync(sessionId)

    suspend fun getMessagesBefore(sessionId: String, beforeTimestamp: Long, beforeId: Long, limit: Long = 100): List<ChatMessage> =
        shared.getMessagesBefore(sessionId, beforeTimestamp, beforeId, limit)

    suspend fun updateMessageContent(id: Long, content: String) = shared.updateMessageContent(id, content)
    suspend fun updateMessageContentAndPreview(sessionId: String, id: Long, content: String, timestamp: Long) =
        shared.updateMessageContentAndPreview(sessionId, id, content, timestamp)
    suspend fun getDisplayEvents(sessionId: String) = shared.getDisplayEvents(sessionId)
    suspend fun addDisplayEventIfAbsent(sessionId: String, messageId: Long, segmentIndex: Int) =
        shared.addDisplayEventIfAbsent(sessionId, messageId, segmentIndex)
    suspend fun deleteDisplayEvent(messageId: Long, segmentIndex: Int) = shared.deleteDisplayEvent(messageId, segmentIndex)
    suspend fun deleteMessageDisplayEvents(messageId: Long) = shared.deleteMessageDisplayEvents(messageId)

    suspend fun sendMessage(sessionId: String, message: ChatMessage) = shared.sendMessage(sessionId, message)

    suspend fun getGiftsByOperator(operatorId: String) = shared.getGiftsByOperator(operatorId)
    suspend fun insertGift(gift: GiftRecord) = shared.insertGift(gift)
    suspend fun deleteGift(id: Long) = shared.deleteGift(id)
    suspend fun deleteGiftsByOperator(operatorId: String) = shared.deleteGiftsByOperator(operatorId)

    suspend fun updateSessionMode(sessionId: String, mode: String) = shared.updateSessionMode(sessionId, mode)

    suspend fun getNextMessageId(): Long = shared.getNextMessageId()

    suspend fun insertPresetOperators() = shared.insertPresetOperators()

    suspend fun migrateOldRelationships() = shared.migrateOldRelationships()

    suspend fun insertPresetRelationships() = shared.insertPresetRelationships()

    suspend fun getPrivateChatSummary(operatorId: String): String? = shared.getPrivateChatSummary(operatorId)

    suspend fun getPrivateChatContext(operatorId: String): String? = shared.getPrivateChatContext(operatorId)

    suspend fun getShortTermMemory(sessionId: String): Memory? = shared.getShortTermMemory(sessionId)

    suspend fun getLongTermImpression(operatorId: String): Memory? = shared.getLongTermImpression(operatorId)

    suspend fun saveMemory(memory: Memory) = shared.saveMemory(memory)
    suspend fun replaceShortTermMemory(memory: Memory) = shared.replaceShortTermMemory(memory)

    suspend fun saveAnchor(anchor: MemoryAnchor) = shared.saveAnchor(anchor)

    suspend fun saveAnchors(anchors: List<MemoryAnchor>) = shared.saveAnchors(anchors)
    suspend fun deleteMemoriesBySession(sessionId: String) = shared.deleteMemoriesBySession(sessionId)
    suspend fun deleteShortTermMemory(sessionId: String) = shared.deleteShortTermMemory(sessionId)
    suspend fun deleteAnchorsBySession(sessionId: String) = shared.deleteAnchorsBySession(sessionId)

    suspend fun getPublicAnchors(operatorId: String): List<MemoryAnchor> = shared.getPublicAnchors(operatorId)

    suspend fun getAnchors(operatorId: String): List<MemoryAnchor> = shared.getAnchors(operatorId)

    suspend fun getRelationships(operatorId: String): List<Relationship> = shared.getRelationships(operatorId)

    suspend fun getReverseRelationships(opId: String): List<Relationship> = shared.getReverseRelationships(opId)

    suspend fun bfsRelationGraph(centerId: String): List<com.rhodes.privatechat.shared.data.BfsNode> = shared.bfsRelationGraph(centerId)

    suspend fun getSharedMemoriesForOperator(operatorId: String): String = shared.getSharedMemoriesForOperator(operatorId)

    suspend fun insertMoment(moment: Moment): Long = shared.insertMoment(moment)

    fun getAllMoments(): Flow<List<Moment>> = shared.getAllMoments()
    suspend fun getAllMomentsSync(): List<Moment> = shared.getAllMomentsSync()

    fun getLikesFlow(momentId: Long): Flow<List<MomentLike>> = shared.getLikesFlow(momentId)

    fun getComments(momentId: Long): Flow<List<MomentComment>> = shared.getComments(momentId)

    suspend fun insertLike(like: MomentLike) = shared.insertLike(like)

    suspend fun insertComment(comment: MomentComment): Long = shared.insertComment(comment)

    suspend fun getMaxCommentId(): Long? = shared.getMaxCommentId()
    suspend fun getCommentById(commentId: Long): MomentComment? = shared.getCommentById(commentId)

    suspend fun markCommentRead(id: Long) = shared.markCommentRead(id)

    suspend fun markAllCommentsRead(userName: String) = shared.markAllCommentsRead(userName)

    suspend fun deleteOldUserComments(cutoff: Long, userName: String) = shared.deleteOldUserComments(cutoff, userName)

    suspend fun updateLikeCount(momentId: Long, count: Int) = shared.updateLikeCount(momentId, count)

    suspend fun updateCommentCount(momentId: Long, count: Int) = shared.updateCommentCount(momentId, count)

    suspend fun getLikeCount(momentId: Long): Int = shared.getLikeCount(momentId)
    suspend fun backfillLikeCounts() = shared.backfillLikeCounts()

    suspend fun getLike(momentId: Long, operatorId: String): MomentLike? = shared.getLike(momentId, operatorId)

    suspend fun getMomentsPaged(limit: Int, offset: Int): List<Moment> = shared.getMomentsPaged(limit, offset)
    suspend fun getMomentsBefore(createdAt: Long, id: Long, limit: Int): List<Moment> = shared.getMomentsBefore(createdAt, id, limit)

    suspend fun getInboxComments(cutoff: Long, userName: String): List<MomentComment> = shared.getInboxComments(cutoff, userName)

    suspend fun getUnreadCommentCount(cutoff: Long, userName: String): Int = shared.getUnreadCommentCount(cutoff, userName)

    suspend fun insertDiary(diary: Diary) = shared.insertDiary(diary)

    suspend fun getDiary(operatorId: String, date: String): Diary? = shared.getDiary(operatorId, date)

    fun getDiariesByOperator(operatorId: String): Flow<List<Diary>> = shared.getDiariesByOperator(operatorId)

    fun getLatestDiaryCreatedAtByOperator(): Flow<Map<String, Long>> = shared.getLatestDiaryCreatedAtByOperator()

    suspend fun getAllDiaryEntries(operatorId: String): List<Diary> = shared.getAllDiaryEntries(operatorId)
    suspend fun getDiaryDates(operatorId: String): List<String> = shared.getDiaryDates(operatorId)

    suspend fun getDiaryCount(): Int = shared.getDiaryCount()

    suspend fun deleteOldDiaries(cutoff: Long) = shared.deleteOldDiaries(cutoff)

    suspend fun deleteOldMoments(cutoff: Long) = shared.deleteOldMoments(cutoff)

    suspend fun deleteOldDispatches(cutoff: Long) = shared.deleteOldDispatches(cutoff)

    suspend fun getAnchorCount(): Int = shared.getAnchorCount()

    suspend fun deleteOldAnchors(cutoff: Long) = shared.deleteOldAnchors(cutoff)
    suspend fun enforceAnchorRetain(operatorId: String, keepCount: Int = 200) = shared.enforceAnchorRetain(operatorId, keepCount)

    suspend fun getMessageCount(): Int = shared.getMessageCount()

    suspend fun getSessionCount(): Int = shared.getSessionCount()

    suspend fun getSession(id: String): ChatSession? = shared.getSession(id)
    suspend fun incrementUnread(sessionId: String, delta: Int = 1) = shared.incrementUnread(sessionId, delta)

    suspend fun getGroupCount(): Int = shared.getGroupCount()

    suspend fun getMessageCountPerSender(): List<com.rhodes.privatechat.shared.data.SenderCount> = shared.getMessageCountPerSender()

    suspend fun getAllLongTermImpressions(): List<Memory> = shared.getAllLongTermImpressions()

    suspend fun getMessagesInRange(start: Long, end: Long): List<ChatMessage> = shared.getMessagesInRange(start, end)

    suspend fun getLatestDaily(): Memory? = shared.getLatestDaily()
    suspend fun getLatestPrivateDaily(operatorId: String): Memory? = shared.getLatestPrivateDaily(operatorId)

    suspend fun enforceMemoryRetain(sessionId: String, keepCount: Int) = shared.enforceMemoryRetain(sessionId, keepCount)

    suspend fun initPresetGroups() = shared.initPresetGroups()

    suspend fun cleanupExpiredData() = shared.cleanupExpiredData()

    suspend fun deleteAllImpressions() = shared.deleteAllImpressions()

    suspend fun getActiveDispatches(): List<DispatchRecord> = shared.getActiveDispatches()

    suspend fun getHistoryDispatches(): List<DispatchRecord> = shared.getHistoryDispatches()

    suspend fun getDispatch(id: String): DispatchRecord? = shared.getDispatch(id)

    suspend fun insertDispatch(record: DispatchRecord) = shared.insertDispatch(record)

    suspend fun updateDispatch(id: String, logChain: String, status: String, endTime: Long = 0, netProfit: Int = 0) =
        shared.updateDispatch(id, logChain, status, endTime, netProfit)

    suspend fun updateDispatchFull(id: String, logChain: String, status: String, endTime: Long = 0, netProfit: Int = 0, totalSegments: Int = 0, segmentInterval: Long = 0) =
        shared.updateDispatchFull(id, logChain, status, endTime, netProfit, totalSegments, segmentInterval)

    suspend fun deleteSession(id: String) = shared.deleteSession(id)

    suspend fun deleteSessionMessages(sessionId: String) = shared.deleteSessionMessages(sessionId)
    suspend fun getChatArchives(sessionId: String) = shared.getChatArchives(sessionId)
    suspend fun getChatArchive(id: String) = shared.getChatArchive(id)
    suspend fun getPendingChatArchives() = shared.getPendingChatArchives()
    suspend fun saveChatArchive(archive: ChatArchive) = shared.saveChatArchive(archive)
    suspend fun updateChatArchiveSummary(id: String, summary: String, status: String, now: Long) = shared.updateChatArchiveSummary(id, summary, status, now)
    suspend fun updateChatArchiveTitle(id: String, title: String, now: Long) = shared.updateChatArchiveTitle(id, title, now)
    suspend fun updateChatArchiveContext(id: String, contextJson: String, now: Long) = shared.updateChatArchiveContext(id, contextJson, now)
    suspend fun deleteChatArchive(id: String) = shared.deleteChatArchive(id)
    suspend fun getChatHistorySegments(sessionId: String) = shared.getChatHistorySegments(sessionId)
    suspend fun saveChatHistorySegment(segment: ChatHistorySegment) = shared.saveChatHistorySegment(segment)
    suspend fun restoreChatArchive(sessionId: String, operatorId: String, history: ChatHistorySegment?, messages: List<ChatMessage>, summary: Memory) =
        shared.restoreChatArchive(sessionId, operatorId, history, messages, summary)

    suspend fun deleteMessage(id: Long) = shared.deleteMessage(id)

    suspend fun insertSession(session: ChatSession) = shared.insertSession(session)

    suspend fun markAllRead() = shared.markAllRead()

    suspend fun deleteOperator(id: String) = shared.deleteOperator(id)

    suspend fun updateOperator(op: Operator) = shared.updateOperator(op)

    suspend fun updateIntimacy(id: String, intimacy: Int) = shared.updateIntimacy(id, intimacy)

    suspend fun insertOperator(op: Operator) = shared.insertOperator(op)

    suspend fun insertRelationship(rel: Relationship) = shared.insertRelationship(rel)

    suspend fun getMomentsByOperator(operatorId: String): List<Moment> = shared.getMomentsByOperator(operatorId)

    suspend fun deleteLike(momentId: Long, operatorId: String) = shared.deleteLike(momentId, operatorId)

    suspend fun getMoment(id: Long): Moment? = shared.getMoment(id)

    suspend fun getMahjongSave(): MahjongSave? = shared.getMahjongSave()

    suspend fun saveMahjong(save: MahjongSave) = shared.saveMahjong(save)

    suspend fun deleteMahjongSave() = shared.deleteMahjongSave()

    suspend fun getLastUserMessageTime(sessionId: String): Long? {
        val msgs = shared.getMessagesSync(sessionId)
        return msgs.filter { it.isMe }.maxOfOrNull { it.timestamp }
    }

    suspend fun getSessionByOperator(operatorId: String): ChatSession? =
        shared.getSessionByOperator(operatorId)

    suspend fun updateLastMessage(sessionId: String, lastMessage: String, lastTime: Long) {
        shared.updateLastMessage(sessionId, lastMessage, lastTime)
    }

    suspend fun deleteRelationshipByOperator(operatorId: String) {
        shared.deleteRelationshipByOperator(operatorId)
    }

    suspend fun replaceRelationships(operatorId: String, relationships: List<Relationship>) =
        shared.replaceRelationships(operatorId, relationships)
}
