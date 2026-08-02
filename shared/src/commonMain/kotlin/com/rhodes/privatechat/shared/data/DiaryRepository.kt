package com.rhodes.privatechat.shared.data

import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.*
import kotlinx.coroutines.Dispatchers
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DiaryRepository(private val wrapper: DatabaseWrapper) {

    private val db: RhodesDatabase get() = wrapper.database
    private val insertMutex = Mutex()

    // --- Diaries ---
    suspend fun insertDiary(diary: Diary): Diary = insertMutex.withLock { withContext(Dispatchers.Default) {
        val version = diary.version.takeIf { it > 0 } ?: db.diariesQueries.getNextVersion(diary.operatorId, diary.date).executeAsOne().toInt()
        db.diariesQueries.insertDiary(diary.operatorId, diary.operatorName, diary.content, diary.date, version.toLong(), diary.createdAt)
        val id = db.diariesQueries.getInsertedDiaryId(diary.operatorId, diary.date, version.toLong(), diary.createdAt, diary.content).executeAsOne()
        diary.copy(id = id, version = version)
    } }

    suspend fun getDiary(operatorId: String, date: String): Diary? = withContext(Dispatchers.Default) {
        db.diariesQueries.getDiary(operatorId, date) { id, opId, opName, content, date_, version, createdAt ->
            Diary(id, opId, opName, content, date_, version.toInt(), createdAt)
        }.executeAsOneOrNull()
    }

    fun getDiariesByOperator(operatorId: String): Flow<List<Diary>> =
        db.diariesQueries.getDiariesByOperator(operatorId) { id, opId, opName, content, date, version, createdAt ->
            Diary(id, opId, opName, content, date, version.toInt(), createdAt)
        }.asFlow().mapToList(Dispatchers.Default)

    fun getLatestDiaryCreatedAtByOperator(): Flow<Map<String, Long>> =
        db.diariesQueries.getLatestDiaryCreatedAtByOperator { operatorId, createdAt ->
            operatorId to createdAt
        }.asFlow().mapToList(Dispatchers.Default).map { it.toMap() }

    suspend fun getAllDiaryEntries(operatorId: String): List<Diary> = withContext(Dispatchers.Default) {
        db.diariesQueries.getAllDiaryEntries(operatorId) { id, opId, opName, content, date, version, createdAt ->
            Diary(id, opId, opName, content, date, version.toInt(), createdAt)
        }.executeAsList()
    }

    suspend fun getAllDiariesForBackup(): List<Diary> = withContext(Dispatchers.Default) {
        db.diariesQueries.getAllDiariesForBackup { id, opId, opName, content, date, version, createdAt ->
            Diary(id, opId, opName, content, date, version.toInt(), createdAt)
        }.executeAsList()
    }

    suspend fun getDiaryDates(operatorId: String): List<String> = withContext(Dispatchers.Default) {
        db.diariesQueries.getDiaryDates(operatorId).executeAsList()
    }

    suspend fun getDiaryCount(): Int = withContext(Dispatchers.Default) { db.diariesQueries.getCount().executeAsOne().toInt() }
    suspend fun deleteOldDiaries(cutoff: Long) = withContext(Dispatchers.Default) { db.diariesQueries.deleteOldDiaries(cutoff) }
}
