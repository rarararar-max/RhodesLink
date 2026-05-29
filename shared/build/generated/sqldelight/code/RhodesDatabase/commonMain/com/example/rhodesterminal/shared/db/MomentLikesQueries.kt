package com.example.rhodesterminal.shared.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Long
import kotlin.String

public class MomentLikesQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> getLikes(momentId: Long, mapper: (
    id: Long,
    momentId: Long,
    operatorId: String,
    operatorName: String,
    createdAt: Long,
  ) -> T): Query<T> = GetLikesQuery(momentId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4)!!
    )
  }

  public fun getLikes(momentId: Long): Query<Moment_likes> = getLikes(momentId) { id, momentId_,
      operatorId, operatorName, createdAt ->
    Moment_likes(
      id,
      momentId_,
      operatorId,
      operatorName,
      createdAt
    )
  }

  public fun <T : Any> getLikesFlow(momentId: Long, mapper: (
    id: Long,
    momentId: Long,
    operatorId: String,
    operatorName: String,
    createdAt: Long,
  ) -> T): Query<T> = GetLikesFlowQuery(momentId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4)!!
    )
  }

  public fun getLikesFlow(momentId: Long): Query<Moment_likes> = getLikesFlow(momentId) { id,
      momentId_, operatorId, operatorName, createdAt ->
    Moment_likes(
      id,
      momentId_,
      operatorId,
      operatorName,
      createdAt
    )
  }

  public fun getLikeCount(momentId: Long): Query<Long> = GetLikeCountQuery(momentId) { cursor ->
    cursor.getLong(0)!!
  }

  public fun <T : Any> getLike(
    momentId: Long,
    operatorId: String,
    mapper: (
      id: Long,
      momentId: Long,
      operatorId: String,
      operatorName: String,
      createdAt: Long,
    ) -> T,
  ): Query<T> = GetLikeQuery(momentId, operatorId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getLong(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4)!!
    )
  }

  public fun getLike(momentId: Long, operatorId: String): Query<Moment_likes> = getLike(momentId,
      operatorId) { id, momentId_, operatorId_, operatorName, createdAt ->
    Moment_likes(
      id,
      momentId_,
      operatorId_,
      operatorName,
      createdAt
    )
  }

  public fun insertLike(
    momentId: Long,
    operatorId: String,
    operatorName: String,
    createdAt: Long,
  ) {
    driver.execute(-98_666_337, """
        |INSERT OR REPLACE INTO moment_likes(momentId, operatorId, operatorName, createdAt)
        |VALUES (?, ?, ?, ?)
        """.trimMargin(), 4) {
          bindLong(0, momentId)
          bindString(1, operatorId)
          bindString(2, operatorName)
          bindLong(3, createdAt)
        }
    notifyQueries(-98_666_337) { emit ->
      emit("moment_likes")
    }
  }

  public fun deleteLike(momentId: Long, operatorId: String) {
    driver.execute(699_757_201,
        """DELETE FROM moment_likes WHERE momentId = ? AND operatorId = ?""", 2) {
          bindLong(0, momentId)
          bindString(1, operatorId)
        }
    notifyQueries(699_757_201) { emit ->
      emit("moment_likes")
    }
  }

  private inner class GetLikesQuery<out T : Any>(
    public val momentId: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("moment_likes", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("moment_likes", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_689_231_243,
        """SELECT moment_likes.id, moment_likes.momentId, moment_likes.operatorId, moment_likes.operatorName, moment_likes.createdAt FROM moment_likes WHERE momentId = ?""",
        mapper, 1) {
      bindLong(0, momentId)
    }

    override fun toString(): String = "MomentLikes.sq:getLikes"
  }

  private inner class GetLikesFlowQuery<out T : Any>(
    public val momentId: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("moment_likes", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("moment_likes", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_028_484_285,
        """SELECT moment_likes.id, moment_likes.momentId, moment_likes.operatorId, moment_likes.operatorName, moment_likes.createdAt FROM moment_likes WHERE momentId = ?""",
        mapper, 1) {
      bindLong(0, momentId)
    }

    override fun toString(): String = "MomentLikes.sq:getLikesFlow"
  }

  private inner class GetLikeCountQuery<out T : Any>(
    public val momentId: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("moment_likes", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("moment_likes", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_071_583_247,
        """SELECT COUNT(*) FROM moment_likes WHERE momentId = ?""", mapper, 1) {
      bindLong(0, momentId)
    }

    override fun toString(): String = "MomentLikes.sq:getLikeCount"
  }

  private inner class GetLikeQuery<out T : Any>(
    public val momentId: Long,
    public val operatorId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("moment_likes", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("moment_likes", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(84_055_998,
        """SELECT moment_likes.id, moment_likes.momentId, moment_likes.operatorId, moment_likes.operatorName, moment_likes.createdAt FROM moment_likes WHERE momentId = ? AND operatorId = ? LIMIT 1""",
        mapper, 2) {
      bindLong(0, momentId)
      bindString(1, operatorId)
    }

    override fun toString(): String = "MomentLikes.sq:getLike"
  }
}
