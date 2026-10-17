package com.rhodes.privatechat.viewmodel.shared

import kotlinx.coroutines.CancellationException

/** A committed post must reach the feed before optional memory/vector work begins. */
internal suspend fun publishSavedMoment(
    refreshFeed: suspend () -> Unit,
    updateMemory: suspend () -> Unit,
    onMemoryFailure: (Exception) -> Unit,
) {
    refreshFeed()
    try {
        updateMemory()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        onMemoryFailure(error)
    }
}
