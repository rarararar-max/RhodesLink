package com.rhodes.privatechat

import com.rhodes.privatechat.shared.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PromptHistorySafetyTest {
    @Test fun trimsOnlyHistoryAndPreservesEveryCurrentTurnBlock() {
        val system = AiMessage("system", "system")
        val tail = listOf(AiMessage("user", "reference"), AiMessage("user", "extra"),
            AiMessage("user", "my question"), AiMessage("user", "output requirements"))
        val messages = (listOf(system, AiMessage("user", "x".repeat(200)), AiMessage("assistant", "y".repeat(200))) + tail).toMutableList()
        val budget = (listOf(system) + tail).sumOf { it.content.length + 10 }
        assertEquals(2, trimPromptHistory(messages, 2, budget) { it.length })
        assertEquals(listOf(system) + tail, messages)
    }

    @Test fun oversizedRequiredContentFailsWithoutDeletingUserInput() {
        val messages = mutableListOf(AiMessage("system", "system"), AiMessage("assistant", "history"),
            AiMessage("user", "my question"), AiMessage("user", "output"))
        val original = messages.toList()
        try {
            trimPromptHistory(messages, 1, 10) { it.length }
            fail("Must reject a budget smaller than required content")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("本轮输入未被裁剪"))
            assertEquals(original, messages)
        }
    }

    @Test fun noHistoryStillProtectsInput() {
        val messages = mutableListOf(AiMessage("system", "s"), AiMessage("user", "question"))
        assertEquals(0, trimPromptHistory(messages, 0, 100) { it.length })
        assertEquals(2, messages.size)
    }

    private fun rows(rounds: Int): List<ChatMessage> = (1..rounds).flatMap { round ->
        (0..3).map { offset -> ChatMessage(id = round * 4L + offset, sessionId = "test", senderName = "test",
            content = "$round/$offset", type = if (offset == 0) "text" else "ai_json",
            timestamp = round.toLong(), isMe = offset == 0) }
    }

    @Test fun sixtyRoundsReadsMoreThanTwoHundredRowsWhenNeeded() = runBlocking {
        val data = rows(150).asReversed()
        var calls = 0
        val window = readPrivateHistoryWindow(60) { cursor, limit ->
            calls++
            data.filter { cursor == null || it.timestamp < cursor.timestamp ||
                (it.timestamp == cursor.timestamp && it.id < cursor.id) }.take(limit)
        }
        assertEquals(2, calls)
        assertEquals(400, window.readCount)
        assertTrue(window.messages.count { it.isMe } >= 60)
        assertFalse(window.capped)
        assertEquals(window.messages.sortedBy { it.id }, window.messages)
    }

    @Test fun restartAndExcludedRowsDoNotCountAsHistory() = runBlocking {
        val data = rows(80).asReversed()
        val excluded = data.filter { it.isMe && it.timestamp >= 71 }.map { it.id }.toSet()
        val window = readPrivateHistoryWindow(60, restartAt = 51, excludedIds = excluded) { cursor, limit ->
            data.filter { cursor == null || it.id < cursor.id }.take(limit)
        }
        val eligible = window.messages.filter { it.timestamp >= 51 && it.id !in excluded }
        assertEquals(20, eligible.count { it.isMe })
        assertFalse(window.capped)
    }

    @Test fun regenerationLoadsPagesUntilTargetIsFound() = runBlocking {
        val data = rows(150).asReversed()
        val targetId = 50 * 4L
        val window = readPrivateHistoryWindow(10, beforeMessageId = targetId) { cursor, limit ->
            data.filter { cursor == null || it.id < cursor.id }.take(limit)
        }
        val target = window.messages.indexOfFirst { it.id == targetId }
        assertTrue(target >= 0)
        assertTrue(window.messages.take(target).count { it.isMe } >= 10)
    }

    @Test fun resourceCapIsReportedWhenTooFewRoundsExist() = runBlocking {
        val data = (1..1000).map { ChatMessage(id = it.toLong(), sessionId = "test", senderName = "test", content = "", isMe = false) }.asReversed()
        val window = readPrivateHistoryWindow(60, maxRows = 200) { cursor, limit ->
            data.filter { cursor == null || it.id < cursor.id }.take(limit)
        }
        assertEquals(200, window.readCount)
        assertTrue(window.capped)
    }
}
