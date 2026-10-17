package com.rhodes.privatechat

import com.rhodes.privatechat.util.chatBottomItemIndex
import com.rhodes.privatechat.viewmodel.shared.publishSavedMoment
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class MomentPublicationTest {
    @Test fun publishedPostIsVisibleWhileMemoryIsStillBlocked() = runBlocking {
        val memoryStarted = CompletableDeferred<Unit>()
        val releaseMemory = CompletableDeferred<Unit>()
        var feedVisible = false
        val job = launch {
            publishSavedMoment(
                refreshFeed = { feedVisible = true },
                updateMemory = { memoryStarted.complete(Unit); releaseMemory.await() },
                onMemoryFailure = { fail("Unexpected failure") },
            )
        }
        memoryStarted.await()
        assertTrue(feedVisible)
        assertTrue(job.isActive)
        releaseMemory.complete(Unit)
        job.join()
    }

    @Test fun failedMemoryDoesNotTurnASavedPostIntoAGenerationFailure() = runBlocking {
        val events = mutableListOf<String>()
        publishSavedMoment(
            refreshFeed = { events += "published" },
            updateMemory = { throw IllegalStateException("vector unavailable") },
            onMemoryFailure = { events += "memory failure" },
        )
        events += "generation succeeded"
        assertEquals(listOf("published", "memory failure", "generation succeeded"), events)
    }

    @Test fun cancellationStillStopsMemoryWorkAfterPublication() = runBlocking {
        var published = false
        var reportedFailure = false
        val entered = CompletableDeferred<Unit>()
        val job = launch {
            publishSavedMoment(
                refreshFeed = { published = true },
                updateMemory = { entered.complete(Unit); awaitCancellation() },
                onMemoryFailure = { reportedFailure = true },
            )
        }
        entered.await()
        job.cancelAndJoin()
        assertTrue(published)
        assertTrue(job.isCancelled)
        assertFalse(reportedFailure)
    }

    @Test fun bottomTargetIsTheFooterAfterEvenASingleVeryTallReply() {
        assertEquals(2, chatBottomItemIndex(1))
        assertEquals(61, chatBottomItemIndex(60))
        assertEquals(1, chatBottomItemIndex(0))
    }
}
