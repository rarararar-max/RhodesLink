package com.rhodes.privatechat.shared.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log

object DatabaseCompatibility {
    private const val DB_NAME = "rhodes_terminal.db"
    private const val TAG = "DbCompatibility"
    private const val TARGET_VERSION = 8
    private const val SETTINGS_SP = "rhodes_settings"
    private const val DERIVED_CLEAN_KEY = "derived_data_cleaned_for_1_05"

    fun prepareBeforeOpen(context: Context) {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) return
        try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                val memoryColumnsBefore = existingColumns(db, "memory_anchors")
                val needsMemoryAnchorReset = memoryAnchorCompatibilityColumns.any { it.first !in memoryColumnsBefore }
                val userVersion = db.rawQuery("PRAGMA user_version", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
                val hasPartialCompatibilityArtifacts = hasPartialCompatibilityArtifacts(db)

                if (userVersion < TARGET_VERSION && hasPartialCompatibilityArtifacts) {
                    ensureCompatibilitySchema(db)
                    if (tableExists(db, "diaries")) {
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_diaries_operator_date ON diaries(operatorId, date)")
                    }
                    val targetVersion = if (hasFullCompatibilitySchema(db)) TARGET_VERSION else userVersion
                    db.execSQL("PRAGMA user_version = $targetVersion")
                } else if (!isDerivedDataCleaned(context)) {
                    ensureOperatorsCompatibility(db)
                }

                ensureCompatibilitySchema(db)
                advanceUserVersionIfSchemaComplete(db, userVersion)
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

    private fun advanceUserVersionIfSchemaComplete(db: SQLiteDatabase, userVersion: Int) {
        if (userVersion >= TARGET_VERSION) return
        if (!tableExists(db, "operators")) return
        val opColumns = existingColumns(db, "operators")
        if ("memoryInjection" in opColumns) {
            db.execSQL("PRAGMA user_version = $TARGET_VERSION")
        }
    }

    private fun ensureCompatibilitySchema(db: SQLiteDatabase) {
        ensureOperatorsCompatibility(db)
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

    private fun ensureOperatorsCompatibility(db: SQLiteDatabase) {
        if (!tableExists(db, "operators")) return
        ensureColumns(
            db = db,
            table = "operators",
            createSql = """
                CREATE TABLE IF NOT EXISTS operators (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    title TEXT NOT NULL DEFAULT '',
                    description TEXT NOT NULL DEFAULT '',
                    gender TEXT NOT NULL DEFAULT '',
                    avatarUri TEXT NOT NULL DEFAULT '',
                    location TEXT NOT NULL DEFAULT '宿舍',
                    activity TEXT NOT NULL DEFAULT '休息',
                    emotion TEXT NOT NULL DEFAULT '平静',
                    intimacy INTEGER NOT NULL DEFAULT 0,
                    privatePrompt TEXT NOT NULL DEFAULT '',
                    groupPrompt TEXT NOT NULL DEFAULT '',
                    memoryInjection TEXT NOT NULL DEFAULT '',
                    userRelation TEXT NOT NULL DEFAULT '',
                    lmb INTEGER NOT NULL DEFAULT 10000,
                    attack REAL NOT NULL DEFAULT 0.5,
                    defense REAL NOT NULL DEFAULT 0.5,
                    meldPref TEXT NOT NULL DEFAULT 'medium',
                    activityLevel REAL NOT NULL DEFAULT 0.5
                )
            """.trimIndent(),
            columns = listOf("memoryInjection" to "TEXT NOT NULL DEFAULT ''")
        )
    }

    private fun hasFullCompatibilitySchema(db: SQLiteDatabase): Boolean {
        val memoryColumns = existingColumns(db, "memory_anchors")
        val worldColumns = existingColumns(db, "world_events")
        val operatorColumns = existingColumns(db, "operators")
        return memoryAnchorCompatibilityColumns.all { it.first in memoryColumns } &&
            worldEventCompatibilityColumns.all { it.first in worldColumns } &&
            (operatorColumns.isEmpty() || "memoryInjection" in operatorColumns)
    }

    private fun hasPartialCompatibilityArtifacts(db: SQLiteDatabase): Boolean {
        val memoryColumns = existingColumns(db, "memory_anchors")
        val worldColumns = existingColumns(db, "world_events")
        val compatibilityMemoryColumns = memoryAnchorCompatibilityColumns.map { it.first }
        return memoryColumns.any { it in compatibilityMemoryColumns } || worldColumns.isNotEmpty()
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
