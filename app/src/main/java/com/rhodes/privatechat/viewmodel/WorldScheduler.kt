package com.rhodes.privatechat.viewmodel

import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.model.WorldEvent
import com.rhodes.privatechat.shared.model.WorldEventType
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.util.DebugLogger
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class WorldScheduler(
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    private val appState: AppStateHolder,
    private val scope: CoroutineScope,
    private val generateOneMoment: suspend (WorldEvent?) -> Boolean,
    private val triggerEventGroups: (WorldEvent?) -> Boolean,
    private val generateDiary: suspend (String) -> Boolean,
    private val triggerProactivePrivate: suspend (WorldEvent) -> Boolean = { false },
    private val canUseWorldTrigger: (String) -> Boolean = { true },
    private val consumeWorldTrigger: (String) -> Unit = { }
) {
    fun tick() {
        if (!settings.autoAiEnabled) return
        if (!settings.worldSchedulerEnabled) return
        scope.launch {
            try {
                cleanupExpiredEvents()
                maybeTriggerMoment()
                maybeRefreshAutoGroups()
                maybeTriggerProactivePrivateEvents()
                maybeGenerateDailyDiaries()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLogger.log("World/Scheduler", "调度失败: ${e.message?.take(100)}")
            }
        }
    }

    private suspend fun cleanupExpiredEvents() {
        val days = settings.cleanDaysWorldEvents
        if (days > 0) repository.deleteExpiredWorldEvents(System.currentTimeMillis() - days * 86_400_000L)
    }

    private suspend fun maybeTriggerMoment() {
        if (!settings.autoMomentEnabled || settings.dailyMomentTarget <= 0) return
        val todayStart = todayStartMillis()
        val todayCount = repository.countChainedWorldEventsByTypeSince(WorldEventType.MOMENT_POSTED, todayStart)
        if (todayCount >= settings.dailyWorldEventLimit) return
        val recent = repository.getUnconsumedWorldEventsByType(WorldEventType.COMMENT_POSTED, "world:moment", 5) +
            repository.getUnconsumedWorldEventsByType(WorldEventType.GROUP_TOPIC, "world:moment", 5) +
            repository.getUnconsumedWorldEventsByType(WorldEventType.STATUS_CHANGED, "world:moment", 5)
        val hasSeed = recent.any { it.type == WorldEventType.COMMENT_POSTED || it.type == WorldEventType.GROUP_TOPIC || it.type == WorldEventType.STATUS_CHANGED }
        val chance = settings.momentTriggerStrength.coerceIn(0, 100)
        if (hasSeed && (0..99).random() < chance) {
            val seed = recent.firstOrNull()
            if (seed != null && seed.chainDepth >= 3) return
            if (!canUseWorldTrigger("event_moment")) return
            DebugLogger.log("World/Scheduler", "触发动态: seed=${seed?.type ?: "none"}")
            if (generateOneMoment(seed)) {
                consumeWorldTrigger("event_moment")
                recent.take(settings.eventContextCount).forEach { repository.markWorldEventConsumed(it.id, "world:moment") }
            }
        }
    }

    private suspend fun maybeRefreshAutoGroups() {
        if (!settings.worldAutoGroupEnabled) return
        val seed = repository.getRecentWorldEvents(8).firstOrNull { it.type == WorldEventType.MOMENT_POSTED || it.type == WorldEventType.COMMENT_POSTED }
        if (seed != null && (0..99).random() < settings.groupTriggerStrength.coerceIn(0, 100)) {
            if (seed.chainDepth >= 3) return
            if (!canUseWorldTrigger("event_group")) return
            DebugLogger.log("World/Scheduler", "事件唤起群聊: recentTopics=true groups=${appState.sessions.value.count { it.operatorId.startsWith("group_") }}")
            if (triggerEventGroups(seed)) consumeWorldTrigger("event_group")
        }
    }

    private suspend fun maybeTriggerProactivePrivateEvents() {
        if (!settings.worldProactiveChatEnabled || settings.dailyProactiveLimit <= 0) return
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
        }.format(java.util.Date())
        val sentKey = "world_private_trigger_sent_$today"
        var sentToday = settings.getInt(sentKey, 0)
        if (sentToday >= settings.dailyProactiveLimit) return
        val triggers = repository.getWorldEventsByType(WorldEventType.PRIVATE_TRIGGER, settings.dailyProactiveLimit)
            .filter { isToday(it.createdAt) && !it.consumedBy.contains("world:private") }
        triggers.forEach { event ->
            if (sentToday >= settings.dailyProactiveLimit) return@forEach
            if (!canUseWorldTrigger("event_private")) return@forEach
            val sent = triggerProactivePrivate(event)
            if (sent) {
                consumeWorldTrigger("event_private")
                sentToday += 1
                settings.putInt(sentKey, sentToday)
                repository.markWorldEventConsumed(event.id, "world:private")
                DebugLogger.log("World/Scheduler", "主动私聊事件已发送: ${event.content.take(80)}")
            } else {
                DebugLogger.log("World/Scheduler", "主动私聊事件等待重试: ${event.content.take(80)}")
            }
        }
    }

    private fun maybeGenerateDailyDiaries() {
        if (!settings.autoDiaryEnabled || settings.dailyDiaryOperatorLimit <= 0) return
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).apply {
            timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
        }.format(java.util.Date())
        if (settings.getString("world_diary_date", "") == today) return
        scope.launch {
            var success = false
            appState.operators.value
            .sortedByDescending { it.activityLevel }
            .take(settings.dailyDiaryOperatorLimit)
            .forEach { op ->
                DebugLogger.log("World/Scheduler", "自动日记: ${op.name}")
                if (generateDiary(op.id)) success = true
            }
            if (success) settings.putString("world_diary_date", today)
        }
    }

    private fun isToday(time: Long): Boolean {
        return time >= todayStartMillis()
    }

    private fun todayStartMillis(): Long {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
