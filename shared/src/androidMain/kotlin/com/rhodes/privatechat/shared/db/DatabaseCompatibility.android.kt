package com.rhodes.privatechat.shared.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log

object DatabaseCompatibility {
    private const val DB_NAME = "rhodes_terminal.db"
    private const val TAG = "DbCompatibility"
    private const val TARGET_VERSION = 7
    private const val SETTINGS_SP = "rhodes_settings"
    private const val DERIVED_CLEAN_KEY = "derived_data_cleaned_for_1_05"

    fun prepareBeforeOpen(context: Context) {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) return
        try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                val userVersion = db.rawQuery("PRAGMA user_version", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
                val memoryColumns = existingColumns(db, "memory_anchors")
                val worldColumns = existingColumns(db, "world_events")
                val hasHalfMigration = userVersion < TARGET_VERSION && (
                    memoryColumns.any { it in memoryAnchorCompatibilityColumns.map { col -> col.first } } ||
                        worldColumns.isNotEmpty()
                )

                if (hasHalfMigration) {
                    ensureCompatibilitySchema(db)
                    clearDerivedTables(db)
                    if (tableExists(db, "diaries")) {
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_diaries_operator_date ON diaries(operatorId, date)")
                    }
                    db.execSQL("PRAGMA user_version = $TARGET_VERSION")
                    Log.w(TAG, "Recovered half-migrated database and advanced user_version to $TARGET_VERSION")
                } else if (!isDerivedDataCleaned(context)) {
                    clearDerivedTables(db)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "数据库兼容准备失败: ${e.message}", e)
        }
    }

    fun markDerivedDataCleaned(context: Context) {
        context.getSharedPreferences(SETTINGS_SP, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(DERIVED_CLEAN_KEY, true)
            .apply()
    }

    private fun isDerivedDataCleaned(context: Context): Boolean = try {
        context.getSharedPreferences(SETTINGS_SP, Context.MODE_PRIVATE)
            .getBoolean(DERIVED_CLEAN_KEY, false)
    } catch (_: Exception) {
        false
    }

    private fun ensureCompatibilitySchema(db: SQLiteDatabase) {
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
                    expiresAt INTEGER NOT NULL DEFAULT 0,
                    source TEXT NOT NULL DEFAULT '',
                    sourceName TEXT NOT NULL DEFAULT '',
                    sourceActor TEXT NOT NULL DEFAULT '',
                    sourceTarget TEXT NOT NULL DEFAULT '',
                    importance TEXT NOT NULL DEFAULT '',
                    knownFrom TEXT NOT NULL DEFAULT ''
                )
            """.trimIndent(),
            columns = memoryAnchorCompatibilityColumns
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
                    consumedBy TEXT NOT NULL DEFAULT '',
                    originType TEXT NOT NULL DEFAULT '',
                    chainDepth INTEGER NOT NULL DEFAULT 0,
                    rootEventId INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent(),
            columns = worldEventCompatibilityColumns
        )
    }

    private fun clearDerivedTables(db: SQLiteDatabase) {
        val tables = listOf(
            "moment_likes",
            "moment_comments",
            "moments",
            "diaries",
            "memories",
            "memory_anchors",
            "world_events",
            "dispatch_records"
        )
        for (table in tables) {
            if (tableExists(db, table)) {
                db.execSQL("DELETE FROM $table")
            }
        }
    }

    private val memoryAnchorCompatibilityColumns = listOf(
        "source" to "TEXT NOT NULL DEFAULT ''",
        "sourceName" to "TEXT NOT NULL DEFAULT ''",
        "sourceActor" to "TEXT NOT NULL DEFAULT ''",
        "sourceTarget" to "TEXT NOT NULL DEFAULT ''",
        "importance" to "TEXT NOT NULL DEFAULT ''",
        "knownFrom" to "TEXT NOT NULL DEFAULT ''"
    )

    private val worldEventCompatibilityColumns = listOf(
        "originType" to "TEXT NOT NULL DEFAULT ''",
        "chainDepth" to "INTEGER NOT NULL DEFAULT 0",
        "rootEventId" to "INTEGER NOT NULL DEFAULT 0"
    )

    private fun ensureColumns(
        db: SQLiteDatabase,
        table: String,
        createSql: String,
        columns: List<Pair<String, String>>
    ) {
        db.execSQL(createSql)
        val existing = existingColumns(db, table)
        for ((name, definition) in columns) {
            if (name !in existing) {
                db.execSQL("ALTER TABLE $table ADD COLUMN $name $definition")
            }
        }
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { cursor ->
            cursor.moveToFirst()
        }

    private fun existingColumns(db: SQLiteDatabase, table: String): Set<String> {
        if (!tableExists(db, table)) return emptySet()
        val existing = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIndex >= 0) existing.add(cursor.getString(nameIndex))
            }
        }
        return existing
    }
}
