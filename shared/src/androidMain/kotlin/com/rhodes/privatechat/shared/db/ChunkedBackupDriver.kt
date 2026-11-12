package com.rhodes.privatechat.shared.db

import android.database.sqlite.SQLiteBlobTooBigException
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import java.io.ByteArrayOutputStream

/** Backup-only fallback. Never resize CursorWindow, truncate values, or skip records. */
class ChunkedBackupDriver(private val delegate: SqlDriver) : SqlDriver by delegate {
    companion object {
        private const val CHUNK_BYTES = 16 * 1024
        private const val INLINE_BYTES = 512
        private val tableSelect = Regex("""^\s*SELECT\s+(.+?)\s+FROM\s+([a-zA-Z_][a-zA-Z_0-9]*)\b""", RegexOption.IGNORE_CASE)
        private fun quoted(value: String) = "\"${value.replace("\"", "\"\"")}\""
    }

    override fun execute(identifier: Int?, sql: String, parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<Long> =
        error("Backup reader cannot modify the database")

    override fun <R> executeQuery(
        identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        return try {
            delegate.executeQuery(identifier, sql, mapper, parameters, binders)
        } catch (error: SQLiteBlobTooBigException) {
            val match = tableSelect.find(sql) ?: throw error
            // The mapper executes synchronously. One transaction keeps row metadata and all
            // subsequent byte slices consistent even while a user continues chatting.
            QueryResult.Value(RhodesDatabase(delegate).transactionWithResult {
                readChunked(match.groupValues[2], match.groupValues[1], sql, match.range.last + 1, mapper, parameters, binders).value
            })
        }
    }

    private fun <R> readChunked(
        table: String, selected: String, sql: String, prefixEnd: Int, mapper: (SqlCursor) -> QueryResult<R>,
        parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
        val tableColumns = delegate.executeQuery(null, "PRAGMA table_info(${quoted(table)})", { cursor ->
            val names = mutableListOf<String>()
            while (cursor.next().value) names += checkNotNull(cursor.getString(1))
            QueryResult.Value(names)
        }, 0).value
        val columns = if (selected.trim() == "*") tableColumns else selected.split(',').map { expression ->
            val parts = expression.trim().split('.')
            check(parts.size in 1..2 && (parts.size == 1 || parts[0] == table) && parts.last() in tableColumns) {
                "Unsupported backup column expression"
            }
            parts.last()
        }
        check(columns.isNotEmpty()) { "Missing backup table: $table" }
        // Only bounded prefixes, scalar lengths and SQLite types enter CursorWindow.
        val projection = columns.joinToString(", ") { name ->
            val col = quoted(name)
            "CASE WHEN typeof($col) IN ('text','blob') AND length(CAST($col AS BLOB)) > $INLINE_BYTES THEN NULL ELSE $col END, " +
                "length(CAST($col AS BLOB)), typeof($col)"
        }
        val source = "SELECT rowid AS __backup_row, * FROM ${quoted(table)}" + sql.substring(prefixEnd).trimEnd().removeSuffix(";")
        return delegate.executeQuery(null, "SELECT $projection, __backup_row FROM ($source)", { cursor ->
            mapper(object : SqlCursor {
                private val values = mutableMapOf<Int, ByteArray>()
                override fun next(): QueryResult<Boolean> {
                    values.clear()
                    return cursor.next()
                }
                private fun bytes(index: Int): ByteArray? {
                    if (cursor.getString(index * 3 + 2) == "null") return null
                    val length = checkNotNull(cursor.getLong(index * 3 + 1))
                    if (length <= INLINE_BYTES) return cursor.getBytes(index * 3)
                    return values.getOrPut(index) {
                        check(length <= Int.MAX_VALUE) { "Backup field exceeds addressable memory: $table.${columns[index]}" }
                        val rowId = checkNotNull(cursor.getLong(columns.size * 3))
                        val output = ByteArrayOutputStream()
                        var offset = 1L
                        while (offset <= length) {
                            val part = delegate.executeQuery(null,
                                "SELECT substr(CAST(${quoted(columns[index])} AS BLOB), ?, ?) FROM ${quoted(table)} WHERE rowid = ?",
                                { piece ->
                                    check(piece.next().value) { "Backup record disappeared: $table/$rowId" }
                                    QueryResult.Value(checkNotNull(piece.getBytes(0)))
                                }, 3, {
                                    bindLong(0, offset)
                                    bindLong(1, CHUNK_BYTES.toLong())
                                    bindLong(2, rowId)
                                }).value
                            check(part.isNotEmpty()) { "Incomplete backup field: $table/$rowId/${columns[index]}" }
                            output.write(part)
                            offset += part.size
                        }
                        check(output.size().toLong() == length) { "Backup field length mismatch: $table/$rowId" }
                        output.toByteArray()
                    }
                }
                override fun getString(index: Int): String? =
                    if ((cursor.getLong(index * 3 + 1) ?: 0) <= INLINE_BYTES) cursor.getString(index * 3)
                    else bytes(index)?.toString(Charsets.UTF_8)
                override fun getBytes(index: Int): ByteArray? = bytes(index)
                override fun getLong(index: Int): Long? = cursor.getLong(index * 3)
                override fun getDouble(index: Int): Double? = cursor.getDouble(index * 3)
                override fun getBoolean(index: Int): Boolean? = cursor.getBoolean(index * 3)
            })
        }, parameters, binders)
    }
}
