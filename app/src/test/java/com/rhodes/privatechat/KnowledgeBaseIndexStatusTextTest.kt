package com.rhodes.privatechat

import com.rhodes.privatechat.ui.settings.indexStatusText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 知识库索引状态的显示文案。
 *
 * 玩家反馈：未完成索引的知识库和"索引完成"看起来一样，根本注意不到。
 * 这里锁定规则——只有 ready 是"索引完成"，其余一律以"等待索引"开头（界面另配红色）。
 */
class KnowledgeBaseIndexStatusTextTest {

    @Test
    fun onlyReadyIsReportedAsComplete() {
        assertEquals("索引完成", indexStatusText("ready"))
    }

    @Test
    fun everyIncompleteStatusLeadsWithWaitingForIndex() {
        listOf(
            "processing",
            "pending",
            "pending_confirm",
            "partial_pending_confirm",
            "indexing",
            "indexing:3/120",
            "partial_indexing:5/60",
            "partial_failed",
            "failed",
            "unknown_state",
        ).forEach { status ->
            val text = indexStatusText(status)
            assertTrue("$status 必须以“等待索引”开头，便于一眼发现: $text", text.startsWith("等待索引"))
        }
    }

    @Test
    fun progressAndNextActionAreStillVisible() {
        assertTrue(indexStatusText("indexing:3/120").contains("3/120"))
        assertTrue(indexStatusText("failed").contains("重建"))
        assertTrue(indexStatusText("pending_confirm").contains("确认"))
    }
}
