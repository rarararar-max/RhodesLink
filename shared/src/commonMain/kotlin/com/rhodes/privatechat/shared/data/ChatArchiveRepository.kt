package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.ChatArchive
import com.rhodes.privatechat.shared.model.ChatHistorySegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatArchiveRepository(private val wrapper: DatabaseWrapper) {
    private val db: RhodesDatabase get() = wrapper.database

    suspend fun getArchives(sessionId: String): List<ChatArchive> = withContext(Dispatchers.Default) {
        db.chatArchivesQueries.getArchivesBySession(sessionId) { id, sid, operatorId, title, note, mode, messagesJson, summary, stateJson, status, createdAt, updatedAt ->
            ChatArchive(id, sid, operatorId, title, note, mode, messagesJson, summary, stateJson, status, createdAt, updatedAt)
        }.executeAsList()
    }

    suspend fun getArchive(id: String): ChatArchive? = withContext(Dispatchers.Default) {
        db.chatArchivesQueries.getArchive(id) { archiveId, sid, operatorId, title, note, mode, messagesJson, summary, stateJson, status, createdAt, updatedAt ->
            ChatArchive(archiveId, sid, operatorId, title, note, mode, messagesJson, summary, stateJson, status, createdAt, updatedAt)
        }.executeAsOneOrNull()
    }

    suspend fun getPendingArchives(): List<ChatArchive> = withContext(Dispatchers.Default) {
        db.chatArchivesQueries.getPendingArchives { id, sid, operatorId, title, note, mode, messagesJson, summary, stateJson, status, createdAt, updatedAt ->
            ChatArchive(id, sid, operatorId, title, note, mode, messagesJson, summary, stateJson, status, createdAt, updatedAt)
        }.executeAsList()
    }

    suspend fun save(archive: ChatArchive) = withContext(Dispatchers.Default) {
        db.chatArchivesQueries.insertArchive(archive.id, archive.sessionId, archive.operatorId, archive.title, archive.note, archive.mode, archive.messagesJson, archive.summary, archive.stateJson, archive.status, archive.createdAt, archive.updatedAt)
    }

    suspend fun updateSummary(id: String, summary: String, status: String, now: Long) = withContext(Dispatchers.Default) {
        db.chatArchivesQueries.updateArchiveSummary(summary, status, now, id)
    }

    suspend fun updateTitle(id: String, title: String, now: Long) = withContext(Dispatchers.Default) {
        db.chatArchivesQueries.updateArchiveTitle(title, now, id)
    }

    suspend fun updateContext(id: String, contextJson: String, now: Long) = withContext(Dispatchers.Default) {
        db.chatArchivesQueries.updateArchiveContext(contextJson, now, id)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.Default) { db.chatArchivesQueries.deleteArchive(id) }
    suspend fun deleteBySession(sessionId: String) = withContext(Dispatchers.Default) { db.chatArchivesQueries.deleteArchivesBySession(sessionId) }

    suspend fun getHistorySegments(sessionId: String): List<ChatHistorySegment> = withContext(Dispatchers.Default) {
        db.chatArchivesQueries.getHistorySegmentsBySession(sessionId) { id, sid, title, reason, messagesJson, createdAt ->
            ChatHistorySegment(id, sid, title, reason, messagesJson, createdAt)
        }.executeAsList()
    }

    suspend fun saveHistory(segment: ChatHistorySegment) = withContext(Dispatchers.Default) {
        db.chatArchivesQueries.insertHistorySegment(segment.id, segment.sessionId, segment.title, segment.reason, segment.messagesJson, segment.createdAt)
    }

    suspend fun deleteHistoryBySession(sessionId: String) = withContext(Dispatchers.Default) {
        db.chatArchivesQueries.deleteHistorySegmentsBySession(sessionId)
    }
}
