package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.*
import kotlinx.coroutines.Dispatchers
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DiaryRepository(private val wrapper: DatabaseWrapper) {

    private val db: RhodesDatabase get() = wrapper.database

    // --- Diaries ---
    suspend fun insertDiary(diary: Diary) = withContext(Dispatchers.Default) {
        db.diariesQueries.insertDiary(diary.operatorId, diary.operatorName, diary.content, diary.date, diary.createdAt)
    }

    suspend fun getDiary(operatorId: String, date: String): Diary? = withContext(Dispatchers.Default) {
        db.diariesQueries.getDiary(operatorId, date) { id, opId, opName, content, date_, createdAt ->
            Diary(id, opId, opName, content, date_, createdAt)
        }.executeAsOneOrNull()
    }

    fun getDiariesByOperator(operatorId: String): Flow<List<Diary>> =
        db.diariesQueries.getDiariesByOperator(operatorId) { id, opId, opName, content, date, createdAt ->
            Diary(id, opId, opName, content, date, createdAt)
        }.asFlow().mapToList(Dispatchers.Default)

    suspend fun getAllDiaryEntries(operatorId: String): List<Diary> = withContext(Dispatchers.Default) {
        db.diariesQueries.getAllDiaryEntries(operatorId) { id, opId, opName, content, date, createdAt ->
            Diary(id, opId, opName, content, date, createdAt)
        }.executeAsList()
    }

    suspend fun getAllDiariesForBackup(): List<Diary> = withContext(Dispatchers.Default) {
        db.diariesQueries.getAllDiariesForBackup { id, opId, opName, content, date, createdAt ->
            Diary(id, opId, opName, content, date, createdAt)
        }.executeAsList()
    }

    suspend fun getDiaryDates(operatorId: String): List<String> = withContext(Dispatchers.Default) {
        db.diariesQueries.getDiaryDates(operatorId).executeAsList()
    }

    suspend fun getDiaryCount(): Int = withContext(Dispatchers.Default) { db.diariesQueries.getCount().executeAsOne().toInt() }
    suspend fun deleteOldDiaries(cutoff: Long) = withContext(Dispatchers.Default) { db.diariesQueries.deleteOldDiaries(cutoff) }
}
