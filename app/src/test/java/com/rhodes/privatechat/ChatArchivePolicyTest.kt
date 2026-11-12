package com.rhodes.privatechat

import com.rhodes.privatechat.shared.model.*
import org.junit.Assert.*
import org.junit.Test

class ChatArchivePolicyTest {
    private fun message(id: Long, me: Boolean, type: String = if (me) "text" else "ai_json") =
        ChatMessage(id, "session", senderName = if (me) "博士" else "凯尔希", content = "$id", type = type, isMe = me)
    @Test fun retainsThirtyCompleteRoundsAndAllReplySegments() {
        val all = (1L..45L).flatMap { n -> listOf(message(n * 10, true), message(n * 10 + 1, false), message(n * 10 + 2, false)) }
        val snapshot = ChatArchivePolicy.snapshot(all)
        assertEquals(90, snapshot.size)
        assertEquals(160L, snapshot.first().id)
        assertEquals(452L, snapshot.last().id)
        assertEquals(30, ChatArchivePolicy.completedRounds(snapshot).size)
    }
    @Test fun excludesPendingAndFailedRoundsWithoutLosingEarlierCompletedOnes() {
        val all = listOf(message(1, true), message(2, false), message(3, true), message(4, false, "send_failed"), message(5, true))
        assertEquals(listOf(1L, 2L), ChatArchivePolicy.snapshot(all).map { it.id })
    }
    @Test fun olderFiveRoundArchivesKeepTheirRealCount() {
        val all = (1L..5L).flatMap { n -> listOf(message(n * 2, true), message(n * 2 + 1, false)) }
        assertEquals(5, ChatArchivePolicy.completedRounds(all).size)
        assertEquals(all, ChatArchivePolicy.snapshot(all))
    }
}
