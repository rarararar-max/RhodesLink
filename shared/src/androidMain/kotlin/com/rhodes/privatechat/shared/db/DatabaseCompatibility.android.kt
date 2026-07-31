package com.rhodes.privatechat.shared.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log

object DatabaseCompatibility {
    private const val DB_NAME = "rhodes_terminal.db"
    private const val TAG = "DbCompatibility"
    // SQLDelight owns migrations 10+. Never advance beyond its last legacy schema here.
    private const val TARGET_VERSION = 9
    private const val SETTINGS_SP = "rhodes_settings"
    private const val DERIVED_CLEAN_KEY = "derived_data_cleaned_for_1_05"
    private const val MESSAGE_TIMESTAMPS_KEY = "chat_message_timestamps_normalized_v1"

    fun prepareBeforeOpen(context: Context) {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) return
        try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                ensureMahjongSavesTable(db)
                val memoryColumnsBefore = existingColumns(db, "memory_anchors")
                val needsMemoryAnchorReset = memoryAnchorCompatibilityColumns.any { it.first !in memoryColumnsBefore }
                val userVersion = db.rawQuery("PRAGMA user_version", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
                val hasPartialCompatibilityArtifacts = hasPartialCompatibilityArtifacts(db)

                if (userVersion < TARGET_VERSION && hasPartialCompatibilityArtifacts) {
                    ensureCompatibilitySchema(db)
                    if (tableExists(db, "diaries")) {
                        db.execSQL("DROP INDEX IF EXISTS idx_diaries_operator_date")
                    }
                    val targetVersion = if (hasFullCompatibilitySchema(db)) TARGET_VERSION else userVersion
                    db.execSQL("PRAGMA user_version = $targetVersion")
                } else if (!isDerivedDataCleaned(context)) {
                    ensureOperatorsCompatibility(db)
                }

                // Older databases must let SQLDelight run its numbered migrations first; adding
                // their columns here would make those ALTER TABLE statements fail.
                if (userVersion >= TARGET_VERSION) {
                    ensureCompatibilitySchema(db)
                    advanceUserVersionIfSchemaComplete(db, userVersion)
                }
                normalizeLegacyMessageTimestamps(context, db)
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
        if (!tableExists(db, "memory_items")) return
        val miColumns = existingColumns(db, "memory_items")
        if ("topicKey" in miColumns) {
            db.execSQL("PRAGMA user_version = $TARGET_VERSION")
        }
    }

