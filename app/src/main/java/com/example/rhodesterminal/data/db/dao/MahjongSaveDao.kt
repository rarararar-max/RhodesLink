package com.example.rhodesterminal.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.rhodesterminal.data.db.entity.MahjongSaveEntity

@Dao
interface MahjongSaveDao {
    @Query("SELECT * FROM mahjong_saves WHERE id = 'current'")
    suspend fun getSave(): MahjongSaveEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: MahjongSaveEntity)

    @Query("DELETE FROM mahjong_saves WHERE id = 'current'")
    suspend fun deleteSave()
}
