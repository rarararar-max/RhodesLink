package com.rhodes.privatechat.automation

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Creates one deterministic delivery plan per Beijing natural day. */
object DailyContentScheduler {
    const val TYPE_PLAN = "plan"
    const val TYPE_MOMENT = "moment"
    const val TYPE_PRIVATE = "private"
    private const val PLAN_WORK = "daily-content-plan"
    private val zone = TimeZone.getTimeZone("Asia/Shanghai")

    fun schedulePlanner(context: Context) {
        val now = System.currentTimeMillis()
        val next = nextCycleStart(now)
        val request = PeriodicWorkRequestBuilder<DailyContentWorker>(24, TimeUnit.HOURS)
            .setInitialDelay((next - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("type" to TYPE_PLAN))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PLAN_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun ensureTodayPlan(context: Context, repository: ChatRepository, settings: SettingsRepository) = runBlocking {
        if (!settings.autoAiEnabled) return@runBlocking
        val cycle = cycleId()
        if (settings.getBoolean("daily_content_planned_$cycle", false)) return@runBlocking
        val now = System.currentTimeMillis()
        val operators = repository.getAllOperatorsSync()
        val cycleStart = cycleStart(now)
        val cycleEnd = cycleStart + TimeUnit.DAYS.toMillis(1)
        operators.filter { settings.getOperatorDynPermission(it.id) }.forEach { op ->
            repeat(settings.dailyMomentTarget) { index ->
                schedule(context, TYPE_MOMENT, op.id, index.toString(), scheduledTime(cycleStart, cycleEnd, "moment:${op.id}:$index", now))
            }
        }
        val dispatchedOperatorIds = repository.getActiveDispatches()
            .flatMap { it.operatorIds.split(",") }
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
        val candidates = operators.filter { op ->
            settings.idleProactiveChatEnabled &&
            settings.getOperatorMsgPermission(op.id) && (0..99).random() < settings.dailyProactiveChance &&
                op.id !in dispatchedOperatorIds &&
                hasConversationContext(repository, op.id)
        }.shuffled().take(settings.dailyProactiveMax)
        candidates.forEachIndexed { index, op ->
            val base = scheduledTime(cycleStart, cycleEnd, "private:${op.id}:$index", now)
            schedule(context, TYPE_PRIVATE, op.id, "0", avoidQuietHours(base, cycleEnd, settings, now))
        }
        settings.putBoolean("daily_content_planned_$cycle", true)
    }

    /** Replaces only today's pending deliveries after the user saves new automatic-content settings. */
    fun rebuildTodayPlan(context: Context, repository: ChatRepository, settings: SettingsRepository) = runBlocking {
        val cycle = cycleId()
        val operators = repository.getAllOperatorsSync()
        val workManager = WorkManager.getInstance(context)
        operators.forEach { op ->
            // dailyMomentTarget is capped at three, so these cover every possible old plan.
            repeat(3) { index -> workManager.cancelUniqueWork(workName(cycle, TYPE_MOMENT, op.id, index.toString())) }
            workManager.cancelUniqueWork(workName(cycle, TYPE_PRIVATE, op.id, "0"))
        }
        settings.remove("daily_content_planned_$cycle")
        ensureTodayPlan(context, repository, settings)
    }

    /** Settings must not fail just because a best-effort background plan rebuild fails. */
    fun rebuildTodayPlanAsync(context: Context, repository: ChatRepository, settings: SettingsRepository, onComplete: (String?) -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            val error = runCatching { rebuildTodayPlan(context.applicationContext, repository, settings) }
                .exceptionOrNull()
                ?.let { "自动计划重建失败：${it.message?.take(80) ?: it.javaClass.simpleName}" }
            if (error != null) DebugLogger.diagnostic("DailyContent/RebuildFailed", error)
            kotlinx.coroutines.withContext(Dispatchers.Main.immediate) { onComplete(error) }
        }
    }

    fun cancelTodayPlanForOperators(context: Context, operatorIds: Collection<String>, onComplete: () -> Unit = {}) {
        if (operatorIds.isEmpty()) { onComplete(); return }
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val cycle = cycleId()
                val workManager = WorkManager.getInstance(context.applicationContext)
                operatorIds.forEach { operatorId ->
                    repeat(3) { index -> workManager.cancelUniqueWork(workName(cycle, TYPE_MOMENT, operatorId, index.toString())) }
                    workManager.cancelUniqueWork(workName(cycle, TYPE_PRIVATE, operatorId, "0"))
                }
            }.onFailure { DebugLogger.diagnostic("DailyContent/CancelDeletedOperatorPlanFailed", it.message ?: it.javaClass.simpleName) }
            kotlinx.coroutines.withContext(Dispatchers.Main.immediate) { onComplete() }
        }
    }

    private suspend fun hasConversationContext(repository: ChatRepository, operatorId: String): Boolean {
        val session = repository.getSessionByOperator(operatorId)
        return session != null && (repository.getMessagesSync(session.id).isNotEmpty() ||
            repository.getShortTermMemory(session.id) != null || repository.getLongTermImpression(operatorId) != null)
    }

    private fun schedule(context: Context, type: String, operatorId: String, deliveryId: String, scheduledAt: Long) {
        val cycle = cycleId()
        val name = workName(cycle, type, operatorId, deliveryId)
        val request = OneTimeWorkRequestBuilder<DailyContentWorker>()
            .setInitialDelay((scheduledAt - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("type" to type, "operatorId" to operatorId, "deliveryId" to deliveryId, "cycle" to cycle))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
    }

    private fun workName(cycle: String, type: String, operatorId: String, deliveryId: String) =
        "daily-content-$cycle-$type-$operatorId-$deliveryId"

    fun cycleId(now: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance(zone).apply { timeInMillis = now }
        return "%04d%02d%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    private fun cycleStart(now: Long): Long {
        val cal = Calendar.getInstance(zone).apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        return cal.timeInMillis
    }

    private fun nextCycleStart(now: Long): Long = cycleStart(now).let { if (it > now) it else it + TimeUnit.DAYS.toMillis(1) }

    private fun scheduledTime(start: Long, end: Long, seed: String, now: Long): Long {
        val span = end - start - TimeUnit.HOURS.toMillis(1)
        val offset = (seed.hashCode().toLong() and Long.MAX_VALUE) % span
        val planned = start + TimeUnit.MINUTES.toMillis(30L) + offset
        if (planned >= now) return planned

        // Planning can happen after the normal slot (first launch, restored worker, etc.).
        // Spread overdue deliveries across the remaining day instead of firing them together.
        val recoveryStart = now + TimeUnit.MINUTES.toMillis(5L)
        val recoveryEnd = end - TimeUnit.MINUTES.toMillis(10L)
        if (recoveryEnd <= recoveryStart) return recoveryStart
        return recoveryStart + ((seed.hashCode().toLong() and Long.MAX_VALUE) % (recoveryEnd - recoveryStart))
    }

    private fun avoidQuietHours(at: Long, cycleEnd: Long, settings: SettingsRepository, now: Long): Long {
        if (!settings.quietHoursEnabled) return at
        val cal = Calendar.getInstance(zone).apply { timeInMillis = at }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val quiet = when {
            settings.quietHoursStart == settings.quietHoursEnd -> false
            settings.quietHoursStart < settings.quietHoursEnd -> hour in settings.quietHoursStart until settings.quietHoursEnd
            else -> hour >= settings.quietHoursStart || hour < settings.quietHoursEnd
        }
        if (!quiet) return at
        cal.set(Calendar.HOUR_OF_DAY, settings.quietHoursEnd); cal.set(Calendar.MINUTE, 10 + (at % 40).toInt()); cal.set(Calendar.SECOND, 0)
        return cal.timeInMillis.coerceAtMost(cycleEnd - TimeUnit.MINUTES.toMillis(10)).coerceAtLeast(now + TimeUnit.MINUTES.toMillis(2))
    }
}
