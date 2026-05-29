package com.example.rhodesterminal.shared.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Long
import kotlin.String

public class MahjongSavesQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> getSave(mapper: (
    id: String,
    saveJson: String,
    ruleType: String,
    savedAt: Long,
  ) -> T): Query<T> = Query(253_192_838, arrayOf("mahjong_saves"), driver, "MahjongSaves.sq",
      "getSave",
      "SELECT mahjong_saves.id, mahjong_saves.saveJson, mahjong_saves.ruleType, mahjong_saves.savedAt FROM mahjong_saves WHERE id = 'current'") {
      cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getLong(3)!!
    )
  }

  public fun getSave(): Query<Mahjong_saves> = getSave { id, saveJson, ruleType, savedAt ->
    Mahjong_saves(
      id,
      saveJson,
      ruleType,
      savedAt
    )
  }

  public fun insertSave(
    id: String,
    saveJson: String,
    ruleType: String,
    savedAt: Long,
  ) {
    driver.execute(-1_038_186_909, """
        |INSERT OR REPLACE INTO mahjong_saves(id, saveJson, ruleType, savedAt)
        |VALUES (?, ?, ?, ?)
        """.trimMargin(), 4) {
          bindString(0, id)
          bindString(1, saveJson)
          bindString(2, ruleType)
          bindLong(3, savedAt)
        }
    notifyQueries(-1_038_186_909) { emit ->
      emit("mahjong_saves")
    }
  }

  public fun deleteSave() {
    driver.execute(-239_763_371, """DELETE FROM mahjong_saves WHERE id = 'current'""", 0)
    notifyQueries(-239_763_371) { emit ->
      emit("mahjong_saves")
    }
  }
}
