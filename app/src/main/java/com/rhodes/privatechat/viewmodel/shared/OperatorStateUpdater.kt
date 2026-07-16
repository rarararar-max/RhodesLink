package com.rhodes.privatechat.viewmodel.shared

import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.shared.model.WorldEvent
import com.rhodes.privatechat.shared.model.WorldEventType
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository

class OperatorStateUpdater(
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    private val sharedUtils: SharedUtils,
    private val operatorsProvider: () -> List<Operator>
) {
    suspend fun updateOperatorStatus(
        operatorId: String,
        location: String,
        activity: String,
        emotion: String,
        onStatusUpdated: ((String, String, String, String) -> Unit)? = null
    ) {
        val op = repository.getOperator(operatorId) ?: return
        val newLoc = location.ifBlank { op.location }
        val newAct = activity.ifBlank { op.activity }
        val newEmo = emotion.ifBlank { op.emotion }
        repository.updateOperator(op.copy(location = newLoc, activity = newAct, emotion = newEmo))
        repository.insertWorldEvent(WorldEvent(
            type = WorldEventType.STATUS_CHANGED,
            actorId = operatorId,
            actorName = op.name,
            source = "status",
            sourceId = operatorId,
            content = "${op.name}现在在${newLoc}，正在${newAct}，情绪${newEmo}",
            createdAt = System.currentTimeMillis(),
            expiresAt = MemoryPolicy.memoryExpiresAt(settings)
        ))
        onStatusUpdated?.invoke(operatorId, newLoc, newAct, newEmo)
        if (newLoc != op.location && newLoc.isNotBlank()) {
            notifyNearbyObservers(listOf(operatorId))
        }
    }

    suspend fun notifyNearbyObservers(movedOpIds: List<String>) {
        val allOps = operatorsProvider()
        for (movedId in movedOpIds) {
            val moved = allOps.find { it.id == movedId } ?: continue
            for (observer in allOps) {
                if (observer.id == movedId) continue
                if (observer.location == moved.location) {
                    // Live state is already available from the operator record.  Do not turn
                    // every movement into a second, competing memory representation.
                }
            }
        }
    }

    suspend fun updateOperatorIntimacy(operatorId: String, delta: Int) {
        val op = repository.getOperator(operatorId) ?: return
        val today = sharedUtils.beijingSdf("yyyyMMdd").format(java.util.Date())
        val lastDate = settings.getString("intimacy_date_$operatorId", "")
        val dailyTotal = if (today == lastDate) settings.getInt("intimacy_daily_$operatorId", 0) else 0
        val dailyCap = settings.dailyIntimacyCap.coerceIn(1, 20)
        val clamped = (dailyTotal + delta).coerceIn(-dailyCap, dailyCap)
        val actualDelta = clamped - dailyTotal
        repository.updateIntimacy(operatorId, (op.intimacy + actualDelta).coerceIn(0, 1000))
        settings.putString("intimacy_date_$operatorId", today)
        settings.putInt("intimacy_daily_$operatorId", clamped)
    }
}
