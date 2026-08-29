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
        var groupId = ""
        var token = ""
        var revision = -1L
        return try {
            if (BackupRestoreMaintenance.active) return Result.success()
            groupId = inputData.getString("groupId") ?: return Result.success()
            token = inputData.getString("token") ?: return Result.success()
            revision = inputData.getLong("revision", -1L)
            if (revision < 0L) return Result.success()
            val repository = get<ChatRepository>(ChatRepository::class.java)
            val settings = get<SettingsRepository>(SettingsRepository::class.java)
            // Validate and claim before constructing a request, so stale or foreground-consumed work is inert.
            val viewModel = MainViewModel(
                applicationContext as Application, repository, settings,
                get<AppStateHolder>(AppStateHolder::class.java), get<SharedUtils>(SharedUtils::class.java),
                get<OperatorStateUpdater>(OperatorStateUpdater::class.java), startBackgroundWork = false
            )
            val executed = viewModel.groupChatViewModel.runScheduledAutoTurn(groupId, token, planRevision = revision)
            val detail = "groupId=$groupId,token=${token.take(8)},revision=$revision,executed=$executed,autoEnabled=${settings.autoAiEnabled},groupEnabled=${settings.getGroupAuto(groupId)}"
            if (executed) com.rhodes.privatechat.util.DebugLogger.log("GroupAuto/WorkerResult", detail)
            else com.rhodes.privatechat.util.DebugLogger.diagnostic("GroupAuto/WorkerSkipped", detail)
            if (BackupRestoreMaintenance.active) return Result.success()
            Result.success()
        } catch (error: Exception) {
            // A failure can happen after the durable plan is claimed. Recreate a pending plan so
            // WorkManager backoff or the next reconcile is not blocked by the claimed marker.
            runCatching {
                if (groupId.isNotBlank() && token.isNotBlank() && revision >= 0L) {
                    GroupAutoChatScheduler.releaseClaimForRetry(
                        applicationContext, get<SettingsRepository>(SettingsRepository::class.java), groupId, token, revision
                    )
                }
            }
            com.rhodes.privatechat.util.DebugLogger.diagnostic("GroupAuto/WorkerFailed", "groupId=$groupId,token=${token.take(8)},revision=$revision,error=${error.javaClass.simpleName}")
            Result.retry()
        }
    }
}
