package com.example.rhodesterminal.shared.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Long
import kotlin.String

public class DispatchRecordsQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> getActiveDispatches(mapper: (
    id: String,
    taskType: String,
    durationHours: Long,
    budget: Long,
    netProfit: Long,
    operatorIds: String,
    logChain: String,
    status: String,
    startTime: Long,
    endTime: Long,
    totalSegments: Long,
    segmentInterval: Long,
    items: String,
  ) -> T): Query<T> = Query(-1_986_638_295, arrayOf("dispatch_records"), driver,
      "DispatchRecords.sq", "getActiveDispatches",
      "SELECT dispatch_records.id, dispatch_records.taskType, dispatch_records.durationHours, dispatch_records.budget, dispatch_records.netProfit, dispatch_records.operatorIds, dispatch_records.logChain, dispatch_records.status, dispatch_records.startTime, dispatch_records.endTime, dispatch_records.totalSegments, dispatch_records.segmentInterval, dispatch_records.items FROM dispatch_records WHERE status IN ('active', 'generating') ORDER BY startTime DESC") {
      cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getLong(2)!!,
      cursor.getLong(3)!!,
      cursor.getLong(4)!!,
      cursor.getString(5)!!,
      cursor.getString(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getLong(10)!!,
      cursor.getLong(11)!!,
      cursor.getString(12)!!
    )
  }

  public fun getActiveDispatches(): Query<Dispatch_records> = getActiveDispatches { id, taskType,
      durationHours, budget, netProfit, operatorIds, logChain, status, startTime, endTime,
      totalSegments, segmentInterval, items ->
    Dispatch_records(
      id,
      taskType,
      durationHours,
      budget,
      netProfit,
      operatorIds,
      logChain,
      status,
      startTime,
      endTime,
      totalSegments,
      segmentInterval,
      items
    )
  }

  public fun <T : Any> getHistoryDispatches(mapper: (
    id: String,
    taskType: String,
    durationHours: Long,
    budget: Long,
    netProfit: Long,
    operatorIds: String,
    logChain: String,
    status: String,
    startTime: Long,
    endTime: Long,
    totalSegments: Long,
    segmentInterval: Long,
    items: String,
  ) -> T): Query<T> = Query(1_276_233_185, arrayOf("dispatch_records"), driver,
      "DispatchRecords.sq", "getHistoryDispatches",
      "SELECT dispatch_records.id, dispatch_records.taskType, dispatch_records.durationHours, dispatch_records.budget, dispatch_records.netProfit, dispatch_records.operatorIds, dispatch_records.logChain, dispatch_records.status, dispatch_records.startTime, dispatch_records.endTime, dispatch_records.totalSegments, dispatch_records.segmentInterval, dispatch_records.items FROM dispatch_records WHERE status NOT IN ('active', 'generating') ORDER BY startTime DESC") {
      cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getLong(2)!!,
      cursor.getLong(3)!!,
      cursor.getLong(4)!!,
      cursor.getString(5)!!,
      cursor.getString(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getLong(10)!!,
      cursor.getLong(11)!!,
      cursor.getString(12)!!
    )
  }

  public fun getHistoryDispatches(): Query<Dispatch_records> = getHistoryDispatches { id, taskType,
      durationHours, budget, netProfit, operatorIds, logChain, status, startTime, endTime,
      totalSegments, segmentInterval, items ->
    Dispatch_records(
      id,
      taskType,
      durationHours,
      budget,
      netProfit,
      operatorIds,
      logChain,
      status,
      startTime,
      endTime,
      totalSegments,
      segmentInterval,
      items
    )
  }

  public fun <T : Any> getDispatch(id: String, mapper: (
    id: String,
    taskType: String,
    durationHours: Long,
    budget: Long,
    netProfit: Long,
    operatorIds: String,
    logChain: String,
    status: String,
    startTime: Long,
    endTime: Long,
    totalSegments: Long,
    segmentInterval: Long,
    items: String,
  ) -> T): Query<T> = GetDispatchQuery(id) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getLong(2)!!,
      cursor.getLong(3)!!,
      cursor.getLong(4)!!,
      cursor.getString(5)!!,
      cursor.getString(6)!!,
      cursor.getString(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getLong(10)!!,
      cursor.getLong(11)!!,
      cursor.getString(12)!!
    )
  }

  public fun getDispatch(id: String): Query<Dispatch_records> = getDispatch(id) { id_, taskType,
      durationHours, budget, netProfit, operatorIds, logChain, status, startTime, endTime,
      totalSegments, segmentInterval, items ->
    Dispatch_records(
      id_,
      taskType,
      durationHours,
      budget,
      netProfit,
      operatorIds,
      logChain,
      status,
      startTime,
      endTime,
      totalSegments,
      segmentInterval,
      items
    )
  }

  public fun insertDispatch(
    id: String,
    taskType: String,
    durationHours: Long,
    budget: Long,
    netProfit: Long,
    operatorIds: String,
    logChain: String,
    status: String,
    startTime: Long,
    endTime: Long,
    totalSegments: Long,
    segmentInterval: Long,
    items: String,
  ) {
    driver.execute(-560_056_626, """
        |INSERT OR REPLACE INTO dispatch_records(id, taskType, durationHours, budget, netProfit, operatorIds, logChain, status, startTime, endTime, totalSegments, segmentInterval, items)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 13) {
          bindString(0, id)
          bindString(1, taskType)
          bindLong(2, durationHours)
          bindLong(3, budget)
          bindLong(4, netProfit)
          bindString(5, operatorIds)
          bindString(6, logChain)
          bindString(7, status)
          bindLong(8, startTime)
          bindLong(9, endTime)
          bindLong(10, totalSegments)
          bindLong(11, segmentInterval)
          bindString(12, items)
        }
    notifyQueries(-560_056_626) { emit ->
      emit("dispatch_records")
    }
  }

  public fun updateDispatch(
    logChain: String,
    status: String,
    endTime: Long,
    netProfit: Long,
    id: String,
  ) {
    driver.execute(-1_013_015_330, """
        |UPDATE dispatch_records SET logChain = ?, status = ?, endTime = ?, netProfit = ?
        |WHERE id = ?
        """.trimMargin(), 5) {
          bindString(0, logChain)
          bindString(1, status)
          bindLong(2, endTime)
          bindLong(3, netProfit)
          bindString(4, id)
        }
    notifyQueries(-1_013_015_330) { emit ->
      emit("dispatch_records")
    }
  }

  public fun deleteOldDispatches(endTime: Long) {
    driver.execute(-197_470_039,
        """DELETE FROM dispatch_records WHERE status NOT IN ('active', 'generating') AND endTime > 0 AND endTime < ?""",
        1) {
          bindLong(0, endTime)
        }
    notifyQueries(-197_470_039) { emit ->
      emit("dispatch_records")
    }
  }

  private inner class GetDispatchQuery<out T : Any>(
    public val id: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("dispatch_records", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("dispatch_records", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> =
        driver.executeQuery(-2_054_047_915,
        """SELECT dispatch_records.id, dispatch_records.taskType, dispatch_records.durationHours, dispatch_records.budget, dispatch_records.netProfit, dispatch_records.operatorIds, dispatch_records.logChain, dispatch_records.status, dispatch_records.startTime, dispatch_records.endTime, dispatch_records.totalSegments, dispatch_records.segmentInterval, dispatch_records.items FROM dispatch_records WHERE id = ?""",
        mapper, 1) {
      bindString(0, id)
    }

    override fun toString(): String = "DispatchRecords.sq:getDispatch"
  }
}
