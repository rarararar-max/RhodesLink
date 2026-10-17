package com.rhodes.privatechat.automation

import android.app.Application
import android.content.Context
import androidx.work.*
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.MainViewModel
import com.rhodes.privatechat.viewmodel.shared.*
import com.rhodes.privatechat.data.backup.BackupRestoreMaintenance
import org.koin.java.KoinJavaComponent.get
import java.util.concurrent.TimeUnit

object ContextMaintenanceScheduler {
    fun schedule(context: Context, sessionId: String, group: Boolean, at: Long = System.currentTimeMillis()) {
        val request = OneTimeWorkRequestBuilder<ContextMaintenanceWorker>()
            .setInitialDelay((at - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf("session" to sessionId, "group" to group)).build()
        WorkManager.getInstance(context).enqueueUniqueWork("context-maintenance-$sessionId", ExistingWorkPolicy.KEEP, request)
    }
}

class ContextMaintenanceWorker(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (BackupRestoreMaintenance.active) return Result.retry()
        val id = inputData.getString("session") ?: return Result.success()
        val group = inputData.getBoolean("group", false)
        val repository = get<ChatRepository>(ChatRepository::class.java)
        val session = repository.getSession(id) ?: return Result.success()
        val settings = get<SettingsRepository>(SettingsRepository::class.java)
        val vm = MainViewModel(applicationContext as Application, repository, settings,
            get<AppStateHolder>(AppStateHolder::class.java), get<SharedUtils>(SharedUtils::class.java),
            get<OperatorStateUpdater>(OperatorStateUpdater::class.java), startBackgroundWork = false)
        return try {
            val done = if (group) vm.groupChatViewModel.runContextMaintenance(session)
                else vm.chatViewModel.runContextMaintenance(session)
            if (done) Result.success() else Result.retry()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}
