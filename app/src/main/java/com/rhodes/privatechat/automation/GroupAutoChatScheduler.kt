package com.rhodes.privatechat.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository
import java.util.UUID
import android.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/** Coordinates one durable pending automatic turn per group across app foreground and background. */
object GroupAutoChatScheduler {
    private const val GROUP_ID = "groupId"
    private const val TOKEN = "token"
    private const val REVISION = "revision"
    // Must outlive the full group reply timeout plus context preparation and persistence.
    private const val CLAIM_LEASE_MS = 5 * 60_000L
    private val locks = ConcurrentHashMap<String, Any>()

    private inline fun <T> synchronizedPlan(groupId: String, block: () -> T): T =
        synchronized(locks.computeIfAbsent(groupId) { Any() }) { block() }

    fun ensurePlan(context: Context, settings: SettingsRepository, groupId: String): SettingsRepository.GroupAutoChatPlan? = synchronizedPlan(groupId) {
        if (!isEnabled(settings, groupId) || settings.isGroupAutoChatComplete(groupId)) return null
        val current = settings.getGroupAutoChatPlan(groupId)
        val now = System.currentTimeMillis()
        val plan = when {
            current.token.isNotBlank() && current.state == "pending" && current.dueAt >= 0L -> current
            current.token.isNotBlank() && current.state == "claimed" && current.leaseExpiresAt > now -> current
            else -> newPlan(settings, groupId, (current.round + 1).coerceAtLeast(1), current.revision + 1)
        }
        if (plan.state == "pending") scheduleWorker(context, groupId, plan)
        return plan
    }

    fun resetPlan(context: Context, settings: SettingsRepository, groupId: String): SettingsRepository.GroupAutoChatPlan? = synchronizedPlan(groupId) {
        val revision = settings.getGroupAutoChatPlan(groupId).revision + 1
        cancel(context, settings, groupId)
        if (!isEnabled(settings, groupId)) return null
        settings.putGroupAutoChatComplete(groupId, false)
        return newPlan(settings, groupId, 1, revision).also { scheduleWorker(context, groupId, it) }
    }

    fun scheduleNext(context: Context, settings: SettingsRepository, groupId: String, completedRound: Int, expectedToken: String? = null, expectedRevision: Long? = null): SettingsRepository.GroupAutoChatPlan? = synchronizedPlan(groupId) {
        if (expectedToken != null || expectedRevision != null) {
            val current = settings.getGroupAutoChatPlan(groupId)
            if (current.token != expectedToken || current.revision != expectedRevision || current.state != "claimed") return@synchronizedPlan null
        }
        if (!isEnabled(settings, groupId) || completedRound >= settings.groupAutoMaxRounds) {
            cancel(context, settings, groupId)
            if (completedRound >= settings.groupAutoMaxRounds) settings.putGroupAutoChatComplete(groupId, true)
            return null
        }
        val revision = settings.getGroupAutoChatPlan(groupId).revision + 1
        return newPlan(settings, groupId, completedRound + 1, revision).also { scheduleWorker(context, groupId, it) }
    }

    fun releaseClaim(context: Context, settings: SettingsRepository, groupId: String, completedRound: Int, token: String, revision: Long) {
        scheduleNext(context, settings, groupId, completedRound, token, revision)
    }

    /** Returns a failed in-flight plan to a fenced pending retry without waiting for its lease. */
    fun releaseClaimForRetry(context: Context, settings: SettingsRepository, groupId: String, token: String, revision: Long, retryDelayMs: Long = 60_000L): SettingsRepository.GroupAutoChatPlan? = synchronizedPlan(groupId) {
        val current = settings.getGroupAutoChatPlan(groupId)
        if (!isEnabled(settings, groupId) || current.token != token || current.revision != revision || current.state != "claimed") return@synchronizedPlan null
        val pending = current.copy(state = "pending", dueAt = System.currentTimeMillis() + retryDelayMs, claimedAt = 0L, leaseExpiresAt = 0L)
        settings.putGroupAutoChatPlan(groupId, pending)
        scheduleWorker(context, groupId, pending)
        pending
    }

    /** Atomically consumes the current plan in this process before either trigger sends a turn. */
    fun claim(settings: SettingsRepository, groupId: String, token: String, revision: Long): SettingsRepository.GroupAutoChatPlan? = synchronizedPlan(groupId) {
        val plan = settings.getGroupAutoChatPlan(groupId)
        if (!isEnabled(settings, groupId) || token.isBlank() || plan.token != token || plan.revision != revision || plan.state != "pending" || plan.dueAt > System.currentTimeMillis()) return null
        val now = System.currentTimeMillis()
        settings.putGroupAutoChatPlan(groupId, plan.copy(state = "claimed", claimedAt = now, leaseExpiresAt = now + CLAIM_LEASE_MS))
        return plan
    }

    fun isCurrentClaim(settings: SettingsRepository, groupId: String, token: String, revision: Long): Boolean {
        val plan = settings.getGroupAutoChatPlan(groupId)
        return isEnabled(settings, groupId) && plan.token == token && plan.revision == revision &&
            plan.state == "claimed" && plan.leaseExpiresAt > System.currentTimeMillis()
    }

    fun cancel(context: Context, settings: SettingsRepository, groupId: String) = synchronizedPlan(groupId) {
        settings.clearGroupAutoChatPlan(groupId)
        WorkManager.getInstance(context).cancelUniqueWork(workName(groupId))
    }

    fun reconcile(context: Context, repository: ChatRepository, settings: SettingsRepository) = runBlocking {
        if (!settings.autoAiEnabled) return@runBlocking
        repository.getAllSessionsSync()
            .filter { it.operatorId.startsWith("group_") || it.operatorId.startsWith("group") }
            .filter { settings.getGroupAuto(it.id) }
            .forEach { ensurePlan(context, settings, it.id) }
    }

    private fun newPlan(settings: SettingsRepository, groupId: String, round: Int, revision: Long): SettingsRepository.GroupAutoChatPlan {
        val minMs = settings.groupChatMinInterval * 1000L
        val maxMs = settings.groupChatMaxInterval * 1000L
        val delayMs = minMs + (Math.random() * (maxMs - minMs).coerceAtLeast(0L)).toLong()
        return SettingsRepository.GroupAutoChatPlan(UUID.randomUUID().toString(), System.currentTimeMillis() + delayMs, round, revision = revision)
            .also { settings.putGroupAutoChatPlan(groupId, it) }
    }

    private fun scheduleWorker(context: Context, groupId: String, plan: SettingsRepository.GroupAutoChatPlan) {
        val request = OneTimeWorkRequestBuilder<GroupAutoChatWorker>()
            .setInitialDelay((plan.dueAt - System.currentTimeMillis()).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(GROUP_ID to groupId, TOKEN to plan.token, REVISION to plan.revision))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(groupId), ExistingWorkPolicy.REPLACE, request)
    }

    private fun isEnabled(settings: SettingsRepository, groupId: String) = settings.autoAiEnabled && settings.getGroupAuto(groupId)
    private fun workName(groupId: String) = "group-auto-chat-${Base64.encodeToString(groupId.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)}"
}
