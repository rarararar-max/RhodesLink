package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseDispatcher
import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.model.ReplyTurn
import kotlinx.coroutines.withContext

class ReplyTurnRepository(private val wrapper: DatabaseWrapper) {
    private val db get() = wrapper.database

    private fun map(
        id: String, sessionId: String, surface: String, triggerKind: String, sourceMessageId: Long?, autoPlanToken: String,
        mode: String, status: String, attemptCount: Long, nextAttemptAt: Long, leaseToken: String, leaseUntil: Long,
        responseMessageId: Long?, lastError: String, createdAt: Long, updatedAt: Long, completedAt: Long,
    ) = ReplyTurn(id, sessionId, surface, triggerKind, sourceMessageId, autoPlanToken, mode, status, attemptCount.toInt(), nextAttemptAt, leaseToken, leaseUntil, responseMessageId, lastError, createdAt, updatedAt, completedAt)

    suspend fun createIfAbsent(turn: ReplyTurn) = withContext(DatabaseDispatcher.dispatcher) {
        db.replyTurnsQueries.insertReplyTurnIfAbsent(turn.id, turn.sessionId, turn.surface, turn.triggerKind, turn.sourceMessageId, turn.autoPlanToken, turn.mode, turn.nextAttemptAt, turn.createdAt, turn.updatedAt)
        get(turn.id)
    }

    suspend fun get(id: String): ReplyTurn? = withContext(DatabaseDispatcher.dispatcher) {
        db.replyTurnsQueries.getReplyTurn(id, ::map).executeAsOneOrNull()
    }

    suspend fun getBySource(sessionId: String, messageId: Long): ReplyTurn? = withContext(DatabaseDispatcher.dispatcher) {
        db.replyTurnsQueries.getReplyTurnBySource(sessionId, messageId, ::map).executeAsOneOrNull()
    }

    suspend fun claim(id: String, token: String, now: Long, leaseUntil: Long): ReplyTurn? = withContext(DatabaseDispatcher.dispatcher) {
        db.replyTurnsQueries.claimReplyTurn(token, leaseUntil, now, id, now, now)
        get(id)?.takeIf { it.status == "running" && it.leaseToken == token }
    }

    suspend fun reserveResponseId(id: String, token: String, responseMessageId: Long, now: Long): ReplyTurn? = withContext(DatabaseDispatcher.dispatcher) {
        db.replyTurnsQueries.reserveReplyResponseId(responseMessageId, now, id, token)
        get(id)?.takeIf { it.leaseToken == token }
    }

    suspend fun complete(id: String, token: String, now: Long): Boolean = withContext(DatabaseDispatcher.dispatcher) {
        db.replyTurnsQueries.completeReplyTurn(now, now, id, token)
        get(id)?.status == "succeeded"
    }

    suspend fun isOwned(id: String, token: String): Boolean = withContext(DatabaseDispatcher.dispatcher) {
        db.replyTurnsQueries.isReplyTurnOwned(id, token).executeAsOne()
    }

    suspend fun release(id: String, token: String, retryAt: Long, now: Long, error: String) = withContext(DatabaseDispatcher.dispatcher) {
        db.replyTurnsQueries.releaseReplyTurn(retryAt, now, error.take(240), id, token)
    }

    suspend fun fail(id: String, token: String, now: Long, error: String) = withContext(DatabaseDispatcher.dispatcher) {
        db.replyTurnsQueries.failReplyTurn(now, error.take(240), id, token)
    }

    suspend fun deleteBySession(sessionId: String) = withContext(DatabaseDispatcher.dispatcher) {
        db.replyTurnsQueries.deleteReplyTurnsBySession(sessionId)
    }

    suspend fun deleteAll() = withContext(DatabaseDispatcher.dispatcher) { db.replyTurnsQueries.deleteAllReplyTurns() }
}
