package com.rhodes.privatechat.viewmodel

/** Subtract only verified intentional deletions; never reset baseline to current count,
 * which would conceal unrelated losses. Missing/invalid legacy baselines remain missing. */
internal data class MemoryCountBaseline(val total: Int, val privateItems: Int) {
    fun afterDeletion(totalDeleted: Int, privateDeleted: Int): MemoryCountBaseline {
        require(totalDeleted >= 0 && privateDeleted in 0..totalDeleted)
        val expectedTotal = (total - totalDeleted).coerceAtLeast(0)
        return MemoryCountBaseline(expectedTotal,
            (privateItems - privateDeleted).coerceIn(0, expectedTotal))
    }
    fun encode() = "$total,$privateItems"
    companion object {
        fun parse(value: String): MemoryCountBaseline? {
            val parts = value.split(',')
            if (parts.size != 2) return null
            val total = parts[0].toIntOrNull() ?: return null
            val privateItems = parts[1].toIntOrNull() ?: return null
            return if (total >= 0 && privateItems in 0..total) MemoryCountBaseline(total, privateItems) else null
        }
    }
}
