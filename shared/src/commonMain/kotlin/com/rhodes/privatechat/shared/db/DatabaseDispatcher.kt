package com.rhodes.privatechat.shared.db

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * SQLDelight calls are synchronous. Keep critical message and vector operations on one owned
 * lane so a timed-out caller cannot multiply those high-contention driver operations.
 */
object DatabaseDispatcher {
    val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
}
