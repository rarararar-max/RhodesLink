package com.example.rhodesterminal.shared.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Double
import kotlin.Long
import kotlin.String

public class OperatorsQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> getAllOperators(mapper: (
    id: String,
    name: String,
    title: String,
    description: String,
    avatarUri: String,
    location: String,
    activity: String,
    emotion: String,
    intimacy: Long,
    privatePrompt: String,
    groupPrompt: String,
    userRelation: String,
    lmb: Long,
    attack: Double,
    defense: Double,
    meldPref: String,
  ) -> T): Query<T> = Query(-1_612_659_198, arrayOf("operators"), driver, "Operators.sq",
      "getAllOperators",
      "SELECT operators.id, operators.name, operators.title, operators.description, operators.avatarUri, operators.location, operators.activity, operators.emotion, operators.intimacy, operators.privatePrompt, operators.groupPrompt, operators.userRelation, operators.lmb, operators.attack, operators.defense, operators.meldPref FROM operators ORDER BY name ASC") {
      cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getString(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)!!,
      cursor.getString(9)!!,
      cursor.getString(10)!!,
      cursor.getString(11)!!,
      cursor.getLong(12)!!,
      cursor.getDouble(13)!!,
      cursor.getDouble(14)!!,
      cursor.getString(15)!!
    )
  }

  public fun getAllOperators(): Query<Operators> = getAllOperators { id, name, title, description,
      avatarUri, location, activity, emotion, intimacy, privatePrompt, groupPrompt, userRelation,
      lmb, attack, defense, meldPref ->
    Operators(
      id,
      name,
      title,
      description,
      avatarUri,
      location,
      activity,
      emotion,
      intimacy,
      privatePrompt,
      groupPrompt,
      userRelation,
      lmb,
      attack,
      defense,
      meldPref
    )
  }

  public fun <T : Any> getRecentChatOperators(mapper: (
    id: String,
    name: String,
    title: String,
    description: String,
    avatarUri: String,
    location: String,
    activity: String,
    emotion: String,
    intimacy: Long,
    privatePrompt: String,
    groupPrompt: String,
    userRelation: String,
    lmb: Long,
    attack: Double,
    defense: Double,
    meldPref: String,
  ) -> T): Query<T> = Query(1_743_920_232, arrayOf("operators", "chat_sessions"), driver,
      "Operators.sq", "getRecentChatOperators",
      "SELECT operators.id, operators.name, operators.title, operators.description, operators.avatarUri, operators.location, operators.activity, operators.emotion, operators.intimacy, operators.privatePrompt, operators.groupPrompt, operators.userRelation, operators.lmb, operators.attack, operators.defense, operators.meldPref FROM operators WHERE id IN (SELECT DISTINCT operatorId FROM chat_sessions) ORDER BY (SELECT lastTime FROM chat_sessions WHERE operatorId = operators.id ORDER BY lastTime DESC LIMIT 1) DESC") {
      cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getString(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)!!,
      cursor.getString(9)!!,
      cursor.getString(10)!!,
      cursor.getString(11)!!,
      cursor.getLong(12)!!,
      cursor.getDouble(13)!!,
      cursor.getDouble(14)!!,
      cursor.getString(15)!!
    )
  }

  public fun getRecentChatOperators(): Query<Operators> = getRecentChatOperators { id, name, title,
      description, avatarUri, location, activity, emotion, intimacy, privatePrompt, groupPrompt,
      userRelation, lmb, attack, defense, meldPref ->
    Operators(
      id,
      name,
      title,
      description,
      avatarUri,
      location,
      activity,
      emotion,
      intimacy,
      privatePrompt,
      groupPrompt,
      userRelation,
      lmb,
      attack,
      defense,
      meldPref
    )
  }

  public fun <T : Any> getOperator(id: String, mapper: (
    id: String,
    name: String,
    title: String,
    description: String,
    avatarUri: String,
    location: String,
    activity: String,
    emotion: String,
    intimacy: Long,
    privatePrompt: String,
    groupPrompt: String,
    userRelation: String,
    lmb: Long,
    attack: Double,
    defense: Double,
    meldPref: String,
  ) -> T): Query<T> = GetOperatorQuery(id) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getString(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)!!,
      cursor.getString(9)!!,
      cursor.getString(10)!!,
      cursor.getString(11)!!,
      cursor.getLong(12)!!,
      cursor.getDouble(13)!!,
      cursor.getDouble(14)!!,
      cursor.getString(15)!!
    )
  }

  public fun getOperator(id: String): Query<Operators> = getOperator(id) { id_, name, title,
      description, avatarUri, location, activity, emotion, intimacy, privatePrompt, groupPrompt,
      userRelation, lmb, attack, defense, meldPref ->
    Operators(
      id_,
      name,
      title,
      description,
      avatarUri,
      location,
      activity,
      emotion,
      intimacy,
      privatePrompt,
      groupPrompt,
      userRelation,
      lmb,
      attack,
      defense,
      meldPref
    )
  }

  public fun getCount(): Query<Long> = Query(-297_985_733, arrayOf("operators"), driver,
      "Operators.sq", "getCount", "SELECT COUNT(*) FROM operators") { cursor ->
    cursor.getLong(0)!!
  }

  public fun insertOperator(
    id: String,
    name: String,
    title: String,
    description: String,
    avatarUri: String,
    location: String,
    activity: String,
    emotion: String,
    intimacy: Long,
    privatePrompt: String,
    groupPrompt: String,
    userRelation: String,
    lmb: Long,
    attack: Double,
    defense: Double,
    meldPref: String,
  ) {
    driver.execute(-1_310_509_345, """
        |INSERT OR REPLACE INTO operators(id, name, title, description, avatarUri, location, activity, emotion, intimacy, privatePrompt, groupPrompt, userRelation, lmb, attack, defense, meldPref)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 16) {
          bindString(0, id)
          bindString(1, name)
          bindString(2, title)
          bindString(3, description)
          bindString(4, avatarUri)
          bindString(5, location)
          bindString(6, activity)
          bindString(7, emotion)
          bindLong(8, intimacy)
          bindString(9, privatePrompt)
          bindString(10, groupPrompt)
          bindString(11, userRelation)
          bindLong(12, lmb)
          bindDouble(13, attack)
          bindDouble(14, defense)
          bindString(15, meldPref)
        }
    notifyQueries(-1_310_509_345) { emit ->
      emit("operators")
    }
  }

  public fun updateOperator(
    name: String,
    title: String,
    description: String,
    avatarUri: String,
    location: String,
    activity: String,
    emotion: String,
    intimacy: Long,
    privatePrompt: String,
    groupPrompt: String,
    userRelation: String,
    lmb: Long,
    attack: Double,
    defense: Double,
    meldPref: String,
    id: String,
  ) {
    driver.execute(-1_763_468_049, """
        |UPDATE operators SET name = ?, title = ?, description = ?, avatarUri = ?, location = ?, activity = ?, emotion = ?, intimacy = ?, privatePrompt = ?, groupPrompt = ?, userRelation = ?, lmb = ?, attack = ?, defense = ?, meldPref = ?
        |WHERE id = ?
        """.trimMargin(), 16) {
          bindString(0, name)
          bindString(1, title)
          bindString(2, description)
          bindString(3, avatarUri)
          bindString(4, location)
          bindString(5, activity)
          bindString(6, emotion)
          bindLong(7, intimacy)
          bindString(8, privatePrompt)
          bindString(9, groupPrompt)
          bindString(10, userRelation)
          bindLong(11, lmb)
          bindDouble(12, attack)
          bindDouble(13, defense)
          bindString(14, meldPref)
          bindString(15, id)
        }
    notifyQueries(-1_763_468_049) { emit ->
      emit("operators")
    }
  }

  public fun updateIntimacy(intimacy: Long, id: String) {
    driver.execute(-688_971_089, """UPDATE operators SET intimacy = ? WHERE id = ?""", 2) {
          bindLong(0, intimacy)
          bindString(1, id)
        }
    notifyQueries(-688_971_089) { emit ->
      emit("operators")
    }
  }

  public fun updatePrompts(
    privatePrompt: String,
    groupPrompt: String,
    id: String,
  ) {
    driver.execute(1_728_260_580,
        """UPDATE operators SET privatePrompt = ?, groupPrompt = ? WHERE id = ?""", 3) {
          bindString(0, privatePrompt)
          bindString(1, groupPrompt)
          bindString(2, id)
        }
    notifyQueries(1_728_260_580) { emit ->
      emit("operators")
    }
  }

  public fun deleteOperator(id: String) {
    driver.execute(-391_649_327, """DELETE FROM operators WHERE id = ?""", 1) {
          bindString(0, id)
        }
    notifyQueries(-391_649_327) { emit ->
      emit("operators")
    }
  }

  public fun insertAllOperators(
    id: String,
    name: String,
    title: String,
    description: String,
    avatarUri: String,
    location: String,
    activity: String,
    emotion: String,
    intimacy: Long,
    privatePrompt: String,
    groupPrompt: String,
    userRelation: String,
    lmb: Long,
    attack: Double,
    defense: Double,
    meldPref: String,
  ) {
    driver.execute(-1_206_562_711, """
        |INSERT OR REPLACE INTO operators(id, name, title, description, avatarUri, location, activity, emotion, intimacy, privatePrompt, groupPrompt, userRelation, lmb, attack, defense, meldPref)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 16) {
          bindString(0, id)
          bindString(1, name)
          bindString(2, title)
          bindString(3, description)
          bindString(4, avatarUri)
          bindString(5, location)
          bindString(6, activity)
          bindString(7, emotion)
          bindLong(8, intimacy)
          bindString(9, privatePrompt)
          bindString(10, groupPrompt)
          bindString(11, userRelation)
          bindLong(12, lmb)
          bindDouble(13, attack)
          bindDouble(14, defense)
          bindString(15, meldPref)
        }
    notifyQueries(-1_206_562_711) { emit ->
      emit("operators")
    }
  }

  public fun deleteAllOperators() {
    driver.execute(-1_437_321_125, """DELETE FROM operators""", 0)
    notifyQueries(-1_437_321_125) { emit ->
      emit("operators")
    }
  }

  private inner class GetOperatorQuery<out T : Any>(
    public val id: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("operators", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("operators", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(275_996_920,
        """SELECT operators.id, operators.name, operators.title, operators.description, operators.avatarUri, operators.location, operators.activity, operators.emotion, operators.intimacy, operators.privatePrompt, operators.groupPrompt, operators.userRelation, operators.lmb, operators.attack, operators.defense, operators.meldPref FROM operators WHERE id = ?""",
        mapper, 1) {
      bindString(0, id)
    }

    override fun toString(): String = "Operators.sq:getOperator"
  }
}
