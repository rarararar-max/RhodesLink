package com.example.rhodesterminal.shared.data

import com.example.rhodesterminal.shared.db.DatabaseWrapper
import com.example.rhodesterminal.shared.db.RhodesDatabase
import com.example.rhodesterminal.shared.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MahjongRepository(private val wrapper: DatabaseWrapper) {

    private val db: RhodesDatabase get() = wrapper.database

    // --- Mahjong ---
    suspend fun getMahjongSave(): MahjongSave? = withContext(Dispatchers.Default) {
        db.mahjongSavesQueries.getSave().executeAsOneOrNull()?.let { MahjongSave(id = it.id, saveJson = it.saveJson, ruleType = it.ruleType, savedAt = it.savedAt) }
    }

    suspend fun saveMahjong(save: MahjongSave) = withContext(Dispatchers.Default) {
        db.mahjongSavesQueries.insertSave(save.id, save.saveJson, save.ruleType, save.savedAt)
    }

    suspend fun deleteMahjongSave() = withContext(Dispatchers.Default) { db.mahjongSavesQueries.deleteSave() }
}
