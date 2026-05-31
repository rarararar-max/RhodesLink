package com.example.rhodesterminal.shared.data

import com.example.rhodesterminal.shared.db.DatabaseWrapper
import com.example.rhodesterminal.shared.db.RhodesDatabase
import com.example.rhodesterminal.shared.model.*
import kotlinx.coroutines.Dispatchers
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MomentRepository(private val wrapper: DatabaseWrapper) {

    private val db: RhodesDatabase get() = wrapper.database

    // --- Moments ---
    suspend fun insertMoment(moment: Moment): Long = withContext(Dispatchers.Default) {
        db.momentsQueries.insertMoment(moment.operatorId, moment.operatorName, moment.content, if (moment.isUserPost) 1L else 0L, moment.mentionedOperatorIds, moment.likeCount.toLong(), moment.commentCount.toLong(), moment.createdAt)
        db.momentsQueries.getLastInsertRowId().executeAsOne()
    }

    fun getAllMoments(): Flow<List<Moment>> =
        db.momentsQueries.getAllMoments { id, opId, opName, content, isUserPost, mentionedIds, likeCount, commentCount, createdAt ->
            Moment(id, opId, opName, content, isUserPost != 0L, mentionedIds, likeCount.toInt(), commentCount.toInt(), createdAt)
        }.asFlow().mapToList(Dispatchers.Default)

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

    suspend fun insertComment(comment: MomentComment): Long = withContext(Dispatchers.Default) {
        db.momentCommentsQueries.insertComment(comment.momentId, comment.operatorId, comment.operatorName, comment.content, comment.parentCommentId, comment.replyToName, comment.createdAt, if (comment.isRead) 1L else 0L)
        db.momentCommentsQueries.getLastInsertRowId().executeAsOne()
    }

    suspend fun getMaxCommentId(): Long? = withContext(Dispatchers.Default) {
        db.momentCommentsQueries.getMaxCommentId().executeAsOne().MAX
    }

    suspend fun markCommentRead(id: Long) = withContext(Dispatchers.Default) { db.momentCommentsQueries.markCommentRead(id) }
    suspend fun markAllCommentsRead(userName: String) = withContext(Dispatchers.Default) { db.momentCommentsQueries.markAllCommentsRead(userName) }
    suspend fun deleteOldUserComments(cutoff: Long, userName: String) = withContext(Dispatchers.Default) { db.momentCommentsQueries.deleteOldUserComments(cutoff, userName) }
    suspend fun updateLikeCount(momentId: Long, count: Int) = withContext(Dispatchers.Default) { db.momentsQueries.updateLikeCount(count.toLong(), momentId) }
    suspend fun updateCommentCount(momentId: Long, count: Int) = withContext(Dispatchers.Default) { db.momentsQueries.updateCommentCount(count.toLong(), momentId) }
    suspend fun getLikeCount(momentId: Long): Int = withContext(Dispatchers.Default) { db.momentLikesQueries.getLikeCount(momentId).executeAsOne().toInt() }

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

    suspend fun deleteLike(momentId: Long, operatorId: String) = withContext(Dispatchers.Default) { db.momentLikesQueries.deleteLike(momentId, operatorId) }

    suspend fun getMoment(id: Long): Moment? = withContext(Dispatchers.Default) {
        db.momentsQueries.getMoment(id) { id_, opId, opName, content, isUserPost, mentionedIds, likeCount, commentCount, createdAt ->
            Moment(id_, opId, opName, content, isUserPost != 0L, mentionedIds, likeCount.toInt(), commentCount.toInt(), createdAt)
        }.executeAsOneOrNull()
    }

    suspend fun deleteOldMoments(cutoff: Long) = withContext(Dispatchers.Default) { db.momentsQueries.deleteOldMoments(cutoff) }
}
