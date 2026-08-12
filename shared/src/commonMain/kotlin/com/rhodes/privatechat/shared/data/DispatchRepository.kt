package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DispatchRepository(private val wrapper: DatabaseWrapper) {

    private val db: RhodesDatabase get() = wrapper.database

    // --- Dispatches ---
    suspend fun getActiveDispatches(): List<DispatchRecord> = withContext(Dispatchers.Default) {
        db.dispatchRecordsQueries.getActiveDispatches() { id, taskType, durationHours, budget, netProfit, opIds, logChain, status, startTime, endTime, totalSegments, segmentInterval, items ->
            DispatchRecord(id, taskType, durationHours.toInt(), budget.toInt(), netProfit.toInt(), opIds, logChain, status, startTime, endTime, totalSegments.toInt(), segmentInterval, items)
        }.executeAsList()
    }

    suspend fun getHistoryDispatches(): List<DispatchRecord> = withContext(Dispatchers.Default) {
        db.dispatchRecordsQueries.getHistoryDispatches() { id, taskType, durationHours, budget, netProfit, opIds, logChain, status, startTime, endTime, totalSegments, segmentInterval, items ->
            DispatchRecord(id, taskType, durationHours.toInt(), budget.toInt(), netProfit.toInt(), opIds, logChain, status, startTime, endTime, totalSegments.toInt(), segmentInterval, items)
        }.executeAsList()
    }

    suspend fun getAllDispatches(): List<DispatchRecord> = withContext(Dispatchers.Default) {
        db.dispatchRecordsQueries.getAllDispatches() { id, taskType, durationHours, budget, netProfit, opIds, logChain, status, startTime, endTime, totalSegments, segmentInterval, items ->
            DispatchRecord(id, taskType, durationHours.toInt(), budget.toInt(), netProfit.toInt(), opIds, logChain, status, startTime, endTime, totalSegments.toInt(), segmentInterval, items)
        }.executeAsList()
    }

    suspend fun getDispatch(id: String): DispatchRecord? = withContext(Dispatchers.Default) {
        db.dispatchRecordsQueries.getDispatch(id) { id_, taskType, durationHours, budget, netProfit, opIds, logChain, status, startTime, endTime, totalSegments, segmentInterval, items ->
            DispatchRecord(id_, taskType, durationHours.toInt(), budget.toInt(), netProfit.toInt(), opIds, logChain, status, startTime, endTime, totalSegments.toInt(), segmentInterval, items)
        }.executeAsOneOrNull()
    }

    suspend fun insertDispatch(record: DispatchRecord) = withContext(Dispatchers.Default) {
        db.dispatchRecordsQueries.insertDispatch(record.id, record.taskType, record.durationHours.toLong(), record.budget.toLong(), record.netProfit.toLong(), record.operatorIds, record.logChain, record.status, record.startTime, record.endTime, record.totalSegments.toLong(), record.segmentInterval, record.items)
    }

    suspend fun updateDispatch(id: String, logChain: String, status: String, endTime: Long = 0, netProfit: Int = 0) = withContext(Dispatchers.Default) {
        db.dispatchRecordsQueries.updateDispatch(logChain, status, endTime, netProfit.toLong(), id)
    }

    suspend fun updateDispatchFull(id: String, logChain: String, status: String, endTime: Long = 0, netProfit: Int = 0, totalSegments: Int = 0, segmentInterval: Long = 0) = withContext(Dispatchers.Default) {
        db.dispatchRecordsQueries.updateDispatchFull(logChain, status, endTime, netProfit.toLong(), totalSegments.toLong(), segmentInterval, id)
    }

    suspend fun deleteOldDispatches(cutoff: Long) = withContext(Dispatchers.Default) { db.dispatchRecordsQueries.deleteOldDispatches(cutoff) }
}
