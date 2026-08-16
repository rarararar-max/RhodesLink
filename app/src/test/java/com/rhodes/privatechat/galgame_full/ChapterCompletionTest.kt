package com.rhodes.privatechat.galgame_full

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterCompletionTest {
    private fun state(requiredEvent: String = "", events: Set<String> = emptySet()): GameState = GameState(
        project = ProjectConfig(
            chapters = listOf(ChapterConfig(id = 1, requiredFlag = requiredEvent)),
            events = listOf(EventConfig("key_event", "取得钥匙"))
        ),
        chapter = 1,
        events = events
    )

    @Test
    fun chapterWithoutRequiredEventCanComplete() {
        assertTrue(state().canCompleteCurrentChapter())
    }

    @Test
    fun chapterWithMissingEventCannotComplete() {
        val state = state("key_event")
        assertFalse(state.canCompleteCurrentChapter())
        assertEquals(listOf("需要完成事件：取得钥匙"), state.unmetChapterRequirements())
    }

    @Test
    fun chapterWithCompletedEventCanComplete() {
        assertTrue(state("key_event", setOf("key_event")).canCompleteCurrentChapter())
    }
}
