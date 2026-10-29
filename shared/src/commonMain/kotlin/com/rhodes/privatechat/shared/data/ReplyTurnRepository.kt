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

    /** Deletes the turn that belongs to a single source message (ids are globally unique). */
    suspend fun deleteBySourceMessageId(messageId: Long) = withContext(DatabaseDispatcher.dispatcher) {
        db.replyTurnsQueries.deleteReplyTurnsBySourceMessageId(messageId)
    }

    /**
     * Drops only *succeeded* turns whose source message no longer exists. These are orphans left behind
     * by recall / archive-load / erase paths and they can block a later message that reuses the same id.
     * Pending or running turns are never touched here: a recovery worker may still own them.
     */
    suspend fun cleanupOrphanedSucceededTurns(): Long = withContext(DatabaseDispatcher.dispatcher) {
        val orphanCount = db.replyTurnsQueries.countOrphanedSucceededReplyTurns().executeAsOne()
        if (orphanCount > 0L) db.replyTurnsQueries.deleteOrphanedSucceededReplyTurns()
        orphanCount
    }

    suspend fun momentTurns(momentId: Long): List<ReplyTurn> = withContext(DatabaseDispatcher.dispatcher) {
        db.replyTurnsQueries.getMomentReplyTurns("moment:$momentId", ::map).executeAsList()
    }

    suspend fun resetFailedMomentTurn(id: String, now: Long) = withContext(DatabaseDispatcher.dispatcher) {
        db.replyTurnsQueries.resetFailedMomentReplyTurn(now, now, id)
    }

    suspend fun pendingMomentTurnIds(): List<String> = withContext(DatabaseDispatcher.dispatcher) {
        db.replyTurnsQueries.selectPendingMomentReplyTurns().executeAsList()
    }

    suspend fun deleteAll() = withContext(DatabaseDispatcher.dispatcher) { db.replyTurnsQueries.deleteAllReplyTurns() }

    /**
     * Terminal state for a turn that must never produce a reply: its user message was folded into a
     * merged batch owned by another turn, or the send was superseded. Without this the turn stayed
     * `running`, the lease expired, and the recovery worker answered the same message a second time.
     */
    suspend fun cancel(id: String, error: String, now: Long = System.currentTimeMillis()) =
        withContext(DatabaseDispatcher.dispatcher) {
            db.replyTurnsQueries.cancelReplyTurn(now, error, id)
        }

    suspend fun cancelBySourceMessage(sessionId: String, messageId: Long, error: String = "source_message_recalled") = withContext(DatabaseDispatcher.dispatcher) {
        db.replyTurnsQueries.cancelReplyTurnBySource(System.currentTimeMillis(), error.take(240), sessionId, messageId)
    }

    /**
     * Ids of recent manual turns that still need a reply but have no worker left to drive them: due
     * pending/failed turns and running turns whose lease has expired. Startup re-arms these, which is
     * what makes "消息已保存，回复稍后自动补上" true even after the process was killed.
     */
    suspend fun retryableTurnIds(since: Long, now: Long, limit: Long = 50L): List<String> =
        withContext(DatabaseDispatcher.dispatcher) {
            db.replyTurnsQueries.selectRetryableReplyTurns(since, now, limit).executeAsList()
        }
}
