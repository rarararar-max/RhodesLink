package com.rhodes.privatechat

import com.rhodes.privatechat.util.MessagePersistenceReceipt
import com.rhodes.privatechat.util.orderChatBubbles
import com.rhodes.privatechat.util.preserveEditedDraft
import org.junit.Assert.*
import org.junit.Test

class ChatInteractionPolicyTest {
    private data class Bubble(val id: Long, val segment: Int, val time: Long, val reveal: Long? = null)
    private val start = 1_790_000_000_000L
    private fun order(bubbles: List<Bubble>) = orderChatBubbles(
        bubbles, { it.time }, { it.id }, { it.segment }, { it.reveal },
    )

    @Test fun laterSegmentsFollowTheInterveningUserMessage() {
        val user1 = Bubble(1, -1, start)
        val firstReply = Bubble(2, 0, start + 10, start + 11)
        val user2 = Bubble(3, -1, start + 20)
        val secondReply = Bubble(2, 1, start + 10, start + 30)
        assertEquals(listOf(user1, firstReply, user2, secondReply),
            order(listOf(user1, firstReply, secondReply, user2)))
    }

    @Test fun reloadUsesPersistedRevealTimesRatherThanContainerOrder() {
        val history = listOf(Bubble(7, 0, start, start + 10), Bubble(7, 1, start, start + 30), Bubble(8, -1, start + 20))
        assertEquals(listOf(7L to 0, 8L to -1, 7L to 1), order(history).map { it.id to it.segment })
        assertEquals(order(history), order(history.reversed()))
    }

    @Test fun legacySequenceEventsAndMessagesWithoutEventsKeepTheirOldOrder() {
        val old = listOf(Bubble(1, -1, start), Bubble(2, 0, start + 1, 1), Bubble(2, 1, start + 1, 2), Bubble(3, -1, start + 2))
        assertEquals(old, order(old.reversed()))
    }

    @Test fun loadingOlderHistoryDoesNotChangeTheExistingRelativeOrder() {
        val recent = listOf(Bubble(4, 0, start + 10, start + 30), Bubble(5, -1, start + 20))
        val old = listOf(Bubble(1, -1, start - 100), Bubble(2, 0, start - 50, 1))
        assertEquals(order(recent), order(old + recent).takeLast(2))
    }

    @Test fun removingOneSegmentKeepsTheSurvivorsInOrder() {
        val bubbles = listOf(Bubble(1, 0, start, start), Bubble(1, 1, start, start + 20), Bubble(2, -1, start + 10))
        assertEquals(order(bubbles).filter { it.segment != 0 }, order(bubbles.filter { it.segment != 0 }))
    }

    @Test fun saveCompletionPreservesTextTypedWhileSaving() {
        assertEquals("下一条消息", preserveEditedDraft("下一条消息", "上一条消息"))
        assertEquals("上一条消息补充", preserveEditedDraft("上一条消息补充", "上一条消息"))
        assertEquals("", preserveEditedDraft("上一条消息", "上一条消息"))
    }

    @Test fun laterReplyFailureCannotClearANewerDraftOrUnlockANewerSend() {
        val results = mutableListOf<Boolean>()
        val receipt = MessagePersistenceReceipt { results += it }
        receipt.complete(true)
        receipt.complete(false)
        receipt.complete(true)
        assertEquals(listOf(true), results)
    }

    @Test fun failedSaveReportsFailureOnlyOnce() {
        val results = mutableListOf<Boolean>()
        val receipt = MessagePersistenceReceipt { results += it }
        receipt.complete(false)
        receipt.complete(false)
        assertEquals(listOf(false), results)
    }
}
