package com.rhodes.privatechat.shared.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log

object DatabaseCompatibility {
    private const val DB_NAME = "rhodes_terminal.db"
    private const val TAG = "DbCompatibility"

    fun repairBeforeOpen(context: Context) {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) return
        try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                ensureColumns(
                    db = db,
                    table = "memory_anchors",
                    createSql = """
                        CREATE TABLE IF NOT EXISTS memory_anchors (
                            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                            sessionId TEXT NOT NULL,
                            operatorId TEXT NOT NULL,
                            type TEXT NOT NULL,
                            content TEXT NOT NULL,
                            isPrivate INTEGER NOT NULL DEFAULT 0,
                            createdAt INTEGER NOT NULL DEFAULT 0,
                            expiresAt INTEGER NOT NULL DEFAULT 0
                        )
                    """.trimIndent(),
                    columns = listOf(
                        "source" to "TEXT NOT NULL DEFAULT ''",
                        "sourceName" to "TEXT NOT NULL DEFAULT ''",
                        "sourceActor" to "TEXT NOT NULL DEFAULT ''",
                        "sourceTarget" to "TEXT NOT NULL DEFAULT ''",
                        "importance" to "TEXT NOT NULL DEFAULT ''",
                        "knownFrom" to "TEXT NOT NULL DEFAULT ''"
                    )
                )
                ensureColumns(
                    db = db,
                    table = "world_events",
                    createSql = """
                        CREATE TABLE IF NOT EXISTS world_events (
                            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                            type TEXT NOT NULL,
                            actorId TEXT NOT NULL DEFAULT '',
                            actorName TEXT NOT NULL DEFAULT '',
                            targetId TEXT NOT NULL DEFAULT '',
                            targetName TEXT NOT NULL DEFAULT '',
                            source TEXT NOT NULL DEFAULT '',
                            sourceId TEXT NOT NULL DEFAULT '',
                            content TEXT NOT NULL,
                            createdAt INTEGER NOT NULL DEFAULT 0,
                            expiresAt INTEGER NOT NULL DEFAULT 9223372036854775807,
                            consumedBy TEXT NOT NULL DEFAULT ''
                        )
                    """.trimIndent(),
                    columns = listOf(
                        "originType" to "TEXT NOT NULL DEFAULT ''",
                        "chainDepth" to "INTEGER NOT NULL DEFAULT 0",
                        "rootEventId" to "INTEGER NOT NULL DEFAULT 0"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "数据库兼容修复失败: ${e.message}", e)
        }
    }

    private fun ensureColumns(
        db: SQLiteDatabase,
        table: String,
        createSql: String,
        columns: List<Pair<String, String>>
    ) {
        db.execSQL(createSql)
        val existing = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIndex >= 0) existing.add(cursor.getString(nameIndex))
            }
        }
        for ((name, definition) in columns) {
            if (name !in existing) {
                db.execSQL("ALTER TABLE $table ADD COLUMN $name $definition")
            }
        }
    }
}
