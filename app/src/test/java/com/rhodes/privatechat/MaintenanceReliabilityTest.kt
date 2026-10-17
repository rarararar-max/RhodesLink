package com.rhodes.privatechat

import com.rhodes.privatechat.shared.memory.MaintenancePolicy
import com.rhodes.privatechat.shared.knowledge.KnowledgeBaseRecallPolicy
import com.rhodes.privatechat.shared.model.MomentReplyTask
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MaintenanceReliabilityTest {
    @Test fun retainedTailDoesNotTriggerEmptySummary() {
        assertEquals(0, MaintenancePolicy.eligibleSummaryRounds(20, 25))
        assertEquals(0, MaintenancePolicy.eligibleSummaryRounds(25, 25))
        assertEquals(20, MaintenancePolicy.eligibleSummaryRounds(45, 25))
    }
    @Test fun summaryBacklogCountsOnlyUnprocessedOlderRounds() {
        assertEquals(0, MaintenancePolicy.remainingSummaryRounds(20, 25, 0))
        assertEquals(0, MaintenancePolicy.remainingSummaryRounds(45, 25, 20))
        assertEquals(45, MaintenancePolicy.remainingSummaryRounds(100, 25, 30))
    }
    @Test fun retryDeadlineIsRespected() {
        assertFalse(MaintenancePolicy.mayAttempt("failed", 101, 100))
        assertTrue(MaintenancePolicy.mayAttempt("failed", 100, 100))
        assertTrue(MaintenancePolicy.mayAttempt("idle", 101, 100))
    }
    @Test fun partialProgressRetainsUsableKnowledgeIndex() {
        assertTrue(KnowledgeBaseRecallPolicy.isUsableIndex("partial_indexing:3/120", "same", "same"))
        assertFalse(KnowledgeBaseRecallPolicy.isUsableIndex("partial_indexing:3/120", "old", "new"))
        assertFalse(KnowledgeBaseRecallPolicy.isUsableIndex("indexing:3/120", "same", "same"))
    }
    @Test fun momentTaskRoundTripsWithoutLosingSourceAndIntent() {
        val task = MomentReplyTask(1, "op", "角色", "带换行\n和引号\"的评论", 2, "用户", mustInsert = true, sourceCommentId = 3)
        assertEquals(task, Json.decodeFromString<MomentReplyTask>(Json.encodeToString(task)))
    }
    @Test fun optionalTaskFieldsHaveBackwardCompatibleDefaults() {
        val task = Json.decodeFromString<MomentReplyTask>("""{"momentId":1,"operatorId":"op","speakerName":"角色","userContent":"内容","parentCommentId":0,"userName":"用户"}""")
        assertFalse(task.mustInsert)
        assertNull(task.sourceCommentId)
        assertNull(task.customPrompt)
    }
}
