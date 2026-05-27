package com.example.rhodesterminal.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.rhodesterminal.data.db.entity.MomentCommentEntity
import com.example.rhodesterminal.data.db.entity.MomentEntity
import com.example.rhodesterminal.data.db.entity.MomentLikeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MomentDao {
    @Query("SELECT * FROM moments ORDER BY createdAt DESC")
    fun getAllMoments(): Flow<List<MomentEntity>>

    @Query("SELECT * FROM moments ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getMomentsPaged(limit: Int, offset: Int): List<MomentEntity>

    @Query("SELECT * FROM moments WHERE id = :id")
    suspend fun getMoment(id: Long): MomentEntity?

    @Query("SELECT * FROM moments WHERE operatorId = :operatorId ORDER BY createdAt DESC")
    fun getMomentsByOperator(operatorId: String): Flow<List<MomentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(moment: MomentEntity): Long

    @Query("UPDATE moments SET likeCount = :count WHERE id = :id")
    suspend fun updateLikeCount(id: Long, count: Int)

    @Query("UPDATE moments SET commentCount = :count WHERE id = :id")
    suspend fun updateCommentCount(id: Long, count: Int)

    @Query("DELETE FROM moments WHERE createdAt < :cutoff")
    suspend fun deleteOldMoments(cutoff: Long)

    @Query("SELECT * FROM moment_comments WHERE momentId = :momentId ORDER BY createdAt ASC")
    fun getComments(momentId: Long): Flow<List<MomentCommentEntity>>

    @Query("SELECT * FROM moment_comments WHERE createdAt > :cutoff AND (momentId IN (SELECT id FROM moments WHERE operatorId = 'user') OR replyToName = :userName) ORDER BY createdAt DESC")
    suspend fun getInboxComments(cutoff: Long, userName: String): List<MomentCommentEntity>

    @Query("SELECT COUNT(*) FROM moment_comments WHERE isRead = 0 AND createdAt > :cutoff AND (momentId IN (SELECT id FROM moments WHERE operatorId = 'user') OR replyToName = :userName)")
    suspend fun getUnreadCommentCount(cutoff: Long, userName: String): Int

    @Query("SELECT MAX(id) FROM moment_comments")
    suspend fun getMaxCommentId(): Long?

    @Query("UPDATE moment_comments SET isRead = 1 WHERE id = :id")
    suspend fun markCommentRead(id: Long)

    @Query("UPDATE moment_comments SET isRead = 1 WHERE id > 0 AND (momentId IN (SELECT id FROM moments WHERE operatorId = 'user') OR replyToName = :userName)")
    suspend fun markAllCommentsRead(userName: String)

    @Query("DELETE FROM moment_comments WHERE createdAt < :cutoff AND (replyToName = :userName OR momentId IN (SELECT id FROM moments WHERE operatorId = 'user'))")
    suspend fun deleteOldUserComments(cutoff: Long, userName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: MomentCommentEntity): Long

    @Query("SELECT * FROM moment_likes WHERE momentId = :momentId")
    suspend fun getLikes(momentId: Long): List<MomentLikeEntity>

    @Query("SELECT * FROM moment_likes WHERE momentId = :momentId")
    fun getLikesFlow(momentId: Long): Flow<List<MomentLikeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLike(like: MomentLikeEntity)

    @Query("DELETE FROM moment_likes WHERE momentId = :momentId AND operatorId = :operatorId")
    suspend fun deleteLike(momentId: Long, operatorId: String)

    @Query("SELECT COUNT(*) FROM moment_likes WHERE momentId = :momentId")
    suspend fun getLikeCount(momentId: Long): Int

    @Query("SELECT * FROM moment_likes WHERE momentId = :momentId AND operatorId = :operatorId LIMIT 1")
    suspend fun getLike(momentId: Long, operatorId: String): MomentLikeEntity?
}
