package com.rhodes.privatechat.automation

import android.app.Application
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.MainViewModel
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.viewmodel.shared.OperatorStateUpdater
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.rhodes.privatechat.data.backup.BackupRestoreMaintenance
import org.koin.java.KoinJavaComponent.get

class GroupAutoChatWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            if (BackupRestoreMaintenance.active) return Result.success()
            val groupId = inputData.getString("groupId") ?: return Result.success()
            val token = inputData.getString("token") ?: return Result.success()
            val repository = get<ChatRepository>(ChatRepository::class.java)
            val settings = get<SettingsRepository>(SettingsRepository::class.java)
            // Validate and claim before constructing a request, so stale or foreground-consumed work is inert.
            val viewModel = MainViewModel(
                applicationContext as Application, repository, settings,
                get<AppStateHolder>(AppStateHolder::class.java), get<SharedUtils>(SharedUtils::class.java),
                get<OperatorStateUpdater>(OperatorStateUpdater::class.java), startBackgroundWork = false
            )
            viewModel.groupChatViewModel.runScheduledAutoTurn(groupId, token)
            if (BackupRestoreMaintenance.active) return Result.success()
            Result.success()
        } catch (_: Exception) {
            // A failure can happen after the durable plan is claimed. Recreate a pending plan so
            // WorkManager backoff or the next reconcile is not blocked by the claimed marker.
            runCatching {
                val groupId = inputData.getString("groupId")
                if (!groupId.isNullOrBlank()) {
                    GroupAutoChatScheduler.ensurePlan(
                        applicationContext,
                        get<SettingsRepository>(SettingsRepository::class.java),
                        groupId
                    )
                }
            }
            Result.retry()
        }
    }
}
