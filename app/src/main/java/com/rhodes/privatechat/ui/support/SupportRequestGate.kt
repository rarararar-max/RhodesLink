package com.rhodes.privatechat.ui.support

/** Main-thread generation guard, independent of cancellation cooperation in external SDKs. */
class SupportRequestGate {
    private var generation = 0L
    fun begin(): Long = ++generation
    fun invalidate() { ++generation }
    fun isCurrent(token: Long): Boolean = generation == token
}

class SupportImageException(val safeMessage: String): IllegalStateException(safeMessage)
