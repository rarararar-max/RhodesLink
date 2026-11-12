package com.rhodes.privatechat

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChatArchiveIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun wrapper(database: RhodesDatabase): DatabaseWrapper = DatabaseWrapper::class.java
        .getDeclaredConstructor(Context::class.java, RhodesDatabase::class.java).apply { isAccessible = true }
        .newInstance(context, database)

    private fun seed(driver: AndroidSqliteDriver) {
        driver.execute(null, "INSERT INTO memories(id,sessionId,operatorId,type,content) VALUES(1,'s_%','kaltsit','SHORT_TERM','old plot'),(2,'profile','kaltsit','LONG_TERM','global impression'),(3,'other','amiya','SHORT_TERM','unrelated')", 0)
        driver.execute(null, "INSERT INTO memory_items(id,ownerType,ownerId,memoryLevel,memoryType,sourceKind,sessionId,content,vectorId) VALUES(1,'operator','kaltsit','L1','event','PRIVATE_CHAT','s_%','old event','v1'),(2,'operator','kaltsit','L2','event','PRIVATE_CHAT','derived','derived event','v2'),(3,'operator','amiya','L1','event','PRIVATE_CHAT','other','other event','v3')", 0)
        driver.execute(null, "INSERT INTO memory_links(parentMemoryId,childMemoryId) VALUES(1,2)", 0)
        driver.execute(null, "INSERT INTO vector_memories(id,ownerType,ownerId,sourceType,sourceId,content) VALUES('v1','operator','kaltsit','memory_v2_l1','s_%:epoch1:1-2','old'),('v2','operator','kaltsit','memory_v2_l2','derived','derived'),('v3','operator','amiya','memory_v2_l1','other','other')", 0)
        driver.execute(null, "INSERT INTO memory_source_queue(sourceKind,ownerType,ownerId,sourceRefId,contentText) VALUES('PRIVATE_CHAT','operator','kaltsit','s_%:epoch1:1-2','old'),('PRIVATE_CHAT','operator','amiya','s_X:epoch1:1-2','unrelated')", 0)
        driver.execute(null, "INSERT INTO chat_messages(id,sessionId,senderName,content,isMe) VALUES(10,'s_%','博士','old current',1)", 0)
    }
    private val history = ChatHistorySegment("old_branch", "s_%", "旧进度", "archive_load", "[]", 123)
    private val summary = Memory(sessionId = "s_%", operatorId = "kaltsit", type = MemoryType.SHORT_TERM, content = "saved plot", createdAt = 124)
    private fun restored(id: Long) = ChatMessage(id, "s_%", senderName = "博士", content = "saved message", isMe = true)

    @Test fun restoreRetainsOldEvidenceAndGlobalImpressionsWithoutCrossSessionDamage() = runBlocking {
        AndroidSqliteDriver(RhodesDatabase.Schema, context, null).use { driver ->
            seed(driver)
            val db = RhodesDatabase(driver)
            ChatRepository(wrapper(db)).restoreChatArchive("s_%", "kaltsit", history, listOf(restored(20)), summary)
            val memories = db.memoriesQueries.getAllMemoriesForBackup().executeAsList()
            assertEquals(4, memories.size)
            assertEquals("old_branch", memories.first { it.id == 1L }.sessionId)
            assertEquals("global impression", db.memoriesQueries.getLatestLongTermImpression("kaltsit").executeAsOne().content)
            assertEquals("saved plot", db.memoriesQueries.getLatestMemory("s_%", "SHORT_TERM").executeAsOne().content)
            assertEquals("archived_timeline", db.memoryItemsQueries.getMemoryItemById(1).executeAsOne().status)
            assertEquals("old_branch", db.memoryItemsQueries.getMemoryItemById(1).executeAsOne().sessionId)
            assertEquals("archived", db.memoryItemsQueries.getMemoryItemById(2).executeAsOne().status)
            assertEquals("active", db.memoryItemsQueries.getMemoryItemById(3).executeAsOne().status)
            val vectors = db.vectorMemoriesQueries.getVectorMemoriesByOwner("operator", "kaltsit").executeAsList()
            assertEquals(2, vectors.size)
            assertTrue(vectors.all { it.expiresAt == 0L })
            assertEquals("archived_timeline", db.memorySourceQueueQueries.getMemorySourceStatus(1).executeAsOne().status)
            assertEquals("pending", db.memorySourceQueueQueries.getMemorySourceStatus(2).executeAsOne().status)
            assertEquals(listOf(20L), db.chatMessagesQueries.getMessages("s_%").executeAsList().map { it.id })
            assertEquals(1, db.chatArchivesQueries.getHistorySegmentsBySession("s_%").executeAsList().size)
        }
    }
    @Test fun failedRestoreRollsBackHistoryMemoryAndMessagesTogether() = runBlocking {
        AndroidSqliteDriver(RhodesDatabase.Schema, context, null).use { driver ->
            seed(driver)
            val db = RhodesDatabase(driver)
            try {
                ChatRepository(wrapper(db)).restoreChatArchive("s_%", "kaltsit", history, listOf(restored(20), restored(20)), summary)
                fail("duplicate message ids must fail")
            } catch (_: android.database.sqlite.SQLiteConstraintException) { }
            assertEquals("s_%", db.memoriesQueries.getAllMemoriesForBackup().executeAsList().first { it.id == 1L }.sessionId)
            assertEquals("active", db.memoryItemsQueries.getMemoryItemById(1).executeAsOne().status)
            assertEquals("active", db.memoryItemsQueries.getMemoryItemById(2).executeAsOne().status)
            assertTrue(db.vectorMemoriesQueries.getVectorMemoriesByOwner("operator", "kaltsit").executeAsList().all { it.expiresAt > 0 })
            assertEquals(listOf(10L), db.chatMessagesQueries.getMessages("s_%").executeAsList().map { it.id })
            assertTrue(db.chatArchivesQueries.getHistorySegmentsBySession("s_%").executeAsList().isEmpty())
        }
    }
    @Test fun archiveListDoesNotMaterializeLargeMessageOrContextFields() {
        AndroidSqliteDriver(RhodesDatabase.Schema, context, null).use { driver ->
            val db = RhodesDatabase(driver)
            val large = "长剧情".repeat(500_000)
            db.chatArchivesQueries.insertArchive("archive", "s", "op", "长剧情", "", "offline", large, "简介", large, "ready", 1, 1)
            val item = db.chatArchivesQueries.getArchiveMetadataBySession("s").executeAsOne()
            assertEquals("", item.messagesJson)
            assertEquals("", item.stateJson)
            assertEquals("简介", item.summary)
        }
    }
}
