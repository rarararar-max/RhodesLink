package com.example.rhodesterminal.shared.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Long
import kotlin.String

public class ChatSessionsQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> getAllSessions(mapper: (
    id: String,
    operatorId: String,
    operatorName: String,
    lastMessage: String,
    lastTime: Long,
    mode: String,
    isPinned: Long,
    unreadCount: Long,
    members: String,
    rules: String,
    avatarUri: String,
    mutedMembers: String,
  ) -> T): Query<T> = Query(1_292_303_718, arrayOf("chat_sessions"), driver, "ChatSessions.sq",
      "getAllSessions",
      "SELECT chat_sessions.id, chat_sessions.operatorId, chat_sessions.operatorName, chat_sessions.lastMessage, chat_sessions.lastTime, chat_sessions.mode, chat_sessions.isPinned, chat_sessions.unreadCount, chat_sessions.members, chat_sessions.rules, chat_sessions.avatarUri, chat_sessions.mutedMembers FROM chat_sessions ORDER BY lastTime DESC") {
      cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4)!!,
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getString(8)!!,
      cursor.getString(9)!!,
      cursor.getString(10)!!,
      cursor.getString(11)!!
    )
  }

  public fun getAllSessions(): Query<Chat_sessions> = getAllSessions { id, operatorId, operatorName,
      lastMessage, lastTime, mode, isPinned, unreadCount, members, rules, avatarUri, mutedMembers ->
    Chat_sessions(
      id,
      operatorId,
      operatorName,
      lastMessage,
      lastTime,
      mode,
      isPinned,
      unreadCount,
      members,
      rules,
      avatarUri,
      mutedMembers
    )
  }

  public fun <T : Any> getSessionByOperator(operatorId: String, mapper: (
    id: String,
    operatorId: String,
    operatorName: String,
    lastMessage: String,
    lastTime: Long,
    mode: String,
    isPinned: Long,
    unreadCount: Long,
    members: String,
    rules: String,
    avatarUri: String,
    mutedMembers: String,
  ) -> T): Query<T> = GetSessionByOperatorQuery(operatorId) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4)!!,
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getString(8)!!,
      cursor.getString(9)!!,
      cursor.getString(10)!!,
      cursor.getString(11)!!
    )
  }

  public fun getSessionByOperator(operatorId: String): Query<Chat_sessions> =
      getSessionByOperator(operatorId) { id, operatorId_, operatorName, lastMessage, lastTime, mode,
      isPinned, unreadCount, members, rules, avatarUri, mutedMembers ->
    Chat_sessions(
      id,
      operatorId_,
      operatorName,
      lastMessage,
      lastTime,
      mode,
      isPinned,
      unreadCount,
      members,
      rules,
      avatarUri,
      mutedMembers
    )
  }

  public fun <T : Any> getSession(id: String, mapper: (
    id: String,
    operatorId: String,
    operatorName: String,
    lastMessage: String,
    lastTime: Long,
    mode: String,
    isPinned: Long,
    unreadCount: Long,
    members: String,
    rules: String,
    avatarUri: String,
    mutedMembers: String,
  ) -> T): Query<T> = GetSessionQuery(id) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getLong(4)!!,
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getString(8)!!,
      cursor.getString(9)!!,
      cursor.getString(10)!!,
      cursor.getString(11)!!
    )
  }

  public fun getSession(id: String): Query<Chat_sessions> = getSession(id) { id_, operatorId,
      operatorName, lastMessage, lastTime, mode, isPinned, unreadCount, members, rules, avatarUri,
      mutedMembers ->
    Chat_sessions(
      id_,
      operatorId,
      operatorName,
      lastMessage,
      lastTime,
      mode,
      isPinned,
      unreadCount,
      members,
      rules,
      avatarUri,
      mutedMembers
    )
  }

  public fun getGroupCount(): Query<Long> = Query(-2_077_884_216, arrayOf("chat_sessions"), driver,
      "ChatSessions.sq", "getGroupCount",
      "SELECT COUNT(*) FROM chat_sessions WHERE operatorId LIKE 'group_%'") { cursor ->
    cursor.getLong(0)!!
  }

  public fun getSessionCount(): Query<Long> = Query(-1_007_579_887, arrayOf("chat_sessions"),
      driver, "ChatSessions.sq", "getSessionCount", "SELECT COUNT(*) FROM chat_sessions") {
      cursor ->
    cursor.getLong(0)!!
  }

  public fun insertSession(
    id: String,
    operatorId: String,
    operatorName: String,
    lastMessage: String,
    lastTime: Long,
    mode: String,
    isPinned: Long,
    unreadCount: Long,
    members: String,
    rules: String,
    avatarUri: String,
    mutedMembers: String,
  ) {
    driver.execute(-1_563_153_249, """
        |INSERT OR REPLACE INTO chat_sessions(id, operatorId, operatorName, lastMessage, lastTime, mode, isPinned, unreadCount, members, rules, avatarUri, mutedMembers)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 12) {
          bindString(0, id)
          bindString(1, operatorId)
          bindString(2, operatorName)
          bindString(3, lastMessage)
          bindLong(4, lastTime)
          bindString(5, mode)
          bindLong(6, isPinned)
          bindLong(7, unreadCount)
          bindString(8, members)
          bindString(9, rules)
          bindString(10, avatarUri)
          bindString(11, mutedMembers)
        }
    notifyQueries(-1_563_153_249) { emit ->
      emit("chat_sessions")
    }
  }

  public fun updateLastMessage(
    lastMessage: String,
    lastTime: Long,
    id: String,
  ) {
    driver.execute(1_639_689_738,
        """UPDATE chat_sessions SET lastMessage = ?, lastTime = ? WHERE id = ?""", 3) {
          bindString(0, lastMessage)
          bindLong(1, lastTime)
          bindString(2, id)
        }
    notifyQueries(1_639_689_738) { emit ->
      emit("chat_sessions")
    }
  }

  public fun updateMode(mode: String, id: String) {
    driver.execute(1_250_419_274, """UPDATE chat_sessions SET mode = ? WHERE id = ?""", 2) {
          bindString(0, mode)
          bindString(1, id)
        }
    notifyQueries(1_250_419_274) { emit ->
      emit("chat_sessions")
    }
  }

  public fun markAllRead() {
    driver.execute(-1_119_433_012, """UPDATE chat_sessions SET unreadCount = 0""", 0)
    notifyQueries(-1_119_433_012) { emit ->
      emit("chat_sessions")
    }
  }

  public fun deleteSession(id: String) {
    driver.execute(-1_256_417_939, """DELETE FROM chat_sessions WHERE id = ?""", 1) {
          bindString(0, id)
        }
    notifyQueries(-1_256_417_939) { emit ->
      emit("chat_sessions")
    }
  }

  public fun deleteAllSessions() {
    driver.execute(1_617_251_509,
        """DELETE FROM chat_sessions WHERE operatorId NOT LIKE 'group_%'""", 0)
    notifyQueries(1_617_251_509) { emit ->
      emit("chat_sessions")
    }
  }

  private inner class GetSessionByOperatorQuery<out T : Any>(
    public val operatorId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("chat_sessions", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("chat_sessions", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-2_069_936_295,
        """SELECT chat_sessions.id, chat_sessions.operatorId, chat_sessions.operatorName, chat_sessions.lastMessage, chat_sessions.lastTime, chat_sessions.mode, chat_sessions.isPinned, chat_sessions.unreadCount, chat_sessions.members, chat_sessions.rules, chat_sessions.avatarUri, chat_sessions.mutedMembers FROM chat_sessions WHERE operatorId = ? LIMIT 1""",
        mapper, 1) {
      bindString(0, operatorId)
    }

    override fun toString(): String = "ChatSessions.sq:getSessionByOperator"
  }

  private inner class GetSessionQuery<out T : Any>(
    public val id: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("chat_sessions", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("chat_sessions", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(273_434_270,
        """SELECT chat_sessions.id, chat_sessions.operatorId, chat_sessions.operatorName, chat_sessions.lastMessage, chat_sessions.lastTime, chat_sessions.mode, chat_sessions.isPinned, chat_sessions.unreadCount, chat_sessions.members, chat_sessions.rules, chat_sessions.avatarUri, chat_sessions.mutedMembers FROM chat_sessions WHERE id = ?""",
        mapper, 1) {
      bindString(0, id)
    }

    override fun toString(): String = "ChatSessions.sq:getSession"
  }
}