    private fun ensureCompatibilitySchema(db: SQLiteDatabase) {
        ensureMahjongSavesTable(db)
        ensureOperatorsCompatibility(db)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS chat_display_events (
                messageId INTEGER NOT NULL,
                segmentIndex INTEGER NOT NULL DEFAULT -1,
                sessionId TEXT NOT NULL,
                revealOrder INTEGER NOT NULL,
                PRIMARY KEY (messageId, segmentIndex)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_display_events_session_order ON chat_display_events(sessionId, revealOrder)")
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
            table = "memory_items",
            createSql = """
                CREATE TABLE IF NOT EXISTS memory_items (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    ownerType TEXT NOT NULL, ownerId TEXT NOT NULL,
                    memoryLevel TEXT NOT NULL, memoryType TEXT NOT NULL,
                    sourceKind TEXT NOT NULL, sourceRefId TEXT NOT NULL DEFAULT '',
                    sessionId TEXT NOT NULL DEFAULT '', content TEXT NOT NULL,
                    nickname TEXT NOT NULL DEFAULT '', importance INTEGER NOT NULL DEFAULT 0,
                    privacy TEXT, unmetNeed INTEGER NOT NULL DEFAULT 0,
                    location TEXT, emotionValence TEXT NOT NULL DEFAULT 'neutral',
                    eventTime TEXT, createdAt INTEGER NOT NULL DEFAULT 0,
                    updatedAt INTEGER NOT NULL DEFAULT 0,
                    expiresAt INTEGER NOT NULL DEFAULT 9223372036854775807,
                    status TEXT NOT NULL DEFAULT 'active',
                    scheduledTime TEXT, action TEXT NOT NULL DEFAULT '',
                    careType TEXT NOT NULL DEFAULT '',
                    topicKey TEXT NOT NULL DEFAULT '', sourceActor TEXT NOT NULL DEFAULT '',
                    sourceTarget TEXT NOT NULL DEFAULT '', lastUsedAt INTEGER NOT NULL DEFAULT 0,
                    usedCount INTEGER NOT NULL DEFAULT 0, confidence REAL NOT NULL DEFAULT 0.8,
                    rawJson TEXT NOT NULL DEFAULT '', vectorId TEXT NOT NULL DEFAULT ''
                )
            """.trimIndent(),
            columns = memoryItemCompatibilityColumns
        )
        val newTables = mapOf(
            "mahjong_saves" to """
                CREATE TABLE IF NOT EXISTS mahjong_saves (
                    id TEXT NOT NULL PRIMARY KEY DEFAULT 'current',
                    saveJson TEXT NOT NULL,
                    ruleType TEXT NOT NULL DEFAULT '',
                    savedAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent(),
            "memory_batches" to """
                CREATE TABLE IF NOT EXISTS memory_batches (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    ownerType TEXT NOT NULL, ownerId TEXT NOT NULL,
                    sourceKind TEXT NOT NULL, targetLevel TEXT NOT NULL,
                    inputCount INTEGER NOT NULL DEFAULT 0, outputCount INTEGER NOT NULL DEFAULT 0,
                    windowStart INTEGER NOT NULL DEFAULT 0, windowEnd INTEGER NOT NULL DEFAULT 0,
                    promptVersion TEXT NOT NULL DEFAULT 'v1',
                    status TEXT NOT NULL DEFAULT 'done', createdAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent(),
            "memory_source_queue" to """
                CREATE TABLE IF NOT EXISTS memory_source_queue (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    sourceKind TEXT NOT NULL, ownerType TEXT NOT NULL, ownerId TEXT NOT NULL,
                    sourceRefId TEXT NOT NULL, contentText TEXT NOT NULL,
                    timestamp INTEGER NOT NULL DEFAULT 0,
                    processedL1 INTEGER NOT NULL DEFAULT 0, processedVector INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent(),
            "memory_links" to """
                CREATE TABLE IF NOT EXISTS memory_links (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    parentMemoryId INTEGER NOT NULL, childMemoryId INTEGER NOT NULL,
                    linkType TEXT NOT NULL DEFAULT 'merge', createdAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent(),
            "vector_memories" to """
                CREATE TABLE IF NOT EXISTS vector_memories (
                    id TEXT NOT NULL PRIMARY KEY,
                    ownerType TEXT NOT NULL, ownerId TEXT NOT NULL,
                    sourceType TEXT NOT NULL, sourceId TEXT NOT NULL,
                    content TEXT NOT NULL, importance REAL NOT NULL DEFAULT 0,
                    embeddingJson TEXT NOT NULL DEFAULT '[]', tags TEXT NOT NULL DEFAULT '',
                    visibility TEXT NOT NULL DEFAULT 'public', embeddingSignature TEXT NOT NULL DEFAULT '',
                    createdAt INTEGER NOT NULL DEFAULT 0,
                    expiresAt INTEGER NOT NULL DEFAULT 9223372036854775807
                )
            """.trimIndent()
        )
        for ((table, ddl) in newTables) {
            if (!tableExists(db, table)) {
                db.execSQL(ddl)
            }
        }
        if (tableExists(db, "vector_memories") && "embeddingSignature" !in existingColumns(db, "vector_memories")) {
            db.execSQL("ALTER TABLE vector_memories ADD COLUMN embeddingSignature TEXT NOT NULL DEFAULT ''")
        }
    }

    /** Gives legacy rows with the schema default timestamp a stable chronological position. */
    private fun normalizeLegacyMessageTimestamps(context: Context, db: SQLiteDatabase) {
        val prefs = context.getSharedPreferences(SETTINGS_SP, Context.MODE_PRIVATE)
        if (prefs.getBoolean(MESSAGE_TIMESTAMPS_KEY, false) || !tableExists(db, "chat_messages")) return
        data class LegacyMessage(val id: Long, val timestamp: Long)
        val rowsBySession = linkedMapOf<String, MutableList<LegacyMessage>>()
        db.rawQuery("SELECT sessionId, id, timestamp FROM chat_messages ORDER BY sessionId ASC, id ASC", null).use { cursor ->
            val sessionIndex = cursor.getColumnIndexOrThrow("sessionId")
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val timestampIndex = cursor.getColumnIndexOrThrow("timestamp")
            while (cursor.moveToNext()) {
                rowsBySession.getOrPut(cursor.getString(sessionIndex)) { mutableListOf() }
                    .add(LegacyMessage(cursor.getLong(idIndex), cursor.getLong(timestampIndex)))
            }
        }
        rowsBySession.forEach { (sessionId, messages) ->
            if (messages.none { it.timestamp <= 0L }) return@forEach
            // There is no safe calendar position to invent when every row lacks a timestamp.
            // The stable id ordering already fixes display order without changing the date.
            if (messages.all { it.timestamp <= 0L }) return@forEach
            val sessionLastTimeValue: Long = db.rawQuery("SELECT lastTime FROM chat_sessions WHERE id = ?", arrayOf(sessionId)).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            }
            val sessionLastTime: Long = if (sessionLastTimeValue > 0L) sessionLastTimeValue else System.currentTimeMillis()
            var index = 0
            while (index < messages.size) {
                if (messages[index].timestamp > 0L) {
                    index++
                    continue
                }
                val start = index
                while (index < messages.size && messages[index].timestamp <= 0L) index++
                val endExclusive = index
                val previous = messages.getOrNull(start - 1)?.timestamp?.takeIf { it > 0L }
                val next = messages.getOrNull(endExclusive)?.timestamp?.takeIf { it > 0L }
                val count = endExclusive - start
                val firstTimestamp = when {
                    previous != null && next != null && next > previous -> previous + (next - previous) / (count + 1)
                    previous != null -> previous + 1_000L
                    next != null -> (next - count * 1_000L).coerceAtLeast(1L)
                    else -> (sessionLastTime - count * 1_000L).coerceAtLeast(1L)
                }
                for (offset in 0 until count) {
                    val timestamp: Long = when {
                        previous != null && next != null && next > previous -> previous + (next - previous) * (offset + 1) / (count + 1)
                        else -> firstTimestamp + offset * 1_000L
                    }
                    db.execSQL("UPDATE chat_messages SET timestamp = ? WHERE sessionId = ? AND id = ? AND timestamp <= 0", arrayOf<Any>(timestamp, sessionId, messages[start + offset].id))
                }
            }
        }
        prefs.edit().putBoolean(MESSAGE_TIMESTAMPS_KEY, true).apply()
    }

    private fun ensureMahjongSavesTable(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS mahjong_saves (
                id TEXT NOT NULL PRIMARY KEY DEFAULT 'current',
                saveJson TEXT NOT NULL,
                ruleType TEXT NOT NULL DEFAULT '',
                savedAt INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
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
            columns = listOf(
                "activityLevel" to "REAL NOT NULL DEFAULT 0.5",
                "gender" to "TEXT NOT NULL DEFAULT ''",
                "memoryInjection" to "TEXT NOT NULL DEFAULT ''",
                "voiceName" to "TEXT NOT NULL DEFAULT ''",
                "voiceSpeed" to "TEXT NOT NULL DEFAULT ''",
                "voicePitch" to "TEXT NOT NULL DEFAULT ''",
            )
        )
    }

    private fun hasFullCompatibilitySchema(db: SQLiteDatabase): Boolean {
        val memoryColumns = existingColumns(db, "memory_anchors")
        val operatorColumns = existingColumns(db, "operators")
        return memoryAnchorCompatibilityColumns.all { it.first in memoryColumns } &&
            (operatorColumns.isEmpty() || "memoryInjection" in operatorColumns)
    }

    private fun hasPartialCompatibilityArtifacts(db: SQLiteDatabase): Boolean {
        val memoryColumns = existingColumns(db, "memory_anchors")
        val compatibilityMemoryColumns = memoryAnchorCompatibilityColumns.map { it.first }
        return memoryColumns.any { it in compatibilityMemoryColumns }
    }

    private val memoryAnchorCompatibilityColumns = listOf(
        "source" to "TEXT NOT NULL DEFAULT ''",
        "sourceName" to "TEXT NOT NULL DEFAULT ''",
        "sourceActor" to "TEXT NOT NULL DEFAULT ''",
        "sourceTarget" to "TEXT NOT NULL DEFAULT ''",
        "importance" to "TEXT NOT NULL DEFAULT ''",
        "knownFrom" to "TEXT NOT NULL DEFAULT ''"
    )

    private val memoryItemCompatibilityColumns = listOf(
        "topicKey" to "TEXT NOT NULL DEFAULT ''",
        "sourceActor" to "TEXT NOT NULL DEFAULT ''",
        "sourceTarget" to "TEXT NOT NULL DEFAULT ''",
        "lastUsedAt" to "INTEGER NOT NULL DEFAULT 0",
        "usedCount" to "INTEGER NOT NULL DEFAULT 0",
        "confidence" to "REAL NOT NULL DEFAULT 0.8"
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
