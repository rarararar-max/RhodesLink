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

public class MomentCommentsQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> getComments(momentId: Long, mapper: (
    id: Long,
    momentId: Long,
    operatorId: String,
    operatorName: String,
    content: String,
    parentCommentId: Long,
    replyToName: String,
    createdAt: Long,
    isRead: Long,
  ) -> T): Query<T> = GetCommentsQuery(momentId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getString(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!
    )
  }

  public fun getComments(momentId: Long): Query<Moment_comments> = getComments(momentId) { id,
      momentId_, operatorId, operatorName, content, parentCommentId, replyToName, createdAt,
      isRead ->
    Moment_comments(
      id,
      momentId_,
      operatorId,
      operatorName,
      content,
      parentCommentId,
      replyToName,
      createdAt,
      isRead
    )
  }

  public fun <T : Any> getInboxComments(
    createdAt: Long,
    replyToName: String,
    mapper: (
      id: Long,
      momentId: Long,
      operatorId: String,
      operatorName: String,
      content: String,
      parentCommentId: Long,
      replyToName: String,
      createdAt: Long,
      isRead: Long,
    ) -> T,
  ): Query<T> = GetInboxCommentsQuery(createdAt, replyToName) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getString(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!
    )
  }

  public fun getInboxComments(createdAt: Long, replyToName: String): Query<Moment_comments> =
      getInboxComments(createdAt, replyToName) { id, momentId, operatorId, operatorName, content,
      parentCommentId, replyToName_, createdAt_, isRead ->
    Moment_comments(
      id,
      momentId,
      operatorId,
      operatorName,
      content,
      parentCommentId,
      replyToName_,
      createdAt_,
      isRead
    )
  }

  public fun getUnreadCommentCount(createdAt: Long, replyToName: String): Query<Long> =
      GetUnreadCommentCountQuery(createdAt, replyToName) { cursor ->
    cursor.getLong(0)!!
  }

  public fun <T : Any> getMaxCommentId(mapper: (MAX: Long?) -> T): Query<T> = Query(-365_836_241,
      arrayOf("moment_comments"), driver, "MomentComments.sq", "getMaxCommentId",
      "SELECT MAX(id) FROM moment_comments") { cursor ->
    mapper(
      cursor.getLong(0)
    )
  }

  public fun getMaxCommentId(): Query<GetMaxCommentId> = getMaxCommentId { MAX ->
    GetMaxCommentId(
      MAX
    )
  }

  public fun getLastInsertRowId(): ExecutableQuery<Long> = Query(366_263_533, driver,
      "MomentComments.sq", "getLastInsertRowId", "SELECT last_insert_rowid()") { cursor ->
    cursor.getLong(0)!!
  }

  public fun insertComment(
    momentId: Long,
    operatorId: String,
    operatorName: String,
    content: String,
    parentCommentId: Long,
    replyToName: String,
    createdAt: Long,
    isRead: Long,
  ) {
    driver.execute(319_184_137, """
        |INSERT OR REPLACE INTO moment_comments(momentId, operatorId, operatorName, content, parentCommentId, replyToName, createdAt, isRead)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 8) {
          bindLong(0, momentId)
          bindString(1, operatorId)
          bindString(2, operatorName)
          bindString(3, content)
          bindLong(4, parentCommentId)
          bindString(5, replyToName)
          bindLong(6, createdAt)
          bindLong(7, isRead)
        }
    notifyQueries(319_184_137) { emit ->
      emit("moment_comments")
    }
  }

  public fun markCommentRead(id: Long) {
    driver.execute(1_560_924_779, """UPDATE moment_comments SET isRead = 1 WHERE id = ?""", 1) {
          bindLong(0, id)
        }
    notifyQueries(1_560_924_779) { emit ->
      emit("moment_comments")
    }
  }

  public fun markAllCommentsRead(replyToName: String) {
    driver.execute(1_018_656_961,
        """UPDATE moment_comments SET isRead = 1 WHERE id > 0 AND (momentId IN (SELECT id FROM moments WHERE operatorId = 'user') OR replyToName = ?)""",
        1) {
          bindString(0, replyToName)
        }
    notifyQueries(1_018_656_961) { emit ->
      emit("moment_comments")
    }
  }

  public fun deleteOldUserComments(createdAt: Long, replyToName: String) {
    driver.execute(-520_744_418,
        """DELETE FROM moment_comments WHERE createdAt < ? AND (replyToName = ? OR momentId IN (SELECT id FROM moments WHERE operatorId = 'user'))""",
        2) {
          bindLong(0, createdAt)
          bindString(1, replyToName)
        }
    notifyQueries(-520_744_418) { emit ->
      emit("moment_comments")
    }
  }

  private inner class GetCommentsQuery<out T : Any>(
    public val momentId: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("moment_comments", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("moment_comments", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(1_198_265_293,
        """SELECT moment_comments.id, moment_comments.momentId, moment_comments.operatorId, moment_comments.operatorName, moment_comments.content, moment_comments.parentCommentId, moment_comments.replyToName, moment_comments.createdAt, moment_comments.isRead FROM moment_comments WHERE momentId = ? ORDER BY createdAt ASC""",
        mapper, 1) {
      bindLong(0, momentId)
    }

    override fun toString(): String = "MomentComments.sq:getComments"
  }

  private inner class GetInboxCommentsQuery<out T : Any>(
    public val createdAt: Long,
    public val replyToName: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("moment_comments", "moments", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("moment_comments", "moments", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-640_808_863,
        """SELECT moment_comments.id, moment_comments.momentId, moment_comments.operatorId, moment_comments.operatorName, moment_comments.content, moment_comments.parentCommentId, moment_comments.replyToName, moment_comments.createdAt, moment_comments.isRead FROM moment_comments WHERE createdAt > ? AND (momentId IN (SELECT id FROM moments WHERE operatorId = 'user') OR replyToName = ?) ORDER BY createdAt DESC""",
        mapper, 2) {
      bindLong(0, createdAt)
      bindString(1, replyToName)
    }

    override fun toString(): String = "MomentComments.sq:getInboxComments"
  }

  private inner class GetUnreadCommentCountQuery<out T : Any>(
    public val createdAt: Long,
    public val replyToName: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("moment_comments", "moments", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("moment_comments", "moments", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(1_061_407_192,
        """SELECT COUNT(*) FROM moment_comments WHERE isRead = 0 AND createdAt > ? AND (momentId IN (SELECT id FROM moments WHERE operatorId = 'user') OR replyToName = ?)""",
        mapper, 2) {
      bindLong(0, createdAt)
      bindString(1, replyToName)
    }

    override fun toString(): String = "MomentComments.sq:getUnreadCommentCount"
  }
}
