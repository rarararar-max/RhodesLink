package com.rhodes.privatechat

import com.rhodes.privatechat.viewmodel.shared.diaryGenerationResult
import com.rhodes.privatechat.shared.model.Diary
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DiaryGenerationOutcomeTest {
    @Test fun requestTimeoutCompletesWithFailureResult() = runBlocking {
        var deliveries = 0
        val diary = diaryGenerationResult { withTimeout(10L) { delay(1000L); null } }
        deliveries++
        assertNull(diary)
        assertEquals(1, deliveries)
    }
    @Test fun ownerCancellationDoesNotDeliverAStaleResult() = runBlocking {
        val entered = CompletableDeferred<Unit>(); var deliveries = 0
        val job = launch { diaryGenerationResult { entered.complete(Unit); awaitCancellation() }; deliveries++ }
        entered.await(); job.cancelAndJoin()
        assertEquals(0, deliveries)
    }
    @Test fun databaseFailureIsReportedWithoutLeavingTheCallerPending() = runBlocking {
        var failure: Exception? = null
        assertNull(diaryGenerationResult(onFailure = { failure = it }) { throw IllegalStateException("database unavailable") })
        assertTrue(failure is IllegalStateException)
    }
    @Test fun savedDiaryPassesThroughWithItsPersistentIdentity() = runBlocking {
        val saved = Diary(id = 42, operatorId = "op", operatorName = "角色", content = "正文", date = "2026-10-15", version = 2)
        assertEquals(saved, diaryGenerationResult { saved })
    }
}
