package com.rhodes.privatechat.data.backup

import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.model.MemoryLevel

/** Restores portable user data in one SQLite transaction. Runtime queues, links and vectors reset. */
class RestoreDatabaseExecutor(private val repository: ChatRepository) {
    suspend fun restore(
        payload: BackupPayload,
        requestedSelection: BackupContentSelection = BackupContentSelection.All,
        onProgress: (BackupRestoreProgress) -> Unit = {},
    ) {
        val selection = requestedSelection.normalized()
        val total = listOf(
            payload.content.operators.orEmpty().size, payload.content.knowledgeBases.orEmpty().size,
            payload.content.knowledgeBaseChunks.orEmpty().size, payload.content.operatorKnowledgeBaseAssignments.orEmpty().size,
            payload.content.relationships.orEmpty().size, payload.content.sessions.orEmpty().size,
            payload.content.messages.orEmpty().size, payload.displayEvents.size, payload.content.memories.orEmpty().size,
            payload.content.anchors.orEmpty().size, payload.content.moments.orEmpty().size,
            payload.content.momentComments.orEmpty().size, payload.content.momentLikes.orEmpty().size,
            payload.content.diaries.orEmpty().size, payload.giftRecords.size, payload.dispatchRecords.size,
            payload.sharedExperiences.size, payload.sharedExperienceParticipants.size, payload.chatArchives.size,
            payload.chatHistorySegments.size, payload.content.memoryItems.orEmpty().size,
        ).sum()
        var completed = 0
        fun report(detail: String, added: Int = 0) {
            completed += added
            onProgress(BackupRestoreProgress(BackupRestoreStage.RESTORING_DATABASE, detail, completed, total))
        }
        repository.runRestoreTransaction {
            // Clear only selected categories, with children removed before their parents.
            if (selection.extras) {
                dailyDeliveriesQueries.deleteAllDailyDeliveries()
                giftRecordsQueries.deleteAllGifts()
                dispatchRecordsQueries.deleteAllDispatches()
                mahjongSavesQueries.deleteSave()
            }
            if (selection.memories) {
                memoryLinksQueries.deleteAllMemoryLinks()
                memorySourceQueueQueries.deleteAllMemorySources()
                memoryBatchesQueries.deleteAllMemoryBatches()
                vectorMemoriesQueries.deleteAllVectorMemories()
                memoryItemsQueries.deleteAllMemoryItems()
                memoryAnchorsQueries.deleteAllAnchors()
                memoriesQueries.deleteAllMemories()
            }
            if (selection.chats) {
                replyTurnsQueries.deleteAllReplyTurns()
                chatDisplayEventsQueries.deleteAllDisplayEvents()
                chatArchivesQueries.deleteAllArchives()
                chatArchivesQueries.deleteAllHistorySegments()
                chatMessagesQueries.deleteAllMessages()
                chatSessionsQueries.deleteAllSessions()
                chatSessionsQueries.deleteAllGroups()
            }
            if (selection.social) {
                momentLikesQueries.deleteAllLikes()
                momentCommentsQueries.deleteAllComments()
                momentsQueries.deleteAllMoments()
                diariesQueries.deleteAllDiaries()
                sharedExperiencesQueries.deleteAllSharedExperienceParticipants()
                sharedExperiencesQueries.deleteAllSharedExperiences()
            }
            if (selection.knowledgeBases) {
                knowledgeBasesQueries.deleteAllKnowledgeBaseAssignments()
                knowledgeBasesQueries.deleteAllKnowledgeBaseChunks()
                knowledgeBasesQueries.deleteAllKnowledgeBases()
            }
            // A role-only restore must preserve roles referenced by unchecked current chats.
            // It therefore upserts imported roles; complete chat restore can safely replace them.
            if (selection.roles && selection.chats) {
                relationshipsQueries.deleteAllRelationships()
                operatorsQueries.deleteAllOperators()
            }

            report("正在恢复角色与关系")
            payload.content.operators.orEmpty().forEach { op ->
                operatorsQueries.insertAllOperators(op.id, op.name, op.title, op.description, op.gender, op.avatarUri, op.location, op.activity, op.emotion, op.intimacy.toLong(), op.privatePrompt, op.groupPrompt, op.memoryInjection, op.userRelation, op.lmb.toLong(), op.attack.toDouble(), op.defense.toDouble(), op.meldPref, op.activityLevel.toDouble(), op.voiceName, op.voiceSpeed, op.voicePitch)
            }
            report("正在恢复知识库")
            payload.content.knowledgeBases.orEmpty().forEach { book ->
                knowledgeBasesQueries.insertKnowledgeBase(book.id, book.name, book.rawContent, book.sourceFileName, book.sourceFormat, book.sourceType, book.chunkingMode, "pending", "", book.createdAt, book.updatedAt)
            }
            payload.content.knowledgeBaseChunks.orEmpty().forEach { chunk ->
                knowledgeBasesQueries.insertKnowledgeBaseChunk(chunk.id, chunk.knowledgeBaseId, chunk.ordinal.toLong(), chunk.sourceHeading, chunk.content, chunk.userKeywords, if (chunk.enabled) 1 else 0, 0L, "", chunk.createdAt, chunk.updatedAt)
            }
            payload.content.operatorKnowledgeBaseAssignments.orEmpty().forEach { assignment ->
                knowledgeBasesQueries.insertKnowledgeBaseAssignment(assignment.operatorId, assignment.knowledgeBaseId, if (assignment.enabled) 1 else 0, assignment.sortOrder.toLong())
            }
            payload.content.relationships.orEmpty().forEach { rel ->
                relationshipsQueries.insertAllRelationships(rel.operatorId, rel.relatedOperatorId, rel.relatedOperatorName, rel.type, rel.intimacy.toLong(), if (rel.isPreset) 1 else 0, rel.note)
            }
            report("已恢复知识库", payload.content.knowledgeBases.orEmpty().size + payload.content.knowledgeBaseChunks.orEmpty().size + payload.content.operatorKnowledgeBaseAssignments.orEmpty().size)
            report("已恢复角色与关系", payload.content.operators.orEmpty().size + payload.content.relationships.orEmpty().size)
            report("正在恢复聊天记录")
            payload.content.sessions.orEmpty().forEach { session ->
                chatSessionsQueries.insertSession(session.id, session.operatorId, session.operatorName, session.lastMessage, session.lastTime, session.mode, if (session.isPinned) 1 else 0, session.unreadCount.toLong(), session.members, session.rules, session.avatarUri, session.mutedMembers)
            }
            payload.content.messages.orEmpty().forEach { message ->
                chatMessagesQueries.insertAllMessages(message.id, message.sessionId, message.senderId, message.senderName, message.content, message.type, message.mode, message.emotion, message.activity, message.location, message.narration, message.segmentGroup, message.intimacyChange.toLong(), message.timestamp, if (message.isMe) 1 else 0)
            }
            payload.displayEvents.forEach { event ->
                chatDisplayEventsQueries.insertDisplayEventIfAbsent(event.messageId, event.segmentIndex.toLong(), event.sessionId, event.revealOrder)
            }
            report("已恢复聊天记录", payload.content.sessions.orEmpty().size + payload.content.messages.orEmpty().size + payload.displayEvents.size)
            report("正在恢复记忆")
            payload.content.memories.orEmpty().forEach { memory ->
                memoriesQueries.insertMemory(memory.sessionId, memory.operatorId, memory.type.name, memory.content, memory.keywords, memory.preferences, memory.taboos, memory.createdAt, memory.expiresAt)
            }
            payload.content.anchors.orEmpty().forEach { anchor ->
                memoryAnchorsQueries.insertAnchor(anchor.sessionId, anchor.operatorId, anchor.type.name, anchor.content, if (anchor.isPrivate) 1 else 0, anchor.createdAt, anchor.expiresAt, anchor.source, anchor.sourceName, anchor.sourceActor, anchor.sourceTarget, anchor.importance, anchor.knownFrom)
            }
            report("已恢复记忆", payload.content.memories.orEmpty().size + payload.content.anchors.orEmpty().size)

            report("正在恢复动态与日记")
            val momentIds = mutableMapOf<Long, Long>()
            payload.content.moments.orEmpty().forEach { moment ->
                momentsQueries.insertMoment(moment.operatorId, moment.operatorName, moment.content, if (moment.isUserPost) 1 else 0, moment.mentionedOperatorIds, moment.likeCount.toLong(), moment.commentCount.toLong(), moment.createdAt)
                momentIds[moment.id] = momentsQueries.getLastInsertRowId().executeAsOne()
            }
            val commentIds = mutableMapOf<Long, Long>()
            val pendingComments = payload.content.momentComments.orEmpty().toMutableList()
            while (pendingComments.isNotEmpty()) {
                val ready = pendingComments.filter { it.parentCommentId == 0L || it.parentCommentId in commentIds }
                check(ready.isNotEmpty()) { "评论父级引用无法恢复" }
                ready.forEach { comment ->
                    val momentId = requireNotNull(momentIds[comment.momentId]) { "评论引用的动态无法恢复" }
                    val parentId = if (comment.parentCommentId == 0L) 0L else requireNotNull(commentIds[comment.parentCommentId]) { "评论父级无法恢复" }
                    momentCommentsQueries.insertComment(momentId, comment.operatorId, comment.operatorName, comment.content, parentId, comment.replyToName, comment.createdAt, if (comment.isRead) 1 else 0)
                    commentIds[comment.id] = momentCommentsQueries.getLastInsertRowId().executeAsOne()
                }
                pendingComments.removeAll(ready.toSet())
            }
            payload.content.momentLikes.orEmpty().forEach { like ->
                momentIds[like.momentId]?.let { momentId -> momentLikesQueries.insertLike(momentId, like.operatorId, like.operatorName, like.createdAt) }
            }
            momentsQueries.backfillLikeCounts()
            momentsQueries.backfillCommentCounts()
            payload.content.diaries.orEmpty().forEach { diary ->
                diariesQueries.insertDiary(diary.operatorId, diary.operatorName, diary.content, diary.date, diary.version.toLong(), diary.createdAt)
            }
            report("已恢复动态与日记", payload.content.moments.orEmpty().size + payload.content.momentComments.orEmpty().size + payload.content.momentLikes.orEmpty().size + payload.content.diaries.orEmpty().size)
            report("正在恢复附加数据")
            payload.giftRecords.forEach { gift -> giftRecordsQueries.insert(gift.id, gift.operatorId, gift.imageUri, gift.giftName, gift.senderName, gift.createdAt) }
            payload.dispatchRecords.forEach { dispatch ->
                val status = when (dispatch.status) {
                    "active", "generating" -> "paused"
                    else -> dispatch.status
                }
                dispatchRecordsQueries.insertDispatch(dispatch.id, dispatch.taskType, dispatch.durationHours.toLong(), dispatch.budget.toLong(), dispatch.netProfit.toLong(), dispatch.operatorIds, dispatch.logChain, status, dispatch.startTime, dispatch.endTime, dispatch.totalSegments.toLong(), dispatch.segmentInterval, dispatch.items)
            }
            payload.mahjongSave?.let { save -> mahjongSavesQueries.insertSave(save.id, save.saveJson, save.ruleType, save.savedAt) }
            val experienceIds = mutableMapOf<Long, Long>()
            payload.sharedExperiences.forEach { experience ->
                sharedExperiencesQueries.insertSharedExperience(experience.sourceKind, experience.sourceRefId, experience.groupId, experience.content, experience.importance.toLong(), experience.status, experience.createdAt, experience.expiresAt)
                experienceIds[experience.id] = sharedExperiencesQueries.getLastSharedExperienceId().executeAsOne()
            }
            payload.sharedExperienceParticipants.forEach { participant ->
                experienceIds[participant.experienceId]?.let { id ->
                    sharedExperiencesQueries.insertSharedExperienceParticipant(id, participant.operatorId, participant.role)
                }
            }
            payload.chatArchives.forEach { archive -> chatArchivesQueries.insertArchive(archive.id, archive.sessionId, archive.operatorId, archive.title, archive.note, archive.mode, archive.messagesJson, archive.summary, archive.stateJson, archive.status, archive.createdAt, archive.updatedAt) }
            payload.chatHistorySegments.forEach { history -> chatArchivesQueries.insertHistorySegment(history.id, history.sessionId, history.title, history.reason, history.messagesJson, history.createdAt) }
            payload.content.memoryItems.orEmpty().forEach { item ->
                val status = if (item.memoryLevel == MemoryLevel.L1) item.status else "archived"
                val sourceRefId = when (item.sourceKind.name) {
                    "MOMENT" -> item.sourceRefId.toLongOrNull()?.let(momentIds::get)?.toString() ?: item.sourceRefId
                    "MOMENT_COMMENT" -> item.sourceRefId.toLongOrNull()?.let(commentIds::get)?.toString() ?: item.sourceRefId
                    else -> item.sourceRefId
                }
                memoryItemsQueries.insertMemoryItem(item.ownerType, item.ownerId, item.memoryLevel.name, item.memoryType, item.sourceKind.name, sourceRefId, item.sessionId, item.content, item.nickname, item.importance.toLong(), item.privacy, if (item.unmetNeed) 1 else 0, item.location, item.emotionValence, item.eventTime, item.createdAt, item.updatedAt, item.expiresAt, status, item.scheduledTime, item.action, item.careType, item.topicKey, item.sourceActor, item.sourceTarget, item.lastUsedAt, item.usedCount.toLong(), item.confidence, item.rawJson, "")
            }
            report("已恢复附加数据", payload.giftRecords.size + payload.dispatchRecords.size + payload.sharedExperiences.size + payload.sharedExperienceParticipants.size + payload.chatArchives.size + payload.chatHistorySegments.size + payload.content.memoryItems.orEmpty().size)
        }
    }
}
