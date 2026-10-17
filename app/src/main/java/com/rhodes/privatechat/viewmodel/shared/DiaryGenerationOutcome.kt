package com.rhodes.privatechat.viewmodel.shared

import com.rhodes.privatechat.shared.model.Diary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/** Request failure completes normally; owner cancellation must still propagate. */
internal suspend fun diaryGenerationResult(onFailure: (Exception) -> Unit = {}, generate: suspend () -> Diary?): Diary? = try {
    generate()
} catch (error: TimeoutCancellationException) {
    onFailure(error)
    null
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    onFailure(error)
    null
}
