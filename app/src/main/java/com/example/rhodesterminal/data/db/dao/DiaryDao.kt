package com.example.rhodesterminal.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.rhodesterminal.data.db.entity.DiaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diaries WHERE operatorId = :operatorId ORDER BY date DESC")
    fun getDiariesByOperator(operatorId: String): Flow<List<DiaryEntity>>

    @Query("SELECT * FROM diaries WHERE operatorId = :operatorId AND date = :date")
    suspend fun getDiary(operatorId: String, date: String): DiaryEntity?

    @Query("SELECT DISTINCT date FROM diaries WHERE operatorId = :operatorId ORDER BY date DESC")
    suspend fun getDiaryDates(operatorId: String): List<String>

    @Query("SELECT COUNT(*) FROM diaries")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(diary: DiaryEntity)

    @Query("DELETE FROM diaries WHERE createdAt < :cutoff")
    suspend fun deleteOldDiaries(cutoff: Long)
}
