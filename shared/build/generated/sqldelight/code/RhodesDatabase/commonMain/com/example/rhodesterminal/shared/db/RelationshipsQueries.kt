package com.example.rhodesterminal.shared.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Long
import kotlin.String

public class RelationshipsQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> getRelationships(operatorId: String, mapper: (
    id: Long,
    operatorId: String,
    relatedOperatorId: String,
    relatedOperatorName: String,
    type: String,
    intimacy: Long,
    isPreset: Long,
    note: String,
  ) -> T): Query<T> = GetRelationshipsQuery(operatorId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getLong(6)!!,
      cursor.getString(7)!!
    )
  }

  public fun getRelationships(operatorId: String): Query<Relationships> =
      getRelationships(operatorId) { id, operatorId_, relatedOperatorId, relatedOperatorName, type,
      intimacy, isPreset, note ->
    Relationships(
      id,
      operatorId_,
      relatedOperatorId,
      relatedOperatorName,
      type,
      intimacy,
      isPreset,
      note
    )
  }

  public fun <T : Any> getRelationshipsSync(operatorId: String, mapper: (
    id: Long,
    operatorId: String,
    relatedOperatorId: String,
    relatedOperatorName: String,
    type: String,
    intimacy: Long,
    isPreset: Long,
    note: String,
  ) -> T): Query<T> = GetRelationshipsSyncQuery(operatorId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getLong(6)!!,
      cursor.getString(7)!!
    )
  }

  public fun getRelationshipsSync(operatorId: String): Query<Relationships> =
      getRelationshipsSync(operatorId) { id, operatorId_, relatedOperatorId, relatedOperatorName,
      type, intimacy, isPreset, note ->
    Relationships(
      id,
      operatorId_,
      relatedOperatorId,
      relatedOperatorName,
      type,
      intimacy,
      isPreset,
      note
    )
  }

  public fun <T : Any> getReverseRelationshipsSync(relatedOperatorId: String, mapper: (
    id: Long,
    operatorId: String,
    relatedOperatorId: String,
    relatedOperatorName: String,
    type: String,
    intimacy: Long,
    isPreset: Long,
    note: String,
  ) -> T): Query<T> = GetReverseRelationshipsSyncQuery(relatedOperatorId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getLong(6)!!,
      cursor.getString(7)!!
    )
  }

  public fun getReverseRelationshipsSync(relatedOperatorId: String): Query<Relationships> =
      getReverseRelationshipsSync(relatedOperatorId) { id, operatorId, relatedOperatorId_,
      relatedOperatorName, type, intimacy, isPreset, note ->
    Relationships(
      id,
      operatorId,
      relatedOperatorId_,
      relatedOperatorName,
      type,
      intimacy,
      isPreset,
      note
    )
  }

  public fun <T : Any> getRelationship(
    operatorId: String,
    relatedOperatorId: String,
    mapper: (
      id: Long,
      operatorId: String,
      relatedOperatorId: String,
      relatedOperatorName: String,
      type: String,
      intimacy: Long,
      isPreset: Long,
      note: String,
    ) -> T,
  ): Query<T> = GetRelationshipQuery(operatorId, relatedOperatorId) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getLong(6)!!,
      cursor.getString(7)!!
    )
  }

  public fun getRelationship(operatorId: String, relatedOperatorId: String): Query<Relationships> =
      getRelationship(operatorId, relatedOperatorId) { id, operatorId_, relatedOperatorId_,
      relatedOperatorName, type, intimacy, isPreset, note ->
    Relationships(
      id,
      operatorId_,
      relatedOperatorId_,
      relatedOperatorName,
      type,
      intimacy,
      isPreset,
      note
    )
  }

  public fun getRelationshipCount(operatorId: String): Query<Long> =
      GetRelationshipCountQuery(operatorId) { cursor ->
    cursor.getLong(0)!!
  }

  public fun getCount(): Query<Long> = Query(1_277_837_735, arrayOf("relationships"), driver,
      "Relationships.sq", "getCount", "SELECT COUNT(*) FROM relationships") { cursor ->
    cursor.getLong(0)!!
  }

  public fun getPresetCount(): Query<Long> = Query(-606_629_880, arrayOf("relationships"), driver,
      "Relationships.sq", "getPresetCount",
      "SELECT COUNT(*) FROM relationships WHERE isPreset = 1") { cursor ->
    cursor.getLong(0)!!
  }

  public fun insertRelationship(
    operatorId: String,
    relatedOperatorId: String,
    relatedOperatorName: String,
    type: String,
    intimacy: Long,
    isPreset: Long,
    note: String,
  ) {
    driver.execute(708_608_095, """
        |INSERT OR REPLACE INTO relationships(operatorId, relatedOperatorId, relatedOperatorName, type, intimacy, isPreset, note)
        |VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 7) {
          bindString(0, operatorId)
          bindString(1, relatedOperatorId)
          bindString(2, relatedOperatorName)
          bindString(3, type)
          bindLong(4, intimacy)
          bindLong(5, isPreset)
          bindString(6, note)
        }
    notifyQueries(708_608_095) { emit ->
      emit("relationships")
    }
  }

  public fun updateRelationship(
    type: String,
    intimacy: Long,
    note: String,
    operatorId: String,
    relatedOperatorId: String,
  ) {
    driver.execute(763_059_823, """
        |UPDATE relationships SET type = ?, intimacy = ?, note = ?
        |WHERE operatorId = ? AND relatedOperatorId = ?
        """.trimMargin(), 5) {
          bindString(0, type)
          bindLong(1, intimacy)
          bindString(2, note)
          bindString(3, operatorId)
          bindString(4, relatedOperatorId)
        }
    notifyQueries(763_059_823) { emit ->
      emit("relationships")
    }
  }

  public fun deleteRelationship(operatorId: String, relatedOperatorId: String) {
    driver.execute(477_849_681,
        """DELETE FROM relationships WHERE operatorId = ? AND relatedOperatorId = ?""", 2) {
          bindString(0, operatorId)
          bindString(1, relatedOperatorId)
        }
    notifyQueries(477_849_681) { emit ->
      emit("relationships")
    }
  }

  public fun deleteByOperator(operatorId: String) {
    driver.execute(1_000_928_884, """DELETE FROM relationships WHERE operatorId = ?""", 1) {
          bindString(0, operatorId)
        }
    notifyQueries(1_000_928_884) { emit ->
      emit("relationships")
    }
  }

  public fun deleteByType(type: String) {
    driver.execute(711_247_114, """DELETE FROM relationships WHERE type = ?""", 1) {
          bindString(0, type)
        }
    notifyQueries(711_247_114) { emit ->
      emit("relationships")
    }
  }

  public fun deletePresets() {
    driver.execute(-70_919_237, """DELETE FROM relationships WHERE isPreset = 1""", 0)
    notifyQueries(-70_919_237) { emit ->
      emit("relationships")
    }
  }

  public fun insertAllRelationships(
    operatorId: String,
    relatedOperatorId: String,
    relatedOperatorName: String,
    type: String,
    intimacy: Long,
    isPreset: Long,
    note: String,
  ) {
    driver.execute(320_410_561, """
        |INSERT OR REPLACE INTO relationships(operatorId, relatedOperatorId, relatedOperatorName, type, intimacy, isPreset, note)
        |VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 7) {
          bindString(0, operatorId)
          bindString(1, relatedOperatorId)
          bindString(2, relatedOperatorName)
          bindString(3, type)
          bindLong(4, intimacy)
          bindLong(5, isPreset)
          bindString(6, note)
        }
    notifyQueries(320_410_561) { emit ->
      emit("relationships")
    }
  }

  public fun deleteAllRelationships() {
    driver.execute(2_061_415_091, """DELETE FROM relationships""", 0)
    notifyQueries(2_061_415_091) { emit ->
      emit("relationships")
    }
  }

  private inner class GetRelationshipsQuery<out T : Any>(
    public val operatorId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("relationships", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("relationships", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-245_421_613,
        """SELECT relationships.id, relationships.operatorId, relationships.relatedOperatorId, relationships.relatedOperatorName, relationships.type, relationships.intimacy, relationships.isPreset, relationships.note FROM relationships WHERE operatorId = ? ORDER BY intimacy DESC""",
        mapper, 1) {
      bindString(0, operatorId)
    }

    override fun toString(): String = "Relationships.sq:getRelationships"
  }

  private inner class GetRelationshipsSyncQuery<out T : Any>(
    public val operatorId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("relationships", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("relationships", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(2_003_277_582,
        """SELECT relationships.id, relationships.operatorId, relationships.relatedOperatorId, relationships.relatedOperatorName, relationships.type, relationships.intimacy, relationships.isPreset, relationships.note FROM relationships WHERE operatorId = ? ORDER BY intimacy DESC""",
        mapper, 1) {
      bindString(0, operatorId)
    }

    override fun toString(): String = "Relationships.sq:getRelationshipsSync"
  }

  private inner class GetReverseRelationshipsSyncQuery<out T : Any>(
    public val relatedOperatorId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("relationships", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("relationships", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(24_965_148,
        """SELECT relationships.id, relationships.operatorId, relationships.relatedOperatorId, relationships.relatedOperatorName, relationships.type, relationships.intimacy, relationships.isPreset, relationships.note FROM relationships WHERE relatedOperatorId = ?""",
        mapper, 1) {
      bindString(0, relatedOperatorId)
    }

    override fun toString(): String = "Relationships.sq:getReverseRelationshipsSync"
  }

  private inner class GetRelationshipQuery<out T : Any>(
    public val operatorId: String,
    public val relatedOperatorId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("relationships", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("relationships", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(2_070_293_152,
        """SELECT relationships.id, relationships.operatorId, relationships.relatedOperatorId, relationships.relatedOperatorName, relationships.type, relationships.intimacy, relationships.isPreset, relationships.note FROM relationships WHERE operatorId = ? AND relatedOperatorId = ?""",
        mapper, 2) {
      bindString(0, operatorId)
      bindString(1, relatedOperatorId)
    }

    override fun toString(): String = "Relationships.sq:getRelationship"
  }

  private inner class GetRelationshipCountQuery<out T : Any>(
    public val operatorId: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("relationships", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("relationships", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(1_959_778_895,
        """SELECT COUNT(*) FROM relationships WHERE operatorId = ?""", mapper, 1) {
      bindString(0, operatorId)
    }

    override fun toString(): String = "Relationships.sq:getRelationshipCount"
  }
}
