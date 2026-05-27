package com.example.rhodesterminal.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.rhodesterminal.data.db.entity.DispatchRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DispatchDao {
    @Query("SELECT * FROM dispatch_records WHERE status IN ('active', 'generating') ORDER BY startTime DESC")
    suspend fun getActiveDispatches(): List<DispatchRecordEntity>

    @Query("SELECT * FROM dispatch_records WHERE status NOT IN ('active', 'generating') ORDER BY startTime DESC")
    suspend fun getHistoryDispatches(): List<DispatchRecordEntity>

    @Query("SELECT * FROM dispatch_records WHERE id = :id")
    suspend fun getDispatch(id: String): DispatchRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DispatchRecordEntity)

    @Query("UPDATE dispatch_records SET logChain = :logChain, status = :status, endTime = :endTime, netProfit = :netProfit WHERE id = :id")
    suspend fun updateDispatch(id: String, logChain: String, status: String, endTime: Long = 0, netProfit: Int = 0)

    @Query("DELETE FROM dispatch_records WHERE status NOT IN ('active', 'generating') AND endTime > 0 AND endTime < :cutoff")
    suspend fun deleteOldDispatches(cutoff: Long)
}
