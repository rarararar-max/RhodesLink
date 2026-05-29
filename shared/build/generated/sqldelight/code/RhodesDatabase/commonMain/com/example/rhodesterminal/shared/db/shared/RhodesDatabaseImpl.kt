package com.example.rhodesterminal.shared.db.shared

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.example.rhodesterminal.shared.db.ChatMessagesQueries
import com.example.rhodesterminal.shared.db.ChatSessionsQueries
import com.example.rhodesterminal.shared.db.DiariesQueries
import com.example.rhodesterminal.shared.db.DispatchRecordsQueries
import com.example.rhodesterminal.shared.db.MahjongSavesQueries
import com.example.rhodesterminal.shared.db.MemoriesQueries
import com.example.rhodesterminal.shared.db.MemoryAnchorsQueries
import com.example.rhodesterminal.shared.db.MomentCommentsQueries
import com.example.rhodesterminal.shared.db.MomentLikesQueries
import com.example.rhodesterminal.shared.db.MomentsQueries
import com.example.rhodesterminal.shared.db.OperatorsQueries
import com.example.rhodesterminal.shared.db.RelationshipsQueries
import com.example.rhodesterminal.shared.db.RhodesDatabase
import kotlin.Long
import kotlin.Unit
import kotlin.reflect.KClass

internal val KClass<RhodesDatabase>.schema: SqlSchema<QueryResult.Value<Unit>>
  get() = RhodesDatabaseImpl.Schema

internal fun KClass<RhodesDatabase>.newInstance(driver: SqlDriver): RhodesDatabase =
    RhodesDatabaseImpl(driver)

