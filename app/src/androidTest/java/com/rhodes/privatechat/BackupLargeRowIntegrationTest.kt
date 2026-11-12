package com.rhodes.privatechat

import android.database.sqlite.SQLiteBlobTooBigException
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.rhodes.privatechat.data.ExportPayload
import com.rhodes.privatechat.data.backup.BackupFileReader
import com.rhodes.privatechat.data.backup.BackupFileWriter
import com.rhodes.privatechat.data.backup.BackupPayload
import com.rhodes.privatechat.shared.db.ChunkedBackupDriver
import com.rhodes.privatechat.shared.db.RhodesDatabase
import com.rhodes.privatechat.shared.model.ChatArchive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class BackupLargeRowIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val text get() = ("罗德岛🌨️\u0000档案𠮷\n").repeat(240_000)

    @Test fun oversizedArchiveSurvivesDatabaseReadBackupAndRestoredDatabase() {
        AndroidSqliteDriver(RhodesDatabase.Schema, context, null).use { driver ->
            val original = RhodesDatabase(driver)
            val large = text
            original.chatArchivesQueries.insertArchive("big", "s", "amiya", "档案", "", "offline", large, "", large, "ready", 1, 2)
            original.chatArchivesQueries.insertArchive("small", "s", "amiya", "小档案", "", "online", "[]", "", "{}", "ready", 0, 0)
            assertThrows(SQLiteBlobTooBigException::class.java) { original.chatArchivesQueries.getAllArchives().executeAsList() }
            val safe = RhodesDatabase(ChunkedBackupDriver(driver))
            val archives = safe.chatArchivesQueries.getAllArchives { id, sid, op, title, note, mode, messages, summary, state, status, created, updated ->
                ChatArchive(id, sid, op, title, note, mode, messages, summary, state, status, created, updated)
            }.executeAsList()
            assertEquals(listOf("big", "small"), archives.map { it.id })
            assertEquals(large, archives.first().messagesJson)
            assertEquals(large, archives.first().stateJson)
            val output = ByteArrayOutputStream()
            BackupFileWriter("test", 1).writeFullBackup(output, BackupPayload(ExportPayload(type = "full_backup",
                operators = listOf(com.rhodes.privatechat.data.OperatorExport(id = "amiya", name = "阿米娅")),
                sessions = listOf(com.rhodes.privatechat.data.SessionExport("s", "amiya", "阿米娅"))), chatArchives = archives))
            val restored = BackupFileReader().read(ByteArrayInputStream(output.toByteArray())).payload.chatArchives
            assertEquals(archives, restored)
            AndroidSqliteDriver(RhodesDatabase.Schema, context, null).use { restoredDriver ->
                val restoredDb = RhodesDatabase(restoredDriver)
                restored.forEach { a -> restoredDb.chatArchivesQueries.insertArchive(a.id, a.sessionId, a.operatorId, a.title, a.note, a.mode, a.messagesJson, a.summary, a.stateJson, a.status, a.createdAt, a.updatedAt) }
                val reread = RhodesDatabase(ChunkedBackupDriver(restoredDriver)).chatArchivesQueries.getAllArchives().executeAsList()
                assertEquals(large, reread.first().messagesJson)
                assertEquals(large, reread.first().stateJson)
                assertEquals(2, reread.size)
            }
        }
    }

    @Test fun oversizedKnowledgeOriginalAndMultipleRowsRemainComplete() {
        AndroidSqliteDriver(RhodesDatabase.Schema, context, null).use { driver ->
            val original = RhodesDatabase(driver)
            val large = text
            original.knowledgeBasesQueries.insertKnowledgeBase("book", "长资料", large, "雪.txt", "txt", "user", "smart", "pending", "", 1, 2)
            original.knowledgeBasesQueries.insertKnowledgeBase("empty", "空资料", "", "", "", "user", "smart", "pending", "", 0, 0)
            val safe = RhodesDatabase(ChunkedBackupDriver(driver))
            val books = safe.knowledgeBasesQueries.getAllKnowledgeBases().executeAsList()
            assertEquals(2, books.size)
            assertEquals(large, books.first().rawContent)
            assertEquals("", books.last().rawContent)
            assertEquals(2L, books.first().updatedAt)
            // Parameter bindings are retained in the fallback, too.
            assertEquals(large, safe.knowledgeBasesQueries.getKnowledgeBase("book").executeAsOne().rawContent)
        }
    }

    @Test fun chunkingPreservesNullBlobNumbersAndRowsWhoseCombinedFieldsOverflow() {
        AndroidSqliteDriver(RhodesDatabase.Schema, context, null).use { driver ->
            driver.execute(null, "CREATE TABLE backup_fixture(id INTEGER PRIMARY KEY, a TEXT, b TEXT, optional TEXT, bytes BLOB, enabled INTEGER, score REAL)", 0)
            val a = "甲🌨".repeat(230_000)
            val b = "乙𠮷".repeat(230_000)
            val blob = ByteArray(3 * 1024 * 1024) { (it % 251).toByte() }
            driver.execute(null, "INSERT INTO backup_fixture VALUES(1, ?, ?, NULL, ?, 1, 1.25)", 3) {
                bindString(0, a); bindString(1, b); bindBytes(2, blob)
            }
            val safe = ChunkedBackupDriver(driver)
            safe.executeQuery(null, "SELECT * FROM backup_fixture WHERE id = ? ORDER BY id", { cursor ->
                assertTrue(cursor.next().value)
                assertEquals(1L, cursor.getLong(0))
                assertEquals(a, cursor.getString(1))
                assertEquals(b, cursor.getString(2))
                assertNull(cursor.getString(3))
                assertArrayEquals(blob, cursor.getBytes(4))
                assertEquals(true, cursor.getBoolean(5))
                assertEquals(1.25, cursor.getDouble(6)!!, 0.0)
                assertFalse(cursor.next().value)
                QueryResult.Value(Unit)
            }, 1) { bindLong(0, 1) }.value
            assertThrows(IllegalStateException::class.java) { safe.execute(null, "DELETE FROM backup_fixture", 0) }
        }
    }
}
