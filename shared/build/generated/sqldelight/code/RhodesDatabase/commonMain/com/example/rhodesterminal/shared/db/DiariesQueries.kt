package com.example.rhodesterminal.shared.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Long
import kotlin.String

public class DiariesQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> getDiariesByOperator(operatorId: String, mapper: (
    id: Long,
    operatorId: String,
    operatorName: String,
    content: String,
    date: String,
    createdAt: Long,
  ) -> T): Query<T> = GetDiariesByOperatorQuery(operatorId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!
    )
  }

  public fun getDiariesByOperator(operatorId: String): Query<Diaries> =
      getDiariesByOperator(operatorId) { id, operatorId_, operatorName, content, date, createdAt ->
    Diaries(
      id,
      operatorId_,
      operatorName,
      content,
      date,
      createdAt
    )
  }

  public fun <T : Any> getDiary(
    operatorId: String,
    date: String,
    mapper: (
      id: Long,
      operatorId: String,
      operatorName: String,
      content: String,
      date: String,
      createdAt: Long,
    ) -> T,
  ): Query<T> = GetDiaryQuery(operatorId, date) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!
    )
  }

  public fun getDiary(operatorId: String, date: String): Query<Diaries> = getDiary(operatorId,
      date) { id, operatorId_, operatorName, content, date_, createdAt ->
    Diaries(
      id,
      operatorId_,
      operatorName,
      content,
      date_,
      createdAt
    )
  }

  public fun getDiaryDates(operatorId: String): Query<String> = GetDiaryDatesQuery(operatorId) {
      cursor ->
    cursor.getString(0)!!
  }

  public fun getCount(): Query<Long> = Query(-563_108_147, arrayOf("diaries"), driver, "Diaries.sq",
      "getCount", "SELECT COUNT(*) FROM diaries") { cursor ->
    cursor.getLong(0)!!
  }

  public fun insertDiary(
    operatorId: String,
    operatorName: String,
    content: String,
    date: String,
    createdAt: Long,
  ) {
    driver.execute(-1_246_746_474, """
        |INSERT OR REPLACE INTO diaries(operatorId, operatorName, content, date, createdAt)
        |VALUES (?, ?, ?, ?, ?)
        """.trimMargin(), 5) {
          bindString(0, operatorId)
          bindString(1, operatorName)
          bindString(2, content)
          bindString(3, date)
          bindLong(4, createdAt)
        }
    notifyQueries(-1_246_746_474) { emit ->
      emit("diaries")
    }
  }

  public fun deleteOldDiaries(createdAt: Long) {
    driver.execute(250_925_817, """DELETE FROM diaries WHERE createdAt < ?""", 1) {
          bindLong(0, createdAt)
        }
    notifyQueries(250_925_817) { emit ->
      emit("diaries")
    }
  }

  private inner class GetDiariesByOperatorQuery<out T : Any>(
    public val operatorId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("diaries", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("diaries", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-803_090_086,
        """SELECT diaries.id, diaries.operatorId, diaries.operatorName, diaries.content, diaries.date, diaries.createdAt FROM diaries WHERE operatorId = ? ORDER BY date DESC""",
        mapper, 1) {
      bindString(0, operatorId)
    }

    override fun toString(): String = "Diaries.sq:getDiariesByOperator"
  }

  private inner class GetDiaryQuery<out T : Any>(
    public val operatorId: String,
    public val date: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("diaries", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("diaries", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-562_382_463,
        """SELECT diaries.id, diaries.operatorId, diaries.operatorName, diaries.content, diaries.date, diaries.createdAt FROM diaries WHERE operatorId = ? AND date = ?""",
        mapper, 2) {
      bindString(0, operatorId)
      bindString(1, date)
    }

    override fun toString(): String = "Diaries.sq:getDiary"
  }

  private inner class GetDiaryDatesQuery<out T : Any>(
    public val operatorId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("diaries", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("diaries", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_369_561_724,
        """SELECT DISTINCT date FROM diaries WHERE operatorId = ? ORDER BY date DESC""", mapper, 1)
        {
      bindString(0, operatorId)
    }

    override fun toString(): String = "Diaries.sq:getDiaryDates"
  }
}
