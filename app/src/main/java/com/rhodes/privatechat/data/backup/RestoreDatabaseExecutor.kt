package com.rhodes.privatechat.data.backup

import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.model.MemoryLevel

/** Restores portable user data in one SQLite transaction. Runtime queues, links and vectors reset. */
class RestoreDatabaseExecutor(private val repository: ChatRepository) {
    suspend fun restore(payload: BackupPayload) {
        repository.runRestoreTransaction {
            // Clear children before parents. Runtime-only state is intentionally discarded.
            dailyDeliveriesQueries.deleteAllDailyDeliveries()
            memoryLinksQueries.deleteAllMemoryLinks()
            memorySourceQueueQueries.deleteAllMemorySources()
            memoryBatchesQueries.deleteAllMemoryBatches()
            vectorMemoriesQueries.deleteAllVectorMemories()
            memoryItemsQueries.deleteAllMemoryItems()
            chatDisplayEventsQueries.deleteAllDisplayEvents()
            chatArchivesQueries.deleteAllArchives()
            chatArchivesQueries.deleteAllHistorySegments()
            momentLikesQueries.deleteAllLikes()
            momentCommentsQueries.deleteAllComments()
            momentsQueries.deleteAllMoments()
            diariesQueries.deleteAllDiaries()
            giftRecordsQueries.deleteAllGifts()
            dispatchRecordsQueries.deleteAllDispatches()
            mahjongSavesQueries.deleteSave()
            sharedExperiencesQueries.deleteAllSharedExperienceParticipants()
            sharedExperiencesQueries.deleteAllSharedExperiences()
            memoryAnchorsQueries.deleteAllAnchors()
            memoriesQueries.deleteAllMemories()
            chatMessagesQueries.deleteAllMessages()
            chatSessionsQueries.deleteAllSessions()
            chatSessionsQueries.deleteAllGroups()
            relationshipsQueries.deleteAllRelationships()
            knowledgeBasesQueries.deleteAllKnowledgeBaseAssignments()
            knowledgeBasesQueries.deleteAllKnowledgeBaseChunks()
            knowledgeBasesQueries.deleteAllKnowledgeBases()
            operatorsQueries.deleteAllOperators()

            payload.content.operators.orEmpty().forEach { op ->
                operatorsQueries.insertAllOperators(op.id, op.name, op.title, op.description, op.gender, op.avatarUri, op.location, op.activity, op.emotion, op.intimacy.toLong(), op.privatePrompt, op.groupPrompt, op.memoryInjection, op.userRelation, op.lmb.toLong(), op.attack.toDouble(), op.defense.toDouble(), op.meldPref, op.activityLevel.toDouble(), op.voiceName, op.voiceSpeed, op.voicePitch)
            }
            payload.content.knowledgeBases.orEmpty().forEach { book ->
                knowledgeBasesQueries.insertKnowledgeBase(book.id, book.name, book.rawContent, book.sourceFileName, book.sourceFormat, book.chunkingMode, "pending", "", book.createdAt, book.updatedAt)
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
            payload.content.sessions.orEmpty().forEach { session ->
                chatSessionsQueries.insertSession(session.id, session.operatorId, session.operatorName, session.lastMessage, session.lastTime, session.mode, if (session.isPinned) 1 else 0, session.unreadCount.toLong(), session.members, session.rules, session.avatarUri, session.mutedMembers)
            }
            payload.content.messages.orEmpty().forEach { message ->
                chatMessagesQueries.insertAllMessages(message.id, message.sessionId, message.senderId, message.senderName, message.content, message.type, message.mode, message.emotion, message.activity, message.location, message.narration, message.segmentGroup, message.intimacyChange.toLong(), message.timestamp, if (message.isMe) 1 else 0)
            }
            payload.displayEvents.forEach { event ->
                chatDisplayEventsQueries.insertDisplayEventIfAbsent(event.messageId, event.segmentIndex.toLong(), event.sessionId, event.revealOrder)
            }
            payload.content.memories.orEmpty().forEach { memory ->
                memoriesQueries.insertMemory(memory.sessionId, memory.operatorId, memory.type.name, memory.content, memory.keywords, memory.preferences, memory.taboos, memory.createdAt, memory.expiresAt)
            }
            payload.content.anchors.orEmpty().forEach { anchor ->
                memoryAnchorsQueries.insertAnchor(anchor.sessionId, anchor.operatorId, anchor.type.name, anchor.content, if (anchor.isPrivate) 1 else 0, anchor.createdAt, anchor.expiresAt, anchor.source, anchor.sourceName, anchor.sourceActor, anchor.sourceTarget, anchor.importance, anchor.knownFrom)
            }

            val momentIds = mutableMapOf<Long, Long>()
            payload.content.moments.orEmpty().forEach { moment ->
                momentsQueries.insertMoment(moment.operatorId, moment.operatorName, moment.content, if (moment.isUserPost) 1 else 0, moment.mentionedOperatorIds, moment.likeCount.toLong(), moment.commentCount.toLong(), moment.createdAt)
                momentIds[moment.id] = momentsQueries.getLastInsertRowId().executeAsOne()
            }
            val commentIds = mutableMapOf<Long, Long>()
            payload.content.momentComments.orEmpty().forEach { comment ->
                val momentId = momentIds[comment.momentId] ?: return@forEach
                val parentId = commentIds[comment.parentCommentId] ?: 0L
                momentCommentsQueries.insertComment(momentId, comment.operatorId, comment.operatorName, comment.content, parentId, comment.replyToName, comment.createdAt, if (comment.isRead) 1 else 0)
                commentIds[comment.id] = momentCommentsQueries.getLastInsertRowId().executeAsOne()
            }
            payload.content.momentLikes.orEmpty().forEach { like ->
                momentIds[like.momentId]?.let { momentId -> momentLikesQueries.insertLike(momentId, like.operatorId, like.operatorName, like.createdAt) }
            }
            momentsQueries.backfillLikeCounts()
            momentsQueries.backfillCommentCounts()
            payload.content.diaries.orEmpty().forEach { diary ->
                diariesQueries.insertDiary(diary.operatorId, diary.operatorName, diary.content, diary.date, diary.version.toLong(), diary.createdAt)
            }
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
        }
    }
}
