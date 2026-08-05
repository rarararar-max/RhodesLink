package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.*
import kotlinx.coroutines.Dispatchers
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MomentRepository(private val wrapper: DatabaseWrapper) {
    companion object {
        // last_insert_rowid() is connection-scoped; serialize its insert/read pair across repositories.
        private val insertIdMutex = Mutex()
    }

    private val db: RhodesDatabase get() = wrapper.database

    // --- Moments ---
    suspend fun insertMoment(moment: Moment): Long = insertIdMutex.withLock { withContext(Dispatchers.Default) {
        db.transactionWithResult {
            db.momentsQueries.insertMoment(moment.operatorId, moment.operatorName, moment.content, if (moment.isUserPost) 1L else 0L, moment.mentionedOperatorIds, moment.likeCount.toLong(), moment.commentCount.toLong(), moment.createdAt)
            db.momentsQueries.getLastInsertRowId().executeAsOne()
        }
    }
    }

    /** Restores social records with newly assigned local IDs while preserving their relationships. */
    suspend fun restoreSocialBackup(
        moments: List<Moment>,
        likes: List<MomentLike>,
        comments: List<MomentComment>
    ) = insertIdMutex.withLock { withContext(Dispatchers.Default) {
        db.transaction {
            val momentIds = mutableMapOf<Long, Long>()
            moments.forEach { moment ->
                db.momentsQueries.insertMoment(
                    moment.operatorId,
                    moment.operatorName,
                    moment.content,
                    if (moment.isUserPost) 1L else 0L,
                    moment.mentionedOperatorIds,
                    moment.likeCount.toLong(),
                    moment.commentCount.toLong(),
                    moment.createdAt
                )
                momentIds[moment.id] = db.momentsQueries.getLastInsertRowId().executeAsOne()
            }

            val commentIds = mutableMapOf<Long, Long>()
            val pending = comments.sortedWith(compareBy<MomentComment> { it.createdAt }.thenBy { it.id }).toMutableList()
            while (pending.isNotEmpty()) {
                val nextIndex = pending.indexOfFirst { it.parentCommentId == 0L || it.parentCommentId in commentIds }
                if (nextIndex < 0) break
                val comment = pending.removeAt(nextIndex)
                val momentId = momentIds[comment.momentId] ?: continue
                val parentId = if (comment.parentCommentId == 0L) 0L else commentIds.getValue(comment.parentCommentId)
                db.momentCommentsQueries.insertComment(
                    momentId,
                    comment.operatorId,
                    comment.operatorName,
                    comment.content,
                    parentId,
                    comment.replyToName,
                    comment.createdAt,
                    if (comment.isRead) 1L else 0L
                )
                commentIds[comment.id] = db.momentCommentsQueries.getLastInsertRowId().executeAsOne()
            }

            // Preserve independently useful comments if a partial backup omitted their parent.
            pending.forEach { comment ->
                val momentId = momentIds[comment.momentId] ?: return@forEach
                db.momentCommentsQueries.insertComment(
                    momentId,
                    comment.operatorId,
                    comment.operatorName,
                    comment.content,
                    0L,
                    comment.replyToName,
                    comment.createdAt,
                    if (comment.isRead) 1L else 0L
                )
            }

            likes.forEach { like ->
                val momentId = momentIds[like.momentId] ?: return@forEach
                db.momentLikesQueries.insertLike(momentId, like.operatorId, like.operatorName, like.createdAt)
            }
            db.momentsQueries.backfillLikeCounts()
            db.momentsQueries.backfillCommentCounts()
        }
    }
    }

    fun getAllMoments(): Flow<List<Moment>> =
        db.momentsQueries.getAllMoments { id, opId, opName, content, isUserPost, mentionedIds, likeCount, commentCount, createdAt ->
            Moment(id, opId, opName, content, isUserPost != 0L, mentionedIds, likeCount.toInt(), commentCount.toInt(), createdAt)
        }.asFlow().mapToList(Dispatchers.Default)

    suspend fun getAllMomentsSync(): List<Moment> = withContext(Dispatchers.Default) {
        db.momentsQueries.getAllMoments { id, opId, opName, content, isUserPost, mentionedIds, likeCount, commentCount, createdAt ->
            Moment(id, opId, opName, content, isUserPost != 0L, mentionedIds, likeCount.toInt(), commentCount.toInt(), createdAt)
        }.executeAsList()
    }

    suspend fun getAllLikesForBackup(): List<MomentLike> = withContext(Dispatchers.Default) {
        db.momentLikesQueries.getAllLikesForBackup { id, mId, opId, opName, createdAt ->
            MomentLike(id, mId, opId, opName, createdAt)
        }.executeAsList()
    }

    suspend fun getAllCommentsForBackup(): List<MomentComment> = withContext(Dispatchers.Default) {
        db.momentCommentsQueries.getAllCommentsForBackup { id, mId, opId, opName, content, parentCommentId, replyToName, createdAt, isRead ->
            MomentComment(id, mId, opId, opName, content, parentCommentId, replyToName, createdAt, isRead != 0L)
        }.executeAsList()
    }

    fun getLikesFlow(momentId: Long): Flow<List<MomentLike>> =
        db.momentLikesQueries.getLikesFlow(momentId) { id, mId, opId, opName, createdAt ->
            MomentLike(id, mId, opId, opName, createdAt)
        }.asFlow().mapToList(Dispatchers.Default)

    fun getComments(momentId: Long): Flow<List<MomentComment>> =
        db.momentCommentsQueries.getComments(momentId) { id, mId, opId, opName, content, parentCommentId, replyToName, createdAt, isRead ->
            MomentComment(id, mId, opId, opName, content, parentCommentId, replyToName, createdAt, isRead != 0L)
        }.asFlow().mapToList(Dispatchers.Default)

    suspend fun insertLike(like: MomentLike) = withContext(Dispatchers.Default) {
        db.momentLikesQueries.insertLike(like.momentId, like.operatorId, like.operatorName, like.createdAt)
    }

    /** Returns false when the moment disappeared before this asynchronous interaction could persist. */
    suspend fun insertLikeIfMomentExists(like: MomentLike): Boolean = withContext(Dispatchers.Default) {
        db.transactionWithResult {
            if (db.momentsQueries.getMoment(like.momentId) { id, opId, opName, content, isUserPost, mentionedIds, likeCount, commentCount, createdAt -> id }.executeAsOneOrNull() == null) {
                false
            } else {
                db.momentLikesQueries.insertLike(like.momentId, like.operatorId, like.operatorName, like.createdAt)
                true
            }
        }
    }

    suspend fun insertComment(comment: MomentComment): Long = insertIdMutex.withLock { withContext(Dispatchers.Default) {
        db.transactionWithResult {
            db.momentCommentsQueries.insertComment(comment.momentId, comment.operatorId, comment.operatorName, comment.content, comment.parentCommentId, comment.replyToName, comment.createdAt, if (comment.isRead) 1L else 0L)
            db.momentCommentsQueries.getLastInsertRowId().executeAsOne()
        }
    }
    }

    /** Atomically rejects delayed comments whose target moment has already expired or been deleted. */
    suspend fun insertCommentIfMomentExists(comment: MomentComment): Long? = insertIdMutex.withLock { withContext(Dispatchers.Default) {
        db.transactionWithResult {
            if (db.momentsQueries.getMoment(comment.momentId) { id, opId, opName, content, isUserPost, mentionedIds, likeCount, commentCount, createdAt -> id }.executeAsOneOrNull() == null) {
                null
            } else {
                db.momentCommentsQueries.insertComment(comment.momentId, comment.operatorId, comment.operatorName, comment.content, comment.parentCommentId, comment.replyToName, comment.createdAt, if (comment.isRead) 1L else 0L)
                db.momentCommentsQueries.getLastInsertRowId().executeAsOne()
            }
        }
    }
    }

    suspend fun getMaxCommentId(): Long? = withContext(Dispatchers.Default) {
        db.momentCommentsQueries.getMaxCommentId().executeAsOne().MAX
    }

    suspend fun getCommentById(commentId: Long): MomentComment? = withContext(Dispatchers.Default) {
        db.momentCommentsQueries.getCommentById(commentId) { id, mId, opId, opName, content, parentCommentId, replyToName, createdAt, isRead ->
            MomentComment(id, mId, opId, opName, content, parentCommentId, replyToName, createdAt, isRead != 0L)
        }.executeAsOneOrNull()
    }

    suspend fun markCommentRead(id: Long) = withContext(Dispatchers.Default) { db.momentCommentsQueries.markCommentRead(id) }
    suspend fun markMomentCommentsReadForUser(momentId: Long, userName: String) = withContext(Dispatchers.Default) { db.momentCommentsQueries.markMomentCommentsReadForUser(momentId, userName) }
    suspend fun markAllCommentsRead(userName: String) = withContext(Dispatchers.Default) { db.momentCommentsQueries.markAllCommentsRead(userName) }
    suspend fun deleteOldUserComments(cutoff: Long, userName: String) = withContext(Dispatchers.Default) { db.momentCommentsQueries.deleteOldUserComments(cutoff, userName) }
    suspend fun updateLikeCount(momentId: Long, count: Int) = withContext(Dispatchers.Default) { db.momentsQueries.updateLikeCount(count.toLong(), momentId) }
    suspend fun updateCommentCount(momentId: Long, count: Int) = withContext(Dispatchers.Default) { db.momentsQueries.updateCommentCount(count.toLong(), momentId) }
    suspend fun getCommentCount(momentId: Long): Int = withContext(Dispatchers.Default) { db.momentCommentsQueries.getCommentCount(momentId).executeAsOne().toInt() }
    suspend fun getLikeCount(momentId: Long): Int = withContext(Dispatchers.Default) { db.momentLikesQueries.getLikeCount(momentId).executeAsOne().toInt() }
    suspend fun backfillLikeCounts() = withContext(Dispatchers.Default) { db.momentsQueries.backfillLikeCounts() }
    suspend fun backfillCommentCounts() = withContext(Dispatchers.Default) { db.momentsQueries.backfillCommentCounts() }

    suspend fun getLike(momentId: Long, operatorId: String): MomentLike? = withContext(Dispatchers.Default) {
        db.momentLikesQueries.getLike(momentId, operatorId) { id, mId, opId, opName, createdAt ->
            MomentLike(id, mId, opId, opName, createdAt)
        }.executeAsOneOrNull()
    }

    suspend fun getMomentsPaged(limit: Int, offset: Int): List<Moment> = withContext(Dispatchers.Default) {
        db.momentsQueries.getMomentsPaged(limit.toLong(), offset.toLong()) { id, opId, opName, content, isUserPost, mentionedIds, likeCount, commentCount, createdAt ->
            Moment(id, opId, opName, content, isUserPost != 0L, mentionedIds, likeCount.toInt(), commentCount.toInt(), createdAt)
        }.executeAsList()
    }

    suspend fun getInboxComments(cutoff: Long, userName: String): List<MomentComment> = withContext(Dispatchers.Default) {
        db.momentCommentsQueries.getInboxComments(cutoff, userName) { id, mId, opId, opName, content, parentCommentId, replyToName, createdAt, isRead ->
            MomentComment(id, mId, opId, opName, content, parentCommentId, replyToName, createdAt, isRead != 0L)
        }.executeAsList()
    }

    suspend fun getUnreadCommentCount(cutoff: Long, userName: String): Int = withContext(Dispatchers.Default) {
        db.momentCommentsQueries.getUnreadCommentCount(cutoff, userName).executeAsOne().toInt()
    }

    suspend fun getMomentsByOperator(operatorId: String): List<Moment> = withContext(Dispatchers.Default) {
        db.momentsQueries.getMomentsByOperator(operatorId) { id, opId, opName, content, isUserPost, mentionedIds, likeCount, commentCount, createdAt ->
            Moment(id, opId, opName, content, isUserPost != 0L, mentionedIds, likeCount.toInt(), commentCount.toInt(), createdAt)
        }.executeAsList()
    }

    suspend fun getMomentsBefore(createdAt: Long, id: Long, limit: Int): List<Moment> = withContext(Dispatchers.Default) {
        db.momentsQueries.getMomentsBefore(createdAt, createdAt, id, limit.toLong()) { momentId, opId, opName, content, isUserPost, mentionedIds, likeCount, commentCount, timestamp ->
            Moment(momentId, opId, opName, content, isUserPost != 0L, mentionedIds, likeCount.toInt(), commentCount.toInt(), timestamp)
        }.executeAsList()
    }

    suspend fun countMomentsByOperatorSince(operatorId: String, since: Long): Int = withContext(Dispatchers.Default) {
        db.momentsQueries.countMomentsByOperatorSince(operatorId, since).executeAsOne().toInt()
    }

    suspend fun deleteLike(momentId: Long, operatorId: String) = withContext(Dispatchers.Default) { db.momentLikesQueries.deleteLike(momentId, operatorId) }

    suspend fun getMoment(id: Long): Moment? = withContext(Dispatchers.Default) {
        db.momentsQueries.getMoment(id) { id_, opId, opName, content, isUserPost, mentionedIds, likeCount, commentCount, createdAt ->
            Moment(id_, opId, opName, content, isUserPost != 0L, mentionedIds, likeCount.toInt(), commentCount.toInt(), createdAt)
        }.executeAsOneOrNull()
    }

    suspend fun getMaxMomentId(): Long = withContext(Dispatchers.Default) {
        db.momentsQueries.getMaxMomentId().executeAsOne().MAX ?: 0L
    }

    suspend fun deleteOldMoments(cutoff: Long) = withContext(Dispatchers.Default) { db.momentsQueries.deleteOldMoments(cutoff) }

    suspend fun deleteMomentsByOperator(operatorId: String) = withContext(Dispatchers.Default) {
        db.transaction {
            val momentIds = db.momentsQueries.getMomentsByOperator(operatorId) { id, _, _, _, _, _, _, _, _ -> id }.executeAsList()
            momentIds.forEach {
                db.momentLikesQueries.deleteLikesByMoment(it)
                db.momentCommentsQueries.deleteCommentsByMoment(it)
            }
            db.momentLikesQueries.deleteLikesByOperator(operatorId)
            db.momentCommentsQueries.deleteCommentsByOperator(operatorId)
            db.momentsQueries.deleteMomentsByOperator(operatorId)
        }
    }
}
