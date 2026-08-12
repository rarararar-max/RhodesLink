package com.rhodes.privatechat.automation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.app.Application
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.MainViewModel
import com.rhodes.privatechat.viewmodel.ScheduledMomentDeliveryResult
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.viewmodel.shared.OperatorStateUpdater
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.rhodes.privatechat.data.backup.BackupRestoreMaintenance
import org.koin.java.KoinJavaComponent.get

class DailyContentWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            if (BackupRestoreMaintenance.active) return Result.success()
            val repository = get<ChatRepository>(ChatRepository::class.java)
            val settings = get<SettingsRepository>(SettingsRepository::class.java)
            if (inputData.getString("type") == DailyContentScheduler.TYPE_PLAN) {
                DailyContentScheduler.ensureTodayPlan(applicationContext, repository, settings)
                return Result.success()
            }
            DailyContentScheduler.ensureTodayPlan(applicationContext, repository, settings)
            val viewModel = MainViewModel(
                applicationContext as Application, repository, settings,
                get<AppStateHolder>(AppStateHolder::class.java), get<SharedUtils>(SharedUtils::class.java),
                get<OperatorStateUpdater>(OperatorStateUpdater::class.java), startBackgroundWork = false
            )
            val operatorId = inputData.getString("operatorId") ?: return Result.success()
            val deliveryId = inputData.getString("deliveryId") ?: "0"
            val cycle = inputData.getString("cycle") ?: DailyContentScheduler.cycleId()
            // One-time work can be delayed by network constraints or backoff. Daily content must
            // never be delivered into a later Beijing calendar day.
            if (cycle != DailyContentScheduler.cycleId()) return Result.success()
            val result = when (inputData.getString("type")) {
                DailyContentScheduler.TYPE_MOMENT -> viewModel.deliverScheduledMoment(operatorId, cycle, deliveryId)
                DailyContentScheduler.TYPE_PRIVATE -> viewModel.deliverScheduledPrivate(operatorId, cycle)
                else -> ScheduledMomentDeliveryResult.SKIPPED
            }
            if (BackupRestoreMaintenance.active) return Result.success()
            if (result == ScheduledMomentDeliveryResult.RETRYABLE_FAILURE) Result.retry() else Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
