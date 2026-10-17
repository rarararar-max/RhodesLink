package com.rhodes.privatechat.shared.memory

object MaintenancePolicy {
    fun eligibleSummaryRounds(total: Int, retained: Int): Int = (total - retained).coerceAtLeast(0)
    fun mayAttempt(state: String, nextRetryAt: Long, now: Long): Boolean = state != "failed" || now >= nextRetryAt
    fun remainingSummaryRounds(total: Int, retained: Int, processed: Int): Int =
        (total - retained - processed).coerceAtLeast(0)
}
