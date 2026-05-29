package com.example.rhodesterminal.shared.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Long
import kotlin.String

public class MemoryAnchorsQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> getPublicAnchors(
    operatorId: String,
    expiresAt: Long,
    mapper: (
      id: Long,
      sessionId: String,
      operatorId: String,
      type: String,
      content: String,
      isPrivate: Long,
      createdAt: Long,
      expiresAt: Long,
    ) -> T,
  ): Query<T> = GetPublicAnchorsQuery(operatorId, expiresAt) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!
    )
  }

  public fun getPublicAnchors(operatorId: String, expiresAt: Long): Query<Memory_anchors> =
      getPublicAnchors(operatorId, expiresAt) { id, sessionId, operatorId_, type, content,
      isPrivate, createdAt, expiresAt_ ->
    Memory_anchors(
      id,
      sessionId,
      operatorId_,
      type,
      content,
      isPrivate,
      createdAt,
      expiresAt_
    )
  }

  public fun <T : Any> getAllAnchors(
    operatorId: String,
    expiresAt: Long,
    mapper: (
      id: Long,
      sessionId: String,
      operatorId: String,
      type: String,
      content: String,
      isPrivate: Long,
      createdAt: Long,
      expiresAt: Long,
    ) -> T,
  ): Query<T> = GetAllAnchorsQuery(operatorId, expiresAt) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!
    )
  }

  public fun getAllAnchors(operatorId: String, expiresAt: Long): Query<Memory_anchors> =
      getAllAnchors(operatorId, expiresAt) { id, sessionId, operatorId_, type, content, isPrivate,
      createdAt, expiresAt_ ->
    Memory_anchors(
      id,
      sessionId,
      operatorId_,
      type,
      content,
      isPrivate,
      createdAt,
      expiresAt_
    )
  }

  public fun <T : Any> getRecentAnchors(
    sessionId: String,
    type: String,
    createdAt: Long,
    mapper: (
      id: Long,
      sessionId: String,
      operatorId: String,
      type: String,
      content: String,
      isPrivate: Long,
      createdAt: Long,
      expiresAt: Long,
    ) -> T,
  ): Query<T> = GetRecentAnchorsQuery(sessionId, type, createdAt) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!
    )
  }

  public fun getRecentAnchors(
    sessionId: String,
    type: String,
    createdAt: Long,
  ): Query<Memory_anchors> = getRecentAnchors(sessionId, type, createdAt) { id, sessionId_,
      operatorId, type_, content, isPrivate, createdAt_, expiresAt ->
    Memory_anchors(
      id,
      sessionId_,
      operatorId,
      type_,
      content,
      isPrivate,
      createdAt_,
      expiresAt
    )
  }

  public fun getAnchorCount(): Query<Long> = Query(232_889_428, arrayOf("memory_anchors"), driver,
      "MemoryAnchors.sq", "getAnchorCount", "SELECT COUNT(*) FROM memory_anchors") { cursor ->
    cursor.getLong(0)!!
  }

  public fun insertAnchor(
    sessionId: String,
    operatorId: String,
    type: String,
    content: String,
    isPrivate: Long,
    createdAt: Long,
    expiresAt: Long,
  ) {
    driver.execute(-77_538_050, """
        |INSERT OR REPLACE INTO memory_anchors(sessionId, operatorId, type, content, isPrivate, createdAt, expiresAt)
        |VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 7) {
          bindString(0, sessionId)
          bindString(1, operatorId)
          bindString(2, type)
          bindString(3, content)
          bindLong(4, isPrivate)
          bindLong(5, createdAt)
          bindLong(6, expiresAt)
        }
    notifyQueries(-77_538_050) { emit ->
      emit("memory_anchors")
    }
  }

  public fun deleteExpiredAnchors(expiresAt: Long) {
    driver.execute(-814_377_420, """DELETE FROM memory_anchors WHERE expiresAt < ?""", 1) {
          bindLong(0, expiresAt)
        }
    notifyQueries(-814_377_420) { emit ->
      emit("memory_anchors")
    }
  }

  public fun deleteOldAnchors(createdAt: Long) {
    driver.execute(-1_747_149_198, """DELETE FROM memory_anchors WHERE createdAt < ?""", 1) {
          bindLong(0, createdAt)
        }
    notifyQueries(-1_747_149_198) { emit ->
      emit("memory_anchors")
    }
  }

  public fun insertAllAnchors(
    sessionId: String,
    operatorId: String,
    type: String,
    content: String,
    isPrivate: Long,
    createdAt: Long,
    expiresAt: Long,
  ) {
    driver.execute(-494_560_954, """
        |INSERT OR REPLACE INTO memory_anchors(sessionId, operatorId, type, content, isPrivate, createdAt, expiresAt)
        |VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 7) {
          bindString(0, sessionId)
          bindString(1, operatorId)
          bindString(2, type)
          bindString(3, content)
          bindLong(4, isPrivate)
          bindLong(5, createdAt)
          bindLong(6, expiresAt)
        }
    notifyQueries(-494_560_954) { emit ->
      emit("memory_anchors")
    }
  }

  private inner class GetPublicAnchorsQuery<out T : Any>(
    public val operatorId: String,
    public val expiresAt: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("memory_anchors", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("memory_anchors", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_956_126_673,
        """SELECT memory_anchors.id, memory_anchors.sessionId, memory_anchors.operatorId, memory_anchors.type, memory_anchors.content, memory_anchors.isPrivate, memory_anchors.createdAt, memory_anchors.expiresAt FROM memory_anchors WHERE operatorId = ? AND isPrivate = 0 AND expiresAt > ? ORDER BY createdAt DESC""",
        mapper, 2) {
      bindString(0, operatorId)
      bindLong(1, expiresAt)
    }

    override fun toString(): String = "MemoryAnchors.sq:getPublicAnchors"
  }

  private inner class GetAllAnchorsQuery<out T : Any>(
    public val operatorId: String,
    public val expiresAt: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("memory_anchors", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("memory_anchors", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-798_838_973,
        """SELECT memory_anchors.id, memory_anchors.sessionId, memory_anchors.operatorId, memory_anchors.type, memory_anchors.content, memory_anchors.isPrivate, memory_anchors.createdAt, memory_anchors.expiresAt FROM memory_anchors WHERE operatorId = ? AND expiresAt > ? ORDER BY createdAt DESC""",
        mapper, 2) {
      bindString(0, operatorId)
      bindLong(1, expiresAt)
    }

    override fun toString(): String = "MemoryAnchors.sq:getAllAnchors"
  }

  private inner class GetRecentAnchorsQuery<out T : Any>(
    public val sessionId: String,
    public val type: String,
    public val createdAt: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("memory_anchors", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("memory_anchors", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_618_978_819,
        """SELECT memory_anchors.id, memory_anchors.sessionId, memory_anchors.operatorId, memory_anchors.type, memory_anchors.content, memory_anchors.isPrivate, memory_anchors.createdAt, memory_anchors.expiresAt FROM memory_anchors WHERE sessionId = ? AND type = ? AND createdAt > ? ORDER BY createdAt DESC""",
        mapper, 3) {
      bindString(0, sessionId)
      bindString(1, type)
      bindLong(2, createdAt)
    }

    override fun toString(): String = "MemoryAnchors.sq:getRecentAnchors"
  }
}
