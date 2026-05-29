package com.example.rhodesterminal.shared.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Long
import kotlin.String

public class ChatMessagesQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> getMessages(sessionId: String, mapper: (
    id: Long,
    sessionId: String,
    senderId: String,
    senderName: String,
    content: String,
    type: String,
    mode: String,
    emotion: String,
    activity: String,
    location: String,
    narration: String,
    segmentGroup: String,
    intimacyChange: Long,
    timestamp: Long,
    isMe: Long,
  ) -> T): Query<T> = GetMessagesQuery(sessionId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getString(6)!!,
      cursor.getString(7)!!,
      cursor.getString(8)!!,
      cursor.getString(9)!!,
      cursor.getString(10)!!,
      cursor.getString(11)!!,
      cursor.getLong(12)!!,
      cursor.getLong(13)!!,
      cursor.getLong(14)!!
    )
  }

  public fun getMessages(sessionId: String): Query<Chat_messages> = getMessages(sessionId) { id,
      sessionId_, senderId, senderName, content, type, mode, emotion, activity, location, narration,
      segmentGroup, intimacyChange, timestamp, isMe ->
    Chat_messages(
      id,
      sessionId_,
      senderId,
      senderName,
      content,
      type,
      mode,
      emotion,
      activity,
      location,
      narration,
      segmentGroup,
      intimacyChange,
      timestamp,
      isMe
    )
  }

  public fun <T : Any> getMessagesSync(sessionId: String, mapper: (
    id: Long,
    sessionId: String,
    senderId: String,
    senderName: String,
    content: String,
    type: String,
    mode: String,
    emotion: String,
    activity: String,
    location: String,
    narration: String,
    segmentGroup: String,
    intimacyChange: Long,
    timestamp: Long,
    isMe: Long,
  ) -> T): Query<T> = GetMessagesSyncQuery(sessionId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getString(6)!!,
      cursor.getString(7)!!,
      cursor.getString(8)!!,
      cursor.getString(9)!!,
      cursor.getString(10)!!,
      cursor.getString(11)!!,
      cursor.getLong(12)!!,
      cursor.getLong(13)!!,
      cursor.getLong(14)!!
    )
  }

  public fun getMessagesSync(sessionId: String): Query<Chat_messages> = getMessagesSync(sessionId) {
      id, sessionId_, senderId, senderName, content, type, mode, emotion, activity, location,
      narration, segmentGroup, intimacyChange, timestamp, isMe ->
    Chat_messages(
      id,
      sessionId_,
      senderId,
      senderName,
      content,
      type,
      mode,
      emotion,
      activity,
      location,
      narration,
      segmentGroup,
      intimacyChange,
      timestamp,
      isMe
    )
  }

  public fun <T : Any> getMaxId(mapper: (MAX: Long?) -> T): Query<T> = Query(-589_713_962,
      arrayOf("chat_messages"), driver, "ChatMessages.sq", "getMaxId",
      "SELECT MAX(id) FROM chat_messages") { cursor ->
    mapper(
      cursor.getLong(0)
    )
  }

  public fun getMaxId(): Query<GetMaxId> = getMaxId { MAX ->
    GetMaxId(
      MAX
    )
  }

  public fun <T : Any> getLastUserMessageTime(sessionId: String, mapper: (MAX: Long?) -> T):
      Query<T> = GetLastUserMessageTimeQuery(sessionId) { cursor ->
    mapper(
      cursor.getLong(0)
    )
  }

  public fun getLastUserMessageTime(sessionId: String): Query<GetLastUserMessageTime> =
      getLastUserMessageTime(sessionId) { MAX ->
    GetLastUserMessageTime(
      MAX
    )
  }

  public fun getMessageCount(): Query<Long> = Query(1_362_045_233, arrayOf("chat_messages"), driver,
      "ChatMessages.sq", "getMessageCount", "SELECT COUNT(*) FROM chat_messages") { cursor ->
    cursor.getLong(0)!!
  }

  public fun <T : Any> getMessageCountPerSender(mapper: (senderName: String, cnt: Long) -> T):
      Query<T> = Query(-1_945_998_463, arrayOf("chat_messages"), driver, "ChatMessages.sq",
      "getMessageCountPerSender",
      "SELECT senderName, COUNT(*) AS cnt FROM chat_messages WHERE isMe = 0 AND senderName != '' AND senderName != '系统' GROUP BY senderName ORDER BY cnt DESC") {
      cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getLong(1)!!
    )
  }

  public fun getMessageCountPerSender(): Query<GetMessageCountPerSender> =
      getMessageCountPerSender { senderName, cnt ->
    GetMessageCountPerSender(
      senderName,
      cnt
    )
  }

  public fun <T : Any> getMessagesInRange(
    timestamp: Long,
    timestamp_: Long,
    mapper: (
      id: Long,
      sessionId: String,
      senderId: String,
      senderName: String,
      content: String,
      type: String,
      mode: String,
      emotion: String,
      activity: String,
      location: String,
      narration: String,
      segmentGroup: String,
      intimacyChange: Long,
      timestamp: Long,
      isMe: Long,
    ) -> T,
  ): Query<T> = GetMessagesInRangeQuery(timestamp, timestamp_) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getString(6)!!,
      cursor.getString(7)!!,
      cursor.getString(8)!!,
      cursor.getString(9)!!,
      cursor.getString(10)!!,
      cursor.getString(11)!!,
      cursor.getLong(12)!!,
      cursor.getLong(13)!!,
      cursor.getLong(14)!!
    )
  }

  public fun getMessagesInRange(timestamp: Long, timestamp_: Long): Query<Chat_messages> =
      getMessagesInRange(timestamp, timestamp_) { id, sessionId, senderId, senderName, content,
      type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp__,
      isMe ->
    Chat_messages(
      id,
      sessionId,
      senderId,
      senderName,
      content,
      type,
      mode,
      emotion,
      activity,
      location,
      narration,
      segmentGroup,
      intimacyChange,
      timestamp__,
      isMe
    )
  }

  public fun insertMessage(
    id: Long?,
    sessionId: String,
    senderId: String,
    senderName: String,
    content: String,
    type: String,
    mode: String,
    emotion: String,
    activity: String,
    location: String,
    narration: String,
    segmentGroup: String,
    intimacyChange: Long,
    timestamp: Long,
    isMe: Long,
  ) {
    driver.execute(1_870_204_225, """
        |INSERT OR REPLACE INTO chat_messages(id, sessionId, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 15) {
          bindLong(0, id)
          bindString(1, sessionId)
          bindString(2, senderId)
          bindString(3, senderName)
          bindString(4, content)
          bindString(5, type)
          bindString(6, mode)
          bindString(7, emotion)
          bindString(8, activity)
          bindString(9, location)
          bindString(10, narration)
          bindString(11, segmentGroup)
          bindLong(12, intimacyChange)
          bindLong(13, timestamp)
          bindLong(14, isMe)
        }
    notifyQueries(1_870_204_225) { emit ->
      emit("chat_messages")
    }
  }

  public fun deleteSessionMessages(sessionId: String) {
    driver.execute(-281_354_230, """DELETE FROM chat_messages WHERE sessionId = ?""", 1) {
          bindString(0, sessionId)
        }
    notifyQueries(-281_354_230) { emit ->
      emit("chat_messages")
    }
  }

  public fun deleteMessage(id: Long) {
    driver.execute(-2_118_027_761, """DELETE FROM chat_messages WHERE id = ?""", 1) {
          bindLong(0, id)
        }
    notifyQueries(-2_118_027_761) { emit ->
      emit("chat_messages")
    }
  }

  public fun deleteAllMessages() {
    driver.execute(1_542_316_053, """DELETE FROM chat_messages""", 0)
    notifyQueries(1_542_316_053) { emit ->
      emit("chat_messages")
    }
  }

  public fun insertAllMessages(
    id: Long?,
    sessionId: String,
    senderId: String,
    senderName: String,
    content: String,
    type: String,
    mode: String,
    emotion: String,
    activity: String,
    location: String,
    narration: String,
    segmentGroup: String,
    intimacyChange: Long,
    timestamp: Long,
    isMe: Long,
  ) {
    driver.execute(-389_902_777, """
        |INSERT OR REPLACE INTO chat_messages(id, sessionId, senderId, senderName, content, type, mode, emotion, activity, location, narration, segmentGroup, intimacyChange, timestamp, isMe)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 15) {
          bindLong(0, id)
          bindString(1, sessionId)
          bindString(2, senderId)
          bindString(3, senderName)
          bindString(4, content)
          bindString(5, type)
          bindString(6, mode)
          bindString(7, emotion)
          bindString(8, activity)
          bindString(9, location)
          bindString(10, narration)
          bindString(11, segmentGroup)
          bindLong(12, intimacyChange)
          bindLong(13, timestamp)
          bindLong(14, isMe)
        }
    notifyQueries(-389_902_777) { emit ->
      emit("chat_messages")
    }
  }

  public fun updateContent(content: String, id: Long) {
    driver.execute(882_366_883, """UPDATE chat_messages SET content = ? WHERE id = ?""", 2) {
          bindString(0, content)
          bindLong(1, id)
        }
    notifyQueries(882_366_883) { emit ->
      emit("chat_messages")
    }
  }

  private inner class GetMessagesQuery<out T : Any>(
    public val sessionId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("chat_messages", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("chat_messages", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(1_693_268_405,
        """SELECT chat_messages.id, chat_messages.sessionId, chat_messages.senderId, chat_messages.senderName, chat_messages.content, chat_messages.type, chat_messages.mode, chat_messages.emotion, chat_messages.activity, chat_messages.location, chat_messages.narration, chat_messages.segmentGroup, chat_messages.intimacyChange, chat_messages.timestamp, chat_messages.isMe FROM chat_messages WHERE sessionId = ? ORDER BY timestamp ASC""",
        mapper, 1) {
      bindString(0, sessionId)
    }

    override fun toString(): String = "ChatMessages.sq:getMessages"
  }

  private inner class GetMessagesSyncQuery<out T : Any>(
    public val sessionId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("chat_messages", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("chat_messages", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(1_405_543_920,
        """SELECT chat_messages.id, chat_messages.sessionId, chat_messages.senderId, chat_messages.senderName, chat_messages.content, chat_messages.type, chat_messages.mode, chat_messages.emotion, chat_messages.activity, chat_messages.location, chat_messages.narration, chat_messages.segmentGroup, chat_messages.intimacyChange, chat_messages.timestamp, chat_messages.isMe FROM chat_messages WHERE sessionId = ? ORDER BY timestamp ASC""",
        mapper, 1) {
      bindString(0, sessionId)
    }

    override fun toString(): String = "ChatMessages.sq:getMessagesSync"
  }

  private inner class GetLastUserMessageTimeQuery<out T : Any>(
    public val sessionId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("chat_messages", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("chat_messages", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-1_127_682_774,
        """SELECT MAX(timestamp) FROM chat_messages WHERE sessionId = ? AND isMe = 1""", mapper, 1)
        {
      bindString(0, sessionId)
    }

    override fun toString(): String = "ChatMessages.sq:getLastUserMessageTime"
  }

  private inner class GetMessagesInRangeQuery<out T : Any>(
    public val timestamp: Long,
    public val timestamp_: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("chat_messages", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("chat_messages", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(296_919_971,
        """SELECT chat_messages.id, chat_messages.sessionId, chat_messages.senderId, chat_messages.senderName, chat_messages.content, chat_messages.type, chat_messages.mode, chat_messages.emotion, chat_messages.activity, chat_messages.location, chat_messages.narration, chat_messages.segmentGroup, chat_messages.intimacyChange, chat_messages.timestamp, chat_messages.isMe FROM chat_messages WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp ASC""",
        mapper, 2) {
      bindLong(0, timestamp)
      bindLong(1, timestamp_)
    }

    override fun toString(): String = "ChatMessages.sq:getMessagesInRange"
  }
}
