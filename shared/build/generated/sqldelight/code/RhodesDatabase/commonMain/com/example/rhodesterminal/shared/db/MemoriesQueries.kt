package com.example.rhodesterminal.shared.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Long
import kotlin.String

public class MemoriesQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> getLatestMemory(
    sessionId: String,
    type: String,
    mapper: (
      id: Long,
      sessionId: String,
      operatorId: String,
      type: String,
      content: String,
      keywords: String,
      preferences: String,
      taboos: String,
      createdAt: Long,
      expiresAt: Long,
    ) -> T,
  ): Query<T> = GetLatestMemoryQuery(sessionId, type) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getString(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!
    )
  }

  public fun getLatestMemory(sessionId: String, type: String): Query<Memories> =
      getLatestMemory(sessionId, type) { id, sessionId_, operatorId, type_, content, keywords,
      preferences, taboos, createdAt, expiresAt ->
    Memories(
      id,
      sessionId_,
      operatorId,
      type_,
      content,
      keywords,
      preferences,
      taboos,
      createdAt,
      expiresAt
    )
  }

  public fun <T : Any> getMemories(
    sessionId: String,
    type: String,
    mapper: (
      id: Long,
      sessionId: String,
      operatorId: String,
      type: String,
      content: String,
      keywords: String,
      preferences: String,
      taboos: String,
      createdAt: Long,
      expiresAt: Long,
    ) -> T,
  ): Query<T> = GetMemoriesQuery(sessionId, type) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getString(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!
    )
  }

  public fun getMemories(sessionId: String, type: String): Query<Memories> = getMemories(sessionId,
      type) { id, sessionId_, operatorId, type_, content, keywords, preferences, taboos, createdAt,
      expiresAt ->
    Memories(
      id,
      sessionId_,
      operatorId,
      type_,
      content,
      keywords,
      preferences,
      taboos,
      createdAt,
      expiresAt
    )
  }

  public fun <T : Any> getLatestLongTermImpression(operatorId: String, mapper: (
    id: Long,
    sessionId: String,
    operatorId: String,
    type: String,
    content: String,
    keywords: String,
    preferences: String,
    taboos: String,
    createdAt: Long,
    expiresAt: Long,
  ) -> T): Query<T> = GetLatestLongTermImpressionQuery(operatorId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getString(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!
    )
  }

  public fun getLatestLongTermImpression(operatorId: String): Query<Memories> =
      getLatestLongTermImpression(operatorId) { id, sessionId, operatorId_, type, content, keywords,
      preferences, taboos, createdAt, expiresAt ->
    Memories(
      id,
      sessionId,
      operatorId_,
      type,
      content,
      keywords,
      preferences,
      taboos,
      createdAt,
      expiresAt
    )
  }

  public fun <T : Any> getAllLongTerm(mapper: (
    id: Long,
    sessionId: String,
    operatorId: String,
    type: String,
    content: String,
    keywords: String,
    preferences: String,
    taboos: String,
    createdAt: Long,
    expiresAt: Long,
  ) -> T): Query<T> = Query(896_648_731, arrayOf("memories"), driver, "Memories.sq",
      "getAllLongTerm",
      "SELECT memories.id, memories.sessionId, memories.operatorId, memories.type, memories.content, memories.keywords, memories.preferences, memories.taboos, memories.createdAt, memories.expiresAt FROM memories WHERE type = 'LONG_TERM' ORDER BY createdAt DESC") {
      cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getString(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!
    )
  }

  public fun getAllLongTerm(): Query<Memories> = getAllLongTerm { id, sessionId, operatorId, type,
      content, keywords, preferences, taboos, createdAt, expiresAt ->
    Memories(
      id,
      sessionId,
      operatorId,
      type,
      content,
      keywords,
      preferences,
      taboos,
      createdAt,
      expiresAt
    )
  }

  public fun <T : Any> getLatestDaily(mapper: (
    id: Long,
    sessionId: String,
    operatorId: String,
    type: String,
    content: String,
    keywords: String,
    preferences: String,
    taboos: String,
    createdAt: Long,
    expiresAt: Long,
  ) -> T): Query<T> = Query(-1_087_556_828, arrayOf("memories"), driver, "Memories.sq",
      "getLatestDaily",
      "SELECT memories.id, memories.sessionId, memories.operatorId, memories.type, memories.content, memories.keywords, memories.preferences, memories.taboos, memories.createdAt, memories.expiresAt FROM memories WHERE type = 'DAILY' ORDER BY createdAt DESC LIMIT 1") {
      cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getString(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!
    )
  }

  public fun getLatestDaily(): Query<Memories> = getLatestDaily { id, sessionId, operatorId, type,
      content, keywords, preferences, taboos, createdAt, expiresAt ->
    Memories(
      id,
      sessionId,
      operatorId,
      type,
      content,
      keywords,
      preferences,
      taboos,
      createdAt,
      expiresAt
    )
  }

  public fun <T : Any> getMemoriesBySession(
    sessionId: String,
    type: String,
    mapper: (
      id: Long,
      sessionId: String,
      operatorId: String,
      type: String,
      content: String,
      keywords: String,
      preferences: String,
      taboos: String,
      createdAt: Long,
      expiresAt: Long,
    ) -> T,
  ): Query<T> = GetMemoriesBySessionQuery(sessionId, type) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getString(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!
    )
  }

  public fun getMemoriesBySession(sessionId: String, type: String): Query<Memories> =
      getMemoriesBySession(sessionId, type) { id, sessionId_, operatorId, type_, content, keywords,
      preferences, taboos, createdAt, expiresAt ->
    Memories(
      id,
      sessionId_,
      operatorId,
      type_,
      content,
      keywords,
      preferences,
      taboos,
      createdAt,
      expiresAt
    )
  }

  public fun insertMemory(
    sessionId: String,
    operatorId: String,
    type: String,
    content: String,
    keywords: String,
    preferences: String,
    taboos: String,
    createdAt: Long,
    expiresAt: Long,
  ) {
    driver.execute(-1_801_293_662, """
        |INSERT OR REPLACE INTO memories(sessionId, operatorId, type, content, keywords, preferences, taboos, createdAt, expiresAt)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 9) {
          bindString(0, sessionId)
          bindString(1, operatorId)
          bindString(2, type)
          bindString(3, content)
          bindString(4, keywords)
          bindString(5, preferences)
          bindString(6, taboos)
          bindLong(7, createdAt)
          bindLong(8, expiresAt)
        }
    notifyQueries(-1_801_293_662) { emit ->
      emit("memories")
    }
  }

  public fun deleteExpired(expiresAt: Long) {
    driver.execute(-1_957_310_350, """DELETE FROM memories WHERE expiresAt < ?""", 1) {
          bindLong(0, expiresAt)
        }
    notifyQueries(-1_957_310_350) { emit ->
      emit("memories")
    }
  }

  public fun deleteMemory(id: Long) {
    driver.execute(979_547_668, """DELETE FROM memories WHERE id = ?""", 1) {
          bindLong(0, id)
        }
    notifyQueries(979_547_668) { emit ->
      emit("memories")
    }
  }

  public fun deleteAllLongTerm() {
    driver.execute(-426_293_098, """DELETE FROM memories WHERE type = 'LONG_TERM'""", 0)
    notifyQueries(-426_293_098) { emit ->
      emit("memories")
    }
  }

  private inner class GetLatestMemoryQuery<out T : Any>(
    public val sessionId: String,
    public val type: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("memories", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("memories", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(906_955_094,
        """SELECT memories.id, memories.sessionId, memories.operatorId, memories.type, memories.content, memories.keywords, memories.preferences, memories.taboos, memories.createdAt, memories.expiresAt FROM memories WHERE sessionId = ? AND type = ? ORDER BY createdAt DESC LIMIT 1""",
        mapper, 2) {
      bindString(0, sessionId)
      bindString(1, type)
    }

    override fun toString(): String = "Memories.sq:getLatestMemory"
  }

  private inner class GetMemoriesQuery<out T : Any>(
    public val sessionId: String,
    public val type: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("memories", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("memories", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-575_255_987,
        """SELECT memories.id, memories.sessionId, memories.operatorId, memories.type, memories.content, memories.keywords, memories.preferences, memories.taboos, memories.createdAt, memories.expiresAt FROM memories WHERE sessionId = ? AND type = ? ORDER BY createdAt DESC""",
        mapper, 2) {
      bindString(0, sessionId)
      bindString(1, type)
    }

    override fun toString(): String = "Memories.sq:getMemories"
  }

  private inner class GetLatestLongTermImpressionQuery<out T : Any>(
    public val operatorId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("memories", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("memories", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-36_103_674,
        """SELECT memories.id, memories.sessionId, memories.operatorId, memories.type, memories.content, memories.keywords, memories.preferences, memories.taboos, memories.createdAt, memories.expiresAt FROM memories WHERE operatorId = ? AND type = 'LONG_TERM' ORDER BY createdAt DESC LIMIT 1""",
        mapper, 1) {
      bindString(0, operatorId)
    }

    override fun toString(): String = "Memories.sq:getLatestLongTermImpression"
  }

  private inner class GetMemoriesBySessionQuery<out T : Any>(
    public val sessionId: String,
    public val type: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("memories", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("memories", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(275_769_970,
        """SELECT memories.id, memories.sessionId, memories.operatorId, memories.type, memories.content, memories.keywords, memories.preferences, memories.taboos, memories.createdAt, memories.expiresAt FROM memories WHERE sessionId = ? AND type = ? ORDER BY createdAt ASC""",
        mapper, 2) {
      bindString(0, sessionId)
      bindString(1, type)
    }

    override fun toString(): String = "Memories.sq:getMemoriesBySession"
  }
}
