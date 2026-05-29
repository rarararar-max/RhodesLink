package com.example.rhodesterminal.shared.db

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.example.rhodesterminal.shared.db.shared.newInstance
import com.example.rhodesterminal.shared.db.shared.schema
import kotlin.Unit

public interface RhodesDatabase : Transacter {
  public val chatMessagesQueries: ChatMessagesQueries

  public val chatSessionsQueries: ChatSessionsQueries

  public val diariesQueries: DiariesQueries

  public val dispatchRecordsQueries: DispatchRecordsQueries

  public val mahjongSavesQueries: MahjongSavesQueries

  public val memoriesQueries: MemoriesQueries

  public val memoryAnchorsQueries: MemoryAnchorsQueries

  public val momentCommentsQueries: MomentCommentsQueries

  public val momentLikesQueries: MomentLikesQueries

  public val momentsQueries: MomentsQueries

  public val operatorsQueries: OperatorsQueries

  public val relationshipsQueries: RelationshipsQueries

  public companion object {
    public val Schema: SqlSchema<QueryResult.Value<Unit>>
      get() = RhodesDatabase::class.schema

    public operator fun invoke(driver: SqlDriver): RhodesDatabase =
        RhodesDatabase::class.newInstance(driver)
  }
}
