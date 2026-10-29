package com.rhodes.privatechat

import com.rhodes.privatechat.util.IncrementalMessageCache
import org.junit.Assert.*
import org.junit.Test

class IncrementalMessageCacheTest {
    private data class Row(val id: Long, val text: String)
    @Test fun appendedAndChangedRowsOnlyAreReparsed() {
        val cache = IncrementalMessageCache<Long, Row, String>()
        val parsed = mutableListOf<Long>()
        fun render(rows: List<Row>) = cache.map(rows, { it.id }) { parsed += it.id; it.text.uppercase() }
        val rows = (1L..50L).map { Row(it, "reply $it") }
        render(rows)
        parsed.clear()
        assertEquals(51, render(rows + Row(51, "new")).size)
        assertEquals(listOf(51L), parsed)
        parsed.clear()
        assertEquals("EDITED", render(rows.map { if (it.id == 25L) it.copy(text = "edited") else it })[24])
        assertEquals(listOf(25L), parsed)
    }
    @Test fun removedRowsAreEvictedAndRecalledRowsInvalidateTheirCache() {
        val cache = IncrementalMessageCache<Long, Row, String>()
        var parses = 0
        fun render(rows: List<Row>) = cache.map(rows, { it.id }) { parses++; it.text }
        val row = Row(1, "{segments: visible}")
        render(listOf(row))
        render(listOf(row.copy(text = "{segments: recalled}")))
        assertEquals(2, parses)
        render(emptyList())
        render(listOf(row))
        assertEquals(3, parses)
    }
}