private class RhodesDatabaseImpl(
  driver: SqlDriver,
) : TransacterImpl(driver), RhodesDatabase {
  override val chatMessagesQueries: ChatMessagesQueries = ChatMessagesQueries(driver)

  override val chatSessionsQueries: ChatSessionsQueries = ChatSessionsQueries(driver)

  override val diariesQueries: DiariesQueries = DiariesQueries(driver)

  override val dispatchRecordsQueries: DispatchRecordsQueries = DispatchRecordsQueries(driver)

  override val mahjongSavesQueries: MahjongSavesQueries = MahjongSavesQueries(driver)

  override val memoriesQueries: MemoriesQueries = MemoriesQueries(driver)

  override val memoryAnchorsQueries: MemoryAnchorsQueries = MemoryAnchorsQueries(driver)

  override val momentCommentsQueries: MomentCommentsQueries = MomentCommentsQueries(driver)

  override val momentLikesQueries: MomentLikesQueries = MomentLikesQueries(driver)

  override val momentsQueries: MomentsQueries = MomentsQueries(driver)

  override val operatorsQueries: OperatorsQueries = OperatorsQueries(driver)

  override val relationshipsQueries: RelationshipsQueries = RelationshipsQueries(driver)

  public object Schema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long
      get() = 1

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
      driver.execute(null, """
          |CREATE TABLE IF NOT EXISTS chat_messages (
          |    id INTEGER NOT NULL PRIMARY KEY,
          |    sessionId TEXT NOT NULL,
          |    senderId TEXT NOT NULL DEFAULT '',
          |    senderName TEXT NOT NULL,
          |    content TEXT NOT NULL,
          |    type TEXT NOT NULL DEFAULT 'text',
          |    mode TEXT NOT NULL DEFAULT 'online',
          |    emotion TEXT NOT NULL DEFAULT '',
          |    activity TEXT NOT NULL DEFAULT '',
          |    location TEXT NOT NULL DEFAULT '',
          |    narration TEXT NOT NULL DEFAULT '',
          |    segmentGroup TEXT NOT NULL DEFAULT '',
          |    intimacyChange INTEGER NOT NULL DEFAULT 0,
          |    timestamp INTEGER NOT NULL DEFAULT 0,
          |    isMe INTEGER NOT NULL
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE IF NOT EXISTS chat_sessions (
          |    id TEXT NOT NULL PRIMARY KEY,
          |    operatorId TEXT NOT NULL,
          |    operatorName TEXT NOT NULL,
          |    lastMessage TEXT NOT NULL DEFAULT '',
          |    lastTime INTEGER NOT NULL DEFAULT 0,
          |    mode TEXT NOT NULL DEFAULT 'online',
          |    isPinned INTEGER NOT NULL DEFAULT 0,
          |    unreadCount INTEGER NOT NULL DEFAULT 0,
          |    members TEXT NOT NULL DEFAULT '',
          |    rules TEXT NOT NULL DEFAULT '',
          |    avatarUri TEXT NOT NULL DEFAULT '',
          |    mutedMembers TEXT NOT NULL DEFAULT ''
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE IF NOT EXISTS diaries (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    operatorId TEXT NOT NULL,
          |    operatorName TEXT NOT NULL,
          |    content TEXT NOT NULL,
          |    date TEXT NOT NULL,
          |    createdAt INTEGER NOT NULL DEFAULT 0
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE IF NOT EXISTS dispatch_records (
          |    id TEXT NOT NULL PRIMARY KEY,
          |    taskType TEXT NOT NULL,
          |    durationHours INTEGER NOT NULL,
          |    budget INTEGER NOT NULL,
          |    netProfit INTEGER NOT NULL DEFAULT 0,
          |    operatorIds TEXT NOT NULL DEFAULT '',
          |    logChain TEXT NOT NULL DEFAULT '',
          |    status TEXT NOT NULL DEFAULT 'active',
          |    startTime INTEGER NOT NULL DEFAULT 0,
          |    endTime INTEGER NOT NULL DEFAULT 0,
          |    totalSegments INTEGER NOT NULL DEFAULT 0,
          |    segmentInterval INTEGER NOT NULL DEFAULT 0,
          |    items TEXT NOT NULL DEFAULT ''
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE IF NOT EXISTS mahjong_saves (
          |    id TEXT NOT NULL PRIMARY KEY DEFAULT 'current',
          |    saveJson TEXT NOT NULL,
          |    ruleType TEXT NOT NULL DEFAULT '',
          |    savedAt INTEGER NOT NULL DEFAULT 0
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE IF NOT EXISTS memories (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    sessionId TEXT NOT NULL,
          |    operatorId TEXT NOT NULL,
          |    type TEXT NOT NULL,
          |    content TEXT NOT NULL,
          |    keywords TEXT NOT NULL DEFAULT '',
          |    preferences TEXT NOT NULL DEFAULT '',
          |    taboos TEXT NOT NULL DEFAULT '',
          |    createdAt INTEGER NOT NULL DEFAULT 0,
          |    expiresAt INTEGER NOT NULL DEFAULT 9223372036854775807
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE IF NOT EXISTS memory_anchors (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    sessionId TEXT NOT NULL,
          |    operatorId TEXT NOT NULL,
          |    type TEXT NOT NULL,
          |    content TEXT NOT NULL,
          |    isPrivate INTEGER NOT NULL DEFAULT 0,
          |    createdAt INTEGER NOT NULL DEFAULT 0,
          |    expiresAt INTEGER NOT NULL DEFAULT 0
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE IF NOT EXISTS moment_comments (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    momentId INTEGER NOT NULL,
          |    operatorId TEXT NOT NULL,
          |    operatorName TEXT NOT NULL,
          |    content TEXT NOT NULL,
          |    parentCommentId INTEGER NOT NULL DEFAULT 0,
          |    replyToName TEXT NOT NULL DEFAULT '',
          |    createdAt INTEGER NOT NULL DEFAULT 0,
          |    isRead INTEGER NOT NULL DEFAULT 0
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE IF NOT EXISTS moment_likes (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    momentId INTEGER NOT NULL,
          |    operatorId TEXT NOT NULL,
          |    operatorName TEXT NOT NULL,
          |    createdAt INTEGER NOT NULL DEFAULT 0
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE IF NOT EXISTS moments (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    operatorId TEXT NOT NULL,
          |    operatorName TEXT NOT NULL,
          |    content TEXT NOT NULL,
          |    isUserPost INTEGER NOT NULL DEFAULT 0,
          |    mentionedOperatorIds TEXT NOT NULL DEFAULT '',
          |    likeCount INTEGER NOT NULL DEFAULT 0,
          |    commentCount INTEGER NOT NULL DEFAULT 0,
          |    createdAt INTEGER NOT NULL DEFAULT 0
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE IF NOT EXISTS operators (
          |    id TEXT NOT NULL PRIMARY KEY,
          |    name TEXT NOT NULL,
          |    title TEXT NOT NULL DEFAULT '',
          |    description TEXT NOT NULL DEFAULT '',
          |    avatarUri TEXT NOT NULL DEFAULT '',
          |    location TEXT NOT NULL DEFAULT '宿舍',
          |    activity TEXT NOT NULL DEFAULT '休息',
          |    emotion TEXT NOT NULL DEFAULT '平静',
          |    intimacy INTEGER NOT NULL DEFAULT 0,
          |    privatePrompt TEXT NOT NULL DEFAULT '',
          |    groupPrompt TEXT NOT NULL DEFAULT '',
          |    userRelation TEXT NOT NULL DEFAULT '',
          |    lmb INTEGER NOT NULL DEFAULT 10000,
          |    attack REAL NOT NULL DEFAULT 0.5,
          |    defense REAL NOT NULL DEFAULT 0.5,
          |    meldPref TEXT NOT NULL DEFAULT 'medium'
          |)
          """.trimMargin(), 0)
      driver.execute(null, """
          |CREATE TABLE IF NOT EXISTS relationships (
          |    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          |    operatorId TEXT NOT NULL,
          |    relatedOperatorId TEXT NOT NULL,
          |    relatedOperatorName TEXT NOT NULL,
          |    type TEXT NOT NULL,
          |    intimacy INTEGER NOT NULL DEFAULT 0,
          |    isPreset INTEGER NOT NULL DEFAULT 0,
          |    note TEXT NOT NULL DEFAULT ''
          |)
          """.trimMargin(), 0)
      return QueryResult.Unit
    }

    override fun migrate(
      driver: SqlDriver,
      oldVersion: Long,
      newVersion: Long,
      vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> = QueryResult.Unit
  }
}
