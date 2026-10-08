package com.rhodes.privatechat

import com.rhodes.privatechat.util.ContextProvenance
import com.rhodes.privatechat.util.ContextProvenance.Entry
import com.rhodes.privatechat.util.ContextProvenance.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 上下文引用的"可解释"判定。
 *
 * 玩家应该能在调试记录里看出记忆/知识库到底生效了没有、没生效是因为什么，
 * 这组断言锁住"每种原因都能对上号、并且都给出下一步去哪开"。
 */
class ContextProvenanceTest {

    @Test
    fun disabledSourceTellsTheUserWhereToTurnItOn() {
        val line = ContextProvenance.line(Entry("关系网传递", Outcome.DISABLED_BY_SWITCH))
        assertTrue("必须说明没开启: $line", line.contains("尚未在设置中开启"))
        assertTrue("必须给出设置路径: $line", line.contains("设置 → 记忆 → 注入"))
    }

    @Test
    fun everyNonInjectedOutcomeExplainsItself() {
        listOf(
            Outcome.DISABLED_BY_SWITCH, Outcome.GENERATION_OFF, Outcome.NO_DATA, Outcome.TRIMMED,
            Outcome.INDEX_NOT_READY, Outcome.NOT_BOUND, Outcome.TIMEOUT, Outcome.NO_MATCH,
        ).forEach { outcome ->
            val reason = ContextProvenance.reason(outcome)
            assertTrue("$outcome 必须有解释文案", reason.isNotBlank())
            assertFalse("$outcome 的文案不能是空的占位", reason == "已注入")
        }
    }

    @Test
    fun injectedChannelsAreListedFirstAndQuietWhenEverythingWorks() {
        val summary = ContextProvenance.summary(
            listOf(
                Entry("个人向量记忆", Outcome.INJECTED),
                Entry("知识库引用", Outcome.INJECTED, "《罗德岛医疗手册》（相似 0.77）"),
            )
        )
        assertTrue(summary.contains("个人向量记忆"))
        assertTrue(summary.contains("知识库引用"))
        assertFalse("全部正常时不应该出现警告", summary.contains("⚠"))
    }

    @Test
    fun noMatchIsNotTreatedAsAProblem() {
        val summary = ContextProvenance.summary(listOf(Entry("知识库引用", Outcome.NO_MATCH)))
        assertFalse("检索无命中属于正常现象，不该报成问题: $summary", summary.contains("⚠"))
        assertTrue(summary.contains("未引用记忆或知识库"))
    }

    @Test
    fun problemsAreCappedSoTheCardStaysReadable() {
        val summary = ContextProvenance.summary(
            (1..6).map { Entry("通道$it", Outcome.DISABLED_BY_SWITCH) },
            maxProblems = 3,
        )
        assertEquals("超出上限的问题要折叠成一行", 4, summary.lines().size)
        assertTrue(summary.contains("另有 3 项"))
    }

    @Test
    fun compactSummaryIsASingleReadableLine() {
        val line = ContextProvenance.compactSummary(
            listOf(
                Entry("滚动摘要", Outcome.INJECTED),
                Entry("个人向量记忆", Outcome.INJECTED),
                Entry("知识库引用", Outcome.INJECTED),
                Entry("关系网传递", Outcome.DISABLED_BY_SWITCH),
                Entry("群聊近况", Outcome.NO_DATA),
            )
        )
        assertTrue("必须点名已注入的通道: $line", line.contains("已注入 滚动摘要、个人向量记忆、知识库引用"))
        assertTrue("必须给出未注入原因: $line", line.contains("关系网传递（设置未开启）"))
        assertTrue("卡片摘要不能换行", !line.contains("\n"))
    }

    @Test
    fun compactSummaryFoldsExtraProblems() {
        val line = ContextProvenance.compactSummary((1..5).map { Entry("通道$it", Outcome.DISABLED_BY_SWITCH) })
        assertTrue("超出两项要折叠: $line", line.contains("另有 3 项见详情"))
    }

    @Test
    fun memoryOutcomePicksTheRightReason() {
        assertEquals(
            Outcome.DISABLED_BY_SWITCH,
            ContextProvenance.memoryOutcome(totalSwitchOn = false, sourceAllowed = true, generationOn = true, hasData = true, injected = true),
        )
        assertEquals(
            Outcome.GENERATION_OFF,
            ContextProvenance.memoryOutcome(totalSwitchOn = true, sourceAllowed = true, generationOn = false, hasData = false, injected = false),
        )
        assertEquals(
            Outcome.NO_DATA,
            ContextProvenance.memoryOutcome(totalSwitchOn = true, sourceAllowed = true, generationOn = true, hasData = false, injected = false),
        )
        assertEquals(
            Outcome.TRIMMED,
            ContextProvenance.memoryOutcome(totalSwitchOn = true, sourceAllowed = true, generationOn = true, hasData = true, injected = false),
        )
        assertEquals(
            Outcome.INJECTED,
            ContextProvenance.memoryOutcome(totalSwitchOn = true, sourceAllowed = true, generationOn = true, hasData = true, injected = true),
        )
    }

    @Test
    fun knowledgeOutcomeMapsRecallReasons() {
        assertEquals(Outcome.NOT_BOUND, ContextProvenance.knowledgeOutcome("no_assignment", 0, 0))
        assertEquals(Outcome.INDEX_NOT_READY, ContextProvenance.knowledgeOutcome("index_not_ready", 0, 0))
        assertEquals(Outcome.INDEX_NOT_READY, ContextProvenance.knowledgeOutcome("embedding_unavailable", 0, 0))
        assertEquals(Outcome.NO_MATCH, ContextProvenance.knowledgeOutcome("no_match", 0, 0))
        assertEquals(Outcome.TRIMMED, ContextProvenance.knowledgeOutcome("", 3, 0))
        assertEquals(Outcome.INJECTED, ContextProvenance.knowledgeOutcome("", 3, 2))
    }
}
