package com.rhodes.privatechat

import com.rhodes.privatechat.viewmodel.MemoryCountBaseline
import org.junit.Assert.*
import org.junit.Test

class MemoryCountBaselineTest {
    @Test fun intentionalPrivateDeletionExplainsOnlyTheRemovedCount() {
        assertEquals("31,23", MemoryCountBaseline(58, 50).afterDeletion(27, 27).encode())
    }
    @Test fun publicDeletionDoesNotChangePrivateBaseline() {
        assertEquals("29,23", MemoryCountBaseline(31, 23).afterDeletion(2, 0).encode())
    }
    @Test fun unrelatedLossStillRemainsDetectable() {
        // Baseline 58, actual 50 before user deletes 2: expected stays 56, not actual 48.
        val expected = MemoryCountBaseline(58, 50).afterDeletion(2, 2)
        assertEquals(56, expected.total)
        assertTrue(48 < expected.total)
        assertTrue(40 < expected.privateItems)
    }
    @Test fun noOpAndPartialFailuresCountOnlyCompletedDeletes() {
        val initial = MemoryCountBaseline(31, 23)
        assertEquals(initial, initial.afterDeletion(0, 0))
        assertEquals("30,22", initial.afterDeletion(1, 1).encode())
    }
    @Test fun deletionsOfNewlyCreatedItemsDoNotProduceNegativeCounts() {
        assertEquals("0,0", MemoryCountBaseline(2, 1).afterDeletion(4, 2).encode())
    }
    @Test fun malformedBaselineNeverBecomesInventedHistory() {
        listOf("", "bad", "1", "1,2", "-1,0", "3,-1", "1,0,2").forEach {
            assertNull(MemoryCountBaseline.parse(it))
        }
        assertEquals(MemoryCountBaseline(31, 23), MemoryCountBaseline.parse("31,23"))
    }
}
