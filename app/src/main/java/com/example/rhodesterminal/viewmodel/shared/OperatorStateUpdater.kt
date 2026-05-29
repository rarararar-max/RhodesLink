package com.example.rhodesterminal.viewmodel.shared

import com.example.rhodesterminal.shared.model.AnchorType
import com.example.rhodesterminal.shared.model.MemoryAnchor
import com.example.rhodesterminal.shared.model.Operator
import com.example.rhodesterminal.data.repository.ChatRepository
import com.example.rhodesterminal.shared.settings.SettingsRepository

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
                    val anchor = MemoryAnchor(
                        sessionId = "nearby_${System.currentTimeMillis()}",
                        operatorId = observer.id,
                        type = AnchorType.EVENT,
                        content = "${moved.name}来到了${moved.location}，正在${moved.activity}，情绪${moved.emotion}",
                        isPrivate = false
                    )
                    repository.saveAnchor(anchor)
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
        repository.updateIntimacy(operatorId, (op.intimacy + actualDelta).coerceIn(0, 100))
        settings.putString("intimacy_date_$operatorId", today)
        settings.putInt("intimacy_daily_$operatorId", clamped)
    }
}
