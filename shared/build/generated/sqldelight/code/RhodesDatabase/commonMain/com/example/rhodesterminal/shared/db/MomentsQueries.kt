package com.example.rhodesterminal.shared.db

import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Long
import kotlin.String

public class MomentsQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> getAllMoments(mapper: (
    id: Long,
    operatorId: String,
    operatorName: String,
    content: String,
    isUserPost: Long,
    mentionedOperatorIds: String,
    likeCount: Long,
    commentCount: Long,
    createdAt: Long,
  ) -> T): Query<T> = Query(667_244_034, arrayOf("moments"), driver, "Moments.sq", "getAllMoments",
      "SELECT moments.id, moments.operatorId, moments.operatorName, moments.content, moments.isUserPost, moments.mentionedOperatorIds, moments.likeCount, moments.commentCount, moments.createdAt FROM moments ORDER BY createdAt DESC") {
      cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4)!!,
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!
    )
  }

  public fun getAllMoments(): Query<Moments> = getAllMoments { id, operatorId, operatorName,
      content, isUserPost, mentionedOperatorIds, likeCount, commentCount, createdAt ->
    Moments(
      id,
      operatorId,
      operatorName,
      content,
      isUserPost,
      mentionedOperatorIds,
      likeCount,
      commentCount,
      createdAt
    )
  }

  public fun <T : Any> getMomentsPaged(
    `value`: Long,
    value_: Long,
    mapper: (
      id: Long,
      operatorId: String,
      operatorName: String,
      content: String,
      isUserPost: Long,
      mentionedOperatorIds: String,
      likeCount: Long,
      commentCount: Long,
      createdAt: Long,
    ) -> T,
  ): Query<T> = GetMomentsPagedQuery(value, value_) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4)!!,
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!
    )
  }

  public fun getMomentsPaged(value_: Long, value__: Long): Query<Moments> = getMomentsPaged(value_,
      value__) { id, operatorId, operatorName, content, isUserPost, mentionedOperatorIds, likeCount,
      commentCount, createdAt ->
    Moments(
      id,
      operatorId,
      operatorName,
      content,
      isUserPost,
      mentionedOperatorIds,
      likeCount,
      commentCount,
      createdAt
    )
  }

  public fun <T : Any> getMoment(id: Long, mapper: (
    id: Long,
    operatorId: String,
    operatorName: String,
    content: String,
    isUserPost: Long,
    mentionedOperatorIds: String,
    likeCount: Long,
    commentCount: Long,
    createdAt: Long,
  ) -> T): Query<T> = GetMomentQuery(id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4)!!,
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!
    )
  }

  public fun getMoment(id: Long): Query<Moments> = getMoment(id) { id_, operatorId, operatorName,
      content, isUserPost, mentionedOperatorIds, likeCount, commentCount, createdAt ->
    Moments(
      id_,
      operatorId,
      operatorName,
      content,
      isUserPost,
      mentionedOperatorIds,
      likeCount,
      commentCount,
      createdAt
    )
  }

  public fun <T : Any> getMomentsByOperator(operatorId: String, mapper: (
    id: Long,
    operatorId: String,
    operatorName: String,
    content: String,
    isUserPost: Long,
    mentionedOperatorIds: String,
    likeCount: Long,
    commentCount: Long,
    createdAt: Long,
  ) -> T): Query<T> = GetMomentsByOperatorQuery(operatorId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4)!!,
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!
    )
  }

  public fun getMomentsByOperator(operatorId: String): Query<Moments> =
      getMomentsByOperator(operatorId) { id, operatorId_, operatorName, content, isUserPost,
      mentionedOperatorIds, likeCount, commentCount, createdAt ->
    Moments(
      id,
      operatorId_,
      operatorName,
      content,
      isUserPost,
      mentionedOperatorIds,
      likeCount,
      commentCount,
      createdAt
    )
  }

  public fun getLastInsertRowId(): ExecutableQuery<Long> = Query(1_960_832_630, driver,
      "Moments.sq", "getLastInsertRowId", "SELECT last_insert_rowid()") { cursor ->
    cursor.getLong(0)!!
  }

  public fun insertMoment(
    operatorId: String,
    operatorName: String,
    content: String,
    isUserPost: Long,
    mentionedOperatorIds: String,
    likeCount: Long,
    commentCount: Long,
    createdAt: Long,
  ) {
    driver.execute(801_038_943, """
        |INSERT OR REPLACE INTO moments(operatorId, operatorName, content, isUserPost, mentionedOperatorIds, likeCount, commentCount, createdAt)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 8) {
          bindString(0, operatorId)
          bindString(1, operatorName)
          bindString(2, content)
          bindLong(3, isUserPost)
          bindString(4, mentionedOperatorIds)
          bindLong(5, likeCount)
          bindLong(6, commentCount)
          bindLong(7, createdAt)
        }
    notifyQueries(801_038_943) { emit ->
      emit("moments")
    }
  }

  public fun updateLikeCount(likeCount: Long, id: Long) {
    driver.execute(-2_118_188_311, """UPDATE moments SET likeCount = ? WHERE id = ?""", 2) {
          bindLong(0, likeCount)
          bindLong(1, id)
        }
    notifyQueries(-2_118_188_311) { emit ->
      emit("moments")
    }
  }

  public fun updateCommentCount(commentCount: Long, id: Long) {
    driver.execute(150_327_583, """UPDATE moments SET commentCount = ? WHERE id = ?""", 2) {
          bindLong(0, commentCount)
          bindLong(1, id)
        }
    notifyQueries(150_327_583) { emit ->
      emit("moments")
    }
  }

  public fun deleteOldMoments(createdAt: Long) {
    driver.execute(-1_076_096_099, """DELETE FROM moments WHERE createdAt < ?""", 1) {
          bindLong(0, createdAt)
        }
    notifyQueries(-1_076_096_099) { emit ->
      emit("moments")
    }
  }

  private inner class GetMomentsPagedQuery<out T : Any>(
    public val `value`: Long,
    public val value_: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("moments", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("moments", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(1_646_024_050,
        """SELECT moments.id, moments.operatorId, moments.operatorName, moments.content, moments.isUserPost, moments.mentionedOperatorIds, moments.likeCount, moments.commentCount, moments.createdAt FROM moments ORDER BY createdAt DESC LIMIT ? OFFSET ?""",
        mapper, 2) {
      bindLong(0, value)
      bindLong(1, value_)
    }

    override fun toString(): String = "Moments.sq:getMomentsPaged"
  }

  private inner class GetMomentQuery<out T : Any>(
    public val id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("moments", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("moments", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_054_107_024,
        """SELECT moments.id, moments.operatorId, moments.operatorName, moments.content, moments.isUserPost, moments.mentionedOperatorIds, moments.likeCount, moments.commentCount, moments.createdAt FROM moments WHERE id = ?""",
        mapper, 1) {
      bindLong(0, id)
    }

    override fun toString(): String = "Moments.sq:getMoment"
  }

  private inner class GetMomentsByOperatorQuery<out T : Any>(
    public val operatorId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("moments", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("moments", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(2_033_252_990,
        """SELECT moments.id, moments.operatorId, moments.operatorName, moments.content, moments.isUserPost, moments.mentionedOperatorIds, moments.likeCount, moments.commentCount, moments.createdAt FROM moments WHERE operatorId = ? ORDER BY createdAt DESC""",
        mapper, 1) {
      bindString(0, operatorId)
    }

    override fun toString(): String = "Moments.sq:getMomentsByOperator"
  }
}
