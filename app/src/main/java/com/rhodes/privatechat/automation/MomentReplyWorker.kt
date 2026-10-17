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

object MomentReplyScheduler {
    fun schedule(context: Context, id: String, at: Long = 0L) {
        val request = OneTimeWorkRequestBuilder<MomentReplyWorker>()
            .setInputData(workDataOf("turn" to id))
            .setInitialDelay((at - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("moment-reply-$id", ExistingWorkPolicy.KEEP, request)
    }
}

class MomentReplyWorker(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (BackupRestoreMaintenance.active) return Result.retry()
        val id = inputData.getString("turn") ?: return Result.success()
        val repository = get<ChatRepository>(ChatRepository::class.java)
        val vm = MainViewModel(applicationContext as Application, repository, get<SettingsRepository>(SettingsRepository::class.java),
            get<AppStateHolder>(AppStateHolder::class.java), get<SharedUtils>(SharedUtils::class.java),
            get<OperatorStateUpdater>(OperatorStateUpdater::class.java), startBackgroundWork = false)
        return if (vm.executeMomentReplyTask(id)) Result.success() else Result.retry()
    }
}
